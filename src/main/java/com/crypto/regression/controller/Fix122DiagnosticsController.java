package com.crypto.regression.controller;

import com.crypto.execution.service.StopLossEvidenceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.sql.Timestamp;
import java.util.*;

/** FIX-122: read-only diagnostics for the selected Replay window, including blocked
 * entries and separately identified Production evaluations. No trading mutations. */
@RestController
@RequestMapping("/api/administration/regression-tests")
@RequiredArgsConstructor
public class Fix122DiagnosticsController {
    private final JdbcTemplate jdbc;
    private final StopLossEvidenceStore store;

    @GetMapping("/proven-trades/{id}/fix122")
    public org.springframework.http.ResponseEntity<String> proven(@PathVariable long id) {
        String json=jdbc.queryForObject("SELECT fix122_diagnostics_json FROM proven_analyzed_trade WHERE id=?",String.class,id);
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(json==null ? "{\"enabled\":null,\"rows\":[],\"scope\":\"PROVEN\"}" : json);
    }

    @GetMapping("/runs/{id}/fix122")
    public Map<String,Object> details(@PathVariable long id) {
        Map<String,Object> run=jdbc.queryForMap("SELECT symbol,start_time,end_time FROM analysis_test_run WHERE id=?",id);
        return details(id,run);
    }

    @GetMapping("/archives/{id}/fix122")
    public Map<String,Object> archive(@PathVariable long id) {
        Map<String,Object> run=jdbc.queryForMap("SELECT source_test_run_id,symbol,start_time,end_time FROM regression_test_archive_batch WHERE id=?",id);
        return details(((Number)run.get("source_test_run_id")).longValue(),run);
    }

    private Map<String,Object> details(long id,Map<String,Object> run) {
        List<Boolean> revision=jdbc.query("SELECT enabled FROM fix122_replay_revision WHERE test_run_id=?",(rs,row)->rs.getBoolean(1),id);
        List<Map<String,Object>> rows=new ArrayList<>(store.replayDetails(id));
        rows.addAll(store.productionDetails((String)run.get("symbol"),
                ((Timestamp)run.get("start_time")).toInstant(),((Timestamp)run.get("end_time")).toInstant()));
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("enabled",revision.isEmpty()?null:revision.get(0));result.put("rows",rows);
        return result;
    }
}
