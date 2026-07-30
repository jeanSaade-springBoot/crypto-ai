package com.crypto.indicator.model;

import java.math.BigDecimal;

public record BollingerBandsResult(
        BigDecimal middle,
        BigDecimal upper,
        BigDecimal lower,
        BigDecimal bandwidth
) {
}