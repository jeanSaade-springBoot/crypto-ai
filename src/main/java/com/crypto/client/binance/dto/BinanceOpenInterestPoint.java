package com.crypto.client.binance.dto;

import java.math.BigDecimal;

public record BinanceOpenInterestPoint(
        String symbol,
        BigDecimal sumOpenInterest,
        BigDecimal sumOpenInterestValue,
        long timestamp
) {}
