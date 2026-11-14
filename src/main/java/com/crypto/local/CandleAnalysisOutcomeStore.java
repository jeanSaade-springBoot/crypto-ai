package com.crypto.local;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable, immutable completion evidence for a candle.  A normal trade signal
 * is not the sole proof that a candle reached an automatic outcome. */
@Repository
public class CandleAnalysisOutcomeStore {
    private final JdbcTemplate jdbc;

    public CandleAnalysisOutcomeStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean record(String symbol, String interval, Instant openTime, String outcome,
                          String reason, Long signalId, String origin) {
        try {
            return jdbc.update("""
                INSERT INTO candle_analysis_outcome
                (symbol,interval_code,candle_open_time,outcome,reason_code,signal_id,origin,created_at)
                VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6))
                """, symbol, interval, Timestamp.from(openTime), outcome, reason, signalId, origin) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    public boolean exists(String symbol, String interval, Instant openTime) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM candle_analysis_outcome
            WHERE symbol=? AND interval_code=? AND candle_open_time=?
            """, Integer.class, symbol, interval, Timestamp.from(openTime));
        return count != null && count > 0;
    }
}
