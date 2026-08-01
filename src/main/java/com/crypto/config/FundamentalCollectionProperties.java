package com.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "fundamentals.collection")
public record FundamentalCollectionProperties(
        boolean enabled,
        String baseUrl,
        Duration fixedDelay,
        Duration staleAfter,
        String vsCurrency,
        Map<String, String> coinIds
) {
    public FundamentalCollectionProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.coingecko.com/api/v3" : baseUrl;
        fixedDelay = fixedDelay == null ? Duration.ofHours(1) : fixedDelay;
        staleAfter = staleAfter == null ? Duration.ofHours(6) : staleAfter;
        vsCurrency = vsCurrency == null || vsCurrency.isBlank() ? "usd" : vsCurrency;
        coinIds = coinIds == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(coinIds));
    }
}
