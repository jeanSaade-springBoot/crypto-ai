package com.crypto.position.service;

import com.crypto.domain.TradeSignal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PositionPriceAuthorityPolicyTest {
    private final PositionPriceAuthorityPolicy policy = new PositionPriceAuthorityPolicy();

    @Test
    void alloIncidentRejectsDelayedFiveMinutePriceFromBeforeEntry() {
        TradeSignal delayed5m = TradeSignal.builder()
                .interval("5m")
                .candleOpenTime(Instant.parse("2026-08-17T18:15:00Z"))
                .latestPrice(new BigDecimal("0.280600000000"))
                .build();

        Instant openedAt = Instant.parse("2026-08-17T18:23:26Z");
        assertEquals(Instant.parse("2026-08-17T18:20:00Z"), policy.marketObservationTime(delayed5m));
        assertFalse(policy.canUseSignalPrice(delayed5m, openedAt));
    }

    @Test
    void postEntryOneMinuteCloseMayBeUsedByHistoricalReplay() {
        TradeSignal oneMinute = TradeSignal.builder()
                .interval("1m")
                .candleOpenTime(Instant.parse("2026-08-17T18:23:00Z"))
                .latestPrice(new BigDecimal("0.282200000000"))
                .build();

        assertTrue(policy.canUseSignalPrice(oneMinute, Instant.parse("2026-08-17T18:23:26Z")));
    }
}
