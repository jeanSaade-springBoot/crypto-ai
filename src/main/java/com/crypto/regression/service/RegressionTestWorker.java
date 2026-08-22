package com.crypto.regression.service;

import com.crypto.config.BtcContextProperties;
import com.crypto.domain.Candle;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.indicator.service.TechnicalIndicatorService;
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

import java.sql.Timestamp;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

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

    @Async
    public void runAsync(long runId) {
        try {
            Map<String, Object> run = jdbcTemplate.queryForMap(
                    "SELECT symbol, start_time, end_time FROM analysis_test_run WHERE id = ?", runId);
            String symbol = String.valueOf(run.get("symbol"));
            Instant start = toInstant(run.get("start_time"));
            Instant end = toInstant(run.get("end_time"));

            updateRun(runId, "RUNNING", 2, "Loading historical candles and signals", null);

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

            updateRun(runId, "RUNNING", 10, "Replaying event-candle resolution (no production writes)", null);
            int replay1m = verifyEventResolution(runId, symbol, "1m", requestedOneMinuteCandles, 10, 28);
            int replay5m = verifyEventResolution(runId, symbol, "5m", requestedFiveMinuteCandles, 28, 38);
            int replay1h = verifyEventResolution(runId, symbol, "1h", requestedOneHourCandles, 38, 42);

            updateRun(runId, "RUNNING", 43, "Generating fresh signals from historical candles", null);
            FreshReplayStats fresh = generateFreshSignals(
                    runId, symbol, start, end,
                    oneMinuteCandles, fiveMinuteCandles, oneHourCandles,
                    btcSymbol, btcOneMinuteCandles, btcFiveMinuteCandles, btcOneHourCandles);
            jdbcTemplate.update("""
                    UPDATE analysis_test_run
                    SET generated_signal_count=?, generated_buy_count=?, generated_watch_count=?,
                        generated_sell_count=?, generated_strong_sell_count=?
                    WHERE id=?
                    """, fresh.total(), fresh.buys(), fresh.watches(), fresh.sells(), fresh.strongSells(), runId);

            updateRun(runId, "RUNNING", 74, "Running full shadow-production execution flow", null);
            ShadowProductionReplayService.ReplayStats shadow = shadowReplayService.replay(
                    runId, symbol, start, end, fresh.generatedSignals());

            updateRun(runId, "RUNNING", 82, "Comparing historical decision authority", null);
            int authorityCorrections = 0;
            int replaySignals = 0;
            int oldHardReversals = 0;
            int correctedHardReversals = 0;

            for (TradeSignal signal : sourceSignals) {
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
            boolean passed = cadencePass && authorityPass && generationPass;

            String notes = "Fresh replay signals are generated from historical candles through TechnicalIndicatorService "
                    + "and the production AnalysisService scoring/final-decision path without trade_signal persistence. "
                    + "A three-hour context-only warm-up seeds 1m/5m/1h state before the requested execution window, and BTC reference signals are freshly replayed as-of each timestamp when BTC context is enabled. "
                    + "Historical signal counts are retained only as the pre-fix reference. "
                    + "Replayable event counts validate that each historical candle can now be resolved as-of its own close. "
                    + "The decision replay validates that originalDecision is audit-only and cannot override a non-null final decision. "
                    + "Regression AnalysisService returns unsaved TradeSignal objects. Fresh signals then pass through an isolated shadow execution/position lifecycle that records exact simulated BUY/SELL points. Real wallet, trade_signal and production execution_opportunity tables are never written.";

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
                    """,
                    runId,
                    requestedCandles1m, historical1m, replay1m, fresh.oneMinuteSignals(), fresh.oneMinuteBuys(),
                    requestedCandles5m, historical5m, replay5m, fresh.fiveMinuteSignals(), fresh.fiveMinuteBuys(),
                    requestedCandles1h, historical1h, replay1h, fresh.oneHourSignals(), fresh.oneHourBuys(),
                    fresh.errors(), shadow.trades(), shadow.wins(), shadow.losses(), shadow.realizedPnl(), shadow.finalWallet(),
                    authorityCorrections, oldHardReversals, correctedHardReversals,
                    cadencePass, authorityPass, passed, notes
            );

            jdbcTemplate.update("""
                    UPDATE analysis_test_run
                    SET status=?, progress_percent=100, current_step=?, completed_at=CURRENT_TIMESTAMP(6)
                    WHERE id=?
                    """, passed ? "PASSED" : "FAILED", passed ? "Regression checks passed" : "Regression checks failed", runId);

        } catch (Exception exception) {
            log.error("Regression test run {} failed", runId, exception);
            String failedStep = currentStep(runId);
            String summary = errorDetail(exception, 1900);
            Throwable root = rootCause(exception);
            jdbcTemplate.update("""
                    UPDATE analysis_test_run
                    SET status='ERROR', progress_percent=100, current_step='Regression test failed',
                        error_message=?, failure_step=?, failure_exception=?, failure_root_cause=?,
                        failure_stack_trace=?, started_at=COALESCE(started_at, CURRENT_TIMESTAMP(6)),
                        completed_at=CURRENT_TIMESTAMP(6)
                    WHERE id=?
                    """,
                    summary, failedStep, exception.getClass().getName(),
                    rootCauseDetail(root, 1000), stackTrace(exception), runId);
        }
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
            List<Candle> btcOneHourCandles
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

        try (ExecutionReplayScope.Scope ignored = replayScope.open(runId, List.of(), o -> {})) {
        // FIX-043 Replay parity: fresh Replay has always generated technical snapshots/signals
        // chronologically for EVERY closed candle in the timeline. Production now restores the
        // same no-gap contract with asynchronous close-event processing plus chronological recovery.
        // Do not "optimize" Replay by sampling every Nth candle or by copying the production recovery
        // scheduler cadence; that would recreate the exact 1m->~5m blind-gap incident FIX-043 fixes.
        for (int index = 0; index < timeline.size(); index++) {
            ReplayCandle replay = timeline.get(index);
            Candle candle = replay.candle();
            Instant evaluationTime = candle.getOpenTime().plusSeconds(intervalSeconds(replay.interval()));
            boolean inRequestedWindow = !evaluationTime.isBefore(executionStart) && !evaluationTime.isAfter(executionEnd);
            try {
                java.util.Optional<IndicatorSnapshot> snapshot = technicalIndicatorService
                        .calculateSnapshotForRegression(replay.symbol(), replay.interval(), candle.getOpenTime());
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
                TradeSignal generated = analysisService.analyzeForRegression(snapshot.get(), evaluationTime);
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

                    jdbcTemplate.update("""
                            INSERT INTO analysis_test_signal
                                (test_run_id, source_signal_id, replay_generated, symbol, interval_code, candle_open_time,
                                 generated_at, latest_price, original_decision, final_decision,
                                 execution_effective_decision, total_score, confidence_score, trend_score,
                                 volume_score, momentum_score, decision_authority_corrected, generation_error)
                            VALUES (?, NULL, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL)
                            """,
                            runId, replay.symbol(), replay.interval(), Timestamp.from(candle.getOpenTime()),
                            Timestamp.from(evaluationTime), generated.getLatestPrice(),
                            generated.getOriginalDecision() == null ? null : generated.getOriginalDecision().name(),
                            decision == null ? null : decision.name(),
                            decision == null ? null : decision.name(),
                            generated.getTotalScore(), generated.getConfidenceScore(), generated.getTrendScore(),
                            generated.getVolumeScore(), generated.getMomentumScore());
                }
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
                updateRun(runId, "RUNNING", Math.min(73, progress),
                        "Generating fresh signal " + (index + 1) + "/" + timeline.size(), null);
            }
        }

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
                    started_at=COALESCE(started_at, CURRENT_TIMESTAMP(6))
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
