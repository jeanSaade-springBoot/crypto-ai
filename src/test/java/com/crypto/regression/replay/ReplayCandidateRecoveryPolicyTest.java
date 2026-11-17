package com.crypto.regression.replay;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ReplayCandidateRecoveryPolicyTest {
    @Test
    void acceptsOnlyStrongRangeOrAtrCandidates() {
        assertTrue(ReplayCandidateRecoveryPolicy.qualifies(75, 65, "RANGE_ENTRY_LOCATION"));
        assertTrue(ReplayCandidateRecoveryPolicy.qualifies(91, 82, "ATR_RISK"));
        assertFalse(ReplayCandidateRecoveryPolicy.qualifies(74, 82, "RANGE_ENTRY_LOCATION"));
        assertFalse(ReplayCandidateRecoveryPolicy.qualifies(91, 64, "ATR_RISK"));
        assertFalse(ReplayCandidateRecoveryPolicy.qualifies(91, 82, "MTF_CONFLUENCE"));
    }

    @Test
    void appliesAdverseCostsToVirtualSettlement() {
        BigDecimal entry = ReplayCandidateRecoveryPolicy.adverseBuyFill(new BigDecimal("100"));
        BigDecimal exit = ReplayCandidateRecoveryPolicy.adverseSellFill(new BigDecimal("101"));
        ReplayCandidateRecoveryPolicy.Settlement result = ReplayCandidateRecoveryPolicy.settle(entry, exit);
        assertEquals(new BigDecimal("100.050000000000"), entry);
        assertTrue(result.feesUsdt().compareTo(new BigDecimal("0.20")) > 0);
        assertTrue(result.realizedPnlUsdt().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void rejectsInvalidLevelsAndUsesConservativeStopGap() {
        assertTrue(ReplayCandidateRecoveryPolicy.validLevels(new BigDecimal("90"), new BigDecimal("100"), new BigDecimal("110")));
        assertFalse(ReplayCandidateRecoveryPolicy.validLevels(new BigDecimal("101"), new BigDecimal("100"), new BigDecimal("110")));
        assertFalse(ReplayCandidateRecoveryPolicy.validLevels(new BigDecimal("90"), new BigDecimal("100"), new BigDecimal("99")));
        assertEquals(new BigDecimal("88"), ReplayCandidateRecoveryPolicy.stopFill(new BigDecimal("88"), new BigDecimal("90")));
        assertEquals(new BigDecimal("90"), ReplayCandidateRecoveryPolicy.stopFill(new BigDecimal("92"), new BigDecimal("90")));
    }

    @Test
    void keepsTheFirstCandidateWindowAndUsesAnExclusiveAntiChaseBoundary() {
        BigDecimal candidate = new BigDecimal("100");
        assertEquals(new BigDecimal("100.750000000000"),
                ReplayCandidateRecoveryPolicy.releaseCeiling(candidate, null, "RANGE_ENTRY_LOCATION"));
        assertTrue(ReplayCandidateRecoveryPolicy.releaseCeiling(candidate, null, "RANGE_ENTRY_LOCATION")
                .compareTo(new BigDecimal("100.75")) == 0);
        assertEquals(new BigDecimal("99.5"), ReplayCandidateRecoveryPolicy.releaseCeiling(candidate,
                new BigDecimal("99.5"), "ATR_RISK"));
    }
}
