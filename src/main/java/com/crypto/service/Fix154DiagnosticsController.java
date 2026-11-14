package com.crypto.service;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Read-only FIX-154 counters for operational health and review evidence. */
@RestController
@RequestMapping("/api/fix154")
public class Fix154DiagnosticsController {
    private final Fix154Metrics metrics;

    public Fix154DiagnosticsController(Fix154Metrics metrics) { this.metrics = metrics; }

    @GetMapping("/health")
    public Map<String, Long> health() { return metrics.snapshot(); }
}
