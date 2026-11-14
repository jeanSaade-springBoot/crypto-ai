package com.crypto.local;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Retry/audit evidence for local reconciliation.  PENDING is the only
 * retryable state; terminal records deliberately never re-enter selection. */
@Repository
public class LocalReconciliationAuditRepository {
    public static final String CANDLE_DATA = "CANDLE_DATA";
    private final JdbcTemplate jdbc;

    public LocalReconciliationAuditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static String candleKey(String symbol, String interval, Instant openTime) {
        // Canonical fixed precision prevents two textual Instant renderings from
        // creating different audit identities for the same exchange candle.
        return symbol + "|" + interval + "|" + openTime.toEpochMilli();
    }

    public Optional<State> findCandle(String symbol, String interval, Instant openTime) {
        var rows = jdbc.query("""
            SELECT status,attempt_count,next_attempt_at FROM local_reconciliation_audit
            WHERE subject_type=? AND subject_key=?
            """, (rs, n) -> new State(rs.getString(1), rs.getInt(2),
                rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()),
            CANDLE_DATA, candleKey(symbol, interval, openTime));
        return rows.stream().findFirst();
    }

    public boolean due(String symbol, String interval, Instant openTime, Instant databaseNow) {
        Optional<State> state = findCandle(symbol, interval, openTime);
        return state.isEmpty() || ("PENDING".equals(state.get().status())
            && (state.get().nextAttemptAt() == null || !state.get().nextAttemptAt().isAfter(databaseNow)));
    }

    public void pending(String symbol, String interval, Instant openTime, int attempts,
                        Instant nextAttempt, String reason, Instant exchangeSnapshot) {
        upsert(symbol, interval, openTime, "PENDING", attempts, nextAttempt, reason, exchangeSnapshot, null, null);
    }

    public void terminal(String symbol, String interval, Instant openTime, String status,
                         String reason, Instant exchangeSnapshot, Instant binanceOpen, Instant binanceClose) {
        upsert(symbol, interval, openTime, status, 0, null, reason, exchangeSnapshot, binanceOpen, binanceClose);
    }

    public void markLateLiveClose(String symbol, String interval, Instant openTime) {
        jdbc.update("""
            UPDATE local_reconciliation_audit
            SET last_observation='LIVE_CLOSE_AFTER_RECOVERY',
                late_live_close_at=COALESCE(late_live_close_at,CURRENT_TIMESTAMP(6)),updated_at=CURRENT_TIMESTAMP(6)
            WHERE subject_type=? AND subject_key=? AND status IN ('RECOVERED','GAP_BACKFILLED')
            """, CANDLE_DATA, candleKey(symbol, interval, openTime));
    }

    private void upsert(String symbol, String interval, Instant openTime, String status, int attempts,
                        Instant nextAttempt, String reason, Instant exchangeSnapshot,
                        Instant binanceOpen, Instant binanceClose) {
        jdbc.update("""
            INSERT INTO local_reconciliation_audit
            (subject_type,subject_key,symbol,interval_code,candle_open_time,status,failure_reason,attempt_count,
             next_attempt_at,exchange_time_snapshot,binance_open_time,binance_close_time,resolved_at,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE status=VALUES(status),failure_reason=VALUES(failure_reason),
             attempt_count=GREATEST(attempt_count,VALUES(attempt_count)),next_attempt_at=VALUES(next_attempt_at),
             exchange_time_snapshot=VALUES(exchange_time_snapshot),binance_open_time=VALUES(binance_open_time),
             binance_close_time=VALUES(binance_close_time),resolved_at=VALUES(resolved_at),updated_at=CURRENT_TIMESTAMP(6)
            """, CANDLE_DATA, candleKey(symbol, interval, openTime), symbol, interval, Timestamp.from(openTime),
            status, reason, attempts, nextAttempt == null ? null : Timestamp.from(nextAttempt),
            exchangeSnapshot == null ? null : Timestamp.from(exchangeSnapshot),
            binanceOpen == null ? null : Timestamp.from(binanceOpen),
            binanceClose == null ? null : Timestamp.from(binanceClose),
            "PENDING".equals(status) ? null : Timestamp.from(Instant.now()));
    }

    public record State(String status, int attempts, Instant nextAttemptAt) {}
}
