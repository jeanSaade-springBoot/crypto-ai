package com.crypto.regression.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** FIX-130: Production incident history for the selected review window. This is
 * NOT simulated Replay output and must never be consumed by trading services. */
@RestController
@RequestMapping("/api/administration/regression-tests")
@RequiredArgsConstructor
public class Fix130DiagnosticsController {
    private final JdbcTemplate jdbc;

    @GetMapping("/runs/{id}/fix130")
    public Map<String,Object> run(@PathVariable long id) {
        return rows(jdbc.queryForMap("SELECT symbol,start_time,end_time FROM analysis_test_run WHERE id=?", id));
    }
    @GetMapping("/archives/{id}/fix130")
    public Map<String,Object> archive(@PathVariable long id) {
        return rows(jdbc.queryForMap("SELECT symbol,start_time,end_time FROM regression_test_archive_batch WHERE id=?", id));
    }
    @GetMapping("/proven-trades/{id}/fix130")
    public Map<String,Object> proven(@PathVariable long id) {
        // Use the saved trade's own identity/window, never a reused Replay run ID.
        return rows(jdbc.queryForMap("SELECT symbol,entry_time AS start_time,COALESCE(exit_time,entry_time) AS end_time FROM proven_analyzed_trade WHERE id=?", id));
    }
    private Map<String,Object> rows(Map<String,Object> window) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT id,symbol,block_start,status,attempts,created_at,updated_at,started_at,finished_at,
                       next_attempt_at,last_error,
                       TIMESTAMPDIFF(SECOND,created_at,COALESCE(started_at,CURRENT_TIMESTAMP(6))) AS queue_age_seconds
                FROM price_move_finalization_work
                WHERE symbol=? AND TIMESTAMPADD(HOUR,8,block_start)>=? AND block_start<=?
                ORDER BY block_start,id LIMIT 501
                """, window.get("symbol"), window.get("start_time"), window.get("end_time"));
        boolean truncated = rows.size() > 500;
        return Map.of("scope", "PRODUCTION_HISTORY", "truncated", truncated,
                "gate", jdbc.queryForMap("SELECT active_job_id,owner_token FROM price_move_finalization_gate WHERE id=1"),
                "pendingCount", jdbc.queryForObject("SELECT COUNT(*) FROM price_move_finalization_work WHERE status IN ('FINALIZATION_PENDING','RETRYABLE_FAILURE')",Long.class),
                "rows", truncated ? rows.subList(0,500) : rows);
    }
}
