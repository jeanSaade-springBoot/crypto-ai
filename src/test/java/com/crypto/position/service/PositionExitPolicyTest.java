package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionExitPolicyTest {
    private final PositionExitPolicy policy = new PositionExitPolicy();

    @Test
    void doesNotSellEarlierPullbackWhenFiveMinuteWatchAndOneHourBuyRemainSupportive() {
        var result = policy.evaluateProfitLockBreach(
                signal("1m", SignalDecision.NEUTRAL),
                signal("5m", SignalDecision.WATCH),
                signal("1h", SignalDecision.BUY));
        assertFalse(result.exit());
        assertEquals("PROFIT_LOCK_HOLD", result.code());
    }

    @Test
    void holdsWatchWatchConsolidationInsteadOfTreatingOneMinuteNoiseAsExit() {
        var result = policy.evaluateProfitLockBreach(
                signal("1m", SignalDecision.NEUTRAL),
                signal("5m", SignalDecision.WATCH),
                signal("1h", SignalDecision.WATCH));
        assertFalse(result.exit());
        assertEquals("PROFIT_LOCK_HOLD", result.code());
    }

    @Test
    void oneHourSellWithNonSupportiveFiveMinuteCreatesNormalExitAuthority() {
        var result = policy.evaluateNormalExit(
                signal("1m", SignalDecision.NEUTRAL),
                signal("5m", SignalDecision.NEUTRAL),
                signal("1h", SignalDecision.SELL));
        assertTrue(result.exit());
        assertEquals("HTF_SELL_CONFIRMED", result.code());
    }

    @Test
    void oneHourSellDoesNotOverrideStillSupportiveFiveMinuteWatch() {
        var result = policy.evaluateNormalExit(
                signal("1m", SignalDecision.NEUTRAL),
                signal("5m", SignalDecision.WATCH),
                signal("1h", SignalDecision.SELL));
        assertFalse(result.exit());
    }

    @Test
    void keepsExistingOneMinuteAndFiveMinuteBearishConfirmationPath() {
        var result = policy.evaluateNormalExit(
                signal("1m", SignalDecision.SELL),
                signal("5m", SignalDecision.SELL),
                signal("1h", SignalDecision.WATCH));
        assertTrue(result.exit());
        assertEquals("SELL_CONFIRMED", result.code());
    }

    private TradeSignal signal(String interval, SignalDecision decision) {
        return TradeSignal.builder().interval(interval).decision(decision).build();
    }
}
