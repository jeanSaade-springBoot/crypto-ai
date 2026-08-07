package com.crypto.administration.controller;

import com.crypto.administration.dto.AddCoinRequest;
import com.crypto.administration.dto.CoinConfigurationView;
import com.crypto.administration.dto.CoinEnabledRequest;
import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.administration.service.DynamicCoinActivationService;
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
    private final DynamicCoinActivationService dynamicCoinActivationService;
    private final BinanceWebSocketManager webSocketManager;

    @GetMapping
    public List<CoinConfigurationView> list() {
        return coinConfigurationService.findAll();
    }

    @PostMapping
    public CoinConfigurationView add(@RequestBody AddCoinRequest request) {
        CoinConfigurationView coin = coinConfigurationService.add(request.symbol());
        dynamicCoinActivationService.activate(coin.symbol());
        return coin;
    }

    @PutMapping("/{id}/enabled")
    public CoinConfigurationView setEnabled(@PathVariable Long id, @RequestBody CoinEnabledRequest request) {
        CoinConfigurationView coin = coinConfigurationService.setEnabled(id, request.enabled());
        if (coin.enabled()) {
            dynamicCoinActivationService.activate(coin.symbol());
        } else {
            dynamicCoinActivationService.reloadStreams();
        }
        return coin;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        coinConfigurationService.remove(id);
        dynamicCoinActivationService.reloadStreams();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reload-streams")
    public Map<String, Object> reloadStreams() {
        webSocketManager.reload();
        return Map.of("message", "Binance streams reloaded", "symbols", coinConfigurationService.enabledSymbols());
    }
}
