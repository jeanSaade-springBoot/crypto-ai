package com.crypto.regression.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** FIX-127: current durable Production processing state alongside a review window.
 * This read-only overlay is not a Replay execution source or an archived snapshot. */
@RestController
@RequestMapping("/api/administration/regression-tests")
@RequiredArgsConstructor
public class Fix127DiagnosticsController {
    private final JdbcTemplate jdbc;

    @GetMapping("/runs/{id}/fix127")
    public Map<String,Object> run(@PathVariable long id) {
        return rows(jdbc.queryForMap("SELECT symbol,start_time,end_time FROM analysis_test_run WHERE id=?",id),false);
    }
    @GetMapping("/archives/{id}/fix127")
    public Map<String,Object> archive(@PathVariable long id) {
        return rows(jdbc.queryForMap("SELECT symbol,start_time,end_time FROM regression_test_archive_batch WHERE id=?",id),false);
    }
    @GetMapping("/proven-trades/{id}/fix127")
    public Map<String,Object> proven(@PathVariable long id) {
        // Include preceding candles for context, not a guessed BUY/signal pairing.
        return rows(jdbc.queryForMap("""
                SELECT symbol,DATE_SUB(entry_time,INTERVAL 1 HOUR) AS start_time,
                COALESCE(exit_time,entry_time) AS end_time FROM proven_analyzed_trade WHERE id=?
                """,id),true);
    }
    private Map<String,Object> rows(Map<String,Object> window, boolean precedingHour) {
        var rows=jdbc.queryForList("""
                SELECT signal_id,symbol,interval_code,candle_open_time,origin,status,attempts,
                updated_at,completed_at,paper_position_id,failure_stage,error_message
                FROM signal_processing_work WHERE symbol=? AND candle_open_time>=? AND candle_open_time<=?
                ORDER BY candle_open_time,signal_id LIMIT 501
                """,window.get("symbol"),window.get("start_time"),window.get("end_time"));
        boolean truncated=rows.size()>500;
        return Map.of("scope","PRODUCTION_CURRENT_PROCESSING_STATE","truncated",truncated,
                "precedingHour",precedingHour,"startTime",window.get("start_time"),"endTime",window.get("end_time"),
                "rows",truncated ? rows.subList(0,500) : rows);
    }
}
