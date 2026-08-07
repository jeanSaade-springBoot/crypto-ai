package com.crypto.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.domain.WalletSettings;
import com.crypto.wallet.repository.WalletSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Execution-only multi-timeframe validation.
 *
 * Frozen responsibilities:
 *  - 1m: the only normal execution timeframe
 *  - 5m: mandatory tactical confirmation
 *  - 1h: strategic filter/veto (neutral is allowed, opposite direction is blocked)
 *
 * This service does not alter scoring, indicators, or FinalDecisionService output.
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

    public ValidationResult validateBuy(TradeSignal executionSignal) {
        ValidationResult base = validateExecutionSignal(executionSignal, true);
        if (!base.allowed()) return base;

        if (settings().isRequireNewBuyTransition()) {
            TradeSignal previous = signalRepository
                    .findTopBySymbolAndIntervalAndGeneratedAtLessThanOrderByGeneratedAtDesc(
                            executionSignal.getSymbol(), EXECUTION_INTERVAL, executionSignal.getGeneratedAt())
                    .orElse(null);
            if (isFresh(previous, executionSignal.getGeneratedAt(), ONE_MINUTE_TRANSITION_MAX_AGE)
                    && isBullish(previous.getDecision())) {
                return ValidationResult.reject("BUY_CONTINUATION",
                        "BUY was not executed because the recent previous 1m signal was already BUY/STRONG_BUY. A new WATCH/NEUTRAL/SELL -> BUY transition is required.");
            }
        }

        return ValidationResult.allow();
    }

    public ValidationResult validateSell(TradeSignal executionSignal) {
        return validateExecutionSignal(executionSignal, false);
    }

    private ValidationResult validateExecutionSignal(TradeSignal signal, boolean buy) {
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

        TradeSignal fiveMinute = latestAtOrBefore(signal, CONFIRMATION_INTERVAL);
        if (!isFresh(fiveMinute, signal.getGeneratedAt(), FIVE_MINUTE_MAX_AGE)) {
            return ValidationResult.reject("MISSING_5M_CONFIRMATION",
                    "No fresh 5m confirmation was available for the 1m execution signal.");
        }
        if (buy && !isBullish(fiveMinute.getDecision())) {
            return ValidationResult.reject("5M_NOT_BULLISH",
                    "1m BUY was blocked because the fresh 5m decision was " + fiveMinute.getDecision() + ".");
        }
        if (!buy && !isBearish(fiveMinute.getDecision())) {
            return ValidationResult.reject("5M_NOT_BEARISH",
                    "1m SELL was blocked because the fresh 5m decision was " + fiveMinute.getDecision() + ".");
        }

        TradeSignal oneHour = latestAtOrBefore(signal, TREND_INTERVAL);
        if (!isFresh(oneHour, signal.getGeneratedAt(), ONE_HOUR_MAX_AGE)) {
            return ValidationResult.reject("MISSING_1H_CONTEXT",
                    "No fresh 1h strategic context was available for the 1m execution signal.");
        }
        if (buy && isBearish(oneHour.getDecision())) {
            return ValidationResult.reject("1H_BEARISH_VETO",
                    "1m BUY was blocked because the fresh 1h strategic decision was " + oneHour.getDecision() + ".");
        }
        if (!buy && isBullish(oneHour.getDecision())) {
            return ValidationResult.reject("1H_BULLISH_VETO",
                    "1m SELL was blocked because the fresh 1h strategic decision was " + oneHour.getDecision() + ".");
        }

        return ValidationResult.allow();
    }

    private TradeSignal latestAtOrBefore(TradeSignal signal, String interval) {
        return signalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        signal.getSymbol(), interval, signal.getGeneratedAt())
                .orElse(null);
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

    private WalletSettings settings() {
        return settingsRepository.findById(1L)
                .orElseGet(() -> WalletSettings.builder()
                        .id(1L)
                        .requireNewBuyTransition(true)
                        .dynamicProfitLockEnabled(true)
                        .profitLockActivationPercent(java.math.BigDecimal.valueOf(70))
                        .profitLockInitialPercent(java.math.BigDecimal.valueOf(40))
                        .profitLockTrailStepPercent(java.math.BigDecimal.valueOf(10))
                        .build());
    }

    public record ValidationResult(boolean allowed, String code, String explanation) {
        public static ValidationResult allow() {
            return new ValidationResult(true, "ALLOWED", "Execution validation passed.");
        }

        public static ValidationResult reject(String code, String explanation) {
            return new ValidationResult(false, code, explanation);
        }
    }
}
