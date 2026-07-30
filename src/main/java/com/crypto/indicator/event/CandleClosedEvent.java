package com.crypto.indicator.event;

import java.time.Instant;

public record CandleClosedEvent(
        String symbol,
        String intervalCode,
        Instant openTime
) {
}