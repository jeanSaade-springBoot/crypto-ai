package com.crypto.position.dto;

import com.crypto.position.domain.PositionRecommendation;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionAnalysisView(
        Long id,
        Long walletPositionId,
        Long tradeSignalId,
        String symbol,
        String intervalCode,
        BigDecimal entryPriceUsdt,
        BigDecimal currentPriceUsdt,
        BigDecimal unrealizedPnlUsdt,
        BigDecimal unrealizedPnlPercent,
        long holdingMinutes,
        int exitScore,
        PositionRecommendation recommendation,
        int confidence,
        String explanation,
        boolean advisoryOnly,
        Instant analyzedAt
) {}
