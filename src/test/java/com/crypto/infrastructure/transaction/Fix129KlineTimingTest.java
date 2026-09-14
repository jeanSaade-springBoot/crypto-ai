package com.crypto.infrastructure.transaction;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class Fix129KlineTimingTest {
    @Test void thresholdPreservesExactLineageAndMeasuresElapsedWork() {
        var clock = new AtomicLong();
        var rows = new ArrayList<KlineTiming.Measurement>();
        var timing = new KlineTiming(clock::get, rows::add);
        Instant candle = Instant.parse("2026-09-10T07:59:00Z");
        var context = timing.context("UNIUSDT", "1m", candle, candle.plusSeconds(60), null);
        timing.measure(context, "INPUT_TRANSACTION", false, () -> clock.addAndGet(999_000_000L));
        assertTrue(rows.isEmpty());
        timing.measure(context, "OBSERVER_TRANSACTION", false, () -> clock.addAndGet(1_000_000_000L));
        assertEquals(1, rows.size());
        assertSame(context, rows.get(0).context());
        assertEquals(candle, rows.get(0).context().candleOpenTime());
        assertEquals(1000L, rows.get(0).elapsedMs());
        assertEquals("RETURNED", rows.get(0).outcome());
    }
    @Test void failingSinkCannotMaskOriginalExceptionOrRunWorkTwice() {
        var timing = new KlineTiming(() -> 0L, row -> { throw new IllegalStateException("sink unavailable"); });
        var count = new AtomicLong();
        var original = new IllegalArgumentException("original");
        assertSame(original, assertThrows(IllegalArgumentException.class, () -> timing.measure(
                timing.context("UNIUSDT", "1m", Instant.EPOCH, Instant.EPOCH, null), "INPUT_TRANSACTION", false,
                () -> { count.incrementAndGet(); throw original; })));
        assertEquals(1, count.get());
    }
    @Test void fastFailureIsRecordedAndStillPropagates() {
        var rows = new ArrayList<KlineTiming.Measurement>();
        var timing = new KlineTiming(() -> 0L, rows::add);
        assertThrows(IllegalStateException.class, () -> timing.measure(
                timing.context("UNIUSDT", "1m", Instant.EPOCH, Instant.EPOCH, null), "PROTECTION_TRANSACTION", false,
                () -> { throw new IllegalStateException(); }));
        assertEquals("FAILED", rows.get(0).outcome());
    }
    @Test void fastBlockRolloverIsRecordedWithoutInventingCandleIdentity() {
        var rows = new ArrayList<KlineTiming.Measurement>();
        var timing = new KlineTiming(() -> 0L, rows::add);
        timing.measure(timing.context("UNIUSDT", null, null, Instant.EPOCH.plusSeconds(28800), Instant.EPOCH),
                "BLOCK_FINALIZATION", true, () -> {});
        assertEquals(1, rows.size());
        assertNull(rows.get(0).context().candleOpenTime());
        assertEquals(Instant.EPOCH, rows.get(0).context().blockStart());
    }
    @Test void failingSinkDoesNotTurnSuccessfulWorkIntoFailure() {
        var timing = new KlineTiming(() -> 0L, row -> { throw new IllegalStateException(); });
        assertDoesNotThrow(() -> timing.measure(timing.context("UNIUSDT", null, null, null, Instant.EPOCH),
                "BLOCK_FINALIZATION", true, () -> {}));
    }
}
