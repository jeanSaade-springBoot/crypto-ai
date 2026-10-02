package com.crypto.service;

import com.crypto.config.BtcContextProperties;
import com.crypto.domain.BtcContextStatus;
import com.crypto.domain.Candle;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.BtcMarketContextResult;
import com.crypto.execution.service.ExecutionReplayScope;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.TradeSignalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FIX-141: BTC context freshness guard. Covers the design's required test list -
 * fresh/stale/missing/future/unknown-time context, the LEARNING branch, Production/Replay
 * parity, and the "recent generatedAt, old candle" case - against the real evaluate() path.
 */
@ExtendWith(MockitoExtension.class)
class BtcMarketContextServiceTest {

    private static final String ASSET = "ETHUSDT";
    private static final String BTC = "BTCUSDT";
    private static final String INTERVAL = "1m";
    private static final Instant EVAL_TIME = Instant.parse("2026-10-01T16:12:00Z");

    @Mock
    private CandleRepository candleRepository;
    @Mock
    private TradeSignalRepository tradeSignalRepository;
    @Mock
    private ExecutionReplayScope replayScope;

    private BtcContextProperties properties(int minimumSamples, boolean blockLearningContext) {
        return new BtcContextProperties(
                true, BTC, 200, minimumSamples,
                new BigDecimal("0.40"), new BigDecimal("0.70"), new BigDecimal("1.30"),
                true, null, blockLearningContext);
    }

    private BtcMarketContextService service(BtcContextProperties properties) {
        // BtcMarketContextService's only @RequiredArgsConstructor (final) fields are
        // candleRepository, tradeSignalRepository, properties; replayScope/contextMetrics
        // are field-injected and optional, matching production wiring.
        BtcMarketContextService service = new BtcMarketContextService(candleRepository, tradeSignalRepository, properties);
        return service;
    }

    private TradeSignal btcSignal(Instant candleOpenTime, Instant generatedAt, SignalDecision decision, int trendScore) {
        return TradeSignal.builder()
                .symbol(BTC).interval(INTERVAL)
                .candleOpenTime(candleOpenTime)
                .generatedAt(generatedAt)
                .decision(decision)
                .trendScore(trendScore) // FIX-141: apply the fixture's intended BTC trend score.
                .build();
    }

    /** No correlation candle history -> sampleSize=0, so every scenario here that gets past
     * freshness without a lot of synthetic candle data lands in LEARNING, not CONFIRMED/NEUTRAL. */
    private void stubNoCorrelationHistory() {
        when(candleRepository.findClosedCandles(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of());
    }

    // ---- Missing / stale / future / unknown-time context: all block, regardless of LEARNING ----

    @Test
    void missingBtcSignal_blocksEntry() {
        // Review correction: relationship() now runs only after the freshness gate passes,
        // so a pure STALE_CONTEXT case never touches candleRepository - no stub needed here.
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.empty());

        BtcMarketContextResult result = service(properties(1, false))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.STALE_CONTEXT, result.contextStatus());
        assertFalse(result.entryAllowed());
    }

    @Test
    void staleBtcSignal_blocksEntry_insteadOfFallingThroughToLearning() {
        // Candle closed 5 minutes ago; default 1m threshold is 120s, so this is well past stale.
        Instant candleOpenTime = EVAL_TIME.minusSeconds(5 * 60 + 60);
        TradeSignal stale = btcSignal(candleOpenTime, candleOpenTime.plusSeconds(65), SignalDecision.BUY, 20);
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(stale));

        BtcMarketContextResult result = service(properties(1, false))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.STALE_CONTEXT, result.contextStatus());
        assertFalse(result.entryAllowed());
        assertTrue(result.contextAgeSeconds() >= 120);
    }

    @Test
    void ageExactlyAtThreshold_blocks() {
        long thresholdSeconds = 120; // default 1m threshold
        Instant candleCloseTime = EVAL_TIME.minusSeconds(thresholdSeconds);
        Instant candleOpenTime = candleCloseTime.minusSeconds(60); // 1m candle length
        TradeSignal atThreshold = btcSignal(candleOpenTime, candleOpenTime.plusSeconds(5), SignalDecision.BUY, 20);
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(atThreshold));

        BtcMarketContextResult result = service(properties(1, false))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        // age == threshold must block ("stale when age >= threshold"), not pass as "still fresh."
        assertEquals(BtcContextStatus.STALE_CONTEXT, result.contextStatus());
        assertFalse(result.entryAllowed());
        assertEquals(thresholdSeconds, result.contextAgeSeconds());
    }

    @Test
    void nullCandleOpenTime_blocksEntry() {
        TradeSignal unknownTime = btcSignal(null, EVAL_TIME.minusSeconds(5), SignalDecision.BUY, 20);
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(unknownTime));

        BtcMarketContextResult result = service(properties(1, false))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.STALE_CONTEXT, result.contextStatus());
        assertFalse(result.entryAllowed());
    }

    @Test
    void futureDatedCandleClose_blocksEntry() {
        // Defensive case: even if a repository/replay bug returned a future-dated candidate,
        // evaluate() must still reject it rather than trusting the selection query alone.
        Instant futureOpenTime = EVAL_TIME.plusSeconds(600);
        TradeSignal future = btcSignal(futureOpenTime, EVAL_TIME.minusSeconds(1), SignalDecision.BUY, 20);
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(future));

        BtcMarketContextResult result = service(properties(1, false))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.STALE_CONTEXT, result.contextStatus());
        assertFalse(result.entryAllowed());
    }

    @Test
    void nullGeneratedAt_blocksEntry() {
        // Review correction: a null generatedAt is an unknown generation time, not a future
        // one - it must not be misreported as FUTURE_DATED. Still blocks entry either way.
        Instant candleOpenTime = EVAL_TIME.minusSeconds(60);
        TradeSignal noGeneratedAt = btcSignal(candleOpenTime, null, SignalDecision.BUY, 20);
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(noGeneratedAt));

        BtcMarketContextResult result = service(properties(1, false))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.STALE_CONTEXT, result.contextStatus());
        assertFalse(result.entryAllowed());
    }

    @Test
    void futureDatedGeneratedAt_blocksEntry() {
        Instant candleOpenTime = EVAL_TIME.minusSeconds(60);
        TradeSignal future = btcSignal(candleOpenTime, EVAL_TIME.plusSeconds(600), SignalDecision.BUY, 20);
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(future));

        BtcMarketContextResult result = service(properties(1, false))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.STALE_CONTEXT, result.contextStatus());
        assertFalse(result.entryAllowed());
    }

    @Test
    void unsupportedInterval_blocksEntry() {
        BtcMarketContextResult result = service(properties(1, false))
                .evaluate(ASSET, "3m", SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.STALE_CONTEXT, result.contextStatus());
        assertFalse(result.entryAllowed());
    }

    // ---- "A recently generated signal for an old candle remains stale" ----

    @Test
    void recentGeneratedAtForOldCandle_remainsStale() {
        // Candle is old (way past the 1m threshold) but was (re)persisted/backfilled seconds ago.
        Instant oldCandleOpenTime = EVAL_TIME.minusSeconds(3600);
        Instant recentGeneratedAt = EVAL_TIME.minusSeconds(2);
        TradeSignal backfilled = btcSignal(oldCandleOpenTime, recentGeneratedAt, SignalDecision.BUY, 20);
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(backfilled));

        BtcMarketContextResult result = service(properties(1, false))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.STALE_CONTEXT, result.contextStatus(),
                "generatedAt is not a substitute for candle freshness");
        assertFalse(result.entryAllowed());
    }

    // ---- Fresh context: LEARNING branch (freshness validated, then correlation gates) ----

    @Test
    void freshContext_insufficientSamples_preservesPassThroughByDefault() {
        stubNoCorrelationHistory(); // sampleSize ends up 0, well below minimumSamples
        Instant candleOpenTime = EVAL_TIME.minusSeconds(65); // closes 5s ago, well under 120s threshold
        TradeSignal fresh = btcSignal(candleOpenTime, candleOpenTime.plusSeconds(10), SignalDecision.BUY, 20);
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(fresh));

        BtcMarketContextResult result = service(properties(5, false))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.LEARNING, result.contextStatus());
        assertTrue(result.entryAllowed(), "default policy preserves existing pass-through once freshness is validated");
    }

    @Test
    void freshContext_insufficientSamples_blocksWhenConfiguredTo() {
        stubNoCorrelationHistory();
        Instant candleOpenTime = EVAL_TIME.minusSeconds(65);
        TradeSignal fresh = btcSignal(candleOpenTime, candleOpenTime.plusSeconds(10), SignalDecision.BUY, 20);
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(fresh));

        BtcMarketContextResult result = service(properties(5, true))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.LEARNING, result.contextStatus());
        assertFalse(result.entryAllowed(), "team-configured policy may block fresh-but-learning context");
    }

    // ---- Fresh context, sufficient samples: existing classify/conflict logic still runs ----

    @Test
    void freshContext_sufficientSamples_confirmsBullishSetup() {
        Instant candleOpenTime = EVAL_TIME.minusSeconds(65);
        List<Candle> assetCandles = trendingCandles(ASSET, candleOpenTime, 10, true);
        List<Candle> btcCandles = trendingCandles(BTC, candleOpenTime, 10, true);
        when(candleRepository.findClosedCandles(anyString(), anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    String symbol = invocation.getArgument(0);
                    return BTC.equals(symbol) ? btcCandles : assetCandles;
                });

        TradeSignal fresh = btcSignal(candleOpenTime, candleOpenTime.plusSeconds(10), SignalDecision.BUY, 25);
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(fresh));

        BtcMarketContextResult result = service(properties(2, false))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.CONFIRMED, result.contextStatus());
        assertTrue(result.entryAllowed());
        assertTrue(result.sampleSize() >= 2, "identical bullish trends on both series should clear minimumSamples");
    }

    // ---- Production / Replay parity ----

    @Test
    void productionAndReplay_agreeOnStaleContext_atSameEvaluationTime() {
        // Review correction: relationship() now runs only after the freshness gate passes, so
        // this STALE_CONTEXT case never reaches candleRepository on either path - no candle
        // stubs needed for production or replay here.
        Instant candleOpenTime = EVAL_TIME.minusSeconds(5 * 60 + 60);
        TradeSignal stale = btcSignal(candleOpenTime, candleOpenTime.plusSeconds(65), SignalDecision.BUY, 20);

        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(stale));
        BtcMarketContextResult production = service(properties(1, false))
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        BtcMarketContextService replayService = service(properties(1, false));
        ReflectionTestUtils.setField(replayService, "replayScope", replayScope);
        when(replayScope.active()).thenReturn(true);
        when(replayScope.latestClosedAtOrBefore(anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(stale));
        BtcMarketContextResult replay = replayService
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(production.contextStatus(), replay.contextStatus());
        assertEquals(production.entryAllowed(), replay.entryAllowed());
        assertEquals(BtcContextStatus.STALE_CONTEXT, replay.contextStatus());
    }

    @Test
    void replay_usesSimulatedEvaluationTime_neverWallClock() {
        BtcMarketContextService replayService = service(properties(1, false));
        ReflectionTestUtils.setField(replayService, "replayScope", replayScope);
        when(replayScope.active()).thenReturn(true);
        // Review correction: this historical signal is stale relative to replayEvalTime, so
        // relationship() never runs and candleRepository is never touched - no stub needed.

        // A signal that would be "fresh" relative to the real current wall-clock time but is
        // stale relative to the replay's own (much earlier) simulated evaluation time.
        Instant replayEvalTime = Instant.parse("2020-01-01T00:05:00Z");
        Instant candleOpenTime = replayEvalTime.minusSeconds(5 * 60 + 60);
        TradeSignal historical = btcSignal(candleOpenTime, candleOpenTime.plusSeconds(10), SignalDecision.BUY, 20);
        when(replayScope.latestClosedAtOrBefore(anyString(), anyString(), any(), any()))
                .thenReturn(Optional.of(historical));

        BtcMarketContextResult result = replayService
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, replayEvalTime);

        assertEquals(BtcContextStatus.STALE_CONTEXT, result.contextStatus());
        // Age must be computed against replayEvalTime (seconds, small number), never Instant.now().
        assertTrue(result.contextAgeSeconds() < 1000);
    }

    // ---- Selection: candle-close ordering, not generatedAt ----

    @Test
    void selection_requestsCandleCloseBound_notGeneratedAtOnly() {
        // No signal -> STALE_CONTEXT (missing) -> relationship() never runs; no candle stub needed.
        when(tradeSignalRepository.findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                anyString(), anyString(), any(), any()))
                .thenReturn(Optional.empty());

        service(properties(1, false)).evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        Instant expectedCandleOpenTimeBound = EVAL_TIME.minusSeconds(60); // 1m duration
        verify(tradeSignalRepository).findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                BTC, INTERVAL, expectedCandleOpenTimeBound, EVAL_TIME);
    }

    // ---- NOT_APPLICABLE / disabled: unaffected by the freshness guard ----

    @Test
    void referenceSymbol_remainsNotApplicable_noFreshnessCheck() {
        BtcMarketContextResult result = service(properties(1, false))
                .evaluate(BTC, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.NOT_APPLICABLE, result.contextStatus());
        assertTrue(result.entryAllowed());
    }

    @Test
    void disabled_remainsUnavailable_noFreshnessCheck() {
        BtcContextProperties disabled = new BtcContextProperties(
                false, BTC, 200, 1, new BigDecimal("0.40"), new BigDecimal("0.70"),
                new BigDecimal("1.30"), true, null, false);

        BtcMarketContextResult result = service(disabled)
                .evaluate(ASSET, INTERVAL, SignalDecision.BUY, true, EVAL_TIME);

        assertEquals(BtcContextStatus.UNAVAILABLE, result.contextStatus());
        assertTrue(result.entryAllowed());
    }

    private List<Candle> trendingCandles(String symbol, Instant lastOpenTime, int count, boolean bullish) {
        List<Candle> candles = new ArrayList<>();
        BigDecimal price = new BigDecimal("100.00");
        BigDecimal step = bullish ? new BigDecimal("1.00") : new BigDecimal("-1.00");
        for (int i = 0; i < count; i++) {
            Instant openTime = lastOpenTime.minusSeconds(60L * (count - i));
            price = price.add(step);
            candles.add(Candle.builder()
                    .symbol(symbol).intervalCode(INTERVAL)
                    .openTime(openTime).closeTime(openTime.plusSeconds(60))
                    .closePrice(price).openPrice(price.subtract(step))
                    .highPrice(price).lowPrice(price.subtract(step))
                    .closed(true)
                    .build());
        }
        return candles;
    }
}
