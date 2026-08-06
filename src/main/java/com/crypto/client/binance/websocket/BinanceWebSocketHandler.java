package com.crypto.client.binance.websocket;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.crypto.service.BinanceKlineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BinanceWebSocketHandler
        extends TextWebSocketHandler {

    private static final Logger log =
        LoggerFactory.getLogger(
            BinanceWebSocketHandler.class
        );
    private final ObjectMapper objectMapper;
    private final BinanceKlineService klineService;
    private final AtomicBoolean connected =
        new AtomicBoolean(false);
    private volatile WebSocketSession session;

    public BinanceWebSocketHandler(
            ObjectMapper objectMapper,
            BinanceKlineService klineService
    ) {
        this.objectMapper = objectMapper;
        this.klineService = klineService;
    }

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) {
        this.session = session;
        connected.set(true);

        log.info(
            "Connected to Binance WebSocket: session={}",
            session.getId()
        );
    }
    
    // handleTextMessage

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) {
        try {
            JsonNode root = objectMapper.readTree(
                message.getPayload()
            );

            boolean candleClosed =
                klineService.processKline(root);

            if (candleClosed) {
                JsonNode data = root.has("data")
                    ? root.get("data")
                    : root;

                JsonNode kline = data.get("k");

                log.info(
                    "Binance candle closed: " +
                    "symbol={}, interval={}, close={}",
                    kline.get("s").asText(),
                    kline.get("i").asText(),
                    kline.get("c").asText()
                );
            }

        } catch (Exception exception) {
            log.error(
                "Failed to process Binance message: {}",
                exception.getMessage(),
                exception
            );
        }
    }
    
    

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) {
        connected.set(false);

        log.error(
            "Binance WebSocket transport error: {}",
            exception.getMessage(),
            exception
        );
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            org.springframework.web.socket.CloseStatus status
    ) {
        connected.set(false);

        log.warn(
            "Binance WebSocket disconnected: {}",
            status
        );
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void close() {
        WebSocketSession current = session;
        connected.set(false);
        if (current != null && current.isOpen()) {
            try {
                current.close(CloseStatus.NORMAL);
            } catch (Exception exception) {
                log.warn("Unable to close Binance WebSocket session cleanly: {}", exception.getMessage());
            }
        }
    }
}