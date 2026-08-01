package com.crypto.dto;

import java.math.BigDecimal;

public record OrderBookLevel(BigDecimal price, BigDecimal quantity) {
    public BigDecimal notional() {
        return price.multiply(quantity);
    }
}
