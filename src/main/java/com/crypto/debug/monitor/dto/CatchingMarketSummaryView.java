package com.crypto.debug.monitor.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * FIX-113: read-only aggregated Catching Market row. This projection summarizes persisted
 * price_move_event evidence only; it is never consumed by Production or Replay trading logic.
 */
public interface CatchingMarketSummaryView {
    String getSymbol();
    String getDirection();
    String getDetectionWindow();
    Long getDirectionCount();
    BigDecimal getAverageProgress();
    Instant getStartTime();
    Instant getEndTime();
    Long getStartEventId();
}
