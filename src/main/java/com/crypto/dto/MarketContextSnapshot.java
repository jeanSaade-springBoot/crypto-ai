package com.crypto.dto;

import com.crypto.domain.BtcContextStatus;
import com.crypto.domain.BtcRelationshipType;
import com.crypto.domain.ConfluenceStatus;
import com.crypto.domain.LiquidityContextStatus;
import com.crypto.domain.DerivativesPositioningStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Read-only context assembled before strategy selection.  It intentionally uses
 * neutral directional inputs so the market can choose a strategy before a BUY
 * or SELL decision exists.
 */
public record MarketContextSnapshot(
        String symbol,
        String interval,
        Instant evaluatedAt,
        boolean dataValid,
        ConfluenceStatus higherTimeframeStatus,
        String higherTimeframeInterval,
        Integer higherTimeframeTrendScore,
        BtcRelationshipType btcRelationshipType,
        BtcContextStatus btcContextStatus,
        BigDecimal btcCorrelation,
        BigDecimal btcBeta,
        BigDecimal btcInfluenceFactor,
        boolean btcRelationshipStable,
        LiquidityContextStatus liquidityStatus,
        BigDecimal orderBookImbalance,
        BigDecimal spreadPercent,
        int orderBookObservations,
        DerivativesPositioningStatus derivativesStatus,
        BigDecimal fundingRate,
        BigDecimal fundingPercentile,
        BigDecimal openInterestChangePercent,
        BigDecimal derivativesPriceChangePercent,
        int derivativesConfidenceAdjustment,
        boolean sentimentEnabled,
        int sentimentProvidersEnabled,
        int sentimentProvidersContributing,
        BigDecimal sentimentCoverage,
        List<String> evidence
) {
    public boolean liquidityRisky() {
        return liquidityStatus == LiquidityContextStatus.BEARISH_PRESSURE
                || liquidityStatus == LiquidityContextStatus.TARGET_BLOCKED
                || liquidityStatus == LiquidityContextStatus.STOP_EXPOSED
                || liquidityStatus == LiquidityContextStatus.THIN_LIQUIDITY;
    }

    public boolean contextUncertain() {
        return !dataValid
                || higherTimeframeStatus == ConfluenceStatus.UNAVAILABLE
                || liquidityStatus == LiquidityContextStatus.UNAVAILABLE
                || liquidityStatus == LiquidityContextStatus.LEARNING
                || btcContextStatus == BtcContextStatus.LEARNING
                || btcContextStatus == BtcContextStatus.UNAVAILABLE
                || derivativesStatus == DerivativesPositioningStatus.UNAVAILABLE
                || derivativesStatus == DerivativesPositioningStatus.LEARNING;
    }
}
