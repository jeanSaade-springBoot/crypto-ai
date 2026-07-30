package com.crypto.controller;

import com.crypto.domain.PaperPosition;
import com.crypto.service.PaperTradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/paper-trades")
@RequiredArgsConstructor
public class PaperTradingController {

    private final PaperTradingService service;

    @PostMapping("/{symbol}")
    public PaperPosition open(@PathVariable String symbol) {
        return service.openFromLatestSignal(symbol);
    }

    @PostMapping("/{id}/close")
    public PaperPosition close(
            @PathVariable Long id,
            @RequestParam BigDecimal exitPrice) {
        return service.close(id, exitPrice);
    }

    @GetMapping
    public List<PaperPosition> list() {
        return service.list();
    }
}
