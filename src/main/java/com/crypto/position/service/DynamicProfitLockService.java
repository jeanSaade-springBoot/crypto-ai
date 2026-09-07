package com.crypto.position.service;

import com.crypto.domain.TradeSignal;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.domain.WalletSettings;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.repository.WalletSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class DynamicProfitLockService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 12;

    private final WalletManagedPositionRepository positionRepository;
    private final WalletSettingsRepository settingsRepository;
    private final ProfitLockPolicy profitLockPolicy;

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
     *
     * FIX-118 behavioral phase:
     * TP_EXTENSION_REBASE preserves the historically earned lock in persistence but makes
     * it non-executable until progress against the CURRENT TP reaches the configured
     * activation threshold again. This gives an approved continuation meaningful room
     * without forgetting that profit protection had already been earned.
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

        ProfitLockProfile profile = configuredProfile(settings);
        BigDecimal activation = profile.activationPercent();
        BigDecimal initialLock = profile.initialLockPercent();
        BigDecimal trailStep = profile.trailStepPercent();
        BigDecimal previousHighest = position.getHighestPriceUsdt();
        boolean previousPersistedActive = position.isProfitLockActive();
        BigDecimal previousLock = position.getProfitLockPriceUsdt();
        BigDecimal previousProgress = position.getProfitLockProgressPercent();
        ProfitLockState previousState = normalizedState(position);

        BigDecimal highest = previousHighest == null ? current : previousHighest.max(current);
        BigDecimal progress = progressPercent(entry, target, highest);
        BigDecimal lockPrice = previousLock;
        ProfitLockState state = previousState;
        boolean executableActive;
        boolean triggered;

        if (previousState == ProfitLockState.TP_EXTENSION_REBASE && progress.compareTo(activation) < 0) {
            // Keep the legacy active flag/lock persisted so history is not lost, but do not
            // allow the superseded geometry to trigger an exit during the rebase window.
            executableActive = false;
            triggered = false;
        } else {
            // FIX-118 refinement: when a TP-extension rebase earns activation against the
            // CURRENT target, rebuild the executable lock from the current geometry instead
            // of carrying the historical pre-extension lock through ProfitLockPolicy's
            // monotonic previous-lock rule.
            boolean freshGeometryActivation = previousState == ProfitLockState.TP_EXTENSION_REBASE;
            ProfitLockPolicy.State policyState = profitLockPolicy.evaluate(
                    entry, target, current, highest,
                    freshGeometryActivation ? false : (previousState == ProfitLockState.ACTIVE || previousPersistedActive),
                    freshGeometryActivation ? null : previousLock,
                    true, activation, initialLock, trailStep);
            highest = policyState.highestPrice();
            progress = policyState.progressPercent();
            lockPrice = policyState.lockPrice();
            executableActive = policyState.active();
            triggered = policyState.triggered();
            state = executableActive ? ProfitLockState.ACTIVE : ProfitLockState.INACTIVE;
        }

        // During REBASE the legacy flag intentionally remains true: protection was historically
        // earned. The explicit state is now the sole execution authority.
        boolean persistedActive = state == ProfitLockState.TP_EXTENSION_REBASE
                ? true
                : executableActive;

        Instant activatedAt = position.getProfitLockActivatedAt();
        if (persistedActive && activatedAt == null) activatedAt = Instant.now();
        Instant rebaseStartedAt = state == ProfitLockState.TP_EXTENSION_REBASE
                ? position.getProfitLockRebaseStartedAt()
                : null;

        boolean changed = position.getHighestPriceUsdt() == null
                || highest.compareTo(position.getHighestPriceUsdt()) != 0
                || position.isProfitLockActive() != persistedActive
                || different(position.getProfitLockPriceUsdt(), lockPrice)
                || different(position.getProfitLockProgressPercent(), progress)
                || position.getProfitLockState() != state
                || !java.util.Objects.equals(position.getProfitLockRebaseStartedAt(), rebaseStartedAt)
                || (position.getProfitLockActivatedAt() == null && activatedAt != null);

        if (changed) {
            position.setHighestPriceUsdt(highest);
            position.setProfitLockActive(persistedActive);
            position.setProfitLockPriceUsdt(lockPrice);
            position.setProfitLockProgressPercent(progress);
            position.setProfitLockActivatedAt(activatedAt);
            position.setProfitLockState(state);
            position.setProfitLockRebaseStartedAt(rebaseStartedAt);
            position.setUpdatedAt(Instant.now());
            positionRepository.save(position);
        }

        BigDecimal hardProfitFloor = entry.multiply(BigDecimal.valueOf(1.0005));
        boolean hardFloorTriggered = state == ProfitLockState.TP_EXTENSION_REBASE
                && current.compareTo(hardProfitFloor) < 0;

        if (changed || triggered || hardFloorTriggered || previousState != state) {
            log.info("[FIX-118][PRODUCTION][PROFIT_LOCK_EVAL] positionId={}, symbol={}, current={}, entry={}, target={}, " +
                            "previousHighest={}, highest={}, previousState={}, state={}, previousPersistedActive={}, persistedActive={}, " +
                            "previousLock={}, lock={}, previousProgressPct={}, progressPct={}, activationPct={}, triggered={}, " +
                            "hardFloor={}, hardFloorTriggered={}, persisted={}",
                    position.getId(), symbol, current, entry, target,
                    previousHighest, highest, previousState, state, previousPersistedActive, persistedActive,
                    previousLock, lockPrice, previousProgress, progress, activation, triggered,
                    hardProfitFloor, hardFloorTriggered, changed);
        }

        String profileText = " Admin profile=" + profile.name() +
                " (activation=" + activation.stripTrailingZeros().toPlainString() + "%, initial lock=" +
                initialLock.stripTrailingZeros().toPlainString() + "%, trail=" +
                trailStep.stripTrailingZeros().toPlainString() + "%).";
        String explanation;
        if (hardFloorTriggered) {
            explanation = "TP-extension rebase is active and price " + current
                    + " breached the protected-profit safety floor " + hardProfitFloor + "." + profileText;
        } else if (state == ProfitLockState.TP_EXTENSION_REBASE) {
            explanation = "Profit Lock is rebasing to the extended take-profit geometry; the historical lock "
                    + lockPrice + " remains persisted but is non-executable. Current best progress is "
                    + progress.setScale(2, RoundingMode.HALF_UP) + "% and reactivation starts at "
                    + activation.stripTrailingZeros().toPlainString() + "%." + profileText;
        } else if (triggered) {
            explanation = "Price " + current + " reached the protected profit-lock level " + lockPrice
                    + " after the position had reached " + progress.setScale(2, RoundingMode.HALF_UP)
                    + "% of its take-profit distance." + profileText;
        } else if (executableActive) {
            explanation = "Profit Lock active at " + lockPrice + "; best progress is "
                    + progress.setScale(2, RoundingMode.HALF_UP) + "% of take-profit distance." + profileText;
        } else {
            explanation = "Profit Lock not active yet; best progress is "
                    + progress.setScale(2, RoundingMode.HALF_UP) + "% and activation starts at "
                    + activation.stripTrailingZeros().toPlainString() + "%." + profileText;
        }

        return new Evaluation(true, executableActive, triggered, position.getId(), current, highest,
                progress, lockPrice, activation, state, hardProfitFloor, hardFloorTriggered, explanation);
    }

    /**
     * FIX-120 correction/refinement to FIX-118. Called in the same transaction that persists the new TP.
     * A TP change invalidates the prior executable lock as a calculation baseline. If current
     * best progress remains at/above activation, stay ACTIVE but derive a fresh lock from the
     * new geometry using raw extensionPrice as current and extensionHighest as the historical
     * best seed. If progress falls below activation, preserve the historical lock for audit but
     * make it non-executable in TP_EXTENSION_REBASE until the current geometry requalifies.
     */
    public ExtensionTransition onTakeProfitExtended(WalletManagedPosition position, BigDecimal newTarget, BigDecimal extensionPrice, Instant changedAt) {
        if (position == null || newTarget == null || position.getAverageEntryPriceUsdt() == null) {
            return ExtensionTransition.none();
        }
        ProfitLockState previousState = normalizedState(position);
        BigDecimal oldTarget = position.getTakeProfitUsdt();
        BigDecimal oldLock = position.getProfitLockPriceUsdt();
        BigDecimal extensionHighest = position.getHighestPriceUsdt() == null
                ? position.getAverageEntryPriceUsdt()
                : position.getHighestPriceUsdt();
        if (extensionPrice != null && extensionPrice.compareTo(extensionHighest) > 0) {
            extensionHighest = extensionPrice;
            position.setHighestPriceUsdt(extensionHighest);
        }
        BigDecimal progress = progressPercent(position.getAverageEntryPriceUsdt(), newTarget, extensionHighest);
        BigDecimal activation = configuredProfile(settings()).activationPercent();
        ProfitLockState nextState = previousState;
        // TP changed now, so persist progress against the new geometry immediately even though
        // the extension tick returns before the next normal Profit Lock evaluation.
        position.setProfitLockProgressPercent(progress);

        if (previousState == ProfitLockState.ACTIVE) {
            if (progress.compareTo(activation) < 0) {
                nextState = ProfitLockState.TP_EXTENSION_REBASE;
                position.setProfitLockState(nextState);
                position.setProfitLockRebaseStartedAt(changedAt == null ? Instant.now() : changedAt);
                // Legacy compatibility flag deliberately stays true; ProfitLockState is the sole
                // authority for whether the persisted historical lock may execute.
                position.setProfitLockActive(true);
            } else {
                // FIX-120: the extension still leaves the winner beyond activation, but the old
                // lock belongs to the superseded TP geometry. Establish a fresh geometry baseline.
                // Deliberately pass raw extensionPrice as current and extensionHighest as previous
                // highest: ProfitLockPolicy then sees the real boundary tick plus the authoritative
                // best price, while previousActive/previousLock are intentionally discarded.
                ProfitLockProfile profile = configuredProfile(settings());
                BigDecimal currentForPolicy = extensionPrice == null ? extensionHighest : extensionPrice;
                ProfitLockPolicy.State rebased = profitLockPolicy.evaluate(
                        position.getAverageEntryPriceUsdt(), newTarget, currentForPolicy, extensionHighest,
                        false, null, true, profile.activationPercent(), profile.initialLockPercent(), profile.trailStepPercent());
                nextState = rebased.active() ? ProfitLockState.ACTIVE : ProfitLockState.TP_EXTENSION_REBASE;
                position.setHighestPriceUsdt(rebased.highestPrice());
                position.setProfitLockProgressPercent(rebased.progressPercent());
                position.setProfitLockPriceUsdt(rebased.lockPrice());
                position.setProfitLockState(nextState);
                position.setProfitLockActive(true);
                position.setProfitLockRebaseStartedAt(nextState == ProfitLockState.TP_EXTENSION_REBASE
                        ? (changedAt == null ? Instant.now() : changedAt)
                        : null);
                progress = rebased.progressPercent();
            }
        } else if (previousState == ProfitLockState.TP_EXTENSION_REBASE) {
            // Repeated TP extensions keep the rebase active and refresh progress against the
            // newest geometry without resetting the original rebase start timestamp.
        }

        log.info("[FIX-120][PRODUCTION][TP_GEOMETRY_REBASE] positionId={}, symbol={}, previousState={}, newState={}, " +
                        "oldTarget={}, newTarget={}, extensionPrice={}, highest={}, newGeometryProgressPct={}, oldLock={}, freshLock={}, " +
                        "activationPct={}, geometryRebased={}",
                position.getId(), position.getSymbol(), previousState, nextState, oldTarget, newTarget,
                extensionPrice, position.getHighestPriceUsdt(), progress,
                oldLock, position.getProfitLockPriceUsdt(),
                activation, previousState == ProfitLockState.ACTIVE);

        return new ExtensionTransition(previousState, nextState, progress, activation);
    }

    private ProfitLockState normalizedState(WalletManagedPosition position) {
        if (position.getProfitLockState() != null) return position.getProfitLockState();
        return position.isProfitLockActive() ? ProfitLockState.ACTIVE : ProfitLockState.INACTIVE;
    }

    private BigDecimal progressPercent(BigDecimal entry, BigDecimal target, BigDecimal highest) {
        if (entry == null || target == null || highest == null || target.compareTo(entry) <= 0) return BigDecimal.ZERO;
        BigDecimal favorable = highest.subtract(entry);
        if (favorable.signum() < 0) favorable = BigDecimal.ZERO;
        return favorable.multiply(HUNDRED)
                .divide(target.subtract(entry), 6, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public boolean isActive(String symbol) {
        if (symbol == null || symbol.isBlank()) return false;
        return positionRepository.findTopBySymbolAndStatusOrderByOpenedAtDesc(
                        symbol.trim().toUpperCase(Locale.ROOT), "OPEN")
                .map(p -> normalizedState(p) == ProfitLockState.ACTIVE)
                .orElse(false);
    }


    /**
     * Administration is the single source of truth for Dynamic Profit Lock.
     * Entry quality must never silently replace the percentages configured by the user.
     */
    private ProfitLockProfile configuredProfile(WalletSettings settings) {
        return new ProfitLockProfile(
                "ADMIN_CONFIG",
                nvl(settings.getProfitLockActivationPercent(), BigDecimal.valueOf(70)),
                nvl(settings.getProfitLockInitialPercent(), BigDecimal.valueOf(40)),
                nvl(settings.getProfitLockTrailStepPercent(), BigDecimal.valueOf(10))
        );
    }

    private record ProfitLockProfile(
            String name,
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

    public record ExtensionTransition(
            ProfitLockState previousState,
            ProfitLockState state,
            BigDecimal progressPercent,
            BigDecimal activationPercent
    ) {
        static ExtensionTransition none() {
            return new ExtensionTransition(ProfitLockState.INACTIVE, ProfitLockState.INACTIVE,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }
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
            ProfitLockState state,
            BigDecimal hardProfitFloor,
            boolean hardProfitFloorTriggered,
            String explanation
    ) {
        public boolean rebasing() {
            return state == ProfitLockState.TP_EXTENSION_REBASE;
        }

        public static Evaluation inactive(String explanation) {
            return new Evaluation(false, false, false, null, null, null,
                    BigDecimal.ZERO, null, null, ProfitLockState.INACTIVE, null, false, explanation);
        }
    }
}
