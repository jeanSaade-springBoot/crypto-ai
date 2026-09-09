package com.crypto.execution.processing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/** FIX-127 recovery gate only. Uses actual closed candle lineage, never generated_at
 * or a nearest candle. Grace periods match FIX-043's existing recovery contract. */
@Service
public class SignalProcessingFreshness {
    private final JdbcTemplate jdbc;
    public SignalProcessingFreshness(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean eligible(SignalProcessingStore.Work work, Instant now) {
        var candles=jdbc.query("""
                SELECT open_time,close_time FROM candle WHERE symbol=? AND interval_code=?
                AND closed=1 AND close_time<=? ORDER BY open_time DESC LIMIT 1
                """,(rs,n)->new Instant[]{rs.getTimestamp(1).toInstant(),rs.getTimestamp(2).toInstant()},
                work.symbol(),work.interval(),Timestamp.from(now));
        if(candles.isEmpty() || !candles.get(0)[0].equals(work.candleOpenTime()))return false;
        return !candles.get(0)[1].plus(grace(work.interval())).isBefore(now);
    }
    static Duration grace(String interval) {
        return switch(interval) {
            case "1m" -> Duration.ofSeconds(90);
            case "5m" -> Duration.ofMinutes(2);
            case "1h" -> Duration.ofMinutes(5);
            case "4h" -> Duration.ofMinutes(10);
            case "1d" -> Duration.ofMinutes(30);
            default -> Duration.ofMinutes(2);
        };
    }
}
