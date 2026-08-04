package com.crypto.position.controller;

import com.crypto.position.dto.PositionAnalysisView;
import com.crypto.position.service.PositionManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/position-management")
@RequiredArgsConstructor
public class PositionManagementController {
    private final PositionManagementService service;

    @GetMapping("/latest")
    public List<PositionAnalysisView> latest() {
        return service.latest();
    }
}
