package com.crypto.indicator.model;

import java.math.BigDecimal;
import java.time.Instant;

public record TechnicalIndicatorResult(
        String symbol,
        String intervalCode,
        Instant candleOpenTime,
        BigDecimal closePrice,
        BigDecimal sma20,
        BigDecimal ema20,
        BigDecimal ema50,
        BigDecimal rsi14,
        BigDecimal bollingerMiddle,
        BigDecimal bollingerUpper,
        BigDecimal bollingerLower,
        BigDecimal bollingerBandwidth,
        BigDecimal atr14,
        BigDecimal volumeSma20
) {
}