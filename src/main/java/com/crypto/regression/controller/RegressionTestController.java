package com.crypto.regression.controller;

import com.crypto.regression.dto.RegressionTestRunRequest;
import com.crypto.regression.service.RegressionTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/administration/regression-tests")
@RequiredArgsConstructor
public class RegressionTestController {

    private final RegressionTestService service;

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> start(@RequestBody RegressionTestRunRequest request) {
        long id = service.start(request);
        return Map.of("id", id, "status", "PENDING");
    }

    @PostMapping("/runs/{id}/archive")
    public Map<String, Object> archive(@PathVariable long id, @RequestBody(required = false) Map<String,Object> body) {
        String reason = body == null ? null : String.valueOf(body.getOrDefault("reason", ""));
        return service.archiveRun(id, reason);
    }

    @GetMapping("/archives")
    public List<Map<String,Object>> archives() { return service.archivedRuns(); }
    @GetMapping("/archives/{id}")
    public Map<String,Object> archiveRun(@PathVariable long id) { return service.archivedRun(id); }
    @GetMapping("/archives/{id}/signals")
    public List<Map<String,Object>> archiveSignals(@PathVariable long id) { return service.archivedSignals(id); }
    @GetMapping("/archives/{id}/trades")
    public List<Map<String,Object>> archiveTrades(@PathVariable long id) { return service.archivedTrades(id); }
    @GetMapping("/archives/{id}/position-management")
    public List<Map<String,Object>> archiveManagement(@PathVariable long id) { return service.archivedPositionManagement(id); }
    @GetMapping("/archives/{id}/opportunities")
    public List<Map<String,Object>> archiveOpportunities(@PathVariable long id) { return service.archivedOpportunities(id); }

    @DeleteMapping("/runs")
    public Map<String, Object> resetAll() {
        return service.resetAllTestData();
    }

    @GetMapping("/runs")
    public List<Map<String, Object>> latestRuns() {
        return service.latestRuns();
    }

    @GetMapping("/runs/{id}")
    public Map<String, Object> run(@PathVariable long id) {
        return service.getRun(id);
    }

    @GetMapping("/runs/{id}/signals")
    public List<Map<String, Object>> signals(@PathVariable long id) {
        return service.signals(id);
    }

    @GetMapping("/runs/{id}/trades")
    public List<Map<String, Object>> trades(@PathVariable long id) {
        return service.trades(id);
    }

    @GetMapping("/runs/{id}/position-management")
    public List<Map<String, Object>> positionManagement(@PathVariable long id) {
        return service.positionManagement(id);
    }

    @GetMapping("/runs/{id}/opportunities")
    public List<Map<String, Object>> opportunities(@PathVariable long id) {
        return service.opportunities(id);
    }

    @PostMapping("/proven-trades/{runId}/{tradeId}")
    public Map<String, Object> markProven(@PathVariable long runId, @PathVariable long tradeId) {
        return service.markProvenSuccess(runId, tradeId);
    }

    @DeleteMapping("/proven-trades/{runId}/{tradeId}")
    public Map<String, Object> unmarkProven(@PathVariable long runId, @PathVariable long tradeId) {
        return service.unmarkProvenSuccess(runId, tradeId);
    }

    @GetMapping("/proven-trades")
    public List<Map<String, Object>> provenTrades() { return service.provenTrades(); }

    @GetMapping("/proven-trades/chart")
    public Map<String, Object> provenTradeChart(@RequestParam String symbol,
                                                 @RequestParam(defaultValue = "5m") String interval) {
        return service.provenTradeChart(symbol, interval);
    }

    @GetMapping("/trade-chart")
    public Map<String, Object> replayTradeChart(@RequestParam String symbol,
                                                 @RequestParam(defaultValue = "5m") String interval,
                                                 @RequestParam Instant from,
                                                 @RequestParam Instant to) {
        return service.replayTradeChart(symbol, interval, from, to);
    }

}
