package com.crypto.client.binance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class BinanceRestAdmissionGateTest {
    @Test void admissionIsRejectedOnlyUntilTheSharedCooldownExpires() {
        AtomicLong clock = new AtomicLong(1_000L);
        BinanceRestAdmissionGate gate = new BinanceRestAdmissionGate(clock::get);
        gate.blockFor(Duration.ofNanos(500));
        assertThrows(BinanceRestAdmissionGate.BinanceRestAdmissionDeferredException.class, gate::admit);
        clock.addAndGet(500L);
        assertDoesNotThrow(gate::admit);
    }

    @Test void laterCooldownNeverShortensAnExistingBan() {
        AtomicLong clock = new AtomicLong(1_000L);
        BinanceRestAdmissionGate gate = new BinanceRestAdmissionGate(clock::get);
        gate.blockFor(Duration.ofNanos(1_000));
        gate.blockFor(Duration.ofNanos(100));
        clock.addAndGet(100L);
        assertThrows(BinanceRestAdmissionGate.BinanceRestAdmissionDeferredException.class, gate::admit);
        clock.addAndGet(900L);
        assertDoesNotThrow(gate::admit);
    }
}
