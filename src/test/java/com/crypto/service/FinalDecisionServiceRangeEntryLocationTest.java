package com.crypto.service;

import com.crypto.domain.BtcContextStatus;
import com.crypto.domain.ConfluenceStatus;
import com.crypto.domain.DerivativesPositioningStatus;
import com.crypto.domain.LiquidityContextStatus;
import com.crypto.domain.SignalDecision;
import com.crypto.dto.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinalDecisionServiceRangeEntryLocationTest {

    private final FinalDecisionService service = new FinalDecisionService();

    @Test
    void eth109885StyleRangeLocationIsVisibleAndBlocksInFinalDecisionPath() {
        AtrRiskAssessment atr = mock(AtrRiskAssessment.class);
        when(atr.immediateEntryAllowed()).thenReturn(true);
        when(atr.explanation()).thenReturn("ATR pass");

        StrategyProfile strategy = mock(StrategyProfile.class);
        when(strategy.entryAllowed()).thenReturn(true);

        MarketRegimeAssessment regime = mock(MarketRegimeAssessment.class);
        when(regime.confidence()).thenReturn(67);

        MarketContextSnapshot context = mock(MarketContextSnapshot.class);
        when(context.dataValid()).thenReturn(true);
        when(context.sentimentEnabled()).thenReturn(false);

        MultiTimeframeConfluenceResult confluence = mock(MultiTimeframeConfluenceResult.class);
        when(confluence.finalDecision()).thenReturn(SignalDecision.BUY);
        when(confluence.entryAllowed()).thenReturn(true);
        when(confluence.status()).thenReturn(ConfluenceStatus.AGREEMENT);
        when(confluence.explanation()).thenReturn("MTF pass");

        BtcMarketContextResult btc = mock(BtcMarketContextResult.class);
        when(btc.finalDecision()).thenReturn(SignalDecision.BUY);
        when(btc.entryAllowed()).thenReturn(true);
        when(btc.contextStatus()).thenReturn(BtcContextStatus.NEUTRAL);
        when(btc.stable()).thenReturn(true);
        when(btc.explanation()).thenReturn("BTC pass");

        DerivativesPositioningResult derivatives = mock(DerivativesPositioningResult.class);
        when(derivatives.finalDecision()).thenReturn(SignalDecision.BUY);
        when(derivatives.entryAllowed()).thenReturn(true);
        when(derivatives.status()).thenReturn(DerivativesPositioningStatus.BALANCED);
        when(derivatives.confidenceAdjustment()).thenReturn(0);
        when(derivatives.explanation()).thenReturn("Derivatives pass");

        OrderBookLiquidityResult liquidity = mock(OrderBookLiquidityResult.class);
        when(liquidity.finalDecision()).thenReturn(SignalDecision.BUY);
        when(liquidity.entryAllowed()).thenReturn(true);
        when(liquidity.status()).thenReturn(LiquidityContextStatus.BALANCED);
        when(liquidity.observations()).thenReturn(6);
        when(liquidity.explanation()).thenReturn("Liquidity pass");

        RangeEntryLocationAssessment location = new RangeEntryLocationAssessment(
                true, false, new BigDecimal("64.26"), false,
                "RANGE_MEAN_REVERSION BUY is too high inside the Bollinger range");

        FinalDecisionResult result = service.decide(
                SignalDecision.BUY, SignalDecision.BUY, atr, location, strategy, regime,
                context, confluence, btc, derivatives, liquidity);

        assertEquals(SignalDecision.BUY, result.finalDecision(),
                "FIX-042 must preserve the directional BUY recommendation");
        assertFalse(result.entryAllowed(),
                "FIX-042 must veto execution of the high-range ETH-style entry");
        assertTrue(result.confidenceScore() <= 49,
                "Existing blocked-entry confidence cap must remain authoritative");
        assertTrue(result.adjustments().stream().anyMatch(step ->
                "RANGE_ENTRY_LOCATION".equals(step.source())
                        && !step.entryAllowedAfter()),
                "The production/replay decision path must expose the RANGE location veto");
    }

    @Test
    void goodRangeLocationPassesWithoutChangingBuyDecision() {
        AtrRiskAssessment atr = mock(AtrRiskAssessment.class);
        when(atr.immediateEntryAllowed()).thenReturn(true);
        when(atr.explanation()).thenReturn("ATR pass");

        StrategyProfile strategy = mock(StrategyProfile.class);
        when(strategy.entryAllowed()).thenReturn(true);

        MarketRegimeAssessment regime = mock(MarketRegimeAssessment.class);
        when(regime.confidence()).thenReturn(67);

        MarketContextSnapshot context = mock(MarketContextSnapshot.class);
        when(context.dataValid()).thenReturn(true);
        when(context.sentimentEnabled()).thenReturn(false);

        MultiTimeframeConfluenceResult confluence = mock(MultiTimeframeConfluenceResult.class);
        when(confluence.finalDecision()).thenReturn(SignalDecision.BUY);
        when(confluence.entryAllowed()).thenReturn(true);
        when(confluence.status()).thenReturn(ConfluenceStatus.AGREEMENT);
        when(confluence.explanation()).thenReturn("MTF pass");

        BtcMarketContextResult btc = mock(BtcMarketContextResult.class);
        when(btc.finalDecision()).thenReturn(SignalDecision.BUY);
        when(btc.entryAllowed()).thenReturn(true);
        when(btc.contextStatus()).thenReturn(BtcContextStatus.NEUTRAL);
        when(btc.stable()).thenReturn(true);
        when(btc.explanation()).thenReturn("BTC pass");

        DerivativesPositioningResult derivatives = mock(DerivativesPositioningResult.class);
        when(derivatives.finalDecision()).thenReturn(SignalDecision.BUY);
        when(derivatives.entryAllowed()).thenReturn(true);
        when(derivatives.status()).thenReturn(DerivativesPositioningStatus.BALANCED);
        when(derivatives.explanation()).thenReturn("Derivatives pass");

        OrderBookLiquidityResult liquidity = mock(OrderBookLiquidityResult.class);
        when(liquidity.finalDecision()).thenReturn(SignalDecision.BUY);
        when(liquidity.entryAllowed()).thenReturn(true);
        when(liquidity.status()).thenReturn(LiquidityContextStatus.BALANCED);
        when(liquidity.observations()).thenReturn(6);
        when(liquidity.explanation()).thenReturn("Liquidity pass");

        RangeEntryLocationAssessment location = new RangeEntryLocationAssessment(
                true, true, new BigDecimal("40.00"), false, "Preferred range entry location");

        FinalDecisionResult result = service.decide(
                SignalDecision.BUY, SignalDecision.BUY, atr, location, strategy, regime,
                context, confluence, btc, derivatives, liquidity);

        assertEquals(SignalDecision.BUY, result.finalDecision());
        assertTrue(result.entryAllowed());
    }
}
