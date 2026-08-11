package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionContinuationPolicyTest {
    private final PositionContinuationPolicy policy = new PositionContinuationPolicy();

    @Test
    void extendsTargetWhileTrendMomentumVolumeAndHigherTimeframeRemainSupportive() {
        TradeSignal one = signal(SignalDecision.BUY, 20, 15, 19);
        TradeSignal five = signal(SignalDecision.WATCH, 19, 14, 18);
        TradeSignal hour = signal(SignalDecision.WATCH, 12, 10, 10);
        var result = policy.evaluate(one, five, hour, 19, 14, 17);
        assertTrue(result.extendTarget());
        assertTrue(result.explanation().contains("STANDARD"));
    }

    @Test
    void extendsDuringFiveMinuteCoolingWhenOneHourThesisIsStillBullish() {
        // Regression for BNBUSDT 2026-08-11 08:12 UTC:
        // current 1m WATCH trend=20 momentum=9 volume=6, 5m NEUTRAL, 1h BUY.
        TradeSignal one = signal(SignalDecision.WATCH, 20, 9, 6);
        TradeSignal five = signal(SignalDecision.NEUTRAL, 13, 9, 14);
        TradeSignal hour = signal(SignalDecision.BUY, 18, 15, 16);
        var result = policy.evaluate(one, five, hour, 19, 9, 12);
        assertTrue(result.extendTarget());
        assertTrue(result.explanation().contains("HTF_TREND"));
    }

    @Test
    void doesNotExtendWhenFiveMinuteTurnsBearish() {
        TradeSignal one = signal(SignalDecision.BUY, 20, 15, 19);
        TradeSignal five = signal(SignalDecision.SELL, 10, 8, 8);
        TradeSignal hour = signal(SignalDecision.BUY, 18, 15, 16);
        var result = policy.evaluate(one, five, hour, 19, 14, 17);
        assertFalse(result.extendTarget());
    }

    @Test
    void doesNotUseHtfExceptionWhenOneHourIsOnlyWatch() {
        TradeSignal one = signal(SignalDecision.WATCH, 20, 9, 6);
        TradeSignal five = signal(SignalDecision.NEUTRAL, 13, 9, 14);
        TradeSignal hour = signal(SignalDecision.WATCH, 18, 15, 16);
        var result = policy.evaluate(one, five, hour, 19, 9, 12);
        assertFalse(result.extendTarget());
    }

    @Test
    void doesNotUseHtfExceptionWhenCurrentMomentumBreaksDown() {
        TradeSignal one = signal(SignalDecision.WATCH, 20, 4, 6);
        TradeSignal five = signal(SignalDecision.NEUTRAL, 13, 9, 14);
        TradeSignal hour = signal(SignalDecision.BUY, 18, 15, 16);
        var result = policy.evaluate(one, five, hour, 19, 9, 12);
        assertFalse(result.extendTarget());
    }

    private TradeSignal signal(SignalDecision decision, int trend, int momentum, int volume) {
        return TradeSignal.builder().decision(decision).trendScore(trend).momentumScore(momentum).volumeScore(volume).build();
    }
}
