package com.crypto.regression.replay;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * FIX-156 / O-8: the only Replay-owned gateway for the exceptional shared
 * diagnostics table. None of these operations may supply decision authority.
 */
@Repository
public class ReplayFix122EvaluationStore {
    private static final String TABLE = "fix122_evaluation";
    private final JdbcTemplate jdbc;

    public ReplayFix122EvaluationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void appendActiveRun(long runId, String symbol, Long signalId, Instant at, String stage, String payload) {
        ReplayAccessPolicy.requireFix122ActiveRunInsert(TABLE, runId);
        jdbc.update("""
                INSERT INTO fix122_evaluation(test_run_id,symbol,signal_id,evaluated_at,stage,payload)
                VALUES (?,?,?,?,?,?)
                """, runId, symbol, signalId, Timestamp.from(at), stage, payload);
    }

    public List<Map<String, Object>> activeRunDetails(long runId) {
        ReplayAccessPolicy.requireFix122ActiveRunRead(TABLE, runId);
        return jdbc.queryForList("SELECT * FROM fix122_evaluation WHERE test_run_id=? ORDER BY id", runId);
    }

    public int purgeReplayRows() {
        ReplayAccessPolicy.requireFix122ScopedPurge(TABLE);
        return jdbc.update("DELETE FROM fix122_evaluation WHERE test_run_id IS NOT NULL");
    }

    /** Display only; caller must not feed this result to state, fixture, or parity logic. */
    public List<Map<String, Object>> productionDisplayDetails(String symbol, Instant from, Instant to) {
        ReplayAccessPolicy.requireFix122ProductionDisplayRead(TABLE);
        return jdbc.queryForList("""
                SELECT * FROM fix122_evaluation WHERE test_run_id IS NULL AND symbol=?
                AND evaluated_at>=? AND evaluated_at<=? ORDER BY id
                """, symbol, Timestamp.from(from), Timestamp.from(to));
    }
}
