package com.crypto.administration.controller;

import com.crypto.administration.dto.AddCoinRequest;
import com.crypto.administration.dto.CoinConfigurationView;
import com.crypto.administration.dto.CoinEnabledRequest;
import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.client.binance.websocket.BinanceWebSocketManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/administration/coins")
@RequiredArgsConstructor
public class CoinConfigurationController {

    private final CoinConfigurationService coinConfigurationService;
    private final BinanceWebSocketManager webSocketManager;

    @GetMapping
    public List<CoinConfigurationView> list() {
        return coinConfigurationService.findAll();
    }

    @PostMapping
    public CoinConfigurationView add(@RequestBody AddCoinRequest request) {
        return coinConfigurationService.add(request.symbol());
    }

    @PutMapping("/{id}/enabled")
    public CoinConfigurationView setEnabled(@PathVariable Long id, @RequestBody CoinEnabledRequest request) {
        return coinConfigurationService.setEnabled(id, request.enabled());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        coinConfigurationService.remove(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reload-streams")
    public Map<String, Object> reloadStreams() {
        webSocketManager.reload();
        return Map.of("message", "Binance streams reloaded", "symbols", coinConfigurationService.enabledSymbols());
    }
}
