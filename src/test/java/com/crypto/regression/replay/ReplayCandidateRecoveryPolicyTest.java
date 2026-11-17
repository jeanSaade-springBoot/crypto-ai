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
}
