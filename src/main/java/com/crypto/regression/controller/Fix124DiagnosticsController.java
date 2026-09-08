package com.crypto.regression.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** FIX-124: Production incident history for the selected review window. This is
 * NOT simulated Replay output and must never be consumed by trading services. */
@RestController
@RequestMapping("/api/administration/regression-tests")
@RequiredArgsConstructor
public class Fix124DiagnosticsController {
    private final JdbcTemplate jdbc;

    @GetMapping("/runs/{id}/fix124")
    public Map<String,Object> run(@PathVariable long id) {
        return rows(jdbc.queryForMap("SELECT symbol,start_time,end_time FROM analysis_test_run WHERE id=?", id));
    }
    @GetMapping("/archives/{id}/fix124")
    public Map<String,Object> archive(@PathVariable long id) {
        return rows(jdbc.queryForMap("SELECT symbol,start_time,end_time FROM regression_test_archive_batch WHERE id=?", id));
    }
    @GetMapping("/proven-trades/{id}/fix124")
    public Map<String,Object> proven(@PathVariable long id) {
        // Use the saved trade's own identity/window, never a reused Replay run ID.
        return rows(jdbc.queryForMap("SELECT symbol,entry_time AS start_time,COALESCE(exit_time,entry_time) AS end_time FROM proven_analyzed_trade WHERE id=?", id));
    }
    private Map<String,Object> rows(Map<String,Object> window) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT id,symbol,interval_code,candle_open_time,observed_at,price,attempts,outcome,error_message
                FROM fix124_protection_incident
                WHERE symbol=? AND observed_at>=? AND observed_at<=?
                ORDER BY observed_at,id LIMIT 501
                """, window.get("symbol"), window.get("start_time"), window.get("end_time"));
        boolean truncated = rows.size() > 500;
        return Map.of("scope", "PRODUCTION_HISTORY", "truncated", truncated,
                "rows", truncated ? rows.subList(0,500) : rows);
    }
}
