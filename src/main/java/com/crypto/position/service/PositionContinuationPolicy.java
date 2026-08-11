package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.springframework.stereotype.Component;

/** Shared production/replay policy for deciding whether a reached profit target should be extended. */
@Component
public class PositionContinuationPolicy {
    public Evaluation evaluate(TradeSignal current, TradeSignal fiveMinute, TradeSignal oneHour,
                               Integer entryTrend, Integer entryMomentum, Integer entryVolume) {
        if (current == null) return new Evaluation(false, "No fresh signal context is available.");
        if (bearish(current.getDecision()) || (fiveMinute != null && bearish(fiveMinute.getDecision()))
                || (oneHour != null && bearish(oneHour.getDecision()))) {
            return new Evaluation(false, "Continuation BLOCKED: a current timeframe is bearish; profit target will not be extended.");
        }

        int trendFloor = Math.max(0, nvl(entryTrend) - 2);
        int momentumFloor = Math.max(0, nvl(entryMomentum) - 2);
        int volumeFloor = Math.max(0, nvl(entryVolume) - 3);

        boolean currentSupport = supportive(current.getDecision());
        boolean fiveSupport = fiveMinute != null && supportive(fiveMinute.getDecision());
        boolean fiveCooling = fiveMinute != null && fiveMinute.getDecision() == SignalDecision.NEUTRAL;
        boolean oneBullish = oneHour != null && bullish(oneHour.getDecision());
        boolean oneSafe = oneHour == null || !bearish(oneHour.getDecision());
        boolean trendHealthy = current.getTrendScore() >= trendFloor;
        boolean momentumHealthy = current.getMomentumScore() >= momentumFloor;
        boolean volumeHealthy = current.getVolumeScore() >= volumeFloor;
        boolean improving = current.getTrendScore() > nvl(entryTrend)
                || current.getMomentumScore() > nvl(entryMomentum)
                || current.getVolumeScore() > nvl(entryVolume);

        // Normal continuation: all short/setup-timeframe components remain supportive.
        boolean standardContinuation = currentSupport && fiveSupport && oneSafe
                && trendHealthy && momentumHealthy && volumeHealthy && improving;

        // Higher-timeframe trend continuation: a strong 1h BUY is allowed to carry a
        // temporarily cooling 5m/volume phase while the 1m trend still supports the trade.
        // This is intentionally NOT allowed for a bearish 5m, bearish current signal,
        // weak current trend, or a non-bullish 1h context.
        boolean htfTrendContinuation = oneBullish && currentSupport && (fiveSupport || fiveCooling)
                && trendHealthy && momentumHealthy;

        boolean extend = standardContinuation || htfTrendContinuation;
        String checks = " [current=" + decision(current) +
                ", 5m=" + decision(fiveMinute) +
                ", 1h=" + decision(oneHour) +
                ", trend=" + current.getTrendScore() + "/floor " + trendFloor +
                ", momentum=" + current.getMomentumScore() + "/floor " + momentumFloor +
                ", volume=" + current.getVolumeScore() + "/floor " + volumeFloor +
                ", improving=" + improving + "]";

        if (standardContinuation) {
            return new Evaluation(true,
                    "Continuation PASS (STANDARD): trend, momentum and volume remain healthy and at least one is improving; extend the profit target and keep the position open." + checks);
        }
        if (htfTrendContinuation) {
            return new Evaluation(true,
                    "Continuation PASS (HTF_TREND): 1h remains bullish and the current trend/momentum remain healthy; temporary 5m/volume cooling is not treated as trend failure. Extend the profit target and tighten protection instead of taking profit early." + checks);
        }
        return new Evaluation(false,
                "Continuation FAIL: the position no longer satisfies either standard continuation or the protected higher-timeframe trend continuation path; allow the normal exit path." + checks);
    }

    private int nvl(Integer v) { return v == null ? 0 : v; }
    private String decision(TradeSignal s) { return s == null || s.getDecision() == null ? "MISSING" : s.getDecision().name(); }
    private boolean supportive(SignalDecision d) { return d == SignalDecision.BUY || d == SignalDecision.STRONG_BUY || d == SignalDecision.WATCH; }
    private boolean bullish(SignalDecision d) { return d == SignalDecision.BUY || d == SignalDecision.STRONG_BUY; }
    private boolean bearish(SignalDecision d) { return d == SignalDecision.SELL || d == SignalDecision.STRONG_SELL; }
    public record Evaluation(boolean extendTarget, String explanation) {}
}
