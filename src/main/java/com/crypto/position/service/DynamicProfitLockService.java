package com.crypto.position.service;

import com.crypto.domain.TradeSignal;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.domain.WalletSettings;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.repository.WalletSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;

/**
 * Position-manager profit protection for already-open long positions.
 *
 * The BUY/SELL analysis engine is deliberately not involved here. The lock is
 * derived only from the immutable entry price/take-profit stored on the wallet
 * position and the best price observed after entry.
 *
 * Adaptive policy:
 *  - derive an entry-quality score from the immutable entry signal score and confidence;
 *  - stronger setups receive more room before protection activates;
 *  - weaker/medium setups protect profits earlier;
 *  - the protected price can only move upward and never loosens after activation.
 *
 * Administration values remain the fallback when entry-quality information is unavailable.
 */
@Service
@RequiredArgsConstructor
public class DynamicProfitLockService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 12;

    private final WalletManagedPositionRepository positionRepository;
    private final WalletSettingsRepository settingsRepository;

    @Transactional
    public Evaluation evaluate(TradeSignal signal) {
        if (signal == null) {
            return Evaluation.inactive("No valid market price is available.");
        }
        return evaluatePrice(signal.getSymbol(), signal.getLatestPrice());
    }

    /**
     * Evaluates Dynamic Profit Lock directly from a live market price.
     * This path is used by live position protection between candle-close signals.
     */
    @Transactional
    public Evaluation evaluatePrice(String symbolValue, BigDecimal currentPrice) {
        if (symbolValue == null || symbolValue.isBlank() || currentPrice == null
                || currentPrice.signum() <= 0) {
            return Evaluation.inactive("No valid market price is available.");
        }

        String symbol = symbolValue.trim().toUpperCase(Locale.ROOT);
        WalletManagedPosition position = positionRepository
                .findFirstBySymbolAndStatusOrderByOpenedAtDesc(symbol, "OPEN")
                .orElse(null);
        if (position == null) {
            return Evaluation.inactive("No open wallet position exists.");
        }

        WalletSettings settings = settings();
        if (!settings.isDynamicProfitLockEnabled()) {
            return Evaluation.inactive("Dynamic Profit Lock is disabled in Administration.");
        }

        BigDecimal entry = position.getAverageEntryPriceUsdt();
        BigDecimal target = position.getTakeProfitUsdt();
        BigDecimal current = currentPrice;
        if (entry == null || target == null || entry.signum() <= 0 || target.compareTo(entry) <= 0) {
            return Evaluation.inactive("The position does not have a valid long take-profit distance.");
        }

        BigDecimal highest = position.getHighestPriceUsdt();
        if (highest == null || highest.compareTo(entry) < 0) highest = entry;
        if (current.compareTo(highest) > 0) highest = current;

        BigDecimal targetDistance = target.subtract(entry);
        BigDecimal progress = highest.subtract(entry)
                .multiply(HUNDRED)
                .divide(targetDistance, 6, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);

        ProfitLockProfile profile = adaptiveProfile(position, settings);
        BigDecimal activation = profile.activationPercent();
        BigDecimal initialLock = profile.initialLockPercent();
        BigDecimal trailStep = profile.trailStepPercent();

        boolean active = position.isProfitLockActive();
        BigDecimal lockPrice = position.getProfitLockPriceUsdt();
        Instant activatedAt = position.getProfitLockActivatedAt();

        if (progress.compareTo(activation) >= 0) {
            active = true;
            if (activatedAt == null) activatedAt = Instant.now();

            BigDecimal progressBeyondActivation = progress.subtract(activation).max(BigDecimal.ZERO);
            BigDecimal completedSteps = progressBeyondActivation
                    .divide(trailStep, 0, RoundingMode.DOWN);
            BigDecimal lockedProgress = initialLock.add(completedSteps.multiply(trailStep));

            // Never set a lock at/above the best observed progress. Preserve at least
            // one configured trail step of breathing room where possible.
            BigDecimal maximumLockProgress = progress.subtract(trailStep).max(initialLock);
            lockedProgress = lockedProgress.min(maximumLockProgress).max(initialLock);

            BigDecimal candidateLock = entry.add(targetDistance
                    .multiply(lockedProgress)
                    .divide(HUNDRED, SCALE, RoundingMode.HALF_UP));
            if (lockPrice == null || candidateLock.compareTo(lockPrice) > 0) {
                lockPrice = candidateLock;
            }
        }

        boolean changed = position.getHighestPriceUsdt() == null
                || highest.compareTo(position.getHighestPriceUsdt()) != 0
                || position.isProfitLockActive() != active
                || different(position.getProfitLockPriceUsdt(), lockPrice)
                || different(position.getProfitLockProgressPercent(), progress)
                || (position.getProfitLockActivatedAt() == null && activatedAt != null);

        if (changed) {
            position.setHighestPriceUsdt(highest);
            position.setProfitLockActive(active);
            position.setProfitLockPriceUsdt(lockPrice);
            position.setProfitLockProgressPercent(progress);
            position.setProfitLockActivatedAt(activatedAt);
            position.setUpdatedAt(Instant.now());
            positionRepository.save(position);
        }

        boolean triggered = active && lockPrice != null && current.compareTo(lockPrice) <= 0;
        String explanation;
        String profileText = " Adaptive profile=" + profile.name() +
                " (entry quality=" + profile.entryQuality() + "/100, activation=" +
                activation.stripTrailingZeros().toPlainString() + "%, initial lock=" +
                initialLock.stripTrailingZeros().toPlainString() + "%, trail=" +
                trailStep.stripTrailingZeros().toPlainString() + "%).";
        if (triggered) {
            explanation = "Price " + current + " reached the protected profit-lock level " + lockPrice
                    + " after the position had reached " + progress.setScale(2, RoundingMode.HALF_UP)
                    + "% of its take-profit distance." + profileText;
        } else if (active) {
            explanation = "Profit Lock active at " + lockPrice + "; best progress is "
                    + progress.setScale(2, RoundingMode.HALF_UP) + "% of take-profit distance." + profileText;
        } else {
            explanation = "Profit Lock not active yet; best progress is "
                    + progress.setScale(2, RoundingMode.HALF_UP) + "% and activation starts at "
                    + activation.stripTrailingZeros().toPlainString() + "%." + profileText;
        }

        return new Evaluation(true, active, triggered, position.getId(), current, highest,
                progress, lockPrice, activation, explanation);
    }

    @Transactional(readOnly = true)
    public boolean isActive(String symbol) {
        if (symbol == null || symbol.isBlank()) return false;
        return positionRepository.findTopBySymbolAndStatusOrderByOpenedAtDesc(
                        symbol.trim().toUpperCase(Locale.ROOT), "OPEN")
                .map(WalletManagedPosition::isProfitLockActive)
                .orElse(false);
    }


    /**
     * Converts immutable entry quality into a profit-protection profile.
     * Quality is intentionally based on both normalized signal score and confidence:
     *   55% signal score + 45% confidence.
     *
     * High-quality trades receive more room to run; medium/low-quality trades protect
     * gains earlier because their continuation probability is lower.
     */
    private ProfitLockProfile adaptiveProfile(WalletManagedPosition position, WalletSettings settings) {
        Integer score = position.getEntryTotalScore();
        Integer confidence = position.getEntryConfidence();
        if (score == null || confidence == null || score <= 0 || confidence <= 0) {
            return new ProfitLockProfile(
                    "CONFIG_FALLBACK",
                    0,
                    nvl(settings.getProfitLockActivationPercent(), BigDecimal.valueOf(70)),
                    nvl(settings.getProfitLockInitialPercent(), BigDecimal.valueOf(40)),
                    nvl(settings.getProfitLockTrailStepPercent(), BigDecimal.valueOf(10))
            );
        }

        int boundedScore = Math.max(0, Math.min(100, score));
        int boundedConfidence = Math.max(0, Math.min(100, confidence));
        int quality = (int) Math.round((boundedScore * 0.55d) + (boundedConfidence * 0.45d));

        if (quality >= 85) {
            return new ProfitLockProfile("HIGH_CONVICTION", quality,
                    BigDecimal.valueOf(75), BigDecimal.valueOf(45), BigDecimal.valueOf(10));
        }
        if (quality >= 80) {
            return new ProfitLockProfile("STRONG", quality,
                    BigDecimal.valueOf(60), BigDecimal.valueOf(35), BigDecimal.valueOf(10));
        }
        if (quality >= 75) {
            return new ProfitLockProfile("BALANCED", quality,
                    BigDecimal.valueOf(40), BigDecimal.valueOf(20), BigDecimal.valueOf(10));
        }
        if (quality >= 70) {
            return new ProfitLockProfile("CAUTIOUS", quality,
                    BigDecimal.valueOf(35), BigDecimal.valueOf(15), BigDecimal.valueOf(5));
        }
        return new ProfitLockProfile("DEFENSIVE", quality,
                BigDecimal.valueOf(30), BigDecimal.valueOf(10), BigDecimal.valueOf(5));
    }

    private record ProfitLockProfile(
            String name,
            int entryQuality,
            BigDecimal activationPercent,
            BigDecimal initialLockPercent,
            BigDecimal trailStepPercent
    ) {}

    private WalletSettings settings() {
        return settingsRepository.findById(1L).orElseGet(() -> settingsRepository.save(
                WalletSettings.builder()
                        .id(1L)
                        .baseTradeAmountUsdt(BigDecimal.valueOf(100))
                        .minimumUsdtReserve(BigDecimal.ZERO)
                        .maximumDailyNewPositions(0)
                        .performanceWindowType("LAST_TRADES")
                        .performanceTradeCount(20)
                        .performancePeriodDays(1)
                        .dashboardIntervals("1m,5m,1h,4h,1d")
                        .requireNewBuyTransition(true)
                        .executionProfile("BALANCED")
                        .dynamicProfitLockEnabled(true)
                        .profitLockActivationPercent(BigDecimal.valueOf(70))
                        .profitLockInitialPercent(BigDecimal.valueOf(40))
                        .profitLockTrailStepPercent(BigDecimal.valueOf(10))
                        .updatedAt(Instant.now())
                        .build()));
    }

    private BigDecimal nvl(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private boolean different(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return a.compareTo(b) != 0;
    }

    public record Evaluation(
            boolean available,
            boolean active,
            boolean triggered,
            Long walletPositionId,
            BigDecimal currentPrice,
            BigDecimal highestPrice,
            BigDecimal progressPercent,
            BigDecimal lockPrice,
            BigDecimal activationPercent,
            String explanation
    ) {
        public static Evaluation inactive(String explanation) {
            return new Evaluation(false, false, false, null, null, null,
                    BigDecimal.ZERO, null, null, explanation);
        }
    }
}
