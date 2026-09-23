package com.crypto.client.binance.websocket;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** FIX-131: callback-local diagnostics only. Never used as candle identity or decision time.
 * ThreadLocal is safe here because processKline and its input commit are synchronous. */
public final class KlineReceipt implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(KlineReceipt.class);
    private static final ThreadLocal<KlineReceipt> CURRENT = new ThreadLocal<>();
    private final String id = UUID.randomUUID().toString();
    private final long generation;
    private final Instant receivedAt;
    private final long start;
    private final boolean closed;
    private boolean committed;
    private KlineReceipt(long generation, Instant receivedAt, long start, boolean closed) {
        this.generation = generation; this.receivedAt = receivedAt; this.start = start; this.closed = closed;
    }
    public static KlineReceipt begin(long generation, Instant receivedAt, long start, boolean closed) {
        var receipt = new KlineReceipt(generation, receivedAt, start, closed);
        CURRENT.set(receipt);
        return receipt;
    }
    public String correlationId() { return id; }
    public static void inputCommitted(String symbol, String interval, Instant open, Instant eventTime) {
        var r = CURRENT.get();
        if (r == null) return;
        r.committed = true;
        long elapsed = (System.nanoTime() - r.start) / 1_000_000;
        // All final candles; slow in-progress updates only, to bound routine log volume.
        if (r.closed || elapsed >= 1000) log.info("[FIX-131][CANDLE_INPUT_COMMITTED] correlation={}, generation={}, symbol={}, interval={}, candleOpenTime={}, binanceEventTime={}, receivedAt={}, inputCommittedAt={}, receiptToCommitMs={}, closed={}",
                r.id, r.generation, symbol, interval, open, eventTime, r.receivedAt, Instant.now(), elapsed, r.closed);
    }
    @Override public void close() {
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        CURRENT.remove();
        if (closed || elapsed >= 1000 || !committed) log.info("[FIX-131][CALLBACK_FINISHED] correlation={}, generation={}, durationMs={}, inputCommitted={}", id, generation, elapsed, committed);
    }
}
