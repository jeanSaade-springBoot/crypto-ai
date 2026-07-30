package com.crypto.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crypto.client.config.binance.BinanceMarketDataProperties;

@RestController
@RequestMapping("/api/binance")
public class BinanceStatusController {

    private final BinanceMarketDataProperties properties;

    public BinanceStatusController(
            BinanceMarketDataProperties properties
    ) {
        this.properties = properties;
    }

    @GetMapping("/configuration")
    public Map<String, Object> configuration() {

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("enabled",
                properties.isEnabled());

        response.put("baseUrl",
                properties.getBaseUrl());

        response.put("websocketBaseUrl",
                properties.getWebsocketBaseUrl());

        response.put("defaultLimit",
                properties.getDefaultLimit());

        response.put("requestTimeoutSeconds",
                properties.getRequestTimeoutSeconds());

        response.put("symbols",
                properties.getSymbols());

        response.put("intervals",
                properties.getIntervals());

        response.put("historicalEnabled",
                properties.getHistorical().isEnabled());

        response.put("historicalLimit",
                properties.getHistorical().getLimit());

        response.put("websocketEnabled",
                properties.getWebsocket().isEnabled());

        response.put("reconnectDelaySeconds",
                properties.getWebsocket().getReconnectDelaySeconds());

        response.put("healthCheckSeconds",
                properties.getWebsocket().getHealthCheckSeconds());

        return response;
    }
}