package com.crypto.regression.replay;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * FIX-156: Replay-owned publication boundary.  A parity result is evidence,
 * never a wallet/account mutation and never a Production trading record.
 */
@Component
public class ReplayRunOutputStore {
    private final JdbcTemplate jdbcTemplate;

    public ReplayRunOutputStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordExecutionSummary(long runId, int trades, int wins, int losses,
                                       BigDecimal realizedPnl, BigDecimal finalValue,
                                       String priceReplayMode, String logicMode) {
        ReplayAccessPolicy.require("replay_run_output", ReplayAccessPolicy.Operation.REPLAY_INSERT);
        String details = "{\"trades\":" + trades
                + ",\"wins\":" + wins
                + ",\"losses\":" + losses
                + ",\"realizedPnl\":\"" + decimal(realizedPnl)
                + "\",\"finalValue\":\"" + decimal(finalValue)
                + "\",\"priceReplayMode\":\"" + json(priceReplayMode)
                + "\",\"logicMode\":\"" + json(logicMode) + "\"}";
        jdbcTemplate.update("""
                INSERT INTO replay_run_output (run_id, result_type, result_status, details_json)
                VALUES (?, 'EXECUTION_SUMMARY', 'NOT_PARITY_CLAIMED', CAST(? AS JSON))
                """, runId, details);
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
