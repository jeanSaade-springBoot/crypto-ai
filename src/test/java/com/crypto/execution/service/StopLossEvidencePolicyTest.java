package com.crypto.execution.service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class StopLossEvidencePolicyTest {
    private final Instant stop=Instant.parse("2026-09-07T14:05:28Z");
    private final StopLossEvidencePolicy.Boundary boundary=new StopLossEvidencePolicy.Boundary(1812,stop);
    private String check(String open,String generated) {
        return StopLossEvidencePolicy.exclusion("1m",Instant.parse(open),Instant.parse(generated),
                Instant.parse("2026-09-07T14:10:00Z"),boundary);
    }
    @Test void delayedOldCandleIsNotFresh() {
        assertEquals("PRE_STOP_CANDLE",check("2026-09-07T14:04:00Z","2026-09-07T14:09:00Z"));
    }
    @Test void candleClosingAfterStopIsEligibleEvenIfItOpenedBeforeStop() {
        assertNull(check("2026-09-07T14:05:00Z","2026-09-07T14:06:02Z"));
    }
    @Test void exactInclusiveCloseEqualityIsExcluded() {
        Instant open=Instant.parse("2026-09-07T14:05:00Z");
        var b=new StopLossEvidencePolicy.Boundary(1,open.plusSeconds(60).minusMillis(1));
        assertEquals("PRE_STOP_CANDLE",StopLossEvidencePolicy.exclusion("1m",open,open.plusSeconds(61),open.plusSeconds(65),b));
    }
    @Test void missingLineageIsExplicitAndDoesNotInventGenerationFallback() {
        assertEquals("LINEAGE_UNRESOLVED",StopLossEvidencePolicy.exclusion("1m",null,stop,stop,boundary));
    }
    @Test void unavailableSignalAndUnclosedCandleAreExcluded() {
        assertEquals("NOT_AVAILABLE",check("2026-09-07T14:09:00Z","2026-09-07T14:10:01Z"));
        assertEquals("NOT_AVAILABLE",check("2026-09-07T14:10:00Z","2026-09-07T14:10:00Z"));
    }
    @Test void noBoundaryPreservesLegacyMissingLineageBehavior() {
        assertNull(StopLossEvidencePolicy.exclusion(null,null,null,null,null));
    }
}
