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
    }

    @Test
    void doesNotExtendWhenFiveMinuteTurnsBearish() {
        TradeSignal one = signal(SignalDecision.BUY, 20, 15, 19);
        TradeSignal five = signal(SignalDecision.SELL, 10, 8, 8);
        var result = policy.evaluate(one, five, null, 19, 14, 17);
        assertFalse(result.extendTarget());
    }

    @Test
    void doesNotExtendWhenContinuationIsNoLongerImproving() {
        TradeSignal one = signal(SignalDecision.WATCH, 17, 12, 14);
        TradeSignal five = signal(SignalDecision.WATCH, 18, 13, 15);
        var result = policy.evaluate(one, five, null, 19, 14, 17);
        assertFalse(result.extendTarget());
    }

    private TradeSignal signal(SignalDecision decision, int trend, int momentum, int volume) {
        return TradeSignal.builder().decision(decision).trendScore(trend).momentumScore(momentum).volumeScore(volume).build();
    }
}
