package com.crypto.inspector.controller;

import com.crypto.dto.TradeInspectorResponse;
import com.crypto.inspector.service.TradeInspectorService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.Map;

@Controller
public class TradeInspectorController {

    private final TradeInspectorService service;

    public TradeInspectorController(TradeInspectorService service) {
        this.service = service;
    }

    @GetMapping("/trade-inspector")
    public String page() {
        return "forward:/trade-inspector.html";
    }

    @GetMapping("/api/trade-inspector")
    @ResponseBody
    public TradeInspectorResponse inspect(
            @RequestParam(required = false, defaultValue = "ALL") String symbol,
            @RequestParam(required = false, defaultValue = "ALL") String venue,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int pageSize) {
        // FIX-106: completed trades are paged at the database boundary. The filters are
        // part of the paged query, so changing symbol never searches only a recent subset.
        return service.inspect(symbol, venue, page, pageSize);
    }
    // FIX-039: blocked BUY/SELL diagnostics accept an explicit UTC window. When omitted,
    // the service defaults to the last three hours. The UI presents the timestamps in KSA.
    @GetMapping("/api/trade-inspector/blocked-buys")
    @ResponseBody
    public java.util.List<Map<String, Object>> blockedBuys(
            @RequestParam(required = false, defaultValue = "ALL") String symbol,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        return service.blockedBuys(symbol, from, to, limit);
    }

    @GetMapping("/api/trade-inspector/blocked-sells")
    @ResponseBody
    public java.util.List<Map<String, Object>> blockedSells(
            @RequestParam(required = false, defaultValue = "ALL") String symbol,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        return service.blockedSells(symbol, from, to, limit);
    }

    // FIX-100: read-only all-signal analysis feed. This endpoint never invokes trading logic;
    // it only exposes already-persisted TradeSignal evidence for the inspector grid.
    @GetMapping("/api/trade-inspector/signals/symbols")
    @ResponseBody
    public java.util.List<String> signalSymbols() {
        return service.signalAnalysisSymbols();
    }

    @GetMapping("/api/trade-inspector/signals")
    @ResponseBody
    public java.util.List<Map<String, Object>> signals(
            @RequestParam(required = false, defaultValue = "ALL") String symbol,
            @RequestParam(required = false, defaultValue = "1h") String period,
            @RequestParam(required = false, defaultValue = "BLOCKED_BUY") String type,
            @RequestParam(required = false, defaultValue = "250") int limit) {
        // FIX-101: compact Trade Analysis filters are resolved server-side from persisted
        // production evidence only. This endpoint remains strictly read-only.
        return service.signalAnalysis(symbol, period, type, limit);
    }

    @GetMapping("/api/trade-inspector/production-exits")
    @ResponseBody
    public java.util.List<Map<String, Object>> productionExits(
            @RequestParam(required = false, defaultValue = "ALL") String symbol,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return service.productionExits(symbol, limit);
    }

    // FIX-024: read-only decision-state path for one Trade Inspector BUY -> SELL pair.
    @GetMapping("/api/trade-inspector/path")
    @ResponseBody
    public Map<String, Object> path(
            @RequestParam Long buyTradeId,
            @RequestParam Long sellTradeId) {
        return service.path(buyTradeId, sellTradeId);
    }

    @GetMapping("/api/trade-inspector/chart")
    @ResponseBody
    public Map<String, Object> chart(
            @RequestParam String symbol,
            @RequestParam(required = false, defaultValue = "1m") String interval,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return service.chart(symbol, interval, from, to);
    }

}
