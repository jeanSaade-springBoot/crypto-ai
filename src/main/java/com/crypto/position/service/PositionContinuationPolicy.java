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
            return new Evaluation(false, "A current timeframe is bearish; profit target will not be extended.");
        }
        boolean fiveSupport = fiveMinute != null && supportive(fiveMinute.getDecision());
        boolean oneSafe = oneHour == null || !bearish(oneHour.getDecision());
        int trendFloor = Math.max(0, nvl(entryTrend) - 2);
        int momentumFloor = Math.max(0, nvl(entryMomentum) - 2);
        int volumeFloor = Math.max(0, nvl(entryVolume) - 3);
        boolean trendHealthy = current.getTrendScore() >= trendFloor;
        boolean momentumHealthy = current.getMomentumScore() >= momentumFloor;
        boolean volumeHealthy = current.getVolumeScore() >= volumeFloor;
        boolean improving = current.getTrendScore() > nvl(entryTrend)
                || current.getMomentumScore() > nvl(entryMomentum)
                || current.getVolumeScore() > nvl(entryVolume);
        boolean extend = fiveSupport && oneSafe && trendHealthy && momentumHealthy && volumeHealthy && improving;
        return new Evaluation(extend, extend
                ? "Trend, momentum and volume remain healthy and at least one is improving; extend the profit target and keep the position open."
                : "Continuation is not strong enough across trend, momentum, volume and higher-timeframe context; allow the normal exit path.");
    }

    private int nvl(Integer v) { return v == null ? 0 : v; }
    private boolean supportive(SignalDecision d) { return d == SignalDecision.BUY || d == SignalDecision.STRONG_BUY || d == SignalDecision.WATCH; }
    private boolean bearish(SignalDecision d) { return d == SignalDecision.SELL || d == SignalDecision.STRONG_SELL; }
    public record Evaluation(boolean extendTarget, String explanation) {}
}
