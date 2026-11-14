package com.crypto.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Small in-memory operational counters; they never gate trading. */
@Component
public class Fix154Metrics {
    private final AtomicLong untrustedBearishOneHourBlocks = new AtomicLong();
    private final AtomicLong auditWriteFailures = new AtomicLong();
    private final AtomicLong softCappedEntries = new AtomicLong();
    private final AtomicLong softAddBlocks = new AtomicLong();

    public void untrustedBearishOneHourBlocked() { untrustedBearishOneHourBlocks.incrementAndGet(); }
    public void auditWriteFailed() { auditWriteFailures.incrementAndGet(); }
    public void softCappedEntry() { softCappedEntries.incrementAndGet(); }
    public void softAddBlocked() { softAddBlocks.incrementAndGet(); }
    public Map<String, Long> snapshot() {
        return Map.of("untrusted_bearish_1h_block_count", untrustedBearishOneHourBlocks.get(),
                "audit_write_failure_count", auditWriteFailures.get(),
                "soft_capped_entry_count", softCappedEntries.get(),
                "soft_add_blocked_count", softAddBlocks.get());
    }
}
