package com.crypto.health.controller;

import com.crypto.health.service.SystemHealthDailyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** FIX-071 read-only operations endpoint for the System Health page. */
@RestController
@RequestMapping("/api/system-health")
@RequiredArgsConstructor
public class SystemHealthController {

    private final SystemHealthDailyService service;

    @GetMapping("/daily")
    public Map<String, Object> daily() {
        return service.dailyHealth();
    }
}
