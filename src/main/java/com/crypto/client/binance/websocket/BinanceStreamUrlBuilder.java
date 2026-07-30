package com.crypto.client.binance.websocket;

import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.crypto.client.config.binance.BinanceMarketDataProperties;

@Component
public class BinanceStreamUrlBuilder {

    private final BinanceMarketDataProperties properties;

    public BinanceStreamUrlBuilder(
            BinanceMarketDataProperties properties
    ) {
        this.properties = properties;
    }

    public String build() {

        if (properties.getSymbols().isEmpty()) {
            throw new IllegalStateException(
                    "No Binance symbols configured.");
        }

        if (properties.getIntervals().isEmpty()) {
            throw new IllegalStateException(
                    "No Binance intervals configured.");
        }

        String streams = properties.getSymbols()
                .stream()
                .flatMap(symbol ->
                        properties.getIntervals()
                                .stream()
                                .map(interval ->
                                        symbol.trim()
                                                .toLowerCase(Locale.ROOT)
                                                + "@kline_"
                                                + interval.trim()))
                .collect(Collectors.joining("/"));

        return properties.getWebsocketBaseUrl()
                + "/stream?streams="
                + streams;
    }
}