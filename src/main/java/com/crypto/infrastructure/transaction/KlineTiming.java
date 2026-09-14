package com.crypto.infrastructure.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** FIX-129: observational wall-duration measurements, never decision inputs.
 * nanoTime measures elapsed work including transaction commit/rollback. UTC wall
 * timestamps locate observations; Binance event time is retained separately.
 */
@Component
public class KlineTiming {
    private static final Logger log = LoggerFactory.getLogger(KlineTiming.class);
    private final LongSupplier nanos;
    private final Consumer<Measurement> sink;
    public record Context(String correlationId, String symbol, String interval,
                          Instant candleOpenTime, Instant observedAt, Instant blockStart) {}
    public record Measurement(Context context, String stage, Instant startedAt, Instant finishedAt,
                              long elapsedMs, String outcome, String threadName) {}
    @Autowired
    public KlineTiming(KlineTimingStore store) { this(System::nanoTime, store::submit); }
    KlineTiming(LongSupplier nanos, Consumer<Measurement> sink) {
        this.nanos = nanos; this.sink = sink;
    }
    // Directly constructed legacy unit-test subjects still get logging, without a database.
    public static KlineTiming loggingOnly() { return new KlineTiming(System::nanoTime, m -> {}); }
    public Context context(String symbol, String interval, Instant candle, Instant observed, Instant block) {
        return new Context(UUID.randomUUID().toString(), symbol, interval, candle, observed, block);
    }
    public void measure(Context context, String stage, boolean alwaysRecord, Runnable work) {
        Instant started = Instant.now();
        long start = nanos.getAsLong();
        String outcome = "FAILED";
        // A BEGIN without END can locate a stuck block. It is not proof of completion.
        if (alwaysRecord) log.info("[FIX-129][BLOCK_BEGIN] context={}, stage={}, thread={}", context, stage, Thread.currentThread().getName());
        try {
            work.run();
            outcome = "RETURNED";
        } finally {
            long elapsed = Math.max(0L, (nanos.getAsLong() - start) / 1_000_000L);
            if (alwaysRecord || elapsed >= 1000 || !"RETURNED".equals(outcome)) {
                Measurement m = new Measurement(context, stage, started, Instant.now(), elapsed,
                        outcome, Thread.currentThread().getName());
                // Diagnostic sink failures must never mask the business exception or change retry eligibility.
                try {
                    log.info("[FIX-129][STAGE_TIMING] context={}, stage={}, elapsedMs={}, outcome={}, thread={}",
                            context, stage, elapsed, outcome, m.threadName());
                    sink.accept(m);
                } catch (RuntimeException ignored) {
                    log.warn("[FIX-129][DIAGNOSTIC_FAILED] stage={}, symbol={}", stage, context.symbol());
                }
            }
        }
    }
}
