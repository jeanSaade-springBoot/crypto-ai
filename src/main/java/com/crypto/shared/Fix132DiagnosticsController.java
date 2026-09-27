package com.crypto.shared;

import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.time.Instant;
import java.sql.Timestamp;

/** Persisted Production delivery evidence, distinct from simulated Replay results. */
@RestController
@RequestMapping("/api/administration/shared-market")
public class Fix132DiagnosticsController {
    private final JdbcTemplate jdbc; private final SharedMarketSource source;
    public Fix132DiagnosticsController(JdbcTemplate jdbc,SharedMarketSource source) { this.jdbc=jdbc;this.source=source; }
    @GetMapping("/status") public Map<String,Object> status() {
        return Map.of("mode",source.mode(),"readPool",source.poolPressure(),"health",jdbc.queryForList("SELECT * FROM shared_market_health"),"states",jdbc.queryForList("SELECT s.*, TIMESTAMPDIFF(MICROSECOND,last_observed_at,CURRENT_TIMESTAMP(6))/1000 last_price_age_ms FROM shared_market_consumer_state s ORDER BY symbol"),
            "pending",jdbc.queryForList("""
                SELECT symbol,status,analysis_status,COUNT(*) event_count,MIN(source_created_at) oldest_event,
                MAX(TIMESTAMPDIFF(MICROSECOND,source_created_at,protection_completed_at)/1000) protection_delay_ms,
                MAX(TIMESTAMPDIFF(MICROSECOND,source_created_at,analysis_completed_at)/1000) analysis_delay_ms
                FROM shared_market_event_delivery WHERE phase<4 OR analysis_status IN ('PENDING','RUNNING','REVIEW_REQUIRED','HISTORICAL_DEFERRED')
                GROUP BY symbol,status,analysis_status
                """));
    }
    @GetMapping("/events") public Map<String,Object> events(@RequestParam String symbol,@RequestParam Instant from,@RequestParam Instant to) {
        if(!to.isAfter(from) || java.time.Duration.between(from,to).compareTo(java.time.Duration.ofDays(7))>0) throw new IllegalArgumentException("Use a window of at most seven days");
        var rows=jdbc.queryForList("""
            SELECT d.*,p.delivery_status price_outcome,
            w.signal_id processing_signal_id,w.status processing_status,w.failure_stage processing_failure_stage,
            TIMESTAMPDIFF(MICROSECOND,d.source_created_at,d.protection_completed_at)/1000 protection_delay_ms,
            TIMESTAMPDIFF(MICROSECOND,d.source_created_at,d.analysis_completed_at)/1000 analysis_delay_ms
            FROM shared_market_event_delivery d LEFT JOIN market_price_event p ON p.source_event_id=d.source_event_id
            LEFT JOIN signal_processing_work w ON w.source_event_id=d.source_event_id
            WHERE d.symbol=? AND d.source_created_at BETWEEN ? AND ? ORDER BY d.symbol_sequence LIMIT 501
            """,symbol.trim().toUpperCase(Locale.ROOT),Timestamp.from(from),Timestamp.from(to));
        return Map.of("scope","PRODUCTION_DELIVERY_HISTORY","truncated",rows.size()>500,"rows",rows.size()>500?rows.subList(0,500):rows);
    }
    @GetMapping("/inputs") public List<Map<String,Object>> inputs(@RequestParam String context) {
        if(!context.matches("(?:LIVE|REPLAY):[0-9]+"))throw new IllegalArgumentException("Use LIVE:eventId or REPLAY:runId");
        return jdbc.queryForList("SELECT * FROM shared_candle_input_audit WHERE context_key=? ORDER BY id LIMIT 1000",context);
    }
}
