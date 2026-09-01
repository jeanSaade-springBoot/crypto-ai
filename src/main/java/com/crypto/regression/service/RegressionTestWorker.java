package com.crypto.regression.service;

import com.crypto.config.BtcContextProperties;
import com.crypto.domain.Candle;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.market.service.MarketPriceEventService;
import com.crypto.execution.service.ExecutionReplayScope;
import com.crypto.service.AnalysisService;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.persistence.Column;

import java.lang.reflect.Field;

import java.sql.Timestamp;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegressionTestWorker {

    /**
     * Replay warm-up is context-only. It gives the production analysis/execution services
     * the same already-closed 1m/5m/1h history they would have had at the requested start
     * without allowing wallet executions before the requested test window.
     */
    private static final Duration REPLAY_CONTEXT_WARMUP = Duration.ofHours(3);

    private final JdbcTemplate jdbcTemplate;
    private final CandleRepository candleRepository;
    private final TradeSignalRepository signalRepository;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final AnalysisService analysisService;
    private final ShadowProductionReplayService shadowReplayService;
    private final ExecutionReplayScope replayScope;
    private final BtcContextProperties btcContextProperties;
    private final MarketPriceEventService marketPriceEventService;

    // FIX-087: heartbeat age alone cannot prove that a long replay worker has died.
    // Some deterministic replay stages can legitimately run for more than the stale-heartbeat
    // threshold without returning to updateRun(). Keep atomic in-JVM ownership so Resume cannot
    // launch a second worker for the same durable run while the first worker is still executing.
    private final Set<Long> activeRunIds = ConcurrentHashMap.newKeySet();

    // FIX-090: cooperative stop requests are tracked separately from active ownership. A Stop Test
    // request never pretends the worker is gone; Delete Data stays blocked until runAsync reaches
    // finally and removes the run from activeRunIds.
    private final Set<Long> stopRequestedRunIds = ConcurrentHashMap.newKeySet();

    public boolean isActive(long runId) {
        return activeRunIds.contains(runId);
    }

    public boolean hasActiveRuns() {
        return !activeRunIds.isEmpty();
    }

    public boolean requestStop(long runId) {
        if (!activeRunIds.contains(runId)) return false;
        stopRequestedRunIds.add(runId);
        return true;
    }

    public boolean isStopRequested(long runId) {
        return stopRequestedRunIds.contains(runId);
    }

    private void checkStopRequested(long runId) {
        if (stopRequestedRunIds.contains(runId)) throw new ReplayCancellationException(runId);
    }

    @Async
    public void runAsync(long runId, ReplayDataSource dataSource) {
        // FIX-087: close the race at worker entry as well. Even if two callers schedule the same
        // run almost simultaneously, only one may execute the deterministic replay pipeline.
        if (!activeRunIds.add(runId)) {
            log.warn("FIX-087: replay run {} already has an active worker; duplicate execution skipped", runId);
            return;
        }
        // FIX-11J: Replay-only observational timers. The worker pipeline below is intentionally
        // left in its existing order; these locals only capture elapsed monotonic time and are
        // persisted once in finally so diagnostics cannot steer or fail Replay business logic.
        final long replayTotalStartedNs = System.nanoTime();
        Long loadHistoricalDataNs = null;
        Long verifyEventResolutionNs = null;
        Long buildReplayDatasetNs = null;
        Long generateFreshSignalsNs = null;
        Long shadowExecutionNs = null;
        Long parityComparisonNs = null;
        try {
            long stageStartedNs = System.nanoTime();
            Map<String, Object> run = jdbcTemplate.queryForMap(
                    "SELECT symbol, start_time, end_time FROM analysis_test_run WHERE id = ?", runId);
            String symbol = String.valueOf(run.get("symbol"));
            Instant start = toInstant(run.get("start_time"));
            Instant end = toInstant(run.get("end_time"));

            updateRun(runId, "RUNNING", 2, "Loading historical candles and signals", null);
            checkStopRequested(runId);

            Instant contextStart = start.minus(REPLAY_CONTEXT_WARMUP);
            List<Candle> oneMinuteCandles = candles(symbol, "1m", contextStart, end);
            List<Candle> fiveMinuteCandles = candles(symbol, "5m", contextStart, end);
            List<Candle> oneHourCandles = candles(symbol, "1h", contextStart, end);
            List<Candle> requestedOneMinuteCandles = candlesInExecutionWindow(oneMinuteCandles, "1m", start, end);
            List<Candle> requestedFiveMinuteCandles = candlesInExecutionWindow(fiveMinuteCandles, "5m", start, end);
            List<Candle> requestedOneHourCandles = candlesInExecutionWindow(oneHourCandles, "1h", start, end);

            String btcSymbol = btcContextProperties.referenceSymbol();
            boolean replayBtcContext = btcContextProperties.enabled() && !btcSymbol.equalsIgnoreCase(symbol);
            List<Candle> btcOneMinuteCandles = replayBtcContext ? candles(btcSymbol, "1m", contextStart, end) : List.of();
            List<Candle> btcFiveMinuteCandles = replayBtcContext ? candles(btcSymbol, "5m", contextStart, end) : List.of();
            List<Candle> btcOneHourCandles = replayBtcContext ? candles(btcSymbol, "1h", contextStart, end) : List.of();

            List<TradeSignal> sourceSignals = signalRepository
                    .findBySymbolAndGeneratedAtBetweenOrderByGeneratedAtAsc(symbol, start, end);

            jdbcTemplate.update("UPDATE analysis_test_run SET source_signal_count=? WHERE id=?",
                    sourceSignals.size(), runId);
            loadHistoricalDataNs = System.nanoTime() - stageStartedNs;

            stageStartedNs = System.nanoTime();
            updateRun(runId, "RUNNING", 10, "Replaying event-candle resolution (no production writes)", null);
            checkStopRequested(runId);
            int replay1m = verifyEventResolution(runId, symbol, "1m", requestedOneMinuteCandles, 10, 28);
            int replay5m = verifyEventResolution(runId, symbol, "5m", requestedFiveMinuteCandles, 28, 38);
            int replay1h = verifyEventResolution(runId, symbol, "1h", requestedOneHourCandles, 38, 42);
            verifyEventResolutionNs = System.nanoTime() - stageStartedNs;

            updateRun(runId, "RUNNING", 43, "Generating fresh signals from historical candles", null);
            checkStopRequested(runId);
            // FIX-11H: OLD=DATABASE and NEW=DATASET are explicit replay-only modes. Production
            // has no dependency on this selector. verifyEventResolution() intentionally remains unchanged.
            ReplayDataset replayDataset;
            if (dataSource == ReplayDataSource.DATASET) {
                stageStartedNs = System.nanoTime();
                replayDataset = loadDataset(symbol, btcSymbol, contextStart, end);
                buildReplayDatasetNs = System.nanoTime() - stageStartedNs;
            } else {
                // DATABASE mode has no ReplayDataset build stage. Persist NULL so the UI can
                // distinguish "not applicable" from a fabricated zero-duration measurement.
                replayDataset = null;
            }
            log.info("FIX-11H replay data source: run={}, mode={}, symbol={}, start={}, end={}",
                    runId, dataSource, symbol, start, end);
            stageStartedNs = System.nanoTime();
            FreshReplayStats fresh = generateFreshSignals(
                    runId, symbol, start, end,
                    oneMinuteCandles, fiveMinuteCandles, oneHourCandles,
                    btcSymbol, btcOneMinuteCandles, btcFiveMinuteCandles, btcOneHourCandles,
                    dataSource, replayDataset);
            jdbcTemplate.update("""
                    UPDATE analysis_test_run
                    SET generated_signal_count=?, generated_buy_count=?, generated_watch_count=?,
                        generated_sell_count=?, generated_strong_sell_count=?
                    WHERE id=?
                    """, fresh.total(), fresh.buys(), fresh.watches(), fresh.sells(), fresh.strongSells(), runId);
            generateFreshSignalsNs = System.nanoTime() - stageStartedNs;

            stageStartedNs = System.nanoTime();
            updateRun(runId, "RUNNING", 74, "Running full shadow-production execution flow", null);
            checkStopRequested(runId);
            // FIX-052: Replay position protection consumes the same canonical live 1m
            // price observations that Production consumed. Historical windows before
            // V64 was deployed naturally have no events and are explicitly degraded to
            // signal/candle-close protection inside ShadowProductionReplayService.
            List<MarketPriceEventService.PriceEvent> productionPriceEvents =
                    marketPriceEventService.find(symbol, contextStart, end);
            ShadowProductionReplayService.ReplayStats shadow = shadowReplayService.replay(
                    runId, symbol, start, end, fresh.generatedSignals(), productionPriceEvents,
                    () -> isStopRequested(runId));
            // FIX-109: persist the parity contract with the run. A SIGNAL_PRICE_FALLBACK
            // result is useful historical evidence, but must never be presented as tick-exact
            // Production reproduction. Production parity is the default logic mode.
            jdbcTemplate.update("UPDATE analysis_test_run SET replay_price_mode=?, replay_logic_mode=? WHERE id=?",
                    shadow.priceReplayMode(), shadow.logicMode(), runId);
            checkStopRequested(runId);
            shadowExecutionNs = System.nanoTime() - stageStartedNs;

            stageStartedNs = System.nanoTime();
            updateRun(runId, "RUNNING", 82, "Comparing historical decision authority", null);
            checkStopRequested(runId);
            int authorityCorrections = 0;
            int replaySignals = 0;
            int oldHardReversals = 0;
            int correctedHardReversals = 0;

            for (TradeSignal signal : sourceSignals) {
                checkStopRequested(runId);
                SignalDecision finalDecision = signal.getDecision();
                SignalDecision original = signal.getOriginalDecision();
                SignalDecision effective = finalDecision != null ? finalDecision : original;
                boolean corrected = finalDecision != null && original != null
                        && isBearish(original) && !isBearish(finalDecision);
                if (corrected) authorityCorrections++;

                // Historical rows are reference-only; fresh replay rows are persisted separately.
                replaySignals++;

                if ("1m".equals(signal.getInterval())) {
                    TradeSignal five = signalRepository
                            .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                                    symbol, "5m", signal.getGeneratedAt()).orElse(null);
                    TradeSignal one = signalRepository
                            .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                                    symbol, "1h", signal.getGeneratedAt()).orElse(null);

                    SignalDecision fiveDecision = five == null ? null : five.getDecision();
                    SignalDecision oneDecision = one == null ? null : one.getDecision();
                    boolean higherBearish = isBearish(fiveDecision) || isBearish(oneDecision);
                    boolean oldHard = finalDecision == SignalDecision.STRONG_SELL
                            || original == SignalDecision.STRONG_SELL
                            || higherBearish;
                    boolean newHard = finalDecision == SignalDecision.STRONG_SELL || higherBearish;
                    if (oldHard) oldHardReversals++;
                    if (newHard) correctedHardReversals++;

                    String code;
                    String explanation;
                    if (oldHard && !newHard) {
                        code = "ORIGINAL_BEARISH_NEUTRALIZED";
                        explanation = "Old execution logic would hard-cancel because originalDecision was STRONG_SELL, "
                                + "but FinalDecisionService neutralized it and higher timeframes are not bearish.";
                    } else if (newHard) {
                        code = "BEARISH_REVERSAL";
                        explanation = "Corrected hard reversal remains valid from the final decision or final 5m/1h context.";
                    } else {
                        code = "NO_HARD_REVERSAL";
                        explanation = "No hard bearish reversal under corrected final-decision authority.";
                    }

                    jdbcTemplate.update("""
                            INSERT INTO execution_opportunity_test
                                (test_run_id, source_signal_id, symbol, generated_at,
                                 current_final_decision, current_original_decision,
                                 five_minute_decision, one_hour_decision,
                                 old_hard_bearish_reversal, corrected_hard_bearish_reversal,
                                 decision_code, decision_explanation)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            runId,
                            signal.getId(),
                            symbol,
                            Timestamp.from(signal.getGeneratedAt()),
                            finalDecision == null ? null : finalDecision.name(),
                            original == null ? null : original.name(),
                            fiveDecision == null ? null : fiveDecision.name(),
                            oneDecision == null ? null : oneDecision.name(),
                            oldHard,
                            newHard,
                            code,
                            explanation
                    );
                }
            }

            jdbcTemplate.update("""
                    UPDATE analysis_test_run
                    SET replay_signal_count=?, neutralized_original_bearish_count=?,
                        historical_hard_reversal_count=?, corrected_hard_reversal_count=?
                    WHERE id=?
                    """, replaySignals, authorityCorrections, oldHardReversals, correctedHardReversals, runId);

            updateRun(runId, "RUNNING", 94, "Calculating regression result", null);
            checkStopRequested(runId);

            int historical1m = countSignals(sourceSignals, "1m");
            int historical5m = countSignals(sourceSignals, "5m");
            int historical1h = countSignals(sourceSignals, "1h");
            int requestedCandles1m = requestedOneMinuteCandles.size();
            int requestedCandles5m = requestedFiveMinuteCandles.size();
            int requestedCandles1h = requestedOneHourCandles.size();
            boolean cadencePass = replay1m == requestedCandles1m
                    && replay5m == requestedCandles5m
                    && replay1h == requestedCandles1h;
            boolean authorityPass = sourceSignals.stream().allMatch(s -> {
                SignalDecision effective = s.getDecision() != null ? s.getDecision() : s.getOriginalDecision();
                return s.getDecision() == null || effective == s.getDecision();
            });
            boolean generationPass = fresh.errors() == 0 && fresh.total() > 0;
            // FIX-052: an "exact Production" replay must include the same live 1m price
            // observations used by LivePositionProtectionService. Do not report a green
            // parity result for older windows that only have candle-close approximations.
            boolean livePriceParityPass = !productionPriceEvents.isEmpty();
            boolean passed = cadencePass && authorityPass && generationPass && livePriceParityPass;

            String notes = "Fresh replay signals are generated from historical candles through TechnicalIndicatorService "
                    + "and the production AnalysisService scoring/final-decision path without trade_signal persistence. "
                    + "A three-hour context-only warm-up seeds 1m/5m/1h state before the requested execution window, and BTC reference signals are freshly replayed as-of each timestamp when BTC context is enabled. "
                    + "Historical signal counts are retained only as the pre-fix reference. "
                    + "Replayable event counts validate that each historical candle can now be resolved as-of its own close. "
                    + (livePriceParityPass
                        ? "FIX-052/FIX-056 exact price parity is active: Replay consumed " + productionPriceEvents.size() + " persisted Production 1m live-price observations in UTC order, exposes the latest event through the shared ExecutionPriceAuthorityService, and revalidates/sizes BUYs from that execution-time price. "
                        : "FIX-052/FIX-056 exact price parity is NOT available for this historical window because no persisted Production live-price observations exist; the run is intentionally not marked fully passed. ")
                    + "The decision replay validates that originalDecision is audit-only and cannot override a non-null final decision. "
                    + "Regression AnalysisService returns unsaved TradeSignal objects. Fresh signals then pass through an isolated shadow execution/position lifecycle that records exact simulated BUY/SELL points. Real wallet, trade_signal and production execution_opportunity tables are never written.";

            // FIX-087: one result row per run is an invariant. Worker ownership prevents the
            // overlap at its source; this idempotent write is the database-level last safety net
            // so a duplicate completion can never turn an otherwise valid replay into ERROR.
            jdbcTemplate.update("""
                    INSERT INTO analysis_test_result
                        (test_run_id,
                         candles_1m, signals_1m_historical, replayable_1m_events, generated_signals_1m, generated_buys_1m,
                         candles_5m, signals_5m_historical, replayable_5m_events, generated_signals_5m, generated_buys_5m,
                         candles_1h, signals_1h_historical, replayable_1h_events, generated_signals_1h, generated_buys_1h,
                         generated_signal_errors, simulated_trades, simulated_wins, simulated_losses,
                         simulated_realized_pnl, simulated_final_wallet,
                         decision_authority_corrections, old_hard_bearish_reversals,
                         corrected_hard_bearish_reversals, cadence_path_passed, decision_authority_passed, test_passed, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        candles_1m=VALUES(candles_1m),
                        signals_1m_historical=VALUES(signals_1m_historical),
                        replayable_1m_events=VALUES(replayable_1m_events),
                        generated_signals_1m=VALUES(generated_signals_1m),
                        generated_buys_1m=VALUES(generated_buys_1m),
                        candles_5m=VALUES(candles_5m),
                        signals_5m_historical=VALUES(signals_5m_historical),
                        replayable_5m_events=VALUES(replayable_5m_events),
                        generated_signals_5m=VALUES(generated_signals_5m),
                        generated_buys_5m=VALUES(generated_buys_5m),
                        candles_1h=VALUES(candles_1h),
                        signals_1h_historical=VALUES(signals_1h_historical),
                        replayable_1h_events=VALUES(replayable_1h_events),
                        generated_signals_1h=VALUES(generated_signals_1h),
                        generated_buys_1h=VALUES(generated_buys_1h),
                        generated_signal_errors=VALUES(generated_signal_errors),
                        simulated_trades=VALUES(simulated_trades),
                        simulated_wins=VALUES(simulated_wins),
                        simulated_losses=VALUES(simulated_losses),
                        simulated_realized_pnl=VALUES(simulated_realized_pnl),
                        simulated_final_wallet=VALUES(simulated_final_wallet),
                        decision_authority_corrections=VALUES(decision_authority_corrections),
                        old_hard_bearish_reversals=VALUES(old_hard_bearish_reversals),
                        corrected_hard_bearish_reversals=VALUES(corrected_hard_bearish_reversals),
                        cadence_path_passed=VALUES(cadence_path_passed),
                        decision_authority_passed=VALUES(decision_authority_passed),
                        test_passed=VALUES(test_passed),
                        notes=VALUES(notes)
                    """,
                    runId,
                    requestedCandles1m, historical1m, replay1m, fresh.oneMinuteSignals(), fresh.oneMinuteBuys(),
                    requestedCandles5m, historical5m, replay5m, fresh.fiveMinuteSignals(), fresh.fiveMinuteBuys(),
                    requestedCandles1h, historical1h, replay1h, fresh.oneHourSignals(), fresh.oneHourBuys(),
                    fresh.errors(), shadow.trades(), shadow.wins(), shadow.losses(), shadow.realizedPnl(), shadow.finalWallet(),
                    authorityCorrections, oldHardReversals, correctedHardReversals,
                    cadencePass, authorityPass, passed, notes
            );
            parityComparisonNs = System.nanoTime() - stageStartedNs;

            jdbcTemplate.update("""
                    UPDATE analysis_test_run
                    SET status=?, progress_percent=100, current_step=?, heartbeat_at=CURRENT_TIMESTAMP(6), completed_at=CURRENT_TIMESTAMP(6)
                    WHERE id=?
                    """, passed ? "PASSED" : "FAILED", passed ? "Regression checks passed" : "Regression checks failed", runId);

        } catch (ReplayCancellationException stop) {
            // FIX-090: only mark ERROR after the replay pipeline has actually unwound to here.
            // This makes ERROR a trustworthy signal that Delete Data may proceed once active_worker=false.
            log.warn("FIX-090: regression test run {} stopped by user request", runId);
            jdbcTemplate.update("""
                    UPDATE analysis_test_run
                    SET status='ERROR', current_step='Stopped by user',
                        error_message='Replay stopped manually by user. Test data may now be deleted.',
                        failure_step='Manual stop', failure_exception=NULL, failure_root_cause=NULL,
                        failure_stack_trace=NULL, heartbeat_at=CURRENT_TIMESTAMP(6), completed_at=CURRENT_TIMESTAMP(6)
                    WHERE id=?
                    """, runId);
        } catch (Exception exception) {
            log.error("Regression test run {} failed", runId, exception);
            String failedStep = currentStep(runId);
            String summary = errorDetail(exception, 1900);
            Throwable root = rootCause(exception);
            jdbcTemplate.update("""
                    UPDATE analysis_test_run
                    SET status='ERROR', progress_percent=100, current_step='Regression test failed', heartbeat_at=CURRENT_TIMESTAMP(6),
                        error_message=?, failure_step=?, failure_exception=?, failure_root_cause=?,
                        failure_stack_trace=?, started_at=COALESCE(started_at, CURRENT_TIMESTAMP(6)),
                        completed_at=CURRENT_TIMESTAMP(6)
                    WHERE id=?
                    """,
                    summary, failedStep, exception.getClass().getName(),
                    rootCauseDetail(root, 1000), stackTrace(exception), runId);
        } finally {
            // FIX-11J: persist one timing snapshot after the existing Replay path has completed or
            // unwound. Diagnostics persistence is deliberately best-effort: it must never replace
            // the Replay's real PASSED/FAILED/ERROR outcome or swallow the original exception.
            long replayTotalNs = System.nanoTime() - replayTotalStartedNs;
            persistReplayTimingsSafely(runId, loadHistoricalDataNs, verifyEventResolutionNs,
                    buildReplayDatasetNs, generateFreshSignalsNs, shadowExecutionNs,
                    parityComparisonNs, replayTotalNs);

            // FIX-087: always release ownership, including ERROR paths, so a genuinely stopped
            // replay remains recoverable later using the same run id and original replay window.
            activeRunIds.remove(runId);
            stopRequestedRunIds.remove(runId);
        }
    }

    private void persistReplayTimingsSafely(long runId, Long loadHistoricalDataNs,
                                            Long verifyEventResolutionNs, Long buildReplayDatasetNs,
                                            Long generateFreshSignalsNs, Long shadowExecutionNs,
                                            Long parityComparisonNs, long replayTotalNs) {
        try {
            jdbcTemplate.update("""
                    UPDATE analysis_test_run
                    SET timing_load_historical_ns=?, timing_verify_event_resolution_ns=?,
                        timing_build_replay_dataset_ns=?, timing_generate_fresh_signals_ns=?,
                        timing_shadow_execution_ns=?, timing_parity_comparison_ns=?, timing_total_ns=?
                    WHERE id=?
                    """, loadHistoricalDataNs, verifyEventResolutionNs, buildReplayDatasetNs,
                    generateFreshSignalsNs, shadowExecutionNs, parityComparisonNs, replayTotalNs, runId);
        } catch (Exception timingPersistenceFailure) {
            log.error("FIX-11J: unable to persist Replay timing diagnostics for run {}",
                    runId, timingPersistenceFailure);
        }
    }

    /**
     * FIX-11H: loads one immutable replay dataset. The prefix comes from the exact existing
     * closed/as-of query, so missing candle gaps cannot shorten history. The forward segment
     * explicitly requires closed=true. No Production caller reaches this method.
     */
    private ReplayDataset loadDataset(String symbol, String btcSymbol, Instant start, Instant end) {
        Map<ReplayDataset.CandleKey, List<Candle>> candleMap = new HashMap<>();
        Set<String> symbols = new LinkedHashSet<>(List.of(symbol, btcSymbol));
        int historyLimit = technicalIndicatorService.regressionHistoryLimit();
        for (String sym : symbols) {
            for (String interval : List.of("1m", "5m", "1h")) {
                List<Candle> prefix = new java.util.ArrayList<>(candleRepository.findClosedCandlesAtOrBefore(
                        sym, interval, start, PageRequest.of(0, historyLimit)));
                java.util.Collections.reverse(prefix);
                List<Candle> forward = candleRepository
                        .findBySymbolAndIntervalCodeAndClosedTrueAndOpenTimeBetweenOrderByOpenTimeAsc(
                                sym, interval, start, end);
                List<Candle> merged = new java.util.ArrayList<>(prefix.size() + forward.size());
                merged.addAll(prefix);
                Instant prefixEnd = prefix.isEmpty() ? Instant.EPOCH : prefix.get(prefix.size() - 1).getOpenTime();
                forward.stream().filter(c -> c.getOpenTime().isAfter(prefixEnd)).forEach(merged::add);
                candleMap.put(new ReplayDataset.CandleKey(sym, interval), merged);
                log.info("FIX-11H dataset candles: symbol={}, interval={}, prefix={}, forward={}, merged={}",
                        sym, interval, prefix.size(), forward.size(), merged.size());
            }
        }

        Map<ReplayDataset.SignalKey, Long> lineage = new HashMap<>();
        // FIX-11H / FIX-112D: lineage is candle identity, not signal persistence timing.
        // Recovery/backfilled Production signals can be generated hours after their candle, so
        // generatedAt MUST NOT define this preload window. Select and match lineage by the same
        // exact candleOpenTime identity used by ReplayDataset.sourceSignalId().
        for (TradeSignal signal : signalRepository.findBySymbolAndCandleOpenTimeBetweenOrderByCandleOpenTimeAsc(symbol, start, end)) {
            ReplayDataset.SignalKey key = new ReplayDataset.SignalKey(
                    signal.getSymbol(), signal.getInterval(), signal.getCandleOpenTime());
            Long previous = lineage.putIfAbsent(key, signal.getId());
            if (previous != null && !previous.equals(signal.getId())) {
                throw new IllegalStateException("Replay lineage invariant violated for " + key
                        + ": signalIds=" + previous + "," + signal.getId());
            }
        }
        log.info("FIX-11H dataset lineage: symbol={}, entries={}", symbol, lineage.size());
        return new ReplayDataset(candleMap, lineage);
    }

    private List<Candle> candles(String symbol, String interval, Instant start, Instant end) {
        return candleRepository.findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(
                symbol, interval, start, end);
    }

    private int verifyEventResolution(long runId, String symbol, String interval, List<Candle> candles,
                                      int progressStart, int progressEnd) {
        int resolved = 0;
        int size = candles.size();
        for (int i = 0; i < size; i++) {
            checkStopRequested(runId);
            Candle candle = candles.get(i);
            List<Candle> latestAtEvent = candleRepository.findClosedCandlesAtOrBefore(
                    symbol, interval, candle.getOpenTime(), PageRequest.of(0, 1));
            if (!latestAtEvent.isEmpty() && candle.getOpenTime().equals(latestAtEvent.get(0).getOpenTime())) {
                resolved++;
            }
            if (i % 25 == 0 && size > 0) {
                int p = progressStart + (int) Math.round((progressEnd - progressStart) * (i / (double) size));
                updateRun(runId, "RUNNING", p,
                        "Replaying " + interval + " candle " + (i + 1) + "/" + size, null);
            }
        }
        return resolved;
    }

    private FreshReplayStats generateFreshSignals(
            long runId,
            String symbol,
            Instant executionStart,
            Instant executionEnd,
            List<Candle> oneMinuteCandles,
            List<Candle> fiveMinuteCandles,
            List<Candle> oneHourCandles,
            String btcSymbol,
            List<Candle> btcOneMinuteCandles,
            List<Candle> btcFiveMinuteCandles,
            List<Candle> btcOneHourCandles,
            ReplayDataSource dataSource,
            ReplayDataset replayDataset
    ) {
        List<ReplayCandle> timeline = new java.util.ArrayList<>();
        oneMinuteCandles.forEach(c -> timeline.add(new ReplayCandle(symbol, "1m", c, true)));
        fiveMinuteCandles.forEach(c -> timeline.add(new ReplayCandle(symbol, "5m", c, true)));
        oneHourCandles.forEach(c -> timeline.add(new ReplayCandle(symbol, "1h", c, true)));
        btcOneMinuteCandles.forEach(c -> timeline.add(new ReplayCandle(btcSymbol, "1m", c, false)));
        btcFiveMinuteCandles.forEach(c -> timeline.add(new ReplayCandle(btcSymbol, "5m", c, false)));
        btcOneHourCandles.forEach(c -> timeline.add(new ReplayCandle(btcSymbol, "1h", c, false)));
        timeline.sort(java.util.Comparator
                .comparing((ReplayCandle rc) -> rc.candle().getOpenTime().plusSeconds(intervalSeconds(rc.interval())))
                .thenComparingInt(rc -> intervalOrder(rc.interval()))
                .thenComparingInt(rc -> rc.primary() ? 1 : 0));

        int oneMinuteSignals = 0, fiveMinuteSignals = 0, oneHourSignals = 0;
        int oneMinuteBuys = 0, fiveMinuteBuys = 0, oneHourBuys = 0;
        int buys = 0, watches = 0, sells = 0, strongSells = 0, errors = 0;
        java.util.List<TradeSignal> generatedSignals = new java.util.ArrayList<>();

        // FIX-11L: Replay-only deep profiling for the Generate fresh signals stage.
        // These monotonic timers observe existing calls only; they do not alter inputs,
        // call ordering, persistence, thresholds, or any Production trading behavior.
        ReplayFreshSignalProfiler profiler = new ReplayFreshSignalProfiler(runId, dataSource);
        // FIX-11M: Enable aggregate timing only for this Replay worker thread.
        // The shared AnalysisService decision path is unchanged; this is observation-only.
        analysisService.beginReplayAnalysisProfiling(runId);
        try (ExecutionReplayScope.Scope ignored = replayScope.open(runId, List.of(), o -> {})) {
        // FIX-043 Replay parity: fresh Replay has always generated technical snapshots/signals
        // chronologically for EVERY closed candle in the timeline. Production now restores the
        // same no-gap contract with asynchronous close-event processing plus chronological recovery.
        // Do not "optimize" Replay by sampling every Nth candle or by copying the production recovery
        // scheduler cadence; that would recreate the exact 1m->~5m blind-gap incident FIX-043 fixes.
        for (int index = 0; index < timeline.size(); index++) {
            checkStopRequested(runId);
            ReplayCandle replay = timeline.get(index);
            Candle candle = replay.candle();
            Instant evaluationTime = candle.getOpenTime().plusSeconds(intervalSeconds(replay.interval()));
            boolean inRequestedWindow = !evaluationTime.isBefore(executionStart) && !evaluationTime.isAfter(executionEnd);
            try {
                long operationStartedNs = System.nanoTime();
                java.util.Optional<IndicatorSnapshot> snapshot;
                try {
                    snapshot = dataSource == ReplayDataSource.DATASET
                            ? technicalIndicatorService.calculateSnapshotForRegression(
                                    replay.symbol(), replay.interval(), candle.getOpenTime(), replayDataset)
                            : technicalIndicatorService.calculateSnapshotForRegression(
                                    replay.symbol(), replay.interval(), candle.getOpenTime());
                } finally {
                    profiler.recordSnapshot(System.nanoTime() - operationStartedNs);
                }
                if (snapshot.isEmpty()) {
                    if (inRequestedWindow) {
                        errors++;
                        saveGenerationError(runId, replay.symbol(), replay.interval(), candle,
                                "Not enough historical candles to calculate the technical snapshot");
                    }
                    continue;
                }

                // Do not generate a signal whose candle would only have closed after the
                // requested test end. Warm-up rows before executionStart are intentionally
                // generated for as-of context, but are not counted or displayed as test output.
                if (evaluationTime.isAfter(executionEnd)) continue;

                replayScope.reference(evaluationTime);
                operationStartedNs = System.nanoTime();
                TradeSignal generated;
                try {
                    generated = analysisService.analyzeForRegression(snapshot.get(), evaluationTime);
                } finally {
                    profiler.recordAnalysis(System.nanoTime() - operationStartedNs);
                }
                replayScope.appendSignal(generated);
                SignalDecision decision = generated.getDecision();
                if (replay.primary()) generatedSignals.add(generated);

                if (replay.primary() && inRequestedWindow) {
                    if ("1m".equals(replay.interval())) { oneMinuteSignals++; if (isBuy(decision)) oneMinuteBuys++; }
                    else if ("5m".equals(replay.interval())) { fiveMinuteSignals++; if (isBuy(decision)) fiveMinuteBuys++; }
                    else if ("1h".equals(replay.interval())) { oneHourSignals++; if (isBuy(decision)) oneHourBuys++; }

                    if (decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY) buys++;
                    else if (decision == SignalDecision.WATCH) watches++;
                    else if (decision == SignalDecision.SELL) sells++;
                    else if (decision == SignalDecision.STRONG_SELL) strongSells++;

                    operationStartedNs = System.nanoTime();
                    try {
                        jdbcTemplate.update("""
                            INSERT INTO analysis_test_signal
                                (test_run_id, source_signal_id, replay_generated, symbol, interval_code, candle_open_time,
                                 generated_at, latest_price, original_decision, final_decision,
                                 execution_effective_decision, total_score, confidence_score, raw_confidence_score,
                                 effective_confidence_score, primary_blocking_stage, detected_regime, candidate_regime,
                                 confirmed_regime, regime_candidate_count, entry_authority, entry_authority_max_position_percent,
                                 trend_score, volume_score, momentum_score, decision_authority_corrected, generation_error)
                            VALUES (?, NULL, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL)
                            """,
                            runId, replay.symbol(), replay.interval(), Timestamp.from(candle.getOpenTime()),
                            Timestamp.from(evaluationTime), generated.getLatestPrice(),
                            generated.getOriginalDecision() == null ? null : generated.getOriginalDecision().name(),
                            decision == null ? null : decision.name(),
                            decision == null ? null : decision.name(),
                            generated.getTotalScore(), generated.getConfidenceScore(), generated.getRawConfidenceScore(),
                            generated.getEffectiveConfidenceScore(), generated.getPrimaryBlockingStage(),
                            generated.getDetectedRegime() == null ? null : generated.getDetectedRegime().name(),
                            generated.getCandidateRegime() == null ? null : generated.getCandidateRegime().name(),
                            generated.getConfirmedRegime() == null ? null : generated.getConfirmedRegime().name(),
                            generated.getRegimeCandidateCount(), generated.getEntryAuthority(),
                            generated.getEntryAuthorityMaxPositionPercent(), generated.getTrendScore(),
                            generated.getVolumeScore(), generated.getMomentumScore());
                    } finally {
                        profiler.recordAnalysisTestSignalPersistence(System.nanoTime() - operationStartedNs);
                    }

                    // FIX-069: Persist the exact production TradeSignal shape as replay output.
                    // analysis_test_signal remains for backward-compatible diagnostics, while
                    // trade_signal_test is the canonical parity table used for field-by-field comparison.
                    // FIX-112D: Replay lineage uses exact market-candle identity, never
                    // generated_at or nearest-time matching. A legitimate Replay-only
                    // signal remains NULL. This metadata lookup cannot alter Production.
                    operationStartedNs = System.nanoTime();
                    Long sourceSignalId;
                    try {
                        sourceSignalId = dataSource == ReplayDataSource.DATASET
                                ? replayDataset.sourceSignalId(
                                        generated.getSymbol(), generated.getInterval(), generated.getCandleOpenTime())
                                : signalRepository.findBySymbolAndIntervalAndCandleOpenTime(
                                        generated.getSymbol(), generated.getInterval(), generated.getCandleOpenTime())
                                        .map(TradeSignal::getId).orElse(null);
                    } finally {
                        profiler.recordLineageLookup(System.nanoTime() - operationStartedNs);
                    }
                    operationStartedNs = System.nanoTime();
                    try {
                        persistTradeSignalTest(runId, generated, sourceSignalId, null);
                    } finally {
                        profiler.recordTradeSignalTestPersistence(System.nanoTime() - operationStartedNs);
                    }
                }
            } catch (ReplayCancellationException stop) {
                throw stop;
            } catch (Exception exception) {
                if (replay.primary() && inRequestedWindow) {
                    errors++;
                    saveGenerationError(runId, replay.symbol(), replay.interval(), candle, errorDetail(exception, 900));
                }
                log.warn("Regression fresh-signal generation failed: run={}, symbol={}, interval={}, candle={}",
                        runId, replay.symbol(), replay.interval(), candle.getOpenTime(), exception);
            }

            if (index % 10 == 0 && !timeline.isEmpty()) {
                int progress = 43 + (int) Math.round(30d * index / timeline.size());
                long progressStartedNs = System.nanoTime();
                try {
                    updateRun(runId, "RUNNING", Math.min(73, progress),
                            "Generating fresh signal " + (index + 1) + "/" + timeline.size(), null);
                } finally {
                    profiler.recordProgressUpdate(System.nanoTime() - progressStartedNs);
                }
            }
        }

        } finally {
            // FIX-11M must never turn a valid Replay into ERROR if diagnostic summary logging fails.
            try {
                analysisService.finishReplayAnalysisProfiling(runId);
            } catch (Exception profilingException) {
                log.warn("FIX11M_REPLAY_ANALYSIS_PROFILE_ERROR run={} message={} productionMutation=false replayDecisionMutation=false",
                        runId, profilingException.getMessage());
            }
            profiler.logSummary(log, timeline.size());
        }
        return new FreshReplayStats(oneMinuteSignals, oneMinuteBuys, fiveMinuteSignals, fiveMinuteBuys,
                oneHourSignals, oneHourBuys, buys, watches, sells, strongSells, errors, generatedSignals);
    }

    private void saveGenerationError(long runId, String symbol, String interval, Candle candle, String error) {
        jdbcTemplate.update("""
                INSERT INTO analysis_test_signal
                    (test_run_id, source_signal_id, replay_generated, symbol, interval_code, candle_open_time,
                     generated_at, latest_price, decision_authority_corrected, generation_error)
                VALUES (?, NULL, 1, ?, ?, ?, ?, ?, 0, ?)
                """, runId, symbol, interval, Timestamp.from(candle.getOpenTime()),
                Timestamp.from(candle.getOpenTime().plusSeconds(intervalSeconds(interval))),
                candle.getClosePrice(), error);
    }

    /**
     * FIX-069: write every field mapped by the TradeSignal JPA entity into trade_signal_test.
     * Reflection is intentional here: whenever production TradeSignal gains another @Column,
     * replay inherits it automatically instead of drifting behind a hand-maintained test schema.
     */
    private void persistTradeSignalTest(long runId, TradeSignal signal, Long sourceSignalId, String generationError) {
        try {
            java.util.List<String> columns = new java.util.ArrayList<>();
            java.util.List<Object> values = new java.util.ArrayList<>();
            columns.add("test_run_id"); values.add(runId);
            // FIX-112D / Replay=Production audit lineage: this ID points only to the
            // exact Production signal for the same symbol + interval + candle_open_time.
            // It is additive Replay metadata and is never an execution authority.
            columns.add("source_signal_id"); values.add(sourceSignalId);
            columns.add("replay_generated"); values.add(1);
            columns.add("generation_error"); values.add(generationError);

            for (Field field : TradeSignal.class.getDeclaredFields()) {
                if ("id".equals(field.getName()) || "createdAt".equals(field.getName())) continue;
                Column column = field.getAnnotation(Column.class);
                if (column == null) continue;
                field.setAccessible(true);
                Object value = field.get(signal);
                if (value instanceof Enum<?> e) value = e.name();
                if (value instanceof Instant instant) value = Timestamp.from(instant);
                String columnName = column.name();
                if (columnName == null || columnName.isBlank()) columnName = snakeCase(field.getName());
                columns.add(columnName);
                values.add(value);
            }

            String placeholders = String.join(",", java.util.Collections.nCopies(values.size(), "?"));
            jdbcTemplate.update("INSERT INTO trade_signal_test (" + String.join(",", columns) + ") VALUES (" + placeholders + ")", values.toArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist production-shaped replay TradeSignal", e);
        }
    }

    private String snakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private long intervalSeconds(String interval) {
        return switch (interval) {
            case "1m" -> 60L;
            case "5m" -> 300L;
            case "1h" -> 3600L;
            default -> 60L;
        };
    }

    private int intervalOrder(String interval) {
        return switch (interval) {
            case "1h" -> 0;
            case "5m" -> 1;
            default -> 2;
        };
    }

    private boolean isBuy(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    /**
     * FIX-11L Replay-only profiler for the fresh-signal generation loop. It deliberately
     * aggregates timings in memory and emits one summary log after the stage; there are no
     * per-signal log writes and no Production/shared-analysis branches.
     */
    private static final class ReplayFreshSignalProfiler {
        private final long runId;
        private final ReplayDataSource dataSource;
        private final long startedNs = System.nanoTime();
        private long snapshotNs;
        private long analysisNs;
        private long analysisTestSignalPersistenceNs;
        private long lineageLookupNs;
        private long tradeSignalTestPersistenceNs;
        private long progressUpdateNs;
        private long snapshotCalls;
        private long analysisCalls;
        private long analysisTestSignalPersistenceCalls;
        private long lineageLookupCalls;
        private long tradeSignalTestPersistenceCalls;
        private long progressUpdateCalls;
        private long maxSnapshotNs;
        private long maxAnalysisNs;

        private ReplayFreshSignalProfiler(long runId, ReplayDataSource dataSource) {
            this.runId = runId;
            this.dataSource = dataSource;
        }

        private void recordSnapshot(long elapsedNs) {
            snapshotNs += elapsedNs;
            snapshotCalls++;
            maxSnapshotNs = Math.max(maxSnapshotNs, elapsedNs);
        }

        private void recordAnalysis(long elapsedNs) {
            analysisNs += elapsedNs;
            analysisCalls++;
            maxAnalysisNs = Math.max(maxAnalysisNs, elapsedNs);
        }

        private void recordAnalysisTestSignalPersistence(long elapsedNs) {
            analysisTestSignalPersistenceNs += elapsedNs;
            analysisTestSignalPersistenceCalls++;
        }

        private void recordLineageLookup(long elapsedNs) {
            lineageLookupNs += elapsedNs;
            lineageLookupCalls++;
        }

        private void recordTradeSignalTestPersistence(long elapsedNs) {
            tradeSignalTestPersistenceNs += elapsedNs;
            tradeSignalTestPersistenceCalls++;
        }

        private void recordProgressUpdate(long elapsedNs) {
            progressUpdateNs += elapsedNs;
            progressUpdateCalls++;
        }

        private void logSummary(org.slf4j.Logger logger, int timelineSize) {
            long totalNs = System.nanoTime() - startedNs;
            long measuredNs = snapshotNs + analysisNs + analysisTestSignalPersistenceNs
                    + lineageLookupNs + tradeSignalTestPersistenceNs + progressUpdateNs;
            long otherNs = Math.max(0L, totalNs - measuredNs);
            logger.info(
                    "FIX11L_REPLAY_SIGNAL_PROFILE run={} dataSource={} timeline={} totalMs={} "
                            + "snapshotMs={} snapshotCalls={} snapshotAvgMs={} snapshotMaxMs={} "
                            + "analysisMs={} analysisCalls={} analysisAvgMs={} analysisMaxMs={} "
                            + "analysisTestSignalPersistMs={} analysisTestSignalPersistCalls={} "
                            + "lineageMs={} lineageCalls={} tradeSignalTestPersistMs={} tradeSignalTestPersistCalls={} "
                            + "progressMs={} progressCalls={} otherMs={} productionMutation=false replayDecisionMutation=false",
                    runId, dataSource, timelineSize, millis(totalNs),
                    millis(snapshotNs), snapshotCalls, averageMillis(snapshotNs, snapshotCalls), millis(maxSnapshotNs),
                    millis(analysisNs), analysisCalls, averageMillis(analysisNs, analysisCalls), millis(maxAnalysisNs),
                    millis(analysisTestSignalPersistenceNs), analysisTestSignalPersistenceCalls,
                    millis(lineageLookupNs), lineageLookupCalls, millis(tradeSignalTestPersistenceNs),
                    tradeSignalTestPersistenceCalls, millis(progressUpdateNs), progressUpdateCalls, millis(otherNs));
        }

        private static String millis(long ns) {
            return String.format(java.util.Locale.ROOT, "%.3f", ns / 1_000_000.0d);
        }

        private static String averageMillis(long ns, long calls) {
            return calls == 0L ? "0.000"
                    : String.format(java.util.Locale.ROOT, "%.3f", (ns / 1_000_000.0d) / calls);
        }
    }

    private record ReplayCandle(String symbol, String interval, Candle candle, boolean primary) {}

    private record FreshReplayStats(
            int oneMinuteSignals, int oneMinuteBuys,
            int fiveMinuteSignals, int fiveMinuteBuys,
            int oneHourSignals, int oneHourBuys,
            int buys, int watches, int sells, int strongSells, int errors,
            java.util.List<TradeSignal> generatedSignals
    ) {
        int total() { return oneMinuteSignals + fiveMinuteSignals + oneHourSignals; }
    }

    private List<Candle> candlesInExecutionWindow(List<Candle> candles, String interval, Instant start, Instant end) {
        return candles.stream()
                .filter(c -> {
                    Instant t = c.getOpenTime().plusSeconds(intervalSeconds(interval));
                    return !t.isBefore(start) && !t.isAfter(end);
                })
                .toList();
    }

    private int countSignals(List<TradeSignal> signals, String interval) {
        return (int) signals.stream().filter(s -> interval.equals(s.getInterval())).count();
    }

    private boolean isBearish(SignalDecision decision) {
        return decision == SignalDecision.SELL || decision == SignalDecision.STRONG_SELL;
    }

    private void updateRun(long runId, String status, int progress, String step, String error) {
        jdbcTemplate.update("""
                UPDATE analysis_test_run
                SET status=?, progress_percent=?, current_step=?, error_message=?,
                    started_at=COALESCE(started_at, CURRENT_TIMESTAMP(6)), heartbeat_at=CURRENT_TIMESTAMP(6)
                WHERE id=?
                """, status, Math.max(0, Math.min(100, progress)), step, error, runId);
    }



    private String currentStep(long runId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT current_step FROM analysis_test_run WHERE id = ?", String.class, runId);
        } catch (Exception ignored) {
            return "Unknown regression phase";
        }
    }

    private Throwable rootCause(Throwable exception) {
        if (exception == null) return null;
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private String rootCauseDetail(Throwable root, int max) {
        if (root == null) return "Unknown root cause";
        String message = root.getMessage();
        String detail = root.getClass().getName() + (message == null || message.isBlank() ? "" : ": " + message);
        return abbreviate(detail, max);
    }

    private String stackTrace(Throwable exception) {
        if (exception == null) return "Unknown regression test error";
        StringWriter buffer = new StringWriter();
        try (PrintWriter writer = new PrintWriter(buffer)) {
            exception.printStackTrace(writer);
        }
        return buffer.toString();
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime localDateTime) return localDateTime.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("Unsupported regression timestamp value: " + value);
    }


    private String errorDetail(Throwable exception, int max) {
        if (exception == null) return "Unknown regression test error";
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        StringBuilder detail = new StringBuilder()
                .append(exception.getClass().getName())
                .append(": ")
                .append(exception.getMessage() == null ? exception.toString() : exception.getMessage());
        if (root != exception) {
            detail.append(" | Root cause: ")
                    .append(root.getClass().getName())
                    .append(": ")
                    .append(root.getMessage() == null ? root.toString() : root.getMessage());
        }
        return abbreviate(detail.toString(), max);
    }

    private String abbreviate(String value, int max) {
        if (value == null) return "Unknown regression test error";
        return value.length() <= max ? value : value.substring(0, max);
    }


}
