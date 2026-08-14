package com.crypto.service;

import com.crypto.domain.ConfluenceStatus;
import com.crypto.domain.MarketRegime;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.domain.TradingStrategy;
import com.crypto.dto.MultiTimeframeConfluenceResult;
import com.crypto.execution.service.ExecutionReplayScope;
import com.crypto.repository.TradeSignalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiTimeframeConfluenceServiceTest {

    @Mock
    private TradeSignalRepository tradeSignalRepository;

    @Test
    void shouldPreserveBuyWhenHigherTimeframeIsNeutralWithWeakStructure() {
        Instant evaluationTime = Instant.parse("2026-08-03T18:15:42Z");
        MultiTimeframeConfluenceService service = new MultiTimeframeConfluenceService(tradeSignalRepository);

        TradeSignal higher = higherSignal(
                "ETHUSDT", "1h", SignalDecision.NEUTRAL, 10,
                MarketRegime.WEAK_DOWNTREND, evaluationTime.minusSeconds(600));

        when(tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        "ETHUSDT", "15m", evaluationTime))
                .thenReturn(Optional.empty());
        when(tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        "ETHUSDT", "1h", evaluationTime))
                .thenReturn(Optional.of(higher));

        MultiTimeframeConfluenceResult result = service.evaluate(
                "ETHUSDT", "5m", SignalDecision.BUY,
                evaluationTime, TradingStrategy.TREND_FOLLOWING);

        assertEquals(SignalDecision.BUY, result.finalDecision());
        assertTrue(result.entryAllowed());
        assertEquals(ConfluenceStatus.CONFLICT, result.status());
        assertTrue(result.explanation().contains("confidence was reduced without blocking the BUY"));
    }

    @Test
    void shouldPreserveBuyWhenHigherTimeframeIsWatchWithWeakStructure() {
        Instant evaluationTime = Instant.parse("2026-08-03T18:15:42Z");
        MultiTimeframeConfluenceService service = new MultiTimeframeConfluenceService(tradeSignalRepository);

        TradeSignal higher = higherSignal(
                "BNBUSDT", "1h", SignalDecision.WATCH, 10,
                MarketRegime.WEAK_DOWNTREND, evaluationTime.minusSeconds(600));

        when(tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        "BNBUSDT", "15m", evaluationTime))
                .thenReturn(Optional.empty());
        when(tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        "BNBUSDT", "1h", evaluationTime))
                .thenReturn(Optional.of(higher));

        MultiTimeframeConfluenceResult result = service.evaluate(
                "BNBUSDT", "5m", SignalDecision.BUY,
                evaluationTime, TradingStrategy.TREND_FOLLOWING);

        assertEquals(SignalDecision.BUY, result.finalDecision());
        assertTrue(result.entryAllowed());
        assertEquals(ConfluenceStatus.CONFLICT, result.status());
    }

    @Test
    void shouldKeepHardVetoForStrongSellHigherTimeframe() {
        Instant evaluationTime = Instant.parse("2026-08-02T02:57:07Z");
        MultiTimeframeConfluenceService service = new MultiTimeframeConfluenceService(tradeSignalRepository);

        TradeSignal higher = higherSignal(
                "BNBUSDT", "1h", SignalDecision.STRONG_SELL, 4,
                MarketRegime.WEAK_DOWNTREND, evaluationTime.minusSeconds(600));

        when(tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        "BNBUSDT", "5m", evaluationTime))
                .thenReturn(Optional.empty());
        when(tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        "BNBUSDT", "1h", evaluationTime))
                .thenReturn(Optional.of(higher));

        MultiTimeframeConfluenceResult result = service.evaluate(
                "BNBUSDT", "1m", SignalDecision.BUY,
                evaluationTime, TradingStrategy.TREND_FOLLOWING);

        assertEquals(SignalDecision.WATCH, result.finalDecision());
        assertEquals(ConfluenceStatus.STRONG_CONFLICT, result.status());
        assertTrue(!result.entryAllowed());
    }

    @Test
    void shouldReduceConfidenceButPreserveBuyForModerateSell() {
        Instant evaluationTime = Instant.parse("2026-08-03T18:15:42Z");
        MultiTimeframeConfluenceService service = new MultiTimeframeConfluenceService(tradeSignalRepository);

        TradeSignal higher = higherSignal(
                "ETHUSDT", "1h", SignalDecision.SELL, 9,
                MarketRegime.WEAK_DOWNTREND, evaluationTime.minusSeconds(600));

        when(tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        "ETHUSDT", "15m", evaluationTime))
                .thenReturn(Optional.empty());
        when(tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        "ETHUSDT", "1h", evaluationTime))
                .thenReturn(Optional.of(higher));

        MultiTimeframeConfluenceResult result = service.evaluate(
                "ETHUSDT", "5m", SignalDecision.BUY,
                evaluationTime, TradingStrategy.TREND_FOLLOWING);

        assertEquals(SignalDecision.BUY, result.finalDecision());
        assertTrue(result.entryAllowed());
        assertEquals(ConfluenceStatus.CONFLICT, result.status());
    }

    private TradeSignal higherSignal(
            String symbol,
            String interval,
            SignalDecision decision,
            int trendScore,
            MarketRegime regime,
            Instant generatedAt
    ) {
        return TradeSignal.builder()
                .symbol(symbol)
                .interval(interval)
                .decision(decision)
                .originalDecision(decision)
                .trendScore(trendScore)
                .marketRegime(regime)
                .generatedAt(generatedAt)
                .build();
    }
    @Test
    void replayScopeUsesFreshReplayHigherTimeframeInsteadOfProductionRepository() {
        Instant evaluationTime = Instant.parse("2026-08-11T19:31:00Z");
        MultiTimeframeConfluenceService service = new MultiTimeframeConfluenceService(tradeSignalRepository);
        ExecutionReplayScope scope = new ExecutionReplayScope();
        ReflectionTestUtils.setField(service, "replayScope", scope);

        TradeSignal replayHour = higherSignal(
                "ETHUSDT", "1h", SignalDecision.STRONG_SELL, 0,
                MarketRegime.WEAK_DOWNTREND, evaluationTime.minusSeconds(1800));

        try (ExecutionReplayScope.Scope ignored = scope.open(1L, java.util.List.of(replayHour), o -> {})) {
            scope.reference(evaluationTime);
            MultiTimeframeConfluenceResult result = service.evaluate(
                    "ETHUSDT", "5m", SignalDecision.BUY,
                    evaluationTime, TradingStrategy.TREND_FOLLOWING);

            assertEquals(SignalDecision.WATCH, result.finalDecision());
            assertEquals(ConfluenceStatus.STRONG_CONFLICT, result.status());
        }
    }

}
