package com.crypto.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CryptoAccountConfigurationResponse(
        Long id,
        String username,
        String exchangeCode,
        String accountLabel,
        String executionMode,
        boolean credentialsConfigured,
        String apiKeyMasked,
        BigDecimal maxOrderUsdt,
        BigDecimal maxTotalExposureUsdt,
        int maxOpenPositions,
        BigDecimal maxDailyLossUsdt,
        Instant updatedAt
) {}
