package com.crypto.whale.dto;

import java.math.BigDecimal;

public record WhaleSentimentResult(
        String symbol,
        BigDecimal score,
        BigDecimal confidence,
        int activityCount,
        BigDecimal totalUsdValue,
        String summary
) {}
