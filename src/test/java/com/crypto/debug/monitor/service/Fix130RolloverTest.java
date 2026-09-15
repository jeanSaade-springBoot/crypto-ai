package com.crypto.debug.monitor.service;

import com.crypto.debug.monitor.domain.PriceMoveMonitorSettings;
import com.crypto.debug.monitor.repository.*;
import com.crypto.repository.*;
import com.crypto.wallet.repository.WalletTradeRepository;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Fix130RolloverTest {
    PriceMoveMonitorService monitor;
    PriceMoveFinalizationStore store;
    WalletTradeRepository trades;
    final Instant before=Instant.parse("2026-09-15T07:59:00Z"),after=Instant.parse("2026-09-15T08:00:00Z");
    @BeforeEach void setup() {
        var settings=mock(PriceMoveMonitorSettingsRepository.class);
        when(settings.findById(1L)).thenReturn(Optional.of(PriceMoveMonitorSettings.builder().enabled(true).selectedSymbols("UNIUSDT").build()));
        var events=mock(PriceMoveEventRepository.class);
        when(events.findBySymbolAndBlockStartTimeAndDirection(any(),any(),any())).thenReturn(Optional.empty());
        trades=mock(WalletTradeRepository.class);
        monitor=new PriceMoveMonitorService(settings,events,mock(TradeSignalRepository.class),mock(CandleRepository.class),trades,mock(ExecutionOpportunityRepository.class));
        store=mock(PriceMoveFinalizationStore.class);monitor.setFinalizationStore(store);
        monitor.onPrice("UNIUSDT",BigDecimal.ONE,before);
    }
    @Test void failedHandoffRetainsTrackerAndTriggeringPointThenRetries() {
        doThrow(new IllegalStateException("database unavailable")).doNothing().when(store).enqueue(any());
        assertThrows(IllegalStateException.class,() -> monitor.onPrice("UNIUSDT",new BigDecimal("1.01"),after));
        assertEquals("ROLLOVER_PERSIST_FAILED",monitor.activeTracker("UNIUSDT").get("phase"));
        assertEquals(1,monitor.activeTracker("UNIUSDT").get("retainedPoints"));
        assertEquals(Instant.parse("2026-09-15T00:00:00Z"),monitor.activeTracker("UNIUSDT").get("blockStart"));
        monitor.onPrice("UNIUSDT",new BigDecimal("1.02"),after.plusSeconds(61));
        assertEquals(after,monitor.activeTracker("UNIUSDT").get("blockStart"));
        assertEquals(0,monitor.activeTracker("UNIUSDT").get("retainedPoints"));
        verifyNoInteractions(trades);
    }
    @Test void concurrentArrivalWaitsForHandoffAndEntersNewBlock() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var saved=new AtomicReference<PriceMoveBlockSnapshot>();
        doAnswer(call -> {saved.set(call.getArgument(0));entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return null;}).when(store).enqueue(any());
        var executor=Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(() -> monitor.onPrice("UNIUSDT",new BigDecimal("1.01"),after));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var second=executor.submit(() -> monitor.onPrice("UNIUSDT",new BigDecimal("1.02"),after.plusSeconds(61)));
            release.countDown();first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);
            assertEquals("2026-09-15T00:00:00Z",saved.get().blockStart());
            assertEquals(new BigDecimal("1.02"),monitor.activeTracker("UNIUSDT").get("lastPrice"));
            verify(store,times(1)).enqueue(any());verifyNoInteractions(trades);
        } finally {release.countDown();executor.shutdownNow();}
    }
    @Test void onlyMysqlDeadlockRollbackQualifiesForAutomaticRetry() {
        assertTrue(PriceMoveFinalizationWorker.rollbackProven(new RuntimeException(new SQLException("deadlock","40001",1213))));
        assertFalse(PriceMoveFinalizationWorker.rollbackProven(new SQLException("connection lost","08006",0)));
        assertFalse(PriceMoveFinalizationWorker.rollbackProven(new IllegalStateException()));
    }
}
