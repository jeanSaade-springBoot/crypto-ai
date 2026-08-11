package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionContinuationPolicyTest {
    private final PositionContinuationPolicy policy = new PositionContinuationPolicy();

    @Test
    void extendsWhenStructureRemainsHealthyWithoutRequiringScoresToImprove() {
        TradeSignal one = signal(SignalDecision.WATCH, 17, 11, 6);
        TradeSignal five = signal(SignalDecision.WATCH, 15, 9, 10);
        TradeSignal hour = signal(SignalDecision.WATCH, 18, 15, 16);
        var result = policy.evaluate(one, five, hour, 19, 14, 17);
        assertTrue(result.extendTarget());
        assertTrue(result.explanation().contains("STANDARD"));
    }

    @Test
    void htfBuyCarriesHealthyWinnerThroughNeutralFiveMinuteAndCoolingVolume() {
        TradeSignal one = signal(SignalDecision.WATCH, 20, 9, 1);
        TradeSignal five = signal(SignalDecision.NEUTRAL, 13, 9, 14);
        TradeSignal hour = signal(SignalDecision.BUY, 18, 15, 16);
        var result = policy.evaluate(one, five, hour, 18, 13, 16);
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
    void doesNotExtendWhenMomentumActuallyBreaksEvenWithOneHourBuy() {
        TradeSignal one = signal(SignalDecision.WATCH, 20, 5, 6);
        TradeSignal five = signal(SignalDecision.NEUTRAL, 13, 8, 10);
        TradeSignal hour = signal(SignalDecision.BUY, 18, 15, 16);
        var result = policy.evaluate(one, five, hour, 18, 13, 16);
        assertFalse(result.extendTarget());
    }

    private TradeSignal signal(SignalDecision decision, int trend, int momentum, int volume) {
        return TradeSignal.builder().decision(decision).trendScore(trend).momentumScore(momentum).volumeScore(volume).build();
    }
}
