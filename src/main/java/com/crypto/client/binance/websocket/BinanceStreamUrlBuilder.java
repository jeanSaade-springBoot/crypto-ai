package com.crypto.client.binance.websocket;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.client.config.binance.BinanceMarketDataProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class BinanceStreamUrlBuilder {

    private final BinanceMarketDataProperties properties;
    private final CoinConfigurationService coinConfigurationService;

    public BinanceStreamUrlBuilder(
            BinanceMarketDataProperties properties,
            CoinConfigurationService coinConfigurationService
    ) {
        this.properties = properties;
        this.coinConfigurationService = coinConfigurationService;
    }

    public String build() {
        List<String> symbols = coinConfigurationService.enabledSymbols();
        if (symbols.isEmpty()) {
            throw new IllegalStateException("No enabled Binance symbols configured in Administration.");
        }
        if (properties.getIntervals().isEmpty()) {
            throw new IllegalStateException("No Binance intervals configured.");
        }

        String streams = symbols.stream()
                .flatMap(symbol -> properties.getIntervals().stream()
                        .map(interval -> symbol.trim().toLowerCase(Locale.ROOT)
                                + "@kline_" + interval.trim()))
                .collect(Collectors.joining("/"));

        return properties.getWebsocketBaseUrl() + "/stream?streams=" + streams;
    }
}
