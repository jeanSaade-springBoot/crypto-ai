package com.crypto.regression.replay;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** FIX-156: append/update-safe Replay decision evidence; never a Production decision input. */
@Component
public class ReplayDecisionTraceStore {
    private final JdbcTemplate jdbcTemplate;

    public ReplayDecisionTraceStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordBuyDecision(long runId, long signalId, Instant at,
                                  ReplayBuyValidationPolicy.Result decision) {
        ReplayAccessPolicy.require("replay_decision_trace", ReplayAccessPolicy.Operation.REPLAY_INSERT);
        String code = escape(decision == null ? "NO_DECISION" : decision.code());
        String explanation = escape(decision == null ? "No local decision was produced." : decision.explanation());
        boolean allowed = decision != null && decision.allowed();
        String actual = "{\"allowed\":" + allowed + ",\"code\":\"" + code
                + "\",\"explanation\":\"" + explanation + "\"}";
        jdbcTemplate.update("""
                INSERT INTO replay_decision_trace
                    (run_id, decision_key, decision_at, decision_path, expected_json, actual_json, result_status)
                VALUES (?, ?, ?, 'BUY_LOCAL_POLICY', NULL, CAST(? AS JSON), 'NOT_MEASURED')
                ON DUPLICATE KEY UPDATE decision_at=VALUES(decision_at), actual_json=VALUES(actual_json),
                    result_status=VALUES(result_status)
                """, runId, "BUY-" + signalId, java.sql.Timestamp.from(at), actual);
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
