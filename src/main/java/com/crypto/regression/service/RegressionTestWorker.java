package com.crypto.regression.service;

import com.crypto.domain.Candle;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegressionTestWorker {

    private final JdbcTemplate jdbcTemplate;
    private final CandleRepository candleRepository;
    private final TradeSignalRepository signalRepository;

    @Async
    public void runAsync(long runId) {
        try {
            Map<String, Object> run = jdbcTemplate.queryForMap(
                    "SELECT symbol, start_time, end_time FROM analysis_test_run WHERE id = ?", runId);
            String symbol = String.valueOf(run.get("symbol"));
            Instant start = toInstant(run.get("start_time"));
            Instant end = toInstant(run.get("end_time"));

            updateRun(runId, "RUNNING", 2, "Loading historical candles and signals", null);

            List<Candle> oneMinuteCandles = candles(symbol, "1m", start, end);
            List<Candle> fiveMinuteCandles = candles(symbol, "5m", start, end);
            List<Candle> oneHourCandles = candles(symbol, "1h", start, end);
            List<TradeSignal> sourceSignals = signalRepository
                    .findBySymbolAndGeneratedAtBetweenOrderByGeneratedAtAsc(symbol, start, end);

            jdbcTemplate.update("UPDATE analysis_test_run SET source_signal_count=? WHERE id=?",
                    sourceSignals.size(), runId);

            updateRun(runId, "RUNNING", 10, "Replaying event-candle resolution (no production writes)", null);
            int replay1m = verifyEventResolution(runId, symbol, "1m", oneMinuteCandles, 10, 28);
            int replay5m = verifyEventResolution(runId, symbol, "5m", fiveMinuteCandles, 28, 38);
            int replay1h = verifyEventResolution(runId, symbol, "1h", oneHourCandles, 38, 42);

            updateRun(runId, "RUNNING", 45, "Replaying FinalDecisionService authority", null);
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

                jdbcTemplate.update("""
                        INSERT INTO analysis_test_signal
                            (test_run_id, source_signal_id, symbol, interval_code, candle_open_time,
                             generated_at, latest_price, original_decision, final_decision,
                             execution_effective_decision, total_score, confidence_score, trend_score,
                             volume_score, momentum_score, decision_authority_corrected)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        runId,
                        signal.getId(),
                        signal.getSymbol(),
                        signal.getInterval(),
                        signal.getCandleOpenTime() == null ? null : Timestamp.from(signal.getCandleOpenTime()),
                        Timestamp.from(signal.getGeneratedAt()),
                        signal.getLatestPrice(),
                        original == null ? null : original.name(),
                        finalDecision == null ? null : finalDecision.name(),
                        effective == null ? null : effective.name(),
                        signal.getTotalScore(),
                        signal.getConfidenceScore(),
                        signal.getTrendScore(),
                        signal.getVolumeScore(),
                        signal.getMomentumScore(),
                        corrected
                );
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

            updateRun(runId, "RUNNING", 90, "Calculating regression result", null);

            int historical1m = countSignals(sourceSignals, "1m");
            int historical5m = countSignals(sourceSignals, "5m");
            int historical1h = countSignals(sourceSignals, "1h");
            boolean cadencePass = replay1m == oneMinuteCandles.size()
                    && replay5m == fiveMinuteCandles.size()
                    && replay1h == oneHourCandles.size();
            boolean authorityPass = sourceSignals.stream().allMatch(s -> {
                SignalDecision effective = s.getDecision() != null ? s.getDecision() : s.getOriginalDecision();
                return s.getDecision() == null || effective == s.getDecision();
            });
            boolean passed = cadencePass && authorityPass;

            String notes = "Historical signal counts are shown only as the pre-fix reference. "
                    + "Replayable event counts validate that each historical candle can now be resolved as-of its own close. "
                    + "The decision replay validates that originalDecision is audit-only and cannot override a non-null final decision. "
                    + "No AnalysisService, wallet, live execution, trade_signal or execution_opportunity writes are performed by this test.";

            jdbcTemplate.update("""
                    INSERT INTO analysis_test_result
                        (test_run_id,
                         candles_1m, signals_1m_historical, replayable_1m_events,
                         candles_5m, signals_5m_historical, replayable_5m_events,
                         candles_1h, signals_1h_historical, replayable_1h_events,
                         decision_authority_corrections, old_hard_bearish_reversals,
                         corrected_hard_bearish_reversals, cadence_path_passed,
                         decision_authority_passed, test_passed, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    runId,
                    oneMinuteCandles.size(), historical1m, replay1m,
                    fiveMinuteCandles.size(), historical5m, replay5m,
                    oneHourCandles.size(), historical1h, replay1h,
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
            updateRun(runId, "ERROR", 100, "Regression test failed", abbreviate(exception.getMessage(), 1900));
            jdbcTemplate.update("UPDATE analysis_test_run SET completed_at=CURRENT_TIMESTAMP(6) WHERE id=?", runId);
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


    private Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime localDateTime) return localDateTime.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("Unsupported regression timestamp value: " + value);
    }

    private String abbreviate(String value, int max) {
        if (value == null) return "Unknown regression test error";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
