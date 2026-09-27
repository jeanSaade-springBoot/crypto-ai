package com.crypto.shared;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** FIX-132: the delivery ledger owns collector-era automatic execution. Legacy
 * recovery must not manufacture a second attempt after an uncertain delivery.
 * Pre-cutover missing history may still be restored, without execution. */
@Component
public class SharedRecoveryAuthority {
    private final SharedMarketSource source;
    private final JdbcTemplate jdbc;
    public SharedRecoveryAuthority(SharedMarketSource source, JdbcTemplate jdbc) {
        this.source=source; this.jdbc=jdbc;
    }
    public boolean ownsLiveExecution() { return source.enabled(); }
    public boolean deferCandle(String symbol, String interval, Instant open, Instant close) {
        if (!source.enabled()) return false;
        var boundaries=jdbc.query("SELECT cutover_at FROM shared_market_consumer_state WHERE symbol=?",
            (rs,n)->rs.getTimestamp(1).toInstant(),symbol);
        // Missing boundary is not permission to run old-path recovery.
        if (boundaries.isEmpty() || close == null || close.isAfter(boundaries.getFirst())) return true;
        return jdbc.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE symbol=? AND interval_code=? AND candle_open_time=?",
            Long.class,symbol,interval,Timestamp.from(open))>0;
    }
}
