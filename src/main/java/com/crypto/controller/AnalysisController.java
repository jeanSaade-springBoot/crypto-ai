package com.crypto.controller;

import com.crypto.domain.TradeSignal;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService service;
    private final TradeSignalRepository repository;

    @PostMapping("/{symbol}")
    public TradeSignal analyze(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1h") String interval) {
        return service.analyze(symbol, interval);
    }

    @GetMapping("/signals")
    public List<TradeSignal> latestSignals() {
        return repository.findTop100ByOrderByGeneratedAtDesc();
    }
}
