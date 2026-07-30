package com.crypto.client.binance.websocket;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.service.BinanceKlineService;

import jakarta.annotation.PreDestroy;

@Component
public class BinanceWebSocketManager {

    private static final Logger log =
        LoggerFactory.getLogger(
            BinanceWebSocketManager.class
        );

    private final BinanceMarketDataProperties properties;
    private final BinanceStreamUrlBuilder urlBuilder;
    private final ObjectMapper objectMapper;
    private final BinanceKlineService klineService;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

    private BinanceWebSocketHandler handler;

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
            15,
            15,
            TimeUnit.SECONDS
        );
    }

    private synchronized void connect() {

        try {
            String url = urlBuilder.build();

            handler = new BinanceWebSocketHandler(
                objectMapper,
                klineService
            );

            StandardWebSocketClient client =
                new StandardWebSocketClient();

            client.execute(
                handler,
                null,
                URI.create(url)
            ).get(Duration.ofSeconds(15).toMillis(),
                  TimeUnit.MILLISECONDS);

            log.info(
                "Binance WebSocket connection started: {}",
                url
            );

        } catch (Exception exception) {
            log.error(
                "Unable to connect to Binance: {}",
                exception.getMessage(),
                exception
            );
        }
    }

    private void reconnectWhenRequired() {

        if (handler == null || !handler.isConnected()) {

            log.warn(
                "Binance connection unavailable; reconnecting"
            );

            connect();
        }
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }
}