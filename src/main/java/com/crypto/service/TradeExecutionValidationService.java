package com.crypto.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.execution.service.ExecutionReplayScope;
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
    @Autowired(required = false)
    private ExecutionReplayScope replayScope;

    public ValidationResult validateBuy(TradeSignal executionSignal) {
        ValidationResult structural = validateBaseSignal(executionSignal, true);
        if (!structural.allowed()) return structural;

        WalletSettings settings = settings();
        if (settings.isRequireNewBuyTransition()) {
            TradeSignal previous = previousSignal(executionSignal.getSymbol(), EXECUTION_INTERVAL, executionSignal.getGeneratedAt()).orElse(null);
            if (isFresh(previous, executionSignal.getGeneratedAt(), ONE_MINUTE_TRANSITION_MAX_AGE)
                    && isBullish(previous.getDecision())) {
                return ValidationResult.reject("BUY_CONTINUATION",
                        "BUY was not executed because the recent previous 1m signal was already BUY/STRONG_BUY. "
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
            case BALANCED -> balancedBuy(fiveMinute, oneHour);
            case AGGRESSIVE -> aggressiveBuy(executionSignal, fiveMinute, oneHour);
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
