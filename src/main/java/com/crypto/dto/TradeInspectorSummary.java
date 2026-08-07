package com.crypto.dto;

import java.math.BigDecimal;

public record TradeInspectorSummary(
        int trades,
        int wins,
        int losses,
        BigDecimal winRate,
        BigDecimal netPnl,
        BigDecimal averagePnl,
        BigDecimal averageWin,
        BigDecimal averageLoss,
        BigDecimal profitFactor
) {}
