package com.crypto.regression.service;

import com.crypto.regression.dto.RegressionTestRunRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegressionTestService {

    private final JdbcTemplate jdbcTemplate;
    private final RegressionTestWorker worker;

    public long start(RegressionTestRunRequest request) {
        if (request == null || request.symbol() == null || request.symbol().isBlank()
                || request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("Symbol, start time and end time are required, and end must be after start.");
        }

        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        String testName = request.testName() == null || request.testName().isBlank()
                ? symbol + " regression"
                : request.testName().trim();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO analysis_test_run
                        (test_name, symbol, start_time, end_time, status, progress_percent, current_step)
                    VALUES (?, ?, ?, ?, 'PENDING', 0, 'Queued from Administration')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, testName);
            statement.setString(2, symbol);
            statement.setTimestamp(3, Timestamp.from(request.startTime()));
            statement.setTimestamp(4, Timestamp.from(request.endTime()));
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not create regression test run.");
        }
        long id = key.longValue();
        worker.runAsync(id);
        return id;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRun(long id) {
        Map<String, Object> run = jdbcTemplate.queryForMap("""
                SELECT id, test_name, symbol, start_time, end_time, status, progress_percent,
                       current_step, source_signal_count, replay_signal_count, generated_signal_count,
                       generated_buy_count, generated_watch_count, generated_sell_count, generated_strong_sell_count,
                       neutralized_original_bearish_count, corrected_hard_reversal_count,
                       historical_hard_reversal_count, error_message, started_at, completed_at, created_at
                FROM analysis_test_run
                WHERE id = ?
                """, id);

        List<Map<String, Object>> results = jdbcTemplate.queryForList("""
                SELECT candles_1m, signals_1m_historical, replayable_1m_events, generated_signals_1m, generated_buys_1m,
                       candles_5m, signals_5m_historical, replayable_5m_events, generated_signals_5m, generated_buys_5m,
                       candles_1h, signals_1h_historical, replayable_1h_events, generated_signals_1h, generated_buys_1h,
                       generated_signal_errors, decision_authority_corrections, old_hard_bearish_reversals,
                       corrected_hard_bearish_reversals, cadence_path_passed,
                       decision_authority_passed, test_passed, notes
                FROM analysis_test_result
                WHERE test_run_id = ?
                """, id);
        run.put("result", results.isEmpty() ? null : results.get(0));
        return run;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> latestRuns() {
        return jdbcTemplate.queryForList("""
                SELECT id, test_name, symbol, start_time, end_time, status, progress_percent,
                       current_step, started_at, completed_at, created_at
                FROM analysis_test_run
                ORDER BY id DESC
                LIMIT 20
                """);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> signals(long runId) {
        return jdbcTemplate.queryForList("""
                SELECT generated_at, interval_code, latest_price, original_decision, final_decision,
                       execution_effective_decision, total_score, confidence_score, trend_score,
                       volume_score, momentum_score, decision_authority_corrected, replay_generated, generation_error
                FROM analysis_test_signal
                WHERE test_run_id = ?
                ORDER BY generated_at ASC, FIELD(interval_code, '1h', '5m', '1m')
                LIMIT 1500
                """, runId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> opportunities(long runId) {
        return jdbcTemplate.queryForList("""
                SELECT generated_at, current_original_decision, current_final_decision,
                       five_minute_decision, one_hour_decision,
                       old_hard_bearish_reversal, corrected_hard_bearish_reversal,
                       decision_code, decision_explanation
                FROM execution_opportunity_test
                WHERE test_run_id = ?
                ORDER BY generated_at ASC
                LIMIT 1500
                """, runId);
    }
}
