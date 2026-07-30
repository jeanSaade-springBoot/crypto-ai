package com.crypto.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record IndicatorSnapshot(
        String symbol,
        String intervalCode,
        Instant candleOpenTime,
        BigDecimal latestPrice,

        BigDecimal sma20,

        BigDecimal ema20,
        BigDecimal ema50,
        BigDecimal ema200,

        BigDecimal rsi14,

        BigDecimal macd,
        BigDecimal macdSignal,
        BigDecimal macdHistogram,

        BigDecimal bollingerMiddle,
        BigDecimal bollingerUpper,
        BigDecimal bollingerLower,
        BigDecimal bollingerBandwidth,

        BigDecimal atr14,

        BigDecimal volumeSma20,
        BigDecimal relativeVolume
) {
}