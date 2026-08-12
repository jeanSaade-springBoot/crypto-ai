package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.springframework.stereotype.Component;

/**
 * Shared production/replay exit authority for an already-open long position.
 *
 * The policy deliberately separates short-timeframe noise from a genuine
 * higher-timeframe breakdown:
 *  - a 1m pullback alone never closes the position;
 *  - the existing 1m SELL + bearish 5m confirmation remains valid;
 *  - a fresh 1h SELL may close a position when 5m is no longer supportive;
 *  - a Profit Lock breach is tolerated while 5m/1h still support the bullish thesis.
 */
@Component
public class PositionExitPolicy {

    public Evaluation evaluateNormalExit(TradeSignal current, TradeSignal fiveMinute, TradeSignal oneHour) {
        SignalDecision currentDecision = decision(current);
        SignalDecision fiveDecision = decision(fiveMinute);
        SignalDecision oneDecision = decision(oneHour);

        boolean oneHourBreakdown = bearish(oneDecision)
                && fiveMinute != null
                && !supportive(fiveDecision);
        if (oneHourBreakdown) {
            return Evaluation.exit("HTF_SELL_CONFIRMED",
                    "1h turned " + label(oneDecision)
                            + " while 5m is no longer supportive (" + label(fiveDecision)
                            + "). The higher-timeframe bullish thesis is broken; close the managed position.");
        }

        boolean lowerFrameConfirmed = current != null
                && "1m".equals(current.getInterval())
                && bearish(currentDecision)
                && fiveMinute != null
                && bearish(fiveDecision)
                && (oneHour == null || !bullish(oneDecision));
        if (lowerFrameConfirmed) {
            return Evaluation.exit("SELL_CONFIRMED",
                    "1m " + label(currentDecision) + " is confirmed by bearish 5m while 1h is not bullish.");
        }

        return Evaluation.hold("No confirmed multi-timeframe exit."
                + " current=" + label(currentDecision)
                + ", 5m=" + label(fiveDecision)
                + ", 1h=" + label(oneDecision) + ".");
    }

    public Evaluation evaluateProfitLockBreach(TradeSignal current, TradeSignal fiveMinute, TradeSignal oneHour) {
        SignalDecision currentDecision = decision(current);
        SignalDecision fiveDecision = decision(fiveMinute);
        SignalDecision oneDecision = decision(oneHour);

        // Strong HTF support: tolerate a sharp 1m pullback instead of throwing away the trend.
        if (bullish(oneDecision) && fiveMinute != null && supportive(fiveDecision)) {
            return Evaluation.hold("PROFIT_LOCK_HOLD",
                    "Profit Lock was breached, but higher-timeframe continuation remains healthy:"
                            + " 1h=" + label(oneDecision) + ", 5m=" + label(fiveDecision)
                            + ", current=" + label(currentDecision)
                            + ". Treat the breach as a protected pullback, not an immediate SELL.");
        }

        // Cooling but still-supported trend: WATCH/WATCH is allowed to breathe while the current frame is not bearish.
        if (oneDecision == SignalDecision.WATCH
                && fiveMinute != null && supportive(fiveDecision)
                && !bearish(currentDecision)) {
            return Evaluation.hold("PROFIT_LOCK_HOLD",
                    "Profit Lock was breached during a supported consolidation:"
                            + " 1h=WATCH, 5m=" + label(fiveDecision)
                            + ", current=" + label(currentDecision)
                            + ". Keep the winner protected while waiting for higher-timeframe confirmation.");
        }

        if (bearish(oneDecision)) {
            return Evaluation.exit("PROFIT_LOCK_HTF_EXIT",
                    "Profit Lock was breached and 1h is " + label(oneDecision)
                            + "; higher-timeframe support has failed.");
        }

        if (bearish(currentDecision) && bearish(fiveDecision)) {
            return Evaluation.exit("PROFIT_LOCK_BEARISH_CONFIRMATION",
                    "Profit Lock was breached with bearish 1m and 5m confirmation.");
        }

        return Evaluation.exit("PROFIT_LOCK_EXIT",
                "Profit Lock was breached without enough higher-timeframe bullish support to justify a protected hold:"
                        + " current=" + label(currentDecision)
                        + ", 5m=" + label(fiveDecision)
                        + ", 1h=" + label(oneDecision) + ".");
    }

    private SignalDecision decision(TradeSignal signal) {
        return signal == null ? null : signal.getDecision();
    }

    private boolean supportive(SignalDecision decision) {
        return decision == SignalDecision.BUY
                || decision == SignalDecision.STRONG_BUY
                || decision == SignalDecision.WATCH;
    }

    private boolean bullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private boolean bearish(SignalDecision decision) {
        return decision == SignalDecision.SELL || decision == SignalDecision.STRONG_SELL;
    }

    private String label(SignalDecision decision) {
        return decision == null ? "MISSING" : decision.name();
    }

    public record Evaluation(boolean exit, String code, String explanation) {
        public static Evaluation exit(String code, String explanation) {
            return new Evaluation(true, code, explanation);
        }

        public static Evaluation hold(String explanation) {
            return new Evaluation(false, "HOLD", explanation);
        }

        public static Evaluation hold(String code, String explanation) {
            return new Evaluation(false, code, explanation);
        }
    }
}
