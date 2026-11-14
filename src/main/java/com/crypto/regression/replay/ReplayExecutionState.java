package com.crypto.regression.replay;

import com.crypto.domain.TradeSignal;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** FIX-156 Replay-owned per-run state; it never publishes into Production execution state. */
@Component
public final class ReplayExecutionState {
    private final ThreadLocal<Set<String>> consumedSignalKeys = ThreadLocal.withInitial(HashSet::new);

    public Scope open(long runId, List<TradeSignal> timeline) {
        consumedSignalKeys.set(new HashSet<>());
        return new Scope(consumedSignalKeys);
    }

    public void fix122Enabled(boolean enabled) {
        // Baseline policy is fixture-owned; this state has no Production switch to mutate.
    }

    public void reference(Instant at) { }
    public void referencePrice(String symbol, Instant at, java.math.BigDecimal price) { }
    public void recordStop(String symbol, long positionId, Instant at, String reason) { }

    public void markEntryConsumed(Long sourceSignalId) {
        consumedSignalKeys.get().add(sourceSignalId == null ? "replay-null-signal" : "source-" + sourceSignalId);
    }

    public static final class Scope implements AutoCloseable {
        private final ThreadLocal<Set<String>> keys;
        private Scope(ThreadLocal<Set<String>> keys) { this.keys = keys; }
        @Override public void close() { keys.remove(); }
    }
}
