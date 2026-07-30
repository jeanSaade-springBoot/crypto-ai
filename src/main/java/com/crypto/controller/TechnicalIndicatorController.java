package com.crypto.controller;

import com.crypto.domain.TechnicalIndicator;
import com.crypto.indicator.service.TechnicalIndicatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/technical-indicators")
public class TechnicalIndicatorController {

    private final TechnicalIndicatorService indicatorService;

    public TechnicalIndicatorController(
            TechnicalIndicatorService indicatorService
    ) {
        this.indicatorService = indicatorService;
    }

    @PostMapping("/{symbol}/{intervalCode}/calculate")
    public ResponseEntity<?> calculate(
            @PathVariable String symbol,
            @PathVariable String intervalCode
    ) {
        return indicatorService
                .calculateAndPersist(
                        symbol,
                        intervalCode,
                        null
                )
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .unprocessableEntity()
                        .body(
                                "At least 210 closed candles are required"
                        ));
    }

    @GetMapping("/{symbol}/{intervalCode}/latest")
    public ResponseEntity<TechnicalIndicator> latest(
            @PathVariable String symbol,
            @PathVariable String intervalCode
    ) {
        return indicatorService
                .getLatest(symbol, intervalCode)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }

    @GetMapping("/{symbol}/{intervalCode}")
    public List<TechnicalIndicator> history(
            @PathVariable String symbol,
            @PathVariable String intervalCode
    ) {
        return indicatorService.getHistory(
                symbol,
                intervalCode
        );
    }
}