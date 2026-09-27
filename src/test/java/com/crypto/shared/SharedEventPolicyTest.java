package com.crypto.shared;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class SharedEventPolicyTest {
    final Instant now=Instant.parse("2026-09-25T12:00:00Z");
    @Test void priceBoundaryAndUnknownTime() {
        assertTrue(SharedEventPolicy.priceEligible("LIVE_WEBSOCKET","LIVE",now.minusSeconds(15),null,now));
        assertFalse(SharedEventPolicy.priceEligible("LIVE_WEBSOCKET","LIVE",now.minusSeconds(15).minusNanos(1),null,now));
        assertFalse(SharedEventPolicy.priceEligible("LIVE_WEBSOCKET","LIVE",null,null,now));
        assertFalse(SharedEventPolicy.priceEligible("LIVE_WEBSOCKET","LIVE",now.plusNanos(1),null,now));
    }
    @Test void sequenceDoesNotOverrideSourceOrObservationOrder() {
        assertFalse(SharedEventPolicy.priceEligible("REST_REPAIR","HISTORICAL_ONLY",now,null,now));
        assertFalse(SharedEventPolicy.priceEligible("LIVE_WEBSOCKET","DUPLICATE",now,null,now));
        assertFalse(SharedEventPolicy.priceEligible("LIVE_WEBSOCKET","LIVE",now.minusSeconds(1),now,now));
        assertTrue(SharedEventPolicy.priceEligible("LIVE_WEBSOCKET","LIVE",now,now,now));
    }
    @Test void allApprovedCloseGracesHaveExactBoundaries() {
        String[] intervals={"1m","5m","1h","4h","1d","15m"};long[] seconds={90,120,300,600,1800,120};
        for(int n=0;n<intervals.length;n++) {
            assertTrue(SharedEventPolicy.closeEligible(intervals[n],now.minusSeconds(seconds[n]),now));
            assertFalse(SharedEventPolicy.closeEligible(intervals[n],now.minusSeconds(seconds[n]).minusNanos(1),now));
            assertFalse(SharedEventPolicy.closeEligible(intervals[n],now.plusNanos(1),now));
        }
    }
}
