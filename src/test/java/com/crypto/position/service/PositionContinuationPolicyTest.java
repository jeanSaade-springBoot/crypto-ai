package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionContinuationPolicyTest {
    private final PositionContinuationPolicy policy = new PositionContinuationPolicy(new PositionThesisPressurePolicy());

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
    void healthyConsolidationExtendsForWatchNeutralWatchWhenUnderlyingScoresRemainHealthy() {
        // Mirrors the 2026-08-11 12:06 BNB continuation state:
        // current WATCH, 5m NEUTRAL, 1h WATCH, trend 21/15, momentum 9/7, volume 16/soft 8.
        TradeSignal one = signal(SignalDecision.WATCH, 21, 9, 16);
        TradeSignal five = signal(SignalDecision.NEUTRAL, 13, 9, 14);
        TradeSignal hour = signal(SignalDecision.WATCH, 18, 11, 9);
        var result = policy.evaluate(one, five, hour, 18, 11, 16);
        assertTrue(result.extendTarget());
        assertTrue(result.explanation().contains("HEALTHY_CONSOLIDATION"));
    }

    @Test
    void healthyConsolidationAllowsCoolingVolumeWhenTrendAndMomentumRemainHealthy() {
        TradeSignal one = signal(SignalDecision.WATCH, 22, 14, 4);
        TradeSignal five = signal(SignalDecision.NEUTRAL, 13, 9, 14);
        TradeSignal hour = signal(SignalDecision.WATCH, 18, 11, 9);
        var result = policy.evaluate(one, five, hour, 18, 11, 16);
        assertTrue(result.extendTarget());
        assertTrue(result.explanation().contains("HEALTHY_CONSOLIDATION"));
    }

    @Test
    void htfBuyCarriesOneNeutralMinuteWhenFiveMinuteStillSupportive() {
        // Mirrors the BNB 08:17 state: 1m NEUTRAL, 5m WATCH, 1h BUY,
        // trend 19/18, momentum 8/7 and cooled volume 5/soft 7.
        TradeSignal one = signal(SignalDecision.NEUTRAL, 19, 8, 5);
        TradeSignal five = signal(SignalDecision.WATCH, 15, 9, 10);
        TradeSignal hour = signal(SignalDecision.BUY, 18, 15, 16);
        var result = policy.evaluate(one, five, hour, 21, 12, 15);
        assertTrue(result.extendTarget());
        assertTrue(result.explanation().contains("HTF_SUPPORTED_CONSOLIDATION"));
    }

    @Test
    void neutralMinuteCannotContinueWithoutStrongOneHourAuthority() {
        TradeSignal one = signal(SignalDecision.NEUTRAL, 19, 8, 5);
        TradeSignal five = signal(SignalDecision.WATCH, 15, 9, 10);
        TradeSignal hour = signal(SignalDecision.WATCH, 18, 11, 9);
        var result = policy.evaluate(one, five, hour, 21, 12, 15);
        assertFalse(result.extendTarget());
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


    @Test
    void solTakeProfitExtendsWhenImmutableBuyThesisIsStillIntactDespiteBinaryTrendFloorFailure() {
        // Exact SOLUSDT 2026-08-19 TP-continuation defect: entry trend/structure/momentum/volume
        // 21/5/13/19 cooled to 16/2/15/7 while 1m=WATCH, 5m=NEUTRAL, 1h=WATCH.
        // PositionManagementService scored trend pressure 2/8 + momentum pressure 0/5 and HOLD.
        // Continuation must use the same thesis pressure instead of failing only because 16 < 18.
        TradeSignal one = signal(SignalDecision.WATCH, 16, 2, 15, 7);
        TradeSignal five = signal(SignalDecision.NEUTRAL, 18, 3, 9, 11);
        TradeSignal hour = signal(SignalDecision.WATCH, 23, 6, 9, 16);

        var result = policy.evaluate(one, five, hour, 21, 5, 13, 19, 78, 88);

        assertTrue(result.extendTarget());
        assertTrue(result.explanation().contains("THESIS_INTACT_CONSOLIDATION"));
        assertTrue(result.explanation().contains("2/8 trend, 0/5 momentum"));
    }

    @Test
    void thesisIntactPathStillRejectsSevereTrendAndStructureBreak() {
        TradeSignal one = signal(SignalDecision.WATCH, 10, 0, 13, 7);
        TradeSignal five = signal(SignalDecision.NEUTRAL, 14, 1, 8, 9);
        TradeSignal hour = signal(SignalDecision.WATCH, 18, 3, 8, 12);

        var result = policy.evaluate(one, five, hour, 21, 5, 13, 19, 78, 88);

        assertFalse(result.extendTarget());
    }

    @Test
    void thesisIntactPathNeverOverridesBearishHigherTimeframe() {
        TradeSignal one = signal(SignalDecision.WATCH, 16, 2, 15, 7);
        TradeSignal five = signal(SignalDecision.SELL, 18, 3, 9, 11);
        TradeSignal hour = signal(SignalDecision.WATCH, 23, 6, 9, 16);

        var result = policy.evaluate(one, five, hour, 21, 5, 13, 19, 78, 88);

        assertFalse(result.extendTarget());
        assertTrue(result.explanation().contains("bearish timeframe"));
    }
    private TradeSignal signal(SignalDecision decision, int trend, int momentum, int volume) {
        return signal(decision, trend, 0, momentum, volume);
    }

    private TradeSignal signal(SignalDecision decision, int trend, int structure, int momentum, int volume) {
        return TradeSignal.builder()
                .decision(decision)
                .trendScore(trend)
                .trendStructureScore(structure)
                .momentumScore(momentum)
                .volumeScore(volume)
                .build();
    }
}
