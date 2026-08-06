package com.crypto.client.binance.websocket;

import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.service.BinanceKlineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class BinanceWebSocketManager {

    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketManager.class);

    private final BinanceMarketDataProperties properties;
    private final BinanceStreamUrlBuilder urlBuilder;
    private final ObjectMapper objectMapper;
    private final BinanceKlineService klineService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile BinanceWebSocketHandler handler;

    public BinanceWebSocketManager(
            BinanceMarketDataProperties properties,
            BinanceStreamUrlBuilder urlBuilder,
            ObjectMapper objectMapper,
            BinanceKlineService klineService
    ) {
        this.properties = properties;
        this.urlBuilder = urlBuilder;
        this.objectMapper = objectMapper;
        this.klineService = klineService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.getWebsocket().isEnabled()) {
            log.info("Binance WebSocket is disabled");
            return;
        }
        connect();
        scheduler.scheduleWithFixedDelay(
                this::reconnectWhenRequired,
                properties.getWebsocket().getHealthCheckSeconds(),
                properties.getWebsocket().getHealthCheckSeconds(),
                TimeUnit.SECONDS);
    }

    public synchronized void reload() {
        if (!properties.getWebsocket().isEnabled()) {
            throw new IllegalStateException("Binance WebSocket is disabled");
        }
        closeCurrentHandler();
        connect();
        if (handler == null || !handler.isConnected()) {
            throw new IllegalStateException("Unable to reload Binance streams");
        }
    }

    private synchronized void connect() {
        try {
            String url = urlBuilder.build();
            BinanceWebSocketHandler newHandler = new BinanceWebSocketHandler(objectMapper, klineService);
            StandardWebSocketClient client = new StandardWebSocketClient();
            client.execute(newHandler, null, URI.create(url))
                    .get(Duration.ofSeconds(15).toMillis(), TimeUnit.MILLISECONDS);
            handler = newHandler;
            log.info("Binance WebSocket connection started: {}", url);
        } catch (Exception exception) {
            handler = null;
            log.error("Unable to connect to Binance: {}", exception.getMessage(), exception);
        }
    }

    private void reconnectWhenRequired() {
        BinanceWebSocketHandler current = handler;
        if (current == null || !current.isConnected()) {
            log.warn("Binance connection unavailable; reconnecting");
            connect();
        }
    }

    private void closeCurrentHandler() {
        BinanceWebSocketHandler current = handler;
        handler = null;
        if (current != null) {
            current.close();
        }
    }

    @PreDestroy
    public void stop() {
        closeCurrentHandler();
        scheduler.shutdownNow();
    }
}
