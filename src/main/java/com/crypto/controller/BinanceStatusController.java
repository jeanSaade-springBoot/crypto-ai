package com.crypto.controller;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.client.config.binance.BinanceMarketDataProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/binance")
public class BinanceStatusController {

    private final BinanceMarketDataProperties properties;
    private final CoinConfigurationService coinConfigurationService;

    public BinanceStatusController(BinanceMarketDataProperties properties,
                                   CoinConfigurationService coinConfigurationService) {
        this.properties = properties;
        this.coinConfigurationService = coinConfigurationService;
    }

    @GetMapping("/configuration")
    public Map<String, Object> configuration() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", properties.isEnabled());
        response.put("baseUrl", properties.getBaseUrl());
        response.put("websocketBaseUrl", properties.getWebsocketBaseUrl());
        response.put("defaultLimit", properties.getDefaultLimit());
        response.put("requestTimeoutSeconds", properties.getRequestTimeoutSeconds());
        response.put("symbols", coinConfigurationService.enabledSymbols());
        response.put("intervals", properties.getIntervals());
        response.put("historicalEnabled", properties.getHistorical().isEnabled());
        response.put("historicalLimit", properties.getHistorical().getLimit());
        response.put("websocketEnabled", properties.getWebsocket().isEnabled());
        response.put("reconnectDelaySeconds", properties.getWebsocket().getReconnectDelaySeconds());
        response.put("healthCheckSeconds", properties.getWebsocket().getHealthCheckSeconds());
        return response;
    }
}
