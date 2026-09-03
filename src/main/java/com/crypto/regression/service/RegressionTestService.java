package com.crypto.regression.service;

import com.crypto.regression.dto.RegressionTestRunRequest;
import com.crypto.regression.dto.RegressionInvestigationCaseRequest;
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
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegressionTestService {

    private final JdbcTemplate jdbcTemplate;
    private final RegressionTestWorker worker;

    /**
     * FIX-065: save multiple historical incidents/winning controls as a durable Investigation Queue.
     * This is metadata only; no production trading table is read or written by this operation.
     */
    @Transactional
    public synchronized List<Map<String, Object>> addInvestigationCases(List<RegressionInvestigationCaseRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("At least one investigation case is required.");
        }
        for (RegressionInvestigationCaseRequest request : requests) {
            validateInvestigationCase(request);
            String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
            String caseName = request.caseName() == null || request.caseName().isBlank()
                    ? symbol + " investigation" : request.caseName().trim();
            jdbcTemplate.update("""
                    INSERT INTO regression_investigation_case
                        (case_name, symbol, start_time, end_time, wallet_id, expected_action, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, caseName, symbol, Timestamp.from(request.startTime()), Timestamp.from(request.endTime()),
                    request.walletId(), blankToNull(request.expectedAction()), blankToNull(request.notes()));
        }
        return investigationCases();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> investigationCases() {
        return jdbcTemplate.queryForList("""
                SELECT c.id, c.case_name, c.symbol, c.start_time, c.end_time, c.wallet_id,
                       c.expected_action, c.notes, c.last_run_id,
                       r.status AS last_run_status, r.progress_percent AS last_run_progress,
                       r.completed_at AS last_run_completed_at, c.created_at, c.updated_at
                FROM regression_investigation_case c
                LEFT JOIN analysis_test_run r ON r.id = c.last_run_id
                ORDER BY c.id ASC
                """);
    }

    /**
     * FIX-065: running a saved case delegates to the exact same start() method as the manual form.
     * Therefore the existing single-active-run backend lock and isolated replay tables stay authoritative.
     */
    public synchronized long startInvestigationCase(long caseId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, case_name, symbol, start_time, end_time
                FROM regression_investigation_case WHERE id = ?
                """, caseId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation case #" + caseId + " not found.");
        Map<String, Object> row = rows.get(0);
        long runId = start(new RegressionTestRunRequest(
                String.valueOf(row.get("case_name")), String.valueOf(row.get("symbol")),
                ((Timestamp) row.get("start_time")).toInstant(), ((Timestamp) row.get("end_time")).toInstant()), ReplayDataSource.DATABASE);
        jdbcTemplate.update("UPDATE regression_investigation_case SET last_run_id=? WHERE id=?", runId, caseId);
        return runId;
    }

    @Transactional
    public synchronized void deleteInvestigationCase(long caseId) {
        int deleted = jdbcTemplate.update("DELETE FROM regression_investigation_case WHERE id=?", caseId);
        if (deleted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigation case #" + caseId + " not found.");
    }

    private void validateInvestigationCase(RegressionInvestigationCaseRequest request) {
        if (request == null || request.symbol() == null || request.symbol().isBlank()
                || request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("Each investigation case requires symbol/start/end and end must be after start.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * FIX-089: only rows that pre-date this service/JVM instance can be leftovers from a real
     * application restart. FIX-088 updated every PENDING/RUNNING row when ApplicationReadyEvent
     * fired, which could falsely mark a replay created by the current JVM as interrupted.
     *
     * Capturing the service construction time makes the distinction deterministic: a replay row
     * created after this instant belongs to the current application instance and must never be
     * touched by startup recovery, even if ApplicationReadyEvent is delivered later than expected.
     */
    private final Instant serviceInstanceStartedAt = Instant.now();

    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedRunsAfterRestart() {
        jdbcTemplate.update("""
                UPDATE analysis_test_run
                SET status='ERROR', current_step='Interrupted by application restart',
                    error_message='Replay was interrupted by an actual application restart. Resume is disabled; delete test data and start a new run.',
                    completed_at=CURRENT_TIMESTAMP(6)
                WHERE status IN ('PENDING','RUNNING')
                  AND created_at < ?
                """, Timestamp.from(serviceInstanceStartedAt));
    }

    // FIX-088: manual Resume was removed. We intentionally do not expose a method that
    // mutates an existing failed run back to PENDING. Users delete test data and start a clean run.


    /**
     * FIX-090: request cooperative cancellation of exactly one active replay worker.
     * We do not mark the row ERROR here because that would be a false claim while Java is
     * still executing. RegressionTestWorker marks ERROR only after it actually unwinds.
     */
    public synchronized Map<String, Object> stopRun(long runId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id,status,current_step FROM analysis_test_run WHERE id=?", runId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Regression test #" + runId + " not found.");
        String status = String.valueOf(rows.get(0).get("status"));
        if (!("PENDING".equals(status) || "RUNNING".equals(status))) {
            return Map.of("id", runId, "status", status, "activeWorker", worker.isActive(runId),
                    "message", "Test is already stopped.");
        }
        boolean requested = worker.requestStop(runId);
        if (!requested) {
            // No worker in this JVM means it is already orphaned/stopped. Mark it ERROR now so
            // Delete Data is not blocked forever by a stale RUNNING database status.
            jdbcTemplate.update("""
                    UPDATE analysis_test_run
                    SET status='ERROR', current_step='Stopped by user',
                        error_message='Replay worker was not active in this application instance; run marked stopped.',
                        completed_at=CURRENT_TIMESTAMP(6), heartbeat_at=CURRENT_TIMESTAMP(6)
                    WHERE id=? AND status IN ('PENDING','RUNNING')
                    """, runId);
            return Map.of("id", runId, "status", "ERROR", "activeWorker", false,
                    "message", "Test had no active worker and is now stopped. You can Delete Data.");
        }
        jdbcTemplate.update("UPDATE analysis_test_run SET current_step='Stop requested — waiting for replay worker to exit', heartbeat_at=CURRENT_TIMESTAMP(6) WHERE id=?", runId);
        return Map.of("id", runId, "status", "STOPPING", "activeWorker", true,
                "message", "Stop requested. Delete Data will unlock after the replay worker has fully stopped.");
    }

    public synchronized long start(RegressionTestRunRequest request) {
        return start(request, ReplayDataSource.DATABASE);
    }

    /** FIX-11H explicit replay-only OLD/NEW selector. Production is not configurable through this mode. */
    public synchronized long start(RegressionTestRunRequest request, ReplayDataSource dataSource) {
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
        ReplayDataSource effectiveSource = dataSource == null ? ReplayDataSource.DATABASE : dataSource;
        String baseTestName = request.testName() == null || request.testName().isBlank()
                ? symbol + " regression" : request.testName().trim();
        String testName = (baseTestName + " [" + effectiveSource.name() + "]").substring(
                0, Math.min(180, baseTestName.length() + effectiveSource.name().length() + 3));

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
        worker.runAsync(id, effectiveSource);
        return id;
    }


    /**
     * Archives one complete replay run before it can be cleared from the active test tables.
     *
     * IMPORTANT regression-history rule (introduced after the ETHUSDT Trade #2 investigation,
     * Aug-2026): test rows are evidence. We must be able to rerun changed production logic without
     * destroying the old pipeline/evidence/health/trade trace that still needs manual analysis.
     * Proven trades remain separate in proven_analyzed_trade and are never moved or deleted here.
     */
    @Transactional
    public synchronized Map<String, Object> archiveRun(long runId, String reason) {
        Integer active = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_test_run WHERE id=? AND status IN ('PENDING','RUNNING')", Integer.class, runId);
        if (active != null && active > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Test #" + runId + " is still running and cannot be archived.");
        }
        List<Map<String,Object>> rows = jdbcTemplate.queryForList("SELECT id,test_name,symbol,start_time,end_time,status FROM analysis_test_run WHERE id=?", runId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Regression test #" + runId + " not found.");
        List<Map<String,Object>> existing = jdbcTemplate.queryForList("SELECT id FROM regression_test_archive_batch WHERE source_test_run_id=?", runId);
        if (!existing.isEmpty()) return Map.of("archiveBatchId", existing.get(0).get("id"), "sourceTestRunId", runId, "alreadyArchived", true);
        Map<String,Object> r = rows.get(0);
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(c -> {
            PreparedStatement ps = c.prepareStatement("INSERT INTO regression_test_archive_batch(source_test_run_id,test_name,symbol,start_time,end_time,source_status,archive_reason) VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, runId); ps.setString(2, String.valueOf(r.get("test_name"))); ps.setString(3, String.valueOf(r.get("symbol")));
            ps.setTimestamp(4, (Timestamp) r.get("start_time")); ps.setTimestamp(5, (Timestamp) r.get("end_time"));
            ps.setString(6, String.valueOf(r.get("status"))); ps.setString(7, reason == null || reason.isBlank() ? "Archived before replay reset" : reason.trim());
            return ps;
        }, kh);
        long batchId = kh.getKey().longValue();
        int runs = jdbcTemplate.update("INSERT INTO analysis_test_run_archive SELECT ?, r.* FROM analysis_test_run r WHERE r.id=?", batchId, runId);
        int signals = jdbcTemplate.update("INSERT INTO analysis_test_signal_archive SELECT ?, r.* FROM analysis_test_signal r WHERE r.test_run_id=?", batchId, runId);
        // FIX-069: archive the full production-shaped replay signal snapshot as well.
        int fullTradeSignals = jdbcTemplate.update("INSERT INTO trade_signal_test_archive SELECT ?, r.* FROM trade_signal_test r WHERE r.test_run_id=?", batchId, runId);
        int opportunities = jdbcTemplate.update("INSERT INTO execution_opportunity_test_archive SELECT ?, r.* FROM execution_opportunity_test r WHERE r.test_run_id=?", batchId, runId);
        int results = jdbcTemplate.update("INSERT INTO analysis_test_result_archive SELECT ?, r.* FROM analysis_test_result r WHERE r.test_run_id=?", batchId, runId);
        int executions = jdbcTemplate.update("INSERT INTO wallet_execution_test_archive SELECT ?, r.* FROM wallet_execution_test r WHERE r.test_run_id=?", batchId, runId);
        int positions = jdbcTemplate.update("INSERT INTO wallet_position_test_archive SELECT ?, r.* FROM wallet_position_test r WHERE r.test_run_id=?", batchId, runId);
        int management = jdbcTemplate.update("INSERT INTO position_management_test_archive SELECT ?, r.* FROM position_management_test r WHERE r.test_run_id=?", batchId, runId);
        int defensiveObservations = jdbcTemplate.update("INSERT INTO defensive_risk_reduction_observation_test_archive SELECT ?, r.* FROM defensive_risk_reduction_observation_test r WHERE r.test_run_id=?", batchId, runId);
        int continuationGraceObservations = jdbcTemplate.update("INSERT INTO one_candle_continuation_grace_test_archive SELECT ?, r.* FROM one_candle_continuation_grace_test r WHERE r.test_run_id=?", batchId, runId);
        return Map.ofEntries(
                Map.entry("archiveBatchId", batchId), Map.entry("sourceTestRunId", runId), Map.entry("alreadyArchived", false),
                Map.entry("runs", runs), Map.entry("signals", signals), Map.entry("tradeSignals", fullTradeSignals),
                Map.entry("opportunities", opportunities), Map.entry("results", results), Map.entry("executions", executions),
                Map.entry("positions", positions), Map.entry("management", management),
                Map.entry("defensiveRiskObservations", defensiveObservations),
                Map.entry("oneCandleContinuationGraceObservations", continuationGraceObservations));
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> archivedRuns() {
        return jdbcTemplate.queryForList("SELECT id AS archive_batch_id,source_test_run_id,test_name,symbol,start_time,end_time,source_status,archive_reason,archived_at FROM regression_test_archive_batch ORDER BY id DESC LIMIT 100");
    }

    @Transactional(readOnly = true)
    public Map<String,Object> archivedRun(long batchId) {
        Map<String,Object> run = jdbcTemplate.queryForMap("SELECT r.* FROM analysis_test_run_archive r WHERE r.archive_batch_id=?", batchId);
        List<Map<String,Object>> result = jdbcTemplate.queryForList("SELECT r.* FROM analysis_test_result_archive r WHERE r.archive_batch_id=?", batchId);
        run.put("result", result.isEmpty() ? null : result.get(0)); run.put("archived", true); run.put("archive_batch_id", batchId); return run;
    }
    @Transactional(readOnly = true) public List<Map<String,Object>> archivedSignals(long batchId) { return jdbcTemplate.queryForList("SELECT generated_at,interval_code,latest_price,original_decision,final_decision,execution_effective_decision,total_score,confidence_score,trend_score,volume_score,momentum_score,decision_authority_corrected,replay_generated,generation_error FROM analysis_test_signal_archive WHERE archive_batch_id=? ORDER BY generated_at ASC, FIELD(interval_code,'1h','5m','1m') LIMIT 1500", batchId); }
    @Transactional(readOnly = true) public List<Map<String,Object>> archivedTrades(long batchId) { return jdbcTemplate.queryForList("SELECT id,entry_time,entry_price,exit_time,exit_price,exit_reason,realized_pnl_usdt,realized_pnl_percent,position_percent,status, EXISTS(SELECT 1 FROM proven_analyzed_trade p WHERE p.source_test_run_id=wallet_position_test_archive.test_run_id AND p.source_trade_id=wallet_position_test_archive.id) AS proven_success FROM wallet_position_test_archive WHERE archive_batch_id=? ORDER BY entry_time ASC LIMIT 500", batchId); }
    @Transactional(readOnly = true) public List<Map<String,Object>> archivedPositionManagement(long batchId) { return jdbcTemplate.queryForList("SELECT generated_at,action_code,current_price,old_take_profit,new_take_profit,highest_price,profit_lock_active,profit_lock_price,explanation FROM position_management_test_archive WHERE archive_batch_id=? ORDER BY generated_at ASC LIMIT 3000", batchId); }
    @Transactional(readOnly = true) public List<Map<String,Object>> archivedDefensiveRiskReductionObservations(long batchId) {
        return jdbcTemplate.queryForList("SELECT id,position_test_id,symbol,observed_at,source_signal_id,current_price,entry_price,highest_price_since_entry,current_profit_percent,peak_profit_percent,giveback_from_peak_percent,consecutive_final_1m_strong_sell,five_minute_signal_id,five_minute_original_decision,five_minute_final_decision,five_minute_confluence_status,one_hour_signal_id,one_hour_final_decision,observation_code FROM defensive_risk_reduction_observation_test_archive WHERE archive_batch_id=? ORDER BY observed_at ASC,id ASC LIMIT 5000", batchId);
    }
    @Transactional(readOnly = true) public List<Map<String,Object>> archivedOneCandleContinuationGraceObservations(long batchId) {
        return jdbcTemplate.queryForList("SELECT * FROM one_candle_continuation_grace_test_archive WHERE archive_batch_id=? ORDER BY grace_at ASC,id ASC LIMIT 5000", batchId);
    }
    @Transactional(readOnly = true) public List<Map<String,Object>> archivedOpportunities(long batchId) { return jdbcTemplate.queryForList("SELECT generated_at,replay_stage,current_original_decision,current_final_decision,five_minute_decision,one_hour_decision,evidence_count,buy_count,watch_count,neutral_count,bearish_count,evidence_score,opportunity_health,recommended_position_percent,decision_code,decision_explanation FROM execution_opportunity_test_archive WHERE archive_batch_id=? AND replay_stage IS NOT NULL ORDER BY generated_at ASC LIMIT 3000", batchId); }

    @Transactional
    public synchronized Map<String, Object> resetAllTestData() {
        // FIX-090: Java worker ownership is authoritative. Database ERROR may be written a few
        // milliseconds before runAsync finally releases ownership; never purge during that gap.
        if (worker.hasActiveRuns()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A Replay/Test worker is still active. Stop it and wait for the worker to fully exit before Delete Data.");
        }
        Integer activeRuns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM analysis_test_run
                WHERE status IN ('PENDING', 'RUNNING')
                """, Integer.class);
        if (activeRuns != null && activeRuns > 0) {
            List<Map<String,Object>> liveRows = jdbcTemplate.queryForList(
                    "SELECT id,status FROM analysis_test_run WHERE status IN ('PENDING','RUNNING')");
            boolean workerStillActive = liveRows.stream()
                    .anyMatch(r -> worker.isActive(((Number) r.get("id")).longValue()));
            if (workerStillActive) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A Replay/Test worker is still active. Use Stop Test and wait until it becomes ERROR before Delete Data.");
            }
            // Defensive cleanup for a stale DB status with no Java worker.
            jdbcTemplate.update("""
                    UPDATE analysis_test_run SET status='ERROR', current_step='Stopped/orphaned worker',
                        error_message='No active Replay/Test worker exists in this application instance.',
                        completed_at=CURRENT_TIMESTAMP(6)
                    WHERE status IN ('PENDING','RUNNING')
                    """);
        }

        // FIX-088: Delete Data is now a true purge, not an archive operation. Every replay/test
        // table that can feed Recent Test Runs, run detail, Shadow Trades, or archived replay
        // history is emptied. Persistent Proven records are deliberately excluded.
        int provenBefore = countRows("proven_analyzed_trade");
        int provenLegsBefore = countRows("proven_trade_leg_archive");

        Map<String, Integer> deleted = new java.util.LinkedHashMap<>();
        // Delete children before parents so the purge remains safe if foreign keys are added later.
        deleted.put("one_candle_continuation_grace_test", jdbcTemplate.update("DELETE FROM one_candle_continuation_grace_test"));
        deleted.put("defensive_risk_reduction_observation_test", jdbcTemplate.update("DELETE FROM defensive_risk_reduction_observation_test"));
        deleted.put("position_management_test", jdbcTemplate.update("DELETE FROM position_management_test"));
        deleted.put("wallet_execution_test", jdbcTemplate.update("DELETE FROM wallet_execution_test"));
        deleted.put("wallet_position_test", jdbcTemplate.update("DELETE FROM wallet_position_test"));
        deleted.put("execution_opportunity_test", jdbcTemplate.update("DELETE FROM execution_opportunity_test"));
        deleted.put("trade_signal_test", jdbcTemplate.update("DELETE FROM trade_signal_test"));
        deleted.put("analysis_test_signal", jdbcTemplate.update("DELETE FROM analysis_test_signal"));
        deleted.put("analysis_test_result", jdbcTemplate.update("DELETE FROM analysis_test_result"));
        deleted.put("analysis_test_run", jdbcTemplate.update("DELETE FROM analysis_test_run"));

        deleted.put("one_candle_continuation_grace_test_archive", jdbcTemplate.update("DELETE FROM one_candle_continuation_grace_test_archive"));
        deleted.put("defensive_risk_reduction_observation_test_archive", jdbcTemplate.update("DELETE FROM defensive_risk_reduction_observation_test_archive"));
        deleted.put("position_management_test_archive", jdbcTemplate.update("DELETE FROM position_management_test_archive"));
        deleted.put("wallet_execution_test_archive", jdbcTemplate.update("DELETE FROM wallet_execution_test_archive"));
        deleted.put("wallet_position_test_archive", jdbcTemplate.update("DELETE FROM wallet_position_test_archive"));
        deleted.put("execution_opportunity_test_archive", jdbcTemplate.update("DELETE FROM execution_opportunity_test_archive"));
        deleted.put("trade_signal_test_archive", jdbcTemplate.update("DELETE FROM trade_signal_test_archive"));
        deleted.put("analysis_test_signal_archive", jdbcTemplate.update("DELETE FROM analysis_test_signal_archive"));
        deleted.put("analysis_test_result_archive", jdbcTemplate.update("DELETE FROM analysis_test_result_archive"));
        deleted.put("analysis_test_run_archive", jdbcTemplate.update("DELETE FROM analysis_test_run_archive"));
        deleted.put("regression_test_archive_batch", jdbcTemplate.update("DELETE FROM regression_test_archive_batch"));

        // Validate the database state inside this same transaction. If any replay table still
        // contains rows, throw and rollback instead of reporting a false successful purge.
        java.util.List<String> replayTables = java.util.List.of(
                "one_candle_continuation_grace_test", "defensive_risk_reduction_observation_test", "position_management_test", "wallet_execution_test", "wallet_position_test",
                "execution_opportunity_test", "trade_signal_test", "analysis_test_signal",
                "analysis_test_result", "analysis_test_run",
                "one_candle_continuation_grace_test_archive", "defensive_risk_reduction_observation_test_archive", "position_management_test_archive", "wallet_execution_test_archive",
                "wallet_position_test_archive", "execution_opportunity_test_archive",
                "trade_signal_test_archive", "analysis_test_signal_archive",
                "analysis_test_result_archive", "analysis_test_run_archive",
                "regression_test_archive_batch");
        Map<String, Integer> remaining = new java.util.LinkedHashMap<>();
        for (String table : replayTables) remaining.put(table, countRows(table));
        java.util.List<String> nonEmpty = remaining.entrySet().stream()
                .filter(e -> e.getValue() != 0).map(Map.Entry::getKey).toList();
        if (!nonEmpty.isEmpty()) {
            throw new IllegalStateException("Delete Test Data validation failed; rows remain in: " + String.join(", ", nonEmpty));
        }

        int provenAfter = countRows("proven_analyzed_trade");
        int provenLegsAfter = countRows("proven_trade_leg_archive");
        if (provenBefore != provenAfter || provenLegsBefore != provenLegsAfter) {
            throw new IllegalStateException("Delete Test Data validation failed; Proven records changed unexpectedly.");
        }

        // Reset only tables that own an AUTO_INCREMENT id. This is cosmetic and happens only
        // after successful delete + validation; Proven ids are never reset.
        java.util.List<String> autoIncrementTables = java.util.List.of(
                "one_candle_continuation_grace_test", "defensive_risk_reduction_observation_test", "wallet_execution_test", "wallet_position_test", "position_management_test",
                "execution_opportunity_test", "trade_signal_test", "analysis_test_signal",
                "analysis_test_result", "analysis_test_run", "regression_test_archive_batch");
        for (String table : autoIncrementTables) jdbcTemplate.execute("ALTER TABLE " + table + " AUTO_INCREMENT = 1");

        return Map.of(
                "success", true,
                "message", "All replay/test data deleted and validated; Proven trades preserved.",
                "deleted", deleted,
                "remaining", remaining,
                "provenTradesPreserved", provenAfter,
                "provenTradeLegsPreserved", provenLegsAfter
        );
    }

    private int countRows(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRun(long id) {
        Map<String, Object> run = jdbcTemplate.queryForMap("""
                SELECT id, test_name, symbol, start_time, end_time, status, progress_percent,
                       current_step, heartbeat_at, source_signal_count, replay_signal_count, generated_signal_count,
                       generated_buy_count, generated_watch_count, generated_sell_count, generated_strong_sell_count,
                       neutralized_original_bearish_count, corrected_hard_reversal_count,
                       historical_hard_reversal_count, error_message, failure_step, failure_exception,
                       failure_root_cause, failure_stack_trace, started_at, completed_at, created_at,
                       replay_price_mode, replay_logic_mode,
                       timing_load_historical_ns, timing_verify_event_resolution_ns,
                       timing_build_replay_dataset_ns, timing_generate_fresh_signals_ns,
                       timing_shadow_execution_ns, timing_parity_comparison_ns, timing_total_ns
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
        List<Map<String, Object>> runs = jdbcTemplate.queryForList("""
                SELECT r.id, r.test_name, r.symbol, r.start_time, r.end_time, r.status, r.progress_percent,
                       r.current_step, r.heartbeat_at, r.started_at, r.completed_at, r.created_at,
                       r.timing_total_ns,
                       (SELECT COUNT(*) FROM wallet_position_test w WHERE w.test_run_id=r.id AND w.exit_time IS NOT NULL) AS closed_trade_count,
                       (SELECT COUNT(*) FROM proven_analyzed_trade p WHERE p.source_test_run_id=r.id) AS proven_trade_count
                FROM analysis_test_run r
                ORDER BY r.id DESC
                LIMIT 20
                """);
        // FIX-087: heartbeat freshness is only a persistence signal; actual worker ownership is
        // surfaced separately so a long healthy replay is not presented as resumable.
        runs.forEach(run -> run.put("active_worker", worker.isActive(((Number) run.get("id")).longValue())));
        return runs;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> signals(long runId) {
        // FIX-069: expose the production-shaped replay table as the canonical replay signal source.
        // Aliases preserve the existing UI contract while every production field remains available.
        return jdbcTemplate.queryForList("""
                SELECT t.*, t.decision AS final_decision, t.decision AS execution_effective_decision,
                       0 AS decision_authority_corrected
                FROM trade_signal_test t
                WHERE t.test_run_id = ?
                ORDER BY t.generated_at ASC, FIELD(t.interval_code, '1h', '5m', '1m')
                LIMIT 5000
                """, runId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> trades(long runId) {
        return jdbcTemplate.queryForList("""
                SELECT id, entry_time, entry_price, exit_time, exit_price, exit_reason,
                       realized_pnl_usdt, realized_pnl_percent, position_percent, status,
                       EXISTS (SELECT 1 FROM proven_analyzed_trade p WHERE p.source_test_run_id = wallet_position_test.test_run_id AND p.source_trade_id = wallet_position_test.id) AS proven_success
                FROM wallet_position_test
                WHERE test_run_id = ?
                ORDER BY entry_time ASC
                LIMIT 500
                """, runId);
    }

    /**
     * FIX-11S: one read-only chart payload for an entire Replay run. Replay executions are
     * taken from the isolated wallet_execution_test rows; Production executions are only
     * queried for the same symbol and requested Replay window. An empty Production result
     * is a normal case and is deliberately returned as [] rather than null.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> allTradesChart(long runId, String interval) {
        List<Map<String, Object>> runs = jdbcTemplate.queryForList("""
                SELECT id, symbol, start_time, end_time
                FROM analysis_test_run
                WHERE id = ?
                """, runId);
        if (runs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Replay run not found.");
        }

        Map<String, Object> run = runs.get(0);
        String symbol = String.valueOf(run.get("symbol")).trim().toUpperCase(Locale.ROOT);
        Timestamp start = (Timestamp) run.get("start_time");
        Timestamp end = (Timestamp) run.get("end_time");
        String normalizedInterval = switch (interval == null ? "5m" : interval.trim().toLowerCase(Locale.ROOT)) {
            case "1m", "5m", "1h", "4h" -> interval.trim().toLowerCase(Locale.ROOT);
            default -> "5m";
        };

        List<Map<String, Object>> candles = jdbcTemplate.queryForList("""
                SELECT open_time, open_price, high_price, low_price, close_price, volume
                FROM candle
                WHERE symbol=? AND interval_code=? AND closed=1 AND open_time BETWEEN ? AND ?
                ORDER BY open_time ASC
                """, symbol, normalizedInterval, start, end);

        List<Map<String, Object>> replayExecutions = jdbcTemplate.queryForList("""
                SELECT id, side, execution_time, execution_price, quantity, notional_usdt,
                       position_percent, signal_interval, signal_decision, execution_source,
                       execution_code, execution_reason, realized_pnl_usdt, realized_pnl_percent
                FROM wallet_execution_test
                WHERE test_run_id=? AND symbol=?
                  AND execution_time BETWEEN ? AND ?
                  AND UPPER(side) IN ('BUY','SELL')
                ORDER BY execution_time ASC, id ASC
                """, runId, symbol, start, end);

        List<Map<String, Object>> productionExecutions = jdbcTemplate.queryForList("""
                SELECT id, signal_id, side, executed_at AS execution_time, price_usdt AS execution_price,
                       quantity, gross_amount_usdt, realized_pnl_usdt, realized_pnl_percent,
                       execution_type, execution_reason, execution_message
                FROM wallet_trade
                WHERE symbol=? AND status='EXECUTED'
                  AND executed_at BETWEEN ? AND ?
                  AND UPPER(side) IN ('BUY','SELL')
                ORDER BY executed_at ASC, id ASC
                """, symbol, start, end);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("symbol", symbol);
        result.put("interval", normalizedInterval);
        result.put("from", start);
        result.put("to", end);
        result.put("candles", candles);
        result.put("replayExecutions", replayExecutions);
        result.put("productionExecutions", productionExecutions);
        return result;
    }

    /** FIX-11K Phase A: read-only replay observations; never Production execution. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> defensiveRiskReductionObservations(long runId) {
        return jdbcTemplate.queryForList("""
                SELECT id, position_test_id, symbol, observed_at, source_signal_id,
                       current_price, entry_price, highest_price_since_entry,
                       current_profit_percent, peak_profit_percent, giveback_from_peak_percent,
                       consecutive_final_1m_strong_sell, five_minute_signal_id,
                       five_minute_original_decision, five_minute_final_decision,
                       five_minute_confluence_status, one_hour_signal_id, one_hour_final_decision,
                       observation_code
                FROM defensive_risk_reduction_observation_test
                WHERE test_run_id = ?
                ORDER BY observed_at ASC, id ASC
                LIMIT 5000
                """, runId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> positionManagement(long runId) {
        return jdbcTemplate.queryForList("""
                SELECT generated_at, action_code, current_price, old_take_profit, new_take_profit,
                       highest_price, profit_lock_active, profit_lock_price, explanation
                FROM position_management_test
                WHERE test_run_id = ?
                ORDER BY generated_at ASC, id ASC
                LIMIT 5000
                """, runId);
    }


    @Transactional
    public Map<String, Object> markProvenSuccess(long runId, long tradeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, test_run_id, symbol, entry_time, entry_price, exit_time, exit_price, exit_reason,
                       realized_pnl_usdt, realized_pnl_percent, position_percent
                FROM wallet_position_test
                WHERE test_run_id = ? AND id = ? AND status = 'CLOSED'
                """, runId, tradeId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shadow trade not found.");
        Map<String, Object> t = rows.get(0);
        jdbcTemplate.update("""
                INSERT INTO proven_analyzed_trade
                    (source_test_run_id, source_trade_id, symbol, entry_time, entry_price, exit_time, exit_price,
                     exit_reason, realized_pnl_usdt, realized_pnl_percent, position_percent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE symbol=VALUES(symbol), entry_time=VALUES(entry_time), entry_price=VALUES(entry_price),
                    exit_time=VALUES(exit_time), exit_price=VALUES(exit_price), exit_reason=VALUES(exit_reason),
                    realized_pnl_usdt=VALUES(realized_pnl_usdt), realized_pnl_percent=VALUES(realized_pnl_percent),
                    position_percent=VALUES(position_percent), marked_at=CURRENT_TIMESTAMP(6)
                """,
                runId, tradeId, t.get("symbol"), t.get("entry_time"), t.get("entry_price"), t.get("exit_time"),
                t.get("exit_price"), t.get("exit_reason"), t.get("realized_pnl_usdt"), t.get("realized_pnl_percent"),
                t.get("position_percent"));
        return Map.of("saved", true, "runId", runId, "tradeId", tradeId);
    }

    @Transactional
    public Map<String, Object> unmarkProvenSuccess(long runId, long tradeId) {
        int deleted = jdbcTemplate.update("DELETE FROM proven_analyzed_trade WHERE source_test_run_id=? AND source_trade_id=?", runId, tradeId);
        return Map.of("saved", false, "deleted", deleted, "runId", runId, "tradeId", tradeId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> provenTrades() {
        return jdbcTemplate.queryForList("""
                SELECT p.id, p.source_test_run_id, p.source_trade_id, p.symbol, p.entry_time, p.entry_price,
                       p.exit_time, p.exit_price, p.exit_reason, p.realized_pnl_usdt, p.realized_pnl_percent,
                       p.position_percent, p.marked_at, p.source_wallet_buy_trade_id, p.source_wallet_sell_trade_id, p.analysis_start_time, p.analysis_end_time, p.analysis_status,
                       EXISTS (SELECT 1 FROM proven_trade_leg_archive a WHERE a.proven_trade_id=p.id AND a.side='BUY') AS buy_archived,
                       EXISTS (SELECT 1 FROM proven_trade_leg_archive a WHERE a.proven_trade_id=p.id AND a.side='SELL') AS sell_archived
                FROM proven_analyzed_trade p
                ORDER BY p.entry_time ASC
                LIMIT 1000
                """);
    }

    /**
     * Archive only one reviewed execution leg. This is intentionally separate from
     * regression-run archiving: a reviewer can preserve the BUY or SELL point on its
     * own without freezing/copying the complete test run. Clear Data still keeps the
     * existing full-run safety archive so replay diagnostics remain recoverable.
     */
    @Transactional
    public Map<String, Object> archiveProvenTradeLeg(long provenTradeId, String requestedSide) {
        String side = requestedSide == null ? "" : requestedSide.trim().toUpperCase(Locale.ROOT);
        if (!side.equals("BUY") && !side.equals("SELL")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archive side must be BUY or SELL.");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, source_test_run_id, source_trade_id, symbol, entry_time, entry_price, exit_time, exit_price,
                       exit_reason, realized_pnl_usdt, realized_pnl_percent
                FROM proven_analyzed_trade
                WHERE id=?
                """, provenTradeId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proven trade not found.");
        Map<String, Object> trade = rows.get(0);
        Object time = side.equals("BUY") ? trade.get("entry_time") : trade.get("exit_time");
        Object price = side.equals("BUY") ? trade.get("entry_price") : trade.get("exit_price");
        if (time == null || price == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, side + " leg is not available for this proven trade.");
        }
        jdbcTemplate.update("""
                INSERT INTO proven_trade_leg_archive
                    (proven_trade_id, source_test_run_id, source_trade_id, symbol, side, execution_time, execution_price,
                     exit_reason, realized_pnl_usdt, realized_pnl_percent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE execution_time=VALUES(execution_time), execution_price=VALUES(execution_price),
                    exit_reason=VALUES(exit_reason), realized_pnl_usdt=VALUES(realized_pnl_usdt),
                    realized_pnl_percent=VALUES(realized_pnl_percent), archived_at=CURRENT_TIMESTAMP(6)
                """, provenTradeId, trade.get("source_test_run_id"), trade.get("source_trade_id"), trade.get("symbol"),
                side, time, price, side.equals("SELL") ? trade.get("exit_reason") : null,
                side.equals("SELL") ? trade.get("realized_pnl_usdt") : null,
                side.equals("SELL") ? trade.get("realized_pnl_percent") : null);
        return Map.of("archived", true, "provenTradeId", provenTradeId, "side", side);
    }

    @Transactional(readOnly = true) public Map<String,Object> provenTradeDetail(long id){List<Map<String,Object>> rows=jdbcTemplate.queryForList("SELECT * FROM proven_analyzed_trade WHERE id=?",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Proven trade not found.");Map<String,Object> m=new LinkedHashMap<>(rows.get(0));m.put("execution_points",jdbcTemplate.queryForList("SELECT wallet_trade_id,side,execution_time,execution_price,quantity,execution_reason,sequence_no FROM proven_trade_execution_point WHERE proven_trade_id=? ORDER BY sequence_no,execution_time",id));return m;}

    @Transactional(readOnly = true)
    public List<Map<String, Object>> archivedProvenTradeLegs() {
        return jdbcTemplate.queryForList("""
                SELECT id, proven_trade_id, source_test_run_id, source_trade_id, symbol, side, execution_time,
                       execution_price, exit_reason, realized_pnl_usdt, realized_pnl_percent, archived_at
                FROM proven_trade_leg_archive
                ORDER BY archived_at DESC, id DESC
                LIMIT 1000
                """);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> provenTradeChart(String symbol, String interval) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        String normalizedInterval = switch (interval == null ? "5m" : interval.trim().toLowerCase(Locale.ROOT)) {
            case "1m", "5m", "1h", "4h" -> interval.trim().toLowerCase(Locale.ROOT);
            default -> "5m";
        };
        List<Map<String, Object>> trades = jdbcTemplate.queryForList("""
                SELECT id, source_test_run_id, source_trade_id, symbol, entry_time, entry_price, exit_time, exit_price,
                       exit_reason, realized_pnl_usdt, realized_pnl_percent, position_percent, marked_at
                FROM proven_analyzed_trade
                WHERE symbol = ?
                ORDER BY entry_time ASC
                LIMIT 250
                """, normalized);
        java.util.LinkedHashMap<String, Map<String, Object>> candles = new java.util.LinkedHashMap<>();
        for (Map<String, Object> trade : trades) {
            Timestamp entry = (Timestamp) trade.get("entry_time");
            Timestamp exit = (Timestamp) trade.get("exit_time");
            if (entry == null) continue;
            // Proven-analysis charts intentionally use real historical candles with a wide context window.
            // Keep seven hours before the BUY and seven hours after the SELL so manual review can
            // detect late entries and premature exits instead of judging only the replay window.
            Instant from = entry.toInstant().minus(java.time.Duration.ofHours(7));
            Instant to = (exit == null ? entry.toInstant() : exit.toInstant()).plus(java.time.Duration.ofHours(7));
            List<Map<String, Object>> segment = jdbcTemplate.queryForList("""
                    SELECT open_time, open_price, high_price, low_price, close_price, volume
                    FROM candle
                    WHERE symbol=? AND interval_code=? AND closed=1 AND open_time BETWEEN ? AND ?
                    ORDER BY open_time ASC
                    """, normalized, normalizedInterval, Timestamp.from(from), Timestamp.from(to));
            for (Map<String, Object> c : segment) candles.put(String.valueOf(c.get("open_time")), c);
        }
        return Map.of("symbol", normalized, "interval", normalizedInterval, "trades", trades,
                "candles", new java.util.ArrayList<>(candles.values()));
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
    }

    @Transactional(readOnly = true)
    public Map<String, Object> replayTradeChart(String symbol, String interval, Instant from, Instant to) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        String normalizedInterval = switch (interval == null ? "5m" : interval.trim().toLowerCase(Locale.ROOT)) {
            case "1m", "5m", "1h", "4h" -> interval.trim().toLowerCase(Locale.ROOT);
            default -> "5m";
        };
        if (normalized.isBlank() || from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("Symbol and a valid chart window are required.");
        }
        List<Map<String, Object>> candles = jdbcTemplate.queryForList("""
                SELECT open_time, open_price, high_price, low_price, close_price, volume
                FROM candle
                WHERE symbol=? AND interval_code=? AND closed=1 AND open_time BETWEEN ? AND ?
                ORDER BY open_time ASC
                """, normalized, normalizedInterval, Timestamp.from(from), Timestamp.from(to));
        return Map.of("symbol", normalized, "interval", normalizedInterval, "candles", candles);
    }

}
