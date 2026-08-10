package com.crypto.service;

import com.crypto.domain.SignalDecision;
import com.crypto.dto.AtrRiskAssessment;
import com.crypto.dto.BtcMarketContextResult;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.dto.MarketContextSnapshot;
import com.crypto.dto.MultiTimeframeConfluenceResult;
import com.crypto.dto.OrderBookLiquidityResult;
import com.crypto.dto.DerivativesPositioningResult;
import com.crypto.dto.ProviderSentiment;
import com.crypto.dto.SentimentOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the complete market context before strategy selection.  Directional
 * services are queried with NEUTRAL so they expose context without vetoing a
 * decision that has not yet been calculated.
 */
@Service
@RequiredArgsConstructor
public class MarketContextService {

    private final MultiTimeframeConfluenceService multiTimeframeConfluenceService;
    private final BtcMarketContextService btcMarketContextService;
    private final OrderBookLiquidityService orderBookLiquidityService;
    private final DerivativesPositioningService derivativesPositioningService;

    public MarketContextSnapshot build(
            IndicatorSnapshot indicator,
            AtrRiskAssessment atrRisk,
            SentimentOverview sentimentOverview,
            boolean sentimentEnabled,
            Instant evaluatedAt,
            boolean historicalReplay
    ) {
        Instant snapshotTime = evaluatedAt == null ? Instant.now() : evaluatedAt;
        MultiTimeframeConfluenceResult timeframe = multiTimeframeConfluenceService.evaluate(
                indicator.symbol(), indicator.intervalCode(), SignalDecision.NEUTRAL, snapshotTime);
        BtcMarketContextResult btc = btcMarketContextService.evaluate(
                indicator.symbol(), indicator.intervalCode(), SignalDecision.NEUTRAL, true, snapshotTime);
        OrderBookLiquidityResult liquidity = historicalReplay
                ? orderBookLiquidityService.evaluateHistorical(
                        indicator.symbol(), indicator.intervalCode(), SignalDecision.NEUTRAL, true,
                        indicator.latestPrice(), atrRisk.stopLoss(), atrRisk.takeProfit(), snapshotTime)
                : orderBookLiquidityService.evaluate(
                        indicator.symbol(), indicator.intervalCode(), SignalDecision.NEUTRAL, true, indicator.latestPrice(),
                        atrRisk.stopLoss(), atrRisk.takeProfit(), snapshotTime);
        DerivativesPositioningResult derivatives = historicalReplay
                ? derivativesPositioningService.evaluateHistorical(
                        indicator.symbol(), indicator.intervalCode(), SignalDecision.NEUTRAL, true, snapshotTime)
                : derivativesPositioningService.evaluate(
                        indicator.symbol(), indicator.intervalCode(), SignalDecision.NEUTRAL, true, snapshotTime);

        List<ProviderSentiment> providers = sentimentOverview == null || sentimentOverview.providers() == null
                ? List.of() : sentimentOverview.providers();
        int enabled = (int) providers.stream().filter(ProviderSentiment::enabled).count();
        int contributing = (int) providers.stream()
                .filter(ProviderSentiment::enabled)
                .filter(provider -> provider.effectiveWeight() != null && provider.effectiveWeight().signum() > 0)
                .count();
        BigDecimal coverage = enabled == 0
                ? (sentimentEnabled ? BigDecimal.ZERO : BigDecimal.ONE)
                : BigDecimal.valueOf(contributing)
                    .divide(BigDecimal.valueOf(enabled), 4, RoundingMode.HALF_UP);

        boolean valid = indicator.latestPrice() != null && indicator.latestPrice().signum() > 0
                && indicator.ema20() != null && indicator.ema50() != null && indicator.ema200() != null;
        List<String> evidence = new ArrayList<>();
        evidence.add("Higher-timeframe context: " + timeframe.status());
        evidence.add("BTC relationship/context: " + btc.relationshipType() + " / " + btc.contextStatus());
        evidence.add("Order-book liquidity: " + liquidity.status());
        evidence.add("Funding/open interest: " + derivatives.status());
        if (sentimentEnabled) {
            evidence.add("Sentiment coverage: " + contributing + "/" + enabled + " enabled providers");
        } else {
            evidence.add("Sentiment is disabled and removed from the available maximum");
        }
        if (!valid) {
            evidence.add("Required technical inputs are incomplete");
        }

        return new MarketContextSnapshot(
                indicator.symbol(), indicator.intervalCode(), snapshotTime, valid,
                timeframe.status(), timeframe.higherInterval(), timeframe.higherTimeframeTrendScore(),
                btc.relationshipType(), btc.contextStatus(), btc.correlation(), btc.beta(),
                btc.influenceFactor(), btc.stable(), liquidity.status(), liquidity.imbalance(),
                liquidity.spreadPercent(), liquidity.observations(), derivatives.status(),
                derivatives.fundingRate(), derivatives.fundingPercentile(),
                derivatives.openInterestChangePercent(), derivatives.priceChangePercent(),
                derivatives.confidenceAdjustment(), sentimentEnabled,
                enabled, contributing, coverage, List.copyOf(evidence));
    }
}
