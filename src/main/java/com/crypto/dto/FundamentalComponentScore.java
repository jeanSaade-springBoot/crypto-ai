package com.crypto.dto;

import java.math.BigDecimal;

public record FundamentalComponentScore(
        String code,
        String label,
        int score,
        int maximum,
        BigDecimal value,
        String metric,
        String status
) {
}
