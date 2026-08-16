package com.crypto.debug.monitor.controller;

import com.crypto.debug.monitor.domain.PriceMoveEvent;
import com.crypto.debug.monitor.domain.PriceMoveMonitorSettings;
import com.crypto.debug.monitor.dto.PriceMoveMonitorSettingsRequest;
import com.crypto.debug.monitor.dto.PriceMoveReviewRequest;
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

    @GetMapping("/active")
    public Map<String, Object> active(@RequestParam String symbol) {
        return service.activeTracker(symbol);
    }

    @GetMapping("/blame-count")
    public Map<String, Long> blameCount() { return Map.of("count", service.outstandingBlameCount()); }

    @GetMapping("/{id}/chart")
    public Map<String, Object> chart(@PathVariable Long id) { return service.eventChart(id); }

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
