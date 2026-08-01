package com.crypto.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.crypto.domain.LiquidityContextStatus;
import com.crypto.domain.SignalDecision;

public record OrderBookLiquidityResult(
        LiquidityContextStatus status,
        SignalDecision originalDecision,
        SignalDecision finalDecision,
        boolean entryAllowed,
        BigDecimal imbalance,
        BigDecimal bidDepth,
        BigDecimal askDepth,
        BigDecimal spreadPercent,
        BigDecimal nearestBidWallPrice,
        BigDecimal nearestBidWallSize,
        BigDecimal nearestAskWallPrice,
        BigDecimal nearestAskWallSize,
        boolean targetBlocked,
        boolean stopExposed,
        int observations,
        String explanation,
        Instant evaluatedAt,
        long windowSeconds,
        long wallPersistenceSeconds,
        BigDecimal influenceFactor,
        boolean vetoAllowed
) {
}
