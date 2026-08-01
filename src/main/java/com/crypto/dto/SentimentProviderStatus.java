package com.crypto.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SentimentProviderStatus(
        String provider,
        String displayName,
        boolean enabled,
        BigDecimal weight,
        long intervalSeconds,
        BigDecimal score,
        BigDecimal confidence,
        int sampleCount,
        Instant latestObservedAt,
        Instant lastCollectionAt,
        Instant lastSuccessAt,
        String status,
        String healthStatus,
        boolean contributing,
        long hoursSinceSuccess,
        String healthMessage,
        String message,
        boolean apiKeyConfigured,
        String apiKeyEnvironmentVariable
) {
}
