package com.crypto.shared;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedTradingReadinessTest {
    @Test void missingAndUnapprovedSymbolsRemainBlockedWithoutBlockingApprovedSymbol() {
        var registry=new SharedTradingReadiness();registry.begin();
        registry.retainSymbols(List.of("BTCUSDT","SOLUSDT","UNIUSDT"));
        registry.record("BTCUSDT",SharedTradingReadiness.Reason.READY);
        registry.record("SOLUSDT",SharedTradingReadiness.Reason.CUTOVER_REQUIRED);
        assertTrue(registry.allowed("BTCUSDT"));assertFalse(registry.allowed("SOLUSDT"));
        assertFalse(registry.allowed("UNIUSDT"));assertEquals("DEGRADED",registry.snapshot().get("status"));
    }
    @Test void readinessExpiresAtExactSixtySecondBoundaryAndRequiresSuccessfulRefresh() {
        var clock=new AtomicReference<>(Instant.parse("2026-10-03T22:20:06Z"));
        var registry=new SharedTradingReadiness(clock::get);registry.begin();
        registry.record("BTCUSDT",SharedTradingReadiness.Reason.READY);
        clock.set(clock.get().plusSeconds(59));assertTrue(registry.allowed("BTCUSDT"));
        clock.set(clock.get().plusSeconds(1));assertFalse(registry.allowed("BTCUSDT"));
        assertEquals(SharedTradingReadiness.Reason.CHECK_EXPIRED,registry.reason("BTCUSDT"));
        registry.record("BTCUSDT",SharedTradingReadiness.Reason.CHECK_FAILED);assertFalse(registry.allowed("BTCUSDT"));
        registry.record("BTCUSDT",SharedTradingReadiness.Reason.READY);assertTrue(registry.allowed("BTCUSDT"));
    }
    @Test void removedAndReenabledSymbolCannotReuseEarlierApprovalCheck() {
        var registry=new SharedTradingReadiness();registry.begin();
        registry.record("BTCUSDT",SharedTradingReadiness.Reason.READY);
        registry.retainSymbols(List.of());registry.retainSymbols(List.of("BTCUSDT"));
        assertFalse(registry.allowed("BTCUSDT"));assertEquals(SharedTradingReadiness.Reason.NOT_CHECKED,registry.reason("BTCUSDT"));
    }
    @Test void allBlockedStillProducesReadableDiagnostics() {
        var registry=new SharedTradingReadiness();registry.begin();registry.retainSymbols(List.of("SOLUSDT"));
        assertEquals("NO_READY_SYMBOLS",registry.snapshot().get("status"));
        assertFalse(registry.allowed("SOLUSDT"));assertFalse(registry.allowed("UNKNOWN"));
    }
}
