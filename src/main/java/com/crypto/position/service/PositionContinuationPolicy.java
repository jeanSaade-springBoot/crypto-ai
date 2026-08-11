package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.springframework.stereotype.Component;

/** Shared production/replay policy for deciding whether a reached profit target should be extended. */
@Component
public class PositionContinuationPolicy {
    public Evaluation evaluate(TradeSignal current, TradeSignal fiveMinute, TradeSignal oneHour,
                               Integer entryTrend, Integer entryMomentum, Integer entryVolume) {
        if (current == null) return new Evaluation(false, "Continuation FAIL · no fresh signal context.");
        if (bearish(current.getDecision()) || (fiveMinute != null && bearish(fiveMinute.getDecision()))
                || (oneHour != null && bearish(oneHour.getDecision()))) {
            return new Evaluation(false, "Continuation FAIL · bearish timeframe detected; normal exit remains active.");
        }

        boolean currentSupport = supportive(current.getDecision());
        boolean fiveSupport = fiveMinute != null && supportive(fiveMinute.getDecision());
        boolean fiveNonBearish = fiveMinute != null && !bearish(fiveMinute.getDecision());
        boolean oneSafe = oneHour == null || !bearish(oneHour.getDecision());
        boolean oneBullish = oneHour != null && bullish(oneHour.getDecision());

        int trendFloor = Math.max(12, nvl(entryTrend) - 3);
        int momentumFloor = Math.max(7, nvl(entryMomentum) - 4);
        int volumeSoftFloor = Math.max(2, nvl(entryVolume) - 8);

        boolean trendHealthy = current.getTrendScore() >= trendFloor;
        boolean momentumHealthy = current.getMomentumScore() >= momentumFloor;
        boolean volumeSupportive = current.getVolumeScore() >= volumeSoftFloor;

        // Standard continuation: structure is still supportive. Volume is deliberately a soft
        // confirmation because breakout volume commonly cools while a healthy trend continues.
        boolean standard = currentSupport && fiveSupport && oneSafe
                && trendHealthy && momentumHealthy
                && (volumeSupportive || oneBullish);

        // Protected HTF continuation: a live 1h BUY may carry a winner through a neutral/cooling
        // 5m phase. It never overrides bearish context and still requires healthy 1m trend/momentum.
        boolean htfProtected = currentSupport && oneBullish && fiveNonBearish
                && trendHealthy && momentumHealthy;

        boolean extend = standard || htfProtected;
        String path = htfProtected ? "HTF_TREND" : (standard ? "STANDARD" : "NONE");
        String checks = "current=" + decision(current)
                + ", 5m=" + decision(fiveMinute)
                + ", 1h=" + decision(oneHour)
                + ", trend=" + current.getTrendScore() + "/" + trendFloor
                + ", momentum=" + current.getMomentumScore() + "/" + momentumFloor
                + ", volume=" + current.getVolumeScore() + "/soft " + volumeSoftFloor;

        return new Evaluation(extend, extend
                ? "Continuation PASS (" + path + "): " + checks
                    + ". Volume cooling alone does not end a healthy trend; extend the profit target and keep protection active."
                : "Continuation FAIL: " + checks
                    + ". Trend/momentum or higher-timeframe structure no longer supports extending the target; allow the normal exit path.");
    }

    private int nvl(Integer v) { return v == null ? 0 : v; }
    private String decision(TradeSignal s) { return s == null || s.getDecision() == null ? "MISSING" : s.getDecision().name(); }
    private boolean supportive(SignalDecision d) { return d == SignalDecision.BUY || d == SignalDecision.STRONG_BUY || d == SignalDecision.WATCH; }
    private boolean bullish(SignalDecision d) { return d == SignalDecision.BUY || d == SignalDecision.STRONG_BUY; }
    private boolean bearish(SignalDecision d) { return d == SignalDecision.SELL || d == SignalDecision.STRONG_SELL; }
    public record Evaluation(boolean extendTarget, String explanation) {}
}
