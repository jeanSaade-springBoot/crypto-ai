package com.crypto.client.binance.websocket;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class GenerationGateTest {
    @Test void pendingRejectsAndRevocationCannotReopen() {
        var gate = new GenerationGate();
        assertNull(gate.tryAdmit()); gate.revoke();
        assertTrue(gate.isDrained()); assertFalse(gate.activate()); assertNull(gate.tryAdmit());
    }
    @Test void activeEmptyIsNotDrained() {
        var gate = new GenerationGate(); assertTrue(gate.activate());
        try (var permit = gate.tryAdmit()) { assertNotNull(permit); }
        assertFalse(gate.isDrained()); gate.revoke(); assertTrue(gate.isDrained());
    }
    @Test void admittedCallbackHeldAcrossRevocationCompletesDrainOnFinalRelease() throws Exception {
        var gate = new GenerationGate(); gate.activate();
        var admitted = new CountDownLatch(1); var release = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        try {
            var future = worker.submit(() -> {
                try (var permit = gate.tryAdmit()) {
                    assertNotNull(permit); admitted.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException ex) { throw new AssertionError(ex); }
            });
            assertTrue(admitted.await(5, TimeUnit.SECONDS));
            gate.revoke(); assertEquals(1, gate.inFlight()); assertFalse(gate.isDrained());
            assertNull(gate.tryAdmit()); release.countDown(); future.get(5, TimeUnit.SECONDS);
            assertTrue(gate.isDrained()); assertNotNull(gate.drainedAt());
        } finally { release.countDown(); worker.shutdownNow(); }
    }
    @Test void permitReleaseIsIdempotentAndExceptionsRelease() {
        var gate = new GenerationGate(); gate.activate(); var permit = gate.tryAdmit();
        gate.revoke(); permit.close(); permit.close(); assertEquals(0, gate.inFlight()); assertTrue(gate.isDrained());
    }
}
