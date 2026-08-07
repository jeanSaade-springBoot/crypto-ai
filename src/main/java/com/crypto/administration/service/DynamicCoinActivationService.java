package com.crypto.administration.service;

import com.crypto.client.binance.websocket.BinanceWebSocketManager;
import com.crypto.service.MarketDataBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicCoinActivationService {

    private final BinanceWebSocketManager webSocketManager;
    private final MarketDataBootstrapService marketDataBootstrapService;

    @Async
    public void activate(String symbol) {
        try {
            webSocketManager.reload();
            log.info("Binance streams reloaded after enabling symbol={}", symbol);
        } catch (Exception exception) {
            log.error("Unable to reload Binance streams after enabling symbol={}", symbol, exception);
        }

        try {
            marketDataBootstrapService.bootstrapSymbol(symbol);
            log.info("Dynamic historical bootstrap completed for symbol={}", symbol);
        } catch (Exception exception) {
            log.error("Dynamic historical bootstrap failed for symbol={}", symbol, exception);
        }
    }

    @Async
    public void reloadStreams() {
        try {
            webSocketManager.reload();
            log.info("Binance streams reloaded after coin configuration change");
        } catch (Exception exception) {
            log.error("Unable to reload Binance streams after coin configuration change", exception);
        }
    }
}
