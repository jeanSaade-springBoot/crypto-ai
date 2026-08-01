package com.crypto.dto;

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
        String explanation
) {
}
