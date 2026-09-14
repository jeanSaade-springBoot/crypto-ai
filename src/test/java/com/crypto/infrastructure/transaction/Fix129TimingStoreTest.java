package com.crypto.infrastructure.transaction;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Fix129TimingStoreTest {
    private KlineTiming.Measurement measurement() {
        return new KlineTiming.Measurement(new KlineTiming.Context("test", "UNIUSDT", "1m",
                Instant.EPOCH, Instant.EPOCH, null), "OBSERVER_TRANSACTION", Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1), 1000, "RETURNED", "caller");
    }
    @Test void persistenceUsesDedicatedThreadAndFailureDoesNotEscapeToCaller() throws Exception {
        DataSource source = mock(DataSource.class);
        var reached = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var thread = new AtomicReference<String>();
        when(source.getConnection()).thenAnswer(call -> {
            thread.set(Thread.currentThread().getName());
            reached.countDown();
            release.await(5, TimeUnit.SECONDS);
            throw new SQLException("unavailable");
        });
        var store = new KlineTimingStore(source);
        try {
            assertDoesNotThrow(() -> store.submit(measurement()));
            assertTrue(reached.await(5, TimeUnit.SECONDS));
            assertEquals("fix129-diagnostics", thread.get());
            assertNotEquals(Thread.currentThread().getName(), thread.get());
        } finally { release.countDown(); store.shutdown(); }
    }
    @Test void rejectedDiagnosticsNeverFallBackToCallerJdbc() {
        DataSource source = mock(DataSource.class);
        var store = new KlineTimingStore(source);
        store.shutdown();
        assertDoesNotThrow(() -> store.submit(measurement()));
        verifyNoInteractions(source);
    }
}
