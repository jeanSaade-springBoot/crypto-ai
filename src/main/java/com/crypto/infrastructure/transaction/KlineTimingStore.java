package com.crypto.infrastructure.transaction;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.*;

/** FIX-129: bounded, best-effort diagnostics only. Never CallerRuns: no diagnostic
 * JDBC work runs on the ingestion thread. Queue loss and database failure are
 * explicit; these rows are not a complete market-event ledger.
 */
@Service
public class KlineTimingStore {
    private static final Logger log = LoggerFactory.getLogger(KlineTimingStore.class);
    private final JdbcTemplate jdbc;
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), task -> {
                Thread thread = new Thread(task, "fix129-diagnostics");
                thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    public KlineTimingStore(DataSource source) {
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(2);
    }
    public void submit(KlineTiming.Measurement m) {
        try { writer.execute(() -> persist(m)); }
        catch (RejectedExecutionException ex) {
            log.warn("[FIX-129][PERSIST_DROPPED] correlationId={}, stage={}; timing remains in logs",
                    m.context().correlationId(), m.stage());
        }
    }
    private void persist(KlineTiming.Measurement m) {
        try {
            var c = m.context();
            // Dedicated thread has no inherited business transaction; single insert only.
            jdbc.update("""
                INSERT INTO fix129_stage_timing
                (correlation_id,symbol,interval_code,candle_open_time,observed_at,block_start,
                 stage,started_at,finished_at,elapsed_ms,outcome,thread_name)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, c.correlationId(),c.symbol(),c.interval(),ts(c.candleOpenTime()),ts(c.observedAt()),
                    ts(c.blockStart()),m.stage(),ts(m.startedAt()),ts(m.finishedAt()),m.elapsedMs(),m.outcome(),m.threadName());
        } catch (RuntimeException ex) {
            log.warn("[FIX-129][PERSIST_FAILED] correlationId={}, stage={}; timing remains in logs",
                    m.context().correlationId(),m.stage(),ex);
        }
    }
    private static Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }
    @PreDestroy public void shutdown() {
        // Do not delay market shutdown for diagnostics. Already queued records may be lost on process exit.
        writer.shutdown();
    }
}
