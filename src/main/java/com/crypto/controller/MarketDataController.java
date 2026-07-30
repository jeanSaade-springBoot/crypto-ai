package com.crypto.controller;

import com.crypto.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/market-data")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService service;

    @PostMapping("/import")
    public Map<String, Object> importCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(defaultValue = "500") int limit) {

        int inserted = service.importCandles(symbol, interval, limit);
        return Map.of("symbol", symbol.toUpperCase(), "interval", interval, "inserted", inserted);
    }
}
