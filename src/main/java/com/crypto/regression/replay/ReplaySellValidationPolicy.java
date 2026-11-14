package com.crypto.regression.replay;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** FIX-156: frozen Replay-local mirror of the Production 1m SELL context rules. */
@Component
public final class ReplaySellValidationPolicy {
    private static final Duration FIVE_MINUTE_MAX_AGE = Duration.ofMinutes(20);
    private static final Duration ONE_HOUR_MAX_AGE = Duration.ofHours(3);

    public Result validate(TradeSignal signal, TradeSignal fiveMinute, TradeSignal oneHour) {
        if (signal == null || signal.getGeneratedAt() == null) return Result.reject("INVALID_SIGNAL");
        if (!"1m".equals(signal.getInterval())) return Result.reject("NON_EXECUTION_TIMEFRAME");
        if (!bearish(signal.getDecision())) return Result.reject("NOT_SELL");
        if (!fresh(fiveMinute, signal, FIVE_MINUTE_MAX_AGE)) return Result.reject("MISSING_5M_CONFIRMATION");
        if (!bearish(fiveMinute.getDecision())) return Result.reject("5M_NOT_BEARISH");
        if (!fresh(oneHour, signal, ONE_HOUR_MAX_AGE)) return Result.reject("MISSING_1H_CONTEXT");
        if (bullish(oneHour.getDecision())) return Result.reject("1H_BULLISH_VETO");
        return new Result(true, "SELL_CONFIRMED", "1m SELL confirmed by fresh bearish 5m context; 1h is not bullish.");
    }

    private boolean fresh(TradeSignal candidate, TradeSignal reference, Duration maximumAge) {
        return candidate != null && candidate.getGeneratedAt() != null
                && !candidate.getGeneratedAt().isAfter(reference.getGeneratedAt())
                && !candidate.getGeneratedAt().isBefore(reference.getGeneratedAt().minus(maximumAge));
    }

    private boolean bearish(SignalDecision decision) {
        return decision == SignalDecision.SELL || decision == SignalDecision.STRONG_SELL;
    }

    private boolean bullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    public record Result(boolean allowed, String code, String explanation) {
        private static Result reject(String code) { return new Result(false, code, code); }
    }
}
