package com.crypto.regression.controller;

import com.crypto.regression.dto.RegressionTestRunRequest;
import com.crypto.regression.service.RegressionTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

}
