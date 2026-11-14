package com.crypto.service;

import static org.junit.jupiter.api.Assertions.*;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.junit.jupiter.api.Test;

class HigherTimeframeBuyAssessmentServiceTest {
    private final HigherTimeframeBuyAssessmentService service = new HigherTimeframeBuyAssessmentService();

    @Test void hardTrustedBearishOneHourVetoesWhenFiveMinuteIsNotBearish() {
        var result = service.assess(signal(SignalDecision.BUY, true, 80), true,
                signal(SignalDecision.STRONG_SELL, true, 70), true);
        assertFalse(result.allowed());
        assertEquals("HTF_TRUSTED_BEARISH_VETO", result.code());
    }

    @Test void softTrustedBearishUsesDetachedNeutralContextAndCapsSize() {
        TradeSignal one = signal(SignalDecision.SELL, true, 80);
        var result = service.assess(signal(SignalDecision.BUY, true, 80), true, one, true);
        assertTrue(result.allowed());
        assertEquals(SignalDecision.NEUTRAL, result.effectiveOneHour());
        assertEquals(25, result.maxInitialPercent());
        assertEquals(SignalDecision.SELL, one.getDecision());
    }

    @Test void bearishFiveMinuteKeepsLegacyReasonBeforeHardOneHour() {
        var result = service.assess(signal(SignalDecision.SELL, true, 80), true,
                signal(SignalDecision.STRONG_SELL, true, 80), true);
        assertEquals("5M_BEARISH_VETO", result.code());
        assertTrue(result.hardConfirmationPresent());
    }

    @Test void unknownBearishOneHourFailsClosed() {
        var result = service.assess(signal(SignalDecision.BUY, true, 80), true,
                signal(SignalDecision.SELL, false, 80), true);
        assertEquals("1H_BEARISH_VETO", result.code());
    }

    @Test void untrustedNonBearishOneHourRetainsItsRawAuthority() {
        var result = service.assess(signal(SignalDecision.BUY, true, 80), true,
                signal(SignalDecision.BUY, false, 80), true);
        assertTrue(result.allowed());
        assertEquals(SignalDecision.BUY, result.effectiveOneHour());
        assertEquals(HigherTimeframeBuyAssessmentService.Band.NOT_TRUSTED, result.band());
    }

    @Test void strongSellAtSixtyNineIsSoftButAtSeventyIsHard() {
        var soft = service.assess(signal(SignalDecision.BUY, true, 80), true,
                signal(SignalDecision.STRONG_SELL, true, 69), true);
        var hard = service.assess(signal(SignalDecision.BUY, true, 80), true,
                signal(SignalDecision.STRONG_SELL, true, 70), true);
        assertTrue(soft.allowed());
        assertEquals(HigherTimeframeBuyAssessmentService.Band.SOFT_CONFLICT, soft.band());
        assertEquals(25, soft.maxInitialPercent());
        assertFalse(hard.allowed());
        assertEquals("HTF_TRUSTED_BEARISH_VETO", hard.code());
    }

    @Test void fiveMinuteQualityDoesNotChangeTheLegacyBearishVeto() {
        TradeSignal five = signal(SignalDecision.SELL, false, 80);
        var result = service.assess(five, true, signal(SignalDecision.BUY, true, 80), true);
        assertEquals("5M_BEARISH_VETO", result.code());
    }

    @Test void bnb150aPersistedUnknownBearishContextKeepsLegacyOneHourVeto() {
        var result = service.assess(signal(SignalDecision.BUY, true, 80), true,
                signal(SignalDecision.SELL, false, 70), true);
        assertFalse(result.allowed());
        assertEquals("1H_BEARISH_VETO", result.code());
    }

    @Test void bnb150bReplayRegeneratedQualityEvidenceMakesTheSameBearishRowSoft() {
        var result = service.assess(signal(SignalDecision.BUY, true, 80), true,
                signal(SignalDecision.SELL, true, 70), true);
        assertTrue(result.allowed());
        assertEquals(HigherTimeframeBuyAssessmentService.Band.SOFT_CONFLICT, result.band());
        assertEquals(25, result.maxInitialPercent());
        // Risk/reward is intentionally evaluated downstream; FIX-154 does not mask it.
    }

    private TradeSignal signal(SignalDecision decision, boolean quality, int confidence) {
        TradeSignal signal = new TradeSignal();
        signal.setDecision(decision); signal.setDataQualityValid(quality); signal.setConfidenceScore(confidence);
        return signal;
    }
}
