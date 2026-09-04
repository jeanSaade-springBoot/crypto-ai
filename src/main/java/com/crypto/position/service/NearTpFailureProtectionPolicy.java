package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;

/**
 * FIX-11T: shared Production/Replay Near-TP Failure Protection policy.
 *
 * This class is deliberately pure: it decides state transitions and whether a partial
 * harvest is eligible, but it never changes wallet balances or persistence itself.  The
 * same policy is called by Production live-price protection and Shadow Production Replay
 * so the trading rule cannot silently diverge between the two modes.
 */
@Component
public class NearTpFailureProtectionPolicy {
    private static final MathContext MC = MathContext.DECIMAL64;

    /** Begin observation only after price has travelled 90% of the planned entry -> TP distance. */
    public static final BigDecimal ARM_PROGRESS_PERCENT = BigDecimal.valueOf(90);
    /** A 20% giveback only starts rejection monitoring; it never sells by itself. */
    public static final BigDecimal REJECTION_GIVEBACK_PERCENT = BigDecimal.valueOf(20);
    /** A single noisy 1m SELL is insufficient. Two distinct fresh bearish 1m signals are required. */
    public static final int REQUIRED_CONSECUTIVE_BEARISH_1M = 2;
    /** 1m evidence older than two minutes is not valid sell evidence. */
    public static final Duration ONE_MINUTE_MAX_AGE = Duration.ofMinutes(2);
    /** Reuse the Production execution-intelligence 5m freshness horizon. */
    public static final Duration FIVE_MINUTE_MAX_AGE = Duration.ofMinutes(20);

    public Evaluation evaluate(State state,
                               BigDecimal entry,
                               BigDecimal takeProfit,
                               BigDecimal price,
                               Instant evaluatedAt,
                               TradeSignal oneMinute,
                               TradeSignal fiveMinute) {
        State current = state == null ? State.inactive() : state;
        if (current.harvestUsed()) {
            return Evaluation.hold(current, "HARVEST_ALREADY_USED",
                    "Near-TP partial harvest has already been used for this position.");
        }
        if (entry == null || takeProfit == null || price == null || evaluatedAt == null) {
            return Evaluation.hold(current, "INVALID_NEAR_TP_INPUT",
                    "Entry, take-profit, current price and evaluation time are required.");
        }

        BigDecimal plannedDistance = takeProfit.subtract(entry, MC);
        if (plannedDistance.signum() <= 0) {
            return Evaluation.hold(current, "INVALID_TP_DISTANCE",
                    "Near-TP protection is long-only and requires take-profit above entry.");
        }

        BigDecimal progress = price.subtract(entry, MC)
                .divide(plannedDistance, MC)
                .multiply(BigDecimal.valueOf(100), MC);

        if (current.nearTpState() == NearTpState.INACTIVE) {
            if (progress.compareTo(ARM_PROGRESS_PERCENT) < 0) {
                return Evaluation.hold(current, "BELOW_ARM_THRESHOLD",
                        "TP progress is below the 90% Near-TP arming threshold.");
            }
            State armed = new State(NearTpState.NEAR_TP_ARMED, price, 0, null, false);
            return Evaluation.transition(armed, "NEAR_TP_ARMED",
                    "Price reached " + progress.stripTrailingZeros().toPlainString()
                            + "% of the planned entry-to-TP distance.", progress, BigDecimal.ZERO);
        }

        if (current.nearTpState() != NearTpState.NEAR_TP_ARMED
                && current.nearTpState() != NearTpState.NEAR_TP_REJECTION_DETECTED) {
            return Evaluation.hold(current, "STATE_NOT_EVALUABLE",
                    "Near-TP state does not permit another protection evaluation.");
        }

        BigDecimal best = current.bestPrice();
        if (best == null || price.compareTo(best) > 0) {
            State recovered = new State(NearTpState.NEAR_TP_ARMED, price, 0, null, false);
            String code = current.nearTpState() == NearTpState.NEAR_TP_REJECTION_DETECTED
                    ? "NEAR_TP_RECOVERY" : "NEAR_TP_NEW_BEST";
            return Evaluation.transition(recovered, code,
                    "A new post-arm best price was established.", progress, BigDecimal.ZERO);
        }

        BigDecimal giveback = best.subtract(price, MC)
                .divide(plannedDistance, MC)
                .multiply(BigDecimal.valueOf(100), MC);

        if (current.nearTpState() == NearTpState.NEAR_TP_ARMED) {
            if (giveback.compareTo(REJECTION_GIVEBACK_PERCENT) < 0) {
                return Evaluation.hold(current, "ARMED_HOLD",
                        "Near-TP is armed but the price giveback has not reached 20% of planned TP distance.",
                        progress, giveback);
            }
            State rejected = new State(NearTpState.NEAR_TP_REJECTION_DETECTED,
                    best, 0, null, false);
            return Evaluation.transition(rejected, "NEAR_TP_REJECTION_DETECTED",
                    "Price gave back at least 20% of the planned entry-to-TP distance; observe only.",
                    progress, giveback);
        }

        // A rejection is no longer active once price recovers back inside the same 20%
        // boundary that created it.  This avoids inventing another arbitrary recovery threshold.
        if (giveback.compareTo(REJECTION_GIVEBACK_PERCENT) < 0) {
            State recovered = new State(NearTpState.NEAR_TP_ARMED, best, 0, null, false);
            return Evaluation.transition(recovered, "NEAR_TP_RECOVERY",
                    "Price recovered inside the 20% rejection boundary; bearish persistence was reset.",
                    progress, giveback);
        }

        boolean oneFresh = isFreshAtOrBefore(oneMinute, evaluatedAt, ONE_MINUTE_MAX_AGE);
        boolean fiveFresh = isFreshAtOrBefore(fiveMinute, evaluatedAt, FIVE_MINUTE_MAX_AGE);
        boolean oneUsable = oneFresh && oneMinute.getOriginalDecision() != null;
        boolean fiveUsable = fiveFresh && fiveMinute.getOriginalDecision() != null;

        int bearishStreak = current.consecutiveBearishOneMinute();
        Long lastEvaluatedOneMinuteSignalId = current.lastEvaluatedOneMinuteSignalId();
        boolean newOneMinuteEvidence = oneMinute != null && oneMinute.getId() != null
                && !oneMinute.getId().equals(lastEvaluatedOneMinuteSignalId);

        if (newOneMinuteEvidence) {
            lastEvaluatedOneMinuteSignalId = oneMinute.getId();
            if (oneUsable && isBearish(oneMinute.getOriginalDecision())) {
                bearishStreak++;
            } else if (oneUsable) {
                bearishStreak = 0;
            } else {
                // A stale/missing evidence gap breaks the meaning of "consecutive".
                bearishStreak = 0;
            }
        } else if (!oneUsable && bearishStreak != 0) {
            bearishStreak = 0;
        }

        State evidenceState = new State(NearTpState.NEAR_TP_REJECTION_DETECTED,
                best, bearishStreak, lastEvaluatedOneMinuteSignalId, false);

        // Fail-safe rule: missing, future or stale evidence can NEVER become sell confirmation.
        if (!oneUsable || !fiveUsable) {
            return Evaluation.hold(evidenceState, "HOLD_MISSING_OR_STALE_EVIDENCE",
                    "Near-TP rejection remains active, but fresh 1m and 5m evidence is required before harvesting.",
                    progress, giveback);
        }

        boolean fiveMinuteStillBullish = isBullish(fiveMinute.getOriginalDecision());
        if (fiveMinuteStillBullish) {
            return Evaluation.hold(evidenceState, "HOLD_5M_BULLISH_SUPPORT",
                    "Fresh 5m original BUY/STRONG_BUY support remains intact.", progress, giveback);
        }

        if (bearishStreak < REQUIRED_CONSECUTIVE_BEARISH_1M) {
            return Evaluation.hold(evidenceState, "HOLD_1M_BEARISH_NOT_PERSISTENT",
                    "Fresh 1m underlying bearish evidence has not persisted for two distinct signals.",
                    progress, giveback);
        }

        State confirmed = new State(NearTpState.NEAR_TP_FAILURE_CONFIRMED,
                best, bearishStreak, lastEvaluatedOneMinuteSignalId, false);
        return Evaluation.harvest(confirmed, "NEAR_TP_FAILURE_CONFIRMED",
                "Near-TP failure confirmed: rejection remains active, 1m original bearish evidence persisted, "
                        + "and fresh 5m original BUY/STRONG_BUY support is absent.", progress, giveback);
    }

    public BigDecimal tpProgress(BigDecimal entry, BigDecimal takeProfit, BigDecimal current) {
        if (entry == null || takeProfit == null || current == null) return BigDecimal.ZERO;
        BigDecimal plannedDistance = takeProfit.subtract(entry, MC);
        if (plannedDistance.signum() <= 0) return BigDecimal.ZERO;
        return current.subtract(entry, MC).divide(plannedDistance, MC)
                .multiply(BigDecimal.valueOf(100), MC);
    }

    private boolean isFreshAtOrBefore(TradeSignal signal, Instant evaluatedAt, Duration maxAge) {
        if (signal == null || signal.getGeneratedAt() == null || evaluatedAt == null) return false;
        if (signal.getGeneratedAt().isAfter(evaluatedAt)) return false;
        return Duration.between(signal.getGeneratedAt(), evaluatedAt).compareTo(maxAge) <= 0;
    }

    private boolean isBullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private boolean isBearish(SignalDecision decision) {
        return decision == SignalDecision.SELL || decision == SignalDecision.STRONG_SELL;
    }

    public record State(NearTpState nearTpState,
                        BigDecimal bestPrice,
                        int consecutiveBearishOneMinute,
                        Long lastEvaluatedOneMinuteSignalId,
                        boolean harvestUsed) {
        public static State inactive() {
            return new State(NearTpState.INACTIVE, null, 0, null, false);
        }
    }

    public record Evaluation(State state,
                             boolean harvestEligible,
                             boolean transition,
                             String code,
                             String explanation,
                             BigDecimal tpProgressPercent,
                             BigDecimal givebackPercent) {
        static Evaluation hold(State state, String code, String explanation) {
            return hold(state, code, explanation, null, null);
        }

        static Evaluation hold(State state, String code, String explanation,
                               BigDecimal progress, BigDecimal giveback) {
            return new Evaluation(state, false, false, code, explanation, progress, giveback);
        }

        static Evaluation transition(State state, String code, String explanation,
                                     BigDecimal progress, BigDecimal giveback) {
            return new Evaluation(state, false, true, code, explanation, progress, giveback);
        }

        static Evaluation harvest(State state, String code, String explanation,
                                  BigDecimal progress, BigDecimal giveback) {
            return new Evaluation(state, true, true, code, explanation, progress, giveback);
        }
    }
}
