package com.crypto.client.binance.websocket;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class CandleGapDiagnosticsTest {
    private final Instant base = Instant.parse("2026-09-23T12:00:00Z");
    @Test void excludesFormingCandleAndCloseGrace() {
        assertTrue(CandleGapDiagnostics.missing(base, base.plusSeconds(89),60, Set.of()).isEmpty());
        assertEquals(Set.of(base), CandleGapDiagnostics.missing(base, base.plusSeconds(90),60,Set.of()));
    }
    @Test void excludesPeriodBeforeSubscriptionAndPartialFirstCandle() {
        assertEquals(Set.of(base.plusSeconds(60)), CandleGapDiagnostics.missing(base.plusSeconds(1), base.plusSeconds(150),60,Set.of()));
    }
    @Test void detectsInteriorAndTrailingGapsWithoutNewReceipt() {
        assertEquals(Set.of(base.plusSeconds(60),base.plusSeconds(180)), CandleGapDiagnostics.missing(base, base.plusSeconds(270),60,Set.of(base,base.plusSeconds(120))));
    }
    @Test void latePersistedCloseRemovesExactMissingIdentity() {
        assertEquals(Set.of(base), CandleGapDiagnostics.missing(base,base.plusSeconds(90),60,Set.of()));
        assertTrue(CandleGapDiagnostics.missing(base,base.plusSeconds(90),60,Set.of(base)).isEmpty());
    }
    @Test void rangesAreExactAndDoNotBridgePresentCandles() {
        var ranges = CandleGapDiagnostics.ranges(Set.of(base,base.plusSeconds(60),base.plusSeconds(180)),60);
        assertEquals(2,ranges.size()); assertEquals(2,ranges.get(0).count()); assertEquals(base.plusSeconds(180),ranges.get(1).from());
    }
    @Test void horizonBoundsMemoryAndCalendarIntervalsAreNotGuessed() {
        assertTrue(CandleGapDiagnostics.missing(base.minusSeconds(10*86400),base,60,Set.of()).size()<=4320);
        assertEquals(0,CandleGapDiagnostics.intervalSeconds("1M"));
    }
}
