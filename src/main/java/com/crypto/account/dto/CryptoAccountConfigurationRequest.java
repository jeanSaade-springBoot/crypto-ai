package com.crypto.account.dto;

import java.math.BigDecimal;

public record CryptoAccountConfigurationRequest(
        String accountLabel,
        String executionMode,
        String apiKey,
        String apiSecret,
        boolean clearCredentials,
        BigDecimal maxOrderUsdt,
        BigDecimal maxTotalExposureUsdt,
        Integer maxOpenPositions,
        BigDecimal maxDailyLossUsdt
) {}
