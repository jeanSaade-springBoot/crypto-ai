package com.crypto.regression.replay;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayBuyValidationPolicyTest {

    private final ReplayBuyValidationPolicy policy = new ReplayBuyValidationPolicy();

    @Test
    void acceptsAOneMinuteBullishSignalUsingOnlyFrozenProfileEvidence() {
        TradeSignal signal = new TradeSignal();
        signal.setInterval("1m");
        signal.setDecision(SignalDecision.BUY);
        signal.setGeneratedAt(Instant.EPOCH);
        signal.setAtrRecommendedPositionPercent(30);

        ReplayBuyValidationPolicy.Result result = policy.evaluate(signal, 0, profile(false));

        assertTrue(result.allowed());
        assertTrue(result.positionPercent() == 30);
    }

    @Test
    void refusesAddsWhenFrozenProfileRequiresANewBuyTransition() {
        TradeSignal signal = new TradeSignal();
        signal.setInterval("1m");
        signal.setDecision(SignalDecision.STRONG_BUY);
        signal.setGeneratedAt(Instant.EPOCH);

        assertFalse(policy.evaluate(signal, 25, profile(true)).allowed());
    }

    private ReplayPolicyProfile profile(boolean requireNewBuyTransition) {
        return new ReplayPolicyProfile(1L, "hash", Instant.EPOCH, "fingerprint", "FIX156",
                requireNewBuyTransition, BigDecimal.ZERO, BigDecimal.TEN, 1, "constraints",
                BigDecimal.ONE, true, BigDecimal.valueOf(70), BigDecimal.valueOf(40), BigDecimal.TEN,
                BigDecimal.TEN);
    }
}
