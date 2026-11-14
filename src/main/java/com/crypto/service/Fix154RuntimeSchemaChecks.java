package com.crypto.service;

import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs during bean construction, before WebSocket admission and scheduled work.
 * FIX-154 uses these columns in every mode, so this is intentionally not OFF-mode
 * conditional and fails with a direct operational error rather than later SQL noise.
 */
@Component
@DependsOn("entityManagerFactory")
public class Fix154RuntimeSchemaChecks {
    public Fix154RuntimeSchemaChecks(JdbcTemplate jdbc) {
        requireColumn(jdbc, "trade_signal", "data_quality_valid");
        requireColumn(jdbc, "trade_signal_test", "btc_context_candle_close_time");
        requireColumn(jdbc, "trade_signal_test", "signal_mode");
        requireColumn(jdbc, "trade_signal_test", "data_quality_valid");
        try {
            jdbc.queryForList("SELECT 1 FROM buy_htf_assessment_audit WHERE 1=0");
        } catch (Exception e) {
            throw new IllegalStateException("FIX-154 requires buy_htf_assessment_audit. Apply Flyway V107 before starting.", e);
        }
    }

    private static void requireColumn(JdbcTemplate jdbc, String table, String column) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Long.class, table, column);
        if (count == null || count != 1) {
            throw new IllegalStateException("FIX-154 requires " + table + "." + column + ". Apply Flyway V107 before starting.");
        }
    }
}
