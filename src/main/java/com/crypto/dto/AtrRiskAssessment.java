package com.crypto.dto;

import com.crypto.domain.AtrEntryType;

import java.math.BigDecimal;

public record AtrRiskAssessment(
        BigDecimal atr,
        BigDecimal atrPercent,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        BigDecimal stopDistance,
        BigDecimal rewardDistance,
        BigDecimal riskRewardRatio,
        BigDecimal candleRangeAtrMultiple,
        String volatilityLevel,
        boolean overextended,
        AtrEntryType entryType,
        int recommendedPositionPercent,
        boolean immediateEntryAllowed,
        BigDecimal retracementEntryPrice,
        String explanation
) {
}
