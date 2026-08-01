package com.crypto.client.binance.dto;

import java.math.BigDecimal;

public record BinanceFundingRate(
        String symbol,
        BigDecimal fundingRate,
        long fundingTime,
        BigDecimal markPrice
) {}
