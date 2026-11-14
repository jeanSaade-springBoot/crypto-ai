package com.crypto.regression.replay;

import com.crypto.market.service.MarketPriceEventService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReplayPriceCoverageStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ReplayPriceCoverageStore store = new ReplayPriceCoverageStore(jdbc);

    @Test
    void qualifiesBoundaryCompleteContinuousPriceEvidence() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        ReplayPriceCoverageStore.Coverage coverage = store.record(8L, "BTCUSDT", from, from.plusSeconds(120), List.of(
                event(from), event(from.plusSeconds(60)), event(from.plusSeconds(120))));

        assertTrue(coverage.qualified());
        verify(jdbc).update(contains("replay_price_coverage"), eq(8L), eq("BTCUSDT"),
                eq(java.sql.Timestamp.from(from)), eq(java.sql.Timestamp.from(from.plusSeconds(120))),
                eq(3), eq(0), eq("QUALIFIED"), contains("boundaryCovered"));
    }

    @Test
    void refusesGapOrMissingBoundary() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        ReplayPriceCoverageStore.Coverage coverage = store.record(9L, "BTCUSDT", from, from.plusSeconds(300), List.of(
                event(from.plusSeconds(1)), event(from.plusSeconds(300))));
        assertFalse(coverage.qualified());
    }

    private static MarketPriceEventService.PriceEvent event(Instant at) {
        return new MarketPriceEventService.PriceEvent(at, BigDecimal.ONE);
    }
}
