package com.crypto.indicator.model;

import java.math.BigDecimal;

public record MacdResult(
        BigDecimal macd,
        BigDecimal signal,
        BigDecimal histogram
) {
}