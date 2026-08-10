package com.crypto.regression.service;

import com.crypto.regression.dto.RegressionTestRunRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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

    public synchronized long start(RegressionTestRunRequest request) {
        if (request == null || request.symbol() == null || request.symbol().isBlank()
                || request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("Symbol, start time and end time are required, and end must be after start.");
        }

        Integer activeRuns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM analysis_test_run
                WHERE status IN ('PENDING', 'RUNNING')
                """, Integer.class);
        if (activeRuns != null && activeRuns > 0) {
            Map<String, Object> active = jdbcTemplate.queryForMap("""
                    SELECT id, test_name, symbol, status, progress_percent, started_at, created_at
                    FROM analysis_test_run
                    WHERE status IN ('PENDING', 'RUNNING')
                    ORDER BY id DESC
                    LIMIT 1
                    """);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Regression test #" + active.get("id") + " is already " + active.get("status")
                            + " for " + active.get("symbol") + ". Wait for it to finish before starting another run.");
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

    public synchronized Map<String, Object> resetAllTestData() {
        Integer activeRuns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM analysis_test_run
                WHERE status IN ('PENDING', 'RUNNING')
                """, Integer.class);
        if (activeRuns != null && activeRuns > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A regression test is still running. Wait for it to finish before resetting test data.");
        }

        int executions = jdbcTemplate.update("DELETE FROM wallet_execution_test");
        int positions = jdbcTemplate.update("DELETE FROM wallet_position_test");
        int opportunities = jdbcTemplate.update("DELETE FROM execution_opportunity_test");
        int signals = jdbcTemplate.update("DELETE FROM analysis_test_signal");
        int results = jdbcTemplate.update("DELETE FROM analysis_test_result");
        int runs = jdbcTemplate.update("DELETE FROM analysis_test_run");

        jdbcTemplate.execute("ALTER TABLE wallet_execution_test AUTO_INCREMENT = 1");
        jdbcTemplate.execute("ALTER TABLE wallet_position_test AUTO_INCREMENT = 1");
        jdbcTemplate.execute("ALTER TABLE execution_opportunity_test AUTO_INCREMENT = 1");
        jdbcTemplate.execute("ALTER TABLE analysis_test_signal AUTO_INCREMENT = 1");
        jdbcTemplate.execute("ALTER TABLE analysis_test_result AUTO_INCREMENT = 1");
        jdbcTemplate.execute("ALTER TABLE analysis_test_run AUTO_INCREMENT = 1");

        return Map.of(
                "runs", runs,
                "results", results,
                "signals", signals,
                "opportunities", opportunities,
                "positions", positions,
                "executions", executions
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRun(long id) {
        Map<String, Object> run = jdbcTemplate.queryForMap("""
                SELECT id, test_name, symbol, start_time, end_time, status, progress_percent,
                       current_step, source_signal_count, replay_signal_count, generated_signal_count,
                       generated_buy_count, generated_watch_count, generated_sell_count, generated_strong_sell_count,
                       neutralized_original_bearish_count, corrected_hard_reversal_count,
                       historical_hard_reversal_count, error_message, failure_step, failure_exception,
                       failure_root_cause, failure_stack_trace, started_at, completed_at, created_at
                FROM analysis_test_run
                WHERE id = ?
                """, id);

        List<Map<String, Object>> results = jdbcTemplate.queryForList("""
                SELECT candles_1m, signals_1m_historical, replayable_1m_events, generated_signals_1m, generated_buys_1m,
                       candles_5m, signals_5m_historical, replayable_5m_events, generated_signals_5m, generated_buys_5m,
                       candles_1h, signals_1h_historical, replayable_1h_events, generated_signals_1h, generated_buys_1h,
                       generated_signal_errors, simulated_trades, simulated_wins, simulated_losses,
                       simulated_realized_pnl, simulated_final_wallet,
                       decision_authority_corrections, old_hard_bearish_reversals,
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
    public List<Map<String, Object>> trades(long runId) {
        return jdbcTemplate.queryForList("""
                SELECT entry_time, entry_price, exit_time, exit_price, exit_reason,
                       realized_pnl_usdt, realized_pnl_percent, position_percent, status
                FROM wallet_position_test
                WHERE test_run_id = ?
                ORDER BY entry_time ASC
                LIMIT 500
                """, runId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> opportunities(long runId) {
        return jdbcTemplate.queryForList("""
                SELECT generated_at, replay_stage, current_original_decision, current_final_decision,
                       five_minute_decision, one_hour_decision, evidence_count, buy_count, watch_count,
                       neutral_count, bearish_count, evidence_score, opportunity_health,
                       recommended_position_percent, decision_code, decision_explanation
                FROM execution_opportunity_test
                WHERE test_run_id = ? AND replay_stage IS NOT NULL
                ORDER BY generated_at ASC
                LIMIT 3000
                """, runId);
    }}
