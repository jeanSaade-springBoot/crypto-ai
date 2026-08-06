package com.crypto.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TradeReplayResponse(
        PositionSummary position,
        SignalSummary entrySignal,
        SignalSummary exitSignal,
        List<SignalSummary> timeline,
        List<CandlePoint> candles,
        List<PositionAdvice> positionAdvice,
        AfterExitSummary afterExit
) {
    public record PositionSummary(
            Long id,
            String symbol,
            String status,
            BigDecimal quantity,
            BigDecimal entryPrice,
            BigDecimal exitPrice,
            BigDecimal stopLoss,
            BigDecimal takeProfit,
            BigDecimal realizedPnl,
            BigDecimal pnlPercent,
            String closeReason,
            String entryReason,
            String exitReason,
            Instant openedAt,
            Instant closedAt
    ) {}

    public record SignalSummary(
            Long id,
            String interval,
            Instant generatedAt,
            BigDecimal price,
            String originalDecision,
            String decision,
            int totalScore,
            int confidenceScore,
            int trendScore,
            int volumeScore,
            int momentumScore,
            int sentimentScore,
            int fundamentalScore,
            String confluenceStatus,
            String liquidityStatus,
            boolean finalEntryAllowed,
            String explanation
    ) {}

    public record CandlePoint(
            Instant openTime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume
    ) {}

    public record PositionAdvice(
            Long id,
            Long tradeSignalId,
            String interval,
            Instant analyzedAt,
            BigDecimal currentPrice,
            BigDecimal unrealizedPnlPercent,
            int exitScore,
            String recommendation,
            int confidence,
            String explanation,
            boolean advisoryOnly
    ) {}

    public record AfterExitSummary(
            int minutesObserved,
            BigDecimal highestPrice,
            BigDecimal lowestPrice,
            BigDecimal highestMovePercent,
            BigDecimal lowestMovePercent,
            String verdict
    ) {}
}
