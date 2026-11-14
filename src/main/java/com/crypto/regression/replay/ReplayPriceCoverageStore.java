package com.crypto.regression.replay;

import com.crypto.market.service.MarketPriceEventService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** FIX-156: fail-closed price-stream coverage evidence for one Replay window. */
@Component
public class ReplayPriceCoverageStore {
    private static final Duration MAX_ACCEPTED_GAP = Duration.ofMinutes(2);
    private final JdbcTemplate jdbcTemplate;

    public ReplayPriceCoverageStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Coverage record(long runId, String symbol, Instant from, Instant to,
                           List<MarketPriceEventService.PriceEvent> suppliedEvents) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("Replay price coverage requires an ordered execution window");
        }
        List<MarketPriceEventService.PriceEvent> events = suppliedEvents == null ? List.of() : suppliedEvents.stream()
                .filter(e -> e != null && e.observedAt() != null && e.price() != null)
                .sorted(Comparator.comparing(MarketPriceEventService.PriceEvent::observedAt))
                .toList();
        int gaps = 0;
        Instant prior = null;
        for (MarketPriceEventService.PriceEvent event : events) {
            if (prior != null && Duration.between(prior, event.observedAt()).compareTo(MAX_ACCEPTED_GAP) > 0) gaps++;
            prior = event.observedAt();
        }
        boolean bounded = !events.isEmpty()
                && !events.get(0).observedAt().isAfter(from)
                && !events.get(events.size() - 1).observedAt().isBefore(to);
        String status = bounded && gaps == 0 ? "QUALIFIED" : "NOT_QUALIFIED";
        Coverage coverage = new Coverage(events.size(), gaps, status);
        ReplayAccessPolicy.require("replay_price_coverage", ReplayAccessPolicy.Operation.REPLAY_INSERT);
        String details = "{\"boundaryCovered\":" + bounded + ",\"maxGapSeconds\":"
                + MAX_ACCEPTED_GAP.toSeconds() + "}";
        jdbcTemplate.update("""
                INSERT INTO replay_price_coverage
                    (run_id, symbol, covered_from, covered_to, accepted_event_count, gap_count, coverage_status, details_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE covered_from=VALUES(covered_from), covered_to=VALUES(covered_to),
                    accepted_event_count=VALUES(accepted_event_count), gap_count=VALUES(gap_count),
                    coverage_status=VALUES(coverage_status), details_json=VALUES(details_json)
                """, runId, symbol, Timestamp.from(from), Timestamp.from(to), events.size(), gaps, status, details);
        return coverage;
    }

    public record Coverage(int acceptedEventCount, int gapCount, String status) {
        public boolean qualified() { return "QUALIFIED".equals(status); }
    }
}
