package com.crypto.debug.monitor.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * FIX-115: UI-only Catching Market row enriched with blocked-BUY attribution.
 * The blocked evidence is read from persisted execution_opportunity rows and is never
 * fed back into Production or Replay decisions. One DTO still represents one catch aggregate.
 */
public record CatchingMarketSummaryRow(
        String symbol,
        String direction,
        String detectionWindow,
        Long directionCount,
        BigDecimal averageProgress,
        Instant startTime,
        Instant endTime,
        Long startEventId,
        long blockedBuyCount,
        String blockedBuyReasons
) {}
