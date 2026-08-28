package com.crypto.service;

import com.crypto.domain.BtcContextStatus;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.execution.service.ExecutionReplayScope;
import com.crypto.execution.service.EntryConsumptionPolicy;
import com.crypto.execution.domain.EntryConsumptionState;
import com.crypto.wallet.domain.ExecutionProfile;
import com.crypto.wallet.domain.WalletSettings;
import com.crypto.wallet.repository.WalletSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Execution-only multi-timeframe validation.
 *
 * Frozen responsibilities:
 *  - 1m: the only normal execution timeframe
 *  - 5m: tactical confirmation
 *  - 1h: strategic filter/veto
 *
 * Execution profiles affect only entry eligibility and position percentage.
 * They never modify indicators, scores, or FinalDecisionService output.
 */
@Service
@RequiredArgsConstructor
public class TradeExecutionValidationService {

    private static final String EXECUTION_INTERVAL = "1m";
    private static final String CONFIRMATION_INTERVAL = "5m";
    private static final String TREND_INTERVAL = "1h";
    private static final Duration ONE_MINUTE_TRANSITION_MAX_AGE = Duration.ofMinutes(10);
    private static final Duration FIVE_MINUTE_MAX_AGE = Duration.ofMinutes(20);
    private static final Duration ONE_HOUR_MAX_AGE = Duration.ofHours(3);

    private final TradeSignalRepository signalRepository;
    private final WalletSettingsRepository settingsRepository;
    private final EntryConsumptionPolicy entryConsumptionPolicy;
    @Autowired(required = false)
    private ExecutionReplayScope replayScope;

    public ValidationResult validateBuy(TradeSignal executionSignal, int entryQualityScore) {
        ValidationResult structural = validateBaseSignal(executionSignal, true);
        if (!structural.allowed()) return structural;

        WalletSettings settings = settings();
        if (settings.isRequireNewBuyTransition()) {
            TradeSignal previous = previousSignal(executionSignal.getSymbol(), EXECUTION_INTERVAL, executionSignal.getGeneratedAt()).orElse(null);
            // FIX-112A: a bullish label alone is not a consumed entry. Only keep
            // BUY_CONTINUATION protection when that exact previous signal has an
            // executed BUY (Production) or executed shadow BUY (Replay).
            if (isFresh(previous, executionSignal.getGeneratedAt(), ONE_MINUTE_TRANSITION_MAX_AGE)
                    && isBullish(previous.getDecision())
                    && entryConsumptionPolicy.resolve(previous.getId()) != EntryConsumptionState.NOT_CONSUMED) {
                return ValidationResult.reject("BUY_CONTINUATION",
                        "BUY was not executed because a recent bullish opportunity for this symbol was already consumed. "
                                + "A new WATCH/NEUTRAL/SELL -> BUY transition is required.");
            }
        }

        TradeSignal fiveMinute = latestAtOrBefore(executionSignal, CONFIRMATION_INTERVAL);
        if (!isFresh(fiveMinute, executionSignal.getGeneratedAt(), FIVE_MINUTE_MAX_AGE)) {
            return ValidationResult.reject("MISSING_5M_CONFIRMATION",
                    "No fresh 5m confirmation was available for the 1m BUY signal.");
        }

        TradeSignal oneHour = latestAtOrBefore(executionSignal, TREND_INTERVAL);
        if (!isFresh(oneHour, executionSignal.getGeneratedAt(), ONE_HOUR_MAX_AGE)) {
            return ValidationResult.reject("MISSING_1H_CONTEXT",
                    "No fresh 1h strategic context was available for the 1m BUY signal.");
        }

        if (isBearish(fiveMinute.getDecision())) {
            return ValidationResult.reject("5M_BEARISH_VETO",
                    "1m BUY was blocked because the fresh 5m decision was " + fiveMinute.getDecision() + ".");
        }
        if (isBearish(oneHour.getDecision())) {
            return ValidationResult.reject("1H_BEARISH_VETO",
                    "1m BUY was blocked because the fresh 1h strategic decision was " + oneHour.getDecision() + ".");
        }

        ExecutionProfile profile = ExecutionProfile.from(settings.getExecutionProfile());
        return switch (profile) {
            case CONSERVATIVE -> conservativeBuy(fiveMinute, oneHour);
            // FIX-112B: only the fresh direct-BUY BALANCED path receives the narrow
            // 5m-NEUTRAL exception. validateBuyContext() and AGGRESSIVE fallback keep
            // calling balancedBuy() directly so their proven Production behavior is unchanged.
            case BALANCED -> balancedBuyWithNeutralFiveException(
                    executionSignal, fiveMinute, oneHour, entryQualityScore);
            case AGGRESSIVE -> aggressiveBuy(executionSignal, fiveMinute, oneHour);
        };
    }

    /**
     * FIX-021 / accumulated-evidence authority parity:
     * Validate only the current 5m/1h execution authority using the same configured
     * execution profile as a normal direct BUY. This intentionally skips the 1m BUY
     * transition requirement because accumulated evidence may be triggered by a WATCH,
     * but it must never be allowed to use weaker higher-timeframe authority than the
     * normal BUY path would accept.
     */
    public ValidationResult validateBuyContext(TradeSignal referenceSignal) {
        if (referenceSignal == null || referenceSignal.getGeneratedAt() == null) {
            return ValidationResult.reject("INVALID_SIGNAL", "Execution reference signal is missing required timing data.");
        }

        TradeSignal fiveMinute = latestAtOrBefore(referenceSignal, CONFIRMATION_INTERVAL);
        if (!isFresh(fiveMinute, referenceSignal.getGeneratedAt(), FIVE_MINUTE_MAX_AGE)) {
            return ValidationResult.reject("MISSING_5M_CONFIRMATION",
                    "No fresh 5m confirmation was available for accumulated-evidence authority.");
        }

        TradeSignal oneHour = latestAtOrBefore(referenceSignal, TREND_INTERVAL);
        if (!isFresh(oneHour, referenceSignal.getGeneratedAt(), ONE_HOUR_MAX_AGE)) {
            return ValidationResult.reject("MISSING_1H_CONTEXT",
                    "No fresh 1h strategic context was available for accumulated-evidence authority.");
        }

        if (isBearish(fiveMinute.getDecision())) {
            return ValidationResult.reject("5M_BEARISH_VETO",
                    "Accumulated evidence was blocked because fresh 5m context is " + fiveMinute.getDecision() + ".");
        }
        if (isBearish(oneHour.getDecision())) {
            return ValidationResult.reject("1H_BEARISH_VETO",
                    "Accumulated evidence was blocked because fresh 1h context is " + oneHour.getDecision() + ".");
        }

        ExecutionProfile profile = ExecutionProfile.from(settings().getExecutionProfile());
        TradeSignal oneMinuteQuality = latestAtOrBefore(referenceSignal, EXECUTION_INTERVAL);
        TradeSignal qualitySignal = oneMinuteQuality == null ? referenceSignal : oneMinuteQuality;
        return switch (profile) {
            case CONSERVATIVE -> conservativeBuy(fiveMinute, oneHour);
            case BALANCED -> balancedBuy(fiveMinute, oneHour);
            case AGGRESSIVE -> aggressiveBuy(qualitySignal, fiveMinute, oneHour);
        };
    }

    public ValidationResult validateSell(TradeSignal executionSignal) {
        ValidationResult base = validateBaseSignal(executionSignal, false);
        if (!base.allowed()) return base;

        TradeSignal fiveMinute = latestAtOrBefore(executionSignal, CONFIRMATION_INTERVAL);
        if (!isFresh(fiveMinute, executionSignal.getGeneratedAt(), FIVE_MINUTE_MAX_AGE)) {
            return ValidationResult.reject("MISSING_5M_CONFIRMATION",
                    "No fresh 5m confirmation was available for the 1m SELL signal.");
        }
        if (!isBearish(fiveMinute.getDecision())) {
            return ValidationResult.reject("5M_NOT_BEARISH",
                    "1m SELL was blocked because the fresh 5m decision was " + fiveMinute.getDecision() + ".");
        }

        TradeSignal oneHour = latestAtOrBefore(executionSignal, TREND_INTERVAL);
        if (!isFresh(oneHour, executionSignal.getGeneratedAt(), ONE_HOUR_MAX_AGE)) {
            return ValidationResult.reject("MISSING_1H_CONTEXT",
                    "No fresh 1h strategic context was available for the 1m SELL signal.");
        }
        if (isBullish(oneHour.getDecision())) {
            return ValidationResult.reject("1H_BULLISH_VETO",
                    "1m SELL was blocked because the fresh 1h strategic decision was " + oneHour.getDecision() + ".");
        }

        return ValidationResult.allow(100, "SELL_CONFIRMED",
                "1m SELL confirmed by fresh bearish 5m context; 1h is not bullish.");
    }

    private ValidationResult conservativeBuy(TradeSignal fiveMinute, TradeSignal oneHour) {
        if (!isBullish(fiveMinute.getDecision())) {
            return ValidationResult.reject("5M_NOT_BULLISH",
                    "Conservative profile requires 5m BUY/STRONG_BUY; fresh 5m is " + fiveMinute.getDecision() + ".");
        }
        return ValidationResult.allow(100, "CONSERVATIVE_FULL",
                "Conservative profile approved a full-size entry: 1m BUY, bullish 5m confirmation, and non-bearish 1h context.");
    }

    /**
     * FIX-112B / Production behavior change:
     * 5m NEUTRAL means short-term confirmation is absent, but it is not bearish
     * opposition. After the existing BALANCED rules reject, allow only a small
     * exploratory entry when the fresh 1m BUY is high-confidence, 1h is genuinely
     * bullish, independent Entry Quality is strong, and BTC has no conflict state.
     *
     * Golden rule: Replay = Production. This method lives in the shared Production
     * validation service and Replay must call this exact path with historical inputs;
     * there is no Replay-only copy, threshold, or behavior fork.
     *
     * balancedBuy() intentionally remains unchanged. Existing BALANCED_FULL,
     * BALANCED_STRONG and BALANCED_EARLY authority always wins before this exception.
     */
    private ValidationResult balancedBuyWithNeutralFiveException(
            TradeSignal executionSignal, TradeSignal fiveMinute, TradeSignal oneHour, int entryQualityScore) {
        ValidationResult balanced = balancedBuy(fiveMinute, oneHour);
        if (balanced.allowed()) return balanced;

        boolean neutralFiveBullishOne = fiveMinute.getDecision() == SignalDecision.NEUTRAL
                && isBullish(oneHour.getDecision());
        BtcContextStatus btcStatus = executionSignal.getBtcContextStatus();
        // FIX-112B: plain/strong BTC conflict cannot be rescued by this exception.
        // Other BTC states retain their existing upstream Production semantics.
        boolean btcSafeForException = btcStatus != BtcContextStatus.CONFLICT
                && btcStatus != BtcContextStatus.STRONG_CONFLICT;
        boolean strongEnough = executionSignal.getConfidenceScore() >= 72
                && entryQualityScore >= 70
                && btcSafeForException;

        if (neutralFiveBullishOne && strongEnough) {
            return ValidationResult.allow(25, "BALANCED_NEUTRAL_5M",
                    "BALANCED_NEUTRAL_5M: 25% exploratory entry approved because 5m is NEUTRAL "
                            + "rather than bearish while 1h remains " + oneHour.getDecision()
                            + ". 1m=" + executionSignal.getDecision()
                            + ", confidence=" + executionSignal.getConfidenceScore()
                            + ", 5m=NEUTRAL, 1h=" + oneHour.getDecision()
                            + ", entryQuality=" + entryQualityScore
                            + ", btcStatus=" + btcStatus
                            + ", position=25%.");
        }
        return balanced;
    }

    private ValidationResult balancedBuy(TradeSignal fiveMinute, TradeSignal oneHour) {
        SignalDecision five = fiveMinute.getDecision();
        SignalDecision one = oneHour.getDecision();

        if (isBullish(five) && isBullish(one)) {
            return ValidationResult.allow(100, "BALANCED_FULL",
                    "Balanced profile approved 100%: both 5m and 1h are bullish.");
        }
        if (isBullish(five) && isWatchOrNeutral(one)) {
            return ValidationResult.allow(75, "BALANCED_STRONG",
                    "Balanced profile approved 75%: 5m is bullish while 1h is " + one + ".");
        }
        if (five == SignalDecision.WATCH && isBullish(one)) {
            return ValidationResult.allow(75, "BALANCED_STRONG",
                    "Balanced profile approved 75%: 1h is bullish while 5m is WATCH.");
        }
        if (five == SignalDecision.WATCH && one == SignalDecision.WATCH) {
            return ValidationResult.allow(50, "BALANCED_EARLY",
                    "Balanced profile approved 50%: both 5m and 1h are WATCH, so this is an early reduced entry.");
        }

        return ValidationResult.reject("BALANCED_CONFIRMATION_INSUFFICIENT",
                "Balanced profile requires bullish/WATCH confirmation. Current 5m=" + five + ", 1h=" + one + ".");
    }

    private ValidationResult aggressiveBuy(TradeSignal executionSignal, TradeSignal fiveMinute, TradeSignal oneHour) {
        ValidationResult balanced = balancedBuy(fiveMinute, oneHour);
        if (balanced.allowed()) return balanced;

        SignalDecision five = fiveMinute.getDecision();
        SignalDecision one = oneHour.getDecision();
        boolean probeAlignment = five == SignalDecision.WATCH && one == SignalDecision.NEUTRAL;
        if (!probeAlignment) {
            return ValidationResult.reject("AGGRESSIVE_CONFIRMATION_INSUFFICIENT",
                    "Aggressive profile still requires 5m WATCH or better and a non-bearish 1h context. Current 5m="
                            + five + ", 1h=" + one + ".");
        }

        int confidence = executionSignal.getConfidenceScore();
        int trend = executionSignal.getTrendScore();
        int volume = executionSignal.getVolumeScore();
        int momentum = executionSignal.getMomentumScore();

        if (confidence < 85 || trend < 22 || volume < 16 || momentum < 12) {
            return ValidationResult.reject("AGGRESSIVE_PROBE_QUALITY",
                    "Aggressive 25% probe requires confidence>=85, trend>=22, volume>=16, momentum>=12. "
                            + "Actual confidence=" + confidence + ", trend=" + trend + ", volume=" + volume
                            + ", momentum=" + momentum + ".");
        }

        return ValidationResult.allow(25, "AGGRESSIVE_PROBE",
                "Aggressive profile approved a 25% probe: 1m BUY is high quality, 5m is WATCH, and 1h is NEUTRAL.");
    }

    private ValidationResult validateBaseSignal(TradeSignal signal, boolean buy) {
        if (signal == null || signal.getGeneratedAt() == null) {
            return ValidationResult.reject("INVALID_SIGNAL", "Execution signal is missing required timing data.");
        }
        if (!EXECUTION_INTERVAL.equals(signal.getInterval())) {
            return ValidationResult.reject("NON_EXECUTION_TIMEFRAME",
                    "Only 1m signals may trigger normal wallet execution. " + signal.getInterval() + " is context-only.");
        }
        if (buy && !isBullish(signal.getDecision())) {
            return ValidationResult.reject("NOT_BUY", "The 1m decision is not BUY/STRONG_BUY.");
        }
        if (!buy && !isBearish(signal.getDecision())) {
            return ValidationResult.reject("NOT_SELL", "The 1m decision is not SELL/STRONG_SELL.");
        }
        return ValidationResult.allow(100, "BASE_VALID", "Base execution signal is valid.");
    }

    private TradeSignal latestAtOrBefore(TradeSignal signal, String interval) {
        return latestSignal(signal.getSymbol(), interval, signal.getGeneratedAt()).orElse(null);
    }

    private java.util.Optional<TradeSignal> previousSignal(String symbol, String interval, Instant reference) {
        if (replayScope != null && replayScope.active()) return replayScope.previousBefore(symbol, interval, reference);
        return signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanOrderByGeneratedAtDesc(symbol, interval, reference);
    }

    private java.util.Optional<TradeSignal> latestSignal(String symbol, String interval, Instant reference) {
        if (replayScope != null && replayScope.active()) return replayScope.latestAtOrBefore(symbol, interval, reference);
        return signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(symbol, interval, reference);
    }

    private boolean isFresh(TradeSignal signal, Instant reference, Duration maximumAge) {
        return signal != null
                && signal.getGeneratedAt() != null
                && !signal.getGeneratedAt().isAfter(reference)
                && !signal.getGeneratedAt().isBefore(reference.minus(maximumAge));
    }

    private boolean isBullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private boolean isBearish(SignalDecision decision) {
        return decision == SignalDecision.SELL || decision == SignalDecision.STRONG_SELL;
    }

    private boolean isWatchOrNeutral(SignalDecision decision) {
        return decision == SignalDecision.WATCH || decision == SignalDecision.NEUTRAL;
    }

    private WalletSettings settings() {
        return settingsRepository.findById(1L)
                .orElseGet(() -> WalletSettings.builder()
                        .id(1L)
                        .executionProfile(ExecutionProfile.BALANCED.name())
                        .requireNewBuyTransition(true)
                        .dynamicProfitLockEnabled(true)
                        .profitLockActivationPercent(BigDecimal.valueOf(70))
                        .profitLockInitialPercent(BigDecimal.valueOf(40))
                        .profitLockTrailStepPercent(BigDecimal.valueOf(10))
                        .build());
    }

    public record ValidationResult(boolean allowed, String code, String explanation, int positionPercent) {
        public static ValidationResult allow(int positionPercent, String code, String explanation) {
            return new ValidationResult(true, code, explanation, Math.max(1, Math.min(100, positionPercent)));
        }

        public static ValidationResult reject(String code, String explanation) {
            return new ValidationResult(false, code, explanation, 0);
        }
    }
}
