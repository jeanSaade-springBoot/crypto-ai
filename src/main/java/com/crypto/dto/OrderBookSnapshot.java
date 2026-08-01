package com.crypto.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderBookSnapshot(
        String symbol,
        Instant capturedAt,
        long lastUpdateId,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks
) {
}
