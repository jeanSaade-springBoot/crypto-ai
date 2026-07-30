package com.crypto.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderSentiment(
        String provider,
        boolean enabled,
        BigDecimal configuredWeight,
        BigDecimal score,
        BigDecimal confidence,
        BigDecimal effectiveWeight,
        int sampleCount,
        Instant latestObservedAt
) {
}
