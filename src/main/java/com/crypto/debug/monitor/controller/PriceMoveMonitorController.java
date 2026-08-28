package com.crypto.debug.monitor.controller;

import com.crypto.debug.monitor.domain.PriceMoveEvent;
import com.crypto.debug.monitor.domain.PriceMoveMonitorSettings;
import com.crypto.debug.monitor.dto.PriceMoveMonitorSettingsRequest;
import com.crypto.debug.monitor.dto.PriceMoveReviewRequest;
import com.crypto.debug.monitor.dto.CatchingMarketPageResponse;
import com.crypto.debug.monitor.service.PriceMoveMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/administration/debug/price-moves")
@RequiredArgsConstructor
public class PriceMoveMonitorController {

    private final PriceMoveMonitorService service;

    @GetMapping
    public List<PriceMoveEvent> events(@RequestParam(required = false) String symbol) {
        return service.recentEvents(symbol);
    }


    @GetMapping("/summary")
    public CatchingMarketPageResponse summary(
            @RequestParam(required = false) String symbols,
            @RequestParam(defaultValue = "HIGH") String level,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page) {
        // FIX-113: read-only database aggregation, fixed 20 rows/page.
        return service.summaryPage(symbols, level, hours, page);
    }

    @GetMapping("/{id}/start-chart")
    public Map<String, Object> startChart(@PathVariable Long id,
                                          @RequestParam(required = false) String interval) {
        // FIX-113: fast chart path highlights only the persisted catch start time.
        return service.eventStartChart(id, interval);
    }

    @GetMapping("/active")
    public Map<String, Object> active(@RequestParam String symbol) {
        return service.activeTracker(symbol);
    }

    @GetMapping("/blame-count")
    public Map<String, Long> blameCount() { return Map.of("count", service.outstandingBlameCount()); }

    @GetMapping("/{id}/chart")
    public Map<String, Object> chart(@PathVariable Long id,
                                     @RequestParam(defaultValue = "1m") String interval) {
        // FIX-094: Catching Market reuses the Trade Inspector popup experience. The selected
        // interval is display-only and is passed into the existing read-only catch chart loader.
        return service.eventChart(id, interval);
    }

    @GetMapping("/settings")
    public PriceMoveMonitorSettings settings() {
        return service.currentSettings();
    }

    @PutMapping("/settings")
    public PriceMoveMonitorSettings updateSettings(@RequestBody PriceMoveMonitorSettingsRequest request) {
        return service.updateSettings(request);
    }

    @PutMapping("/{id}/review-status")
    public PriceMoveEvent updateReviewStatus(@PathVariable Long id, @RequestBody PriceMoveReviewRequest request) {
        return service.updateReviewStatus(id, request.status());
    }

    @PostMapping("/cleanup")
    public Map<String, Integer> cleanup() {
        return Map.of("deleted", service.cleanupExpired());
    }
}
