package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shared production/replay policy for deciding whether a reached profit target should be extended.
 *
 * IMPORTANT: this class manages an ALREADY-OPEN winner only. It does not loosen BUY generation,
 * entry validation, stop loss, or bearish timeframe vetoes.
 */
@Component
@RequiredArgsConstructor
public class PositionContinuationPolicy {
    /** Minor/moderate immutable-thesis deterioration is tolerated at a TP checkpoint. */
    static final int MAX_CONTINUATION_TREND_PRESSURE = 2;
    /** Momentum must remain essentially intact for the thesis-preservation continuation path. */
    static final int MAX_CONTINUATION_MOMENTUM_PRESSURE = 1;

    private final PositionThesisPressurePolicy thesisPressurePolicy;

    /**
     * Backward-compatible evaluator used by older unit fixtures. Production/replay should pass
     * the full immutable entry thesis through the overload below.
     */
    public Evaluation evaluate(TradeSignal current, TradeSignal fiveMinute, TradeSignal oneHour,
                               Integer entryTrend, Integer entryMomentum, Integer entryVolume) {
        return evaluate(current, fiveMinute, oneHour,
                entryTrend, null, entryMomentum, entryVolume, null, null);
    }

    public Evaluation evaluate(TradeSignal current, TradeSignal fiveMinute, TradeSignal oneHour,
                               Integer entryTrend, Integer entryStructure,
                               Integer entryMomentum, Integer entryVolume,
                               Integer entryConfidence, Integer entryTotal) {
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

        // Healthy consolidation: low volume is deliberately NOT a hard veto for an already
        // profitable position. If 1m is still WATCH/BUY, the higher timeframes are non-bearish,
        // and trend/momentum remain healthy, cooling volume alone must not force a TP exit.
        boolean healthyConsolidation = currentSupport && fiveNonBearish && oneSafe
                && trendHealthy && momentumHealthy;

        // HTF-supported consolidation: a strong 1h BUY may carry a winner through one neutral
        // 1m candle when 5m remains non-bearish. This is position-management-only; supportive()
        // is intentionally unchanged so entry rules are not loosened.
        boolean currentNonBearish = !bearish(current.getDecision());
        boolean htfSupportedConsolidation = currentNonBearish && !currentSupport
                && oneBullish && fiveNonBearish && trendHealthy && momentumHealthy;

        PositionThesisPressurePolicy.ThesisPressure thesis = thesisPressurePolicy.evaluate(
                entryTrend, entryStructure, entryMomentum, entryVolume,
                entryConfidence, entryTotal, current);

        // FIX-011 / SOLUSDT 2026-08-19:
        // At TP, SOL had 1m WATCH, 5m NEUTRAL, 1h WATCH and momentum improved 13->15.
        // The old binary trend floor rejected continuation solely because trend fell 21->16
        // (16 < 18), even though PositionManagementService scored the SAME immutable thesis as
        // trend pressure 2/8, momentum pressure 0/5 and HOLD. This narrow path aligns both
        // components: minor thesis cooling may extend a winner, but any bearish timeframe still
        // vetoes above and genuine trend/momentum breakdown still fails these pressure limits.
        boolean thesisIntactConsolidation = currentSupport && fiveNonBearish && oneSafe
                && thesis.trendPressure() <= MAX_CONTINUATION_TREND_PRESSURE
                && thesis.momentumPressure() <= MAX_CONTINUATION_MOMENTUM_PRESSURE;

        boolean extend = standard || htfProtected || healthyConsolidation
                || htfSupportedConsolidation || thesisIntactConsolidation;
        String path = htfProtected ? "HTF_TREND"
                : (standard ? "STANDARD"
                : (htfSupportedConsolidation ? "HTF_SUPPORTED_CONSOLIDATION"
                : (healthyConsolidation ? "HEALTHY_CONSOLIDATION"
                : (thesisIntactConsolidation ? "THESIS_INTACT_CONSOLIDATION" : "NONE"))));
        String checks = "current=" + decision(current)
                + ", 5m=" + decision(fiveMinute)
                + ", 1h=" + decision(oneHour)
                + ", trend=" + current.getTrendScore() + "/" + trendFloor
                + ", momentum=" + current.getMomentumScore() + "/" + momentumFloor
                + ", volume=" + current.getVolumeScore() + "/soft " + volumeSoftFloor
                + ", thesisPressure=" + thesis.trendPressure() + "/8 trend, "
                + thesis.momentumPressure() + "/5 momentum";

        return new Evaluation(extend, extend
                ? "Continuation PASS (" + path + "): " + checks
                    + ". Volume is advisory during a healthy winner; cooling volume alone does not end the trend."
                : "Continuation FAIL: " + checks
                    + ". Trend/momentum or higher-timeframe structure no longer supports extending the target; allow normal protection/exit logic.");
    }

    private int nvl(Integer v) { return v == null ? 0 : v; }
    private String decision(TradeSignal s) { return s == null || s.getDecision() == null ? "MISSING" : s.getDecision().name(); }
    private boolean supportive(SignalDecision d) { return d == SignalDecision.BUY || d == SignalDecision.STRONG_BUY || d == SignalDecision.WATCH; }
    private boolean bullish(SignalDecision d) { return d == SignalDecision.BUY || d == SignalDecision.STRONG_BUY; }
    private boolean bearish(SignalDecision d) { return d == SignalDecision.SELL || d == SignalDecision.STRONG_SELL; }
    public record Evaluation(boolean extendTarget, String explanation) {}
}
