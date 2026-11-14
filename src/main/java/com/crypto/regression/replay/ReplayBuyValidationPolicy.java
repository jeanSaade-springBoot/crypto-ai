package com.crypto.regression.replay;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.springframework.stereotype.Component;

/**
 * FIX-156 Replay-owned BUY admission mirror.
 *
 * <p>This policy deliberately has no dependency on wallet/account repositories,
 * Spring execution services, or exchange clients. Its inputs are the frozen
 * Replay profile and the signal evidence already selected for the Replay
 * timeline. A missing required input refuses admission rather than consulting
 * current Production state.</p>
 */
@Component
public final class ReplayBuyValidationPolicy {

    public Result evaluate(TradeSignal signal, int currentAllocationPercent,
                           ReplayPolicyProfile profile) {
        if (signal == null || signal.getGeneratedAt() == null) {
            return Result.reject("INVALID_SIGNAL");
        }
        if (!"1m".equals(signal.getInterval())) {
            return Result.reject("NON_EXECUTION_TIMEFRAME");
        }
        if (!bullish(signal.getDecision())) {
            return Result.reject("NOT_BULLISH");
        }
        if (profile == null) {
            return Result.reject("MISSING_FROZEN_PROFILE");
        }
        if (profile.requireNewBuyTransition() && currentAllocationPercent > 0) {
            return Result.reject("NEW_BUY_TRANSITION_REQUIRED");
        }
        int recommended = signal.getAtrRecommendedPositionPercent() > 0
                ? signal.getAtrRecommendedPositionPercent() : 100;
        int positionPercent = Math.max(1, Math.min(100 - Math.max(0, currentAllocationPercent), recommended));
        if (positionPercent <= 0) {
            return Result.reject("ALLOCATION_FULL");
        }
        return new Result(true, positionPercent, "REPLAY_BUY_ALLOWED",
                "Replay-local BUY mirror accepted frozen historical signal evidence.");
    }

    private boolean bullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    public record Result(boolean allowed, int positionPercent, String code, String explanation) {
        private static Result reject(String code) {
            return new Result(false, 0, code, code);
        }
    }
}
