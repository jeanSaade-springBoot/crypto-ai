package com.crypto.client.binance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.client.binance.dto.BinanceKline;

@Component
public class BinanceMarketDataClient {

    private final RestClient restClient;
    private final BinanceMarketDataProperties properties;

    public BinanceMarketDataClient(
        RestClient.Builder restClientBuilder,
        BinanceMarketDataProperties properties
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    public List<BinanceKline> getKlines(
        String symbol,
        String interval,
        int limit
    ) {
        validateRequest(symbol, interval, limit);

        List<List<Object>> response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v3/klines")
                .queryParam("symbol", symbol.toUpperCase())
                .queryParam("interval", interval)
                .queryParam("limit", limit)
                .build())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });

        if (response == null || response.isEmpty()) {
            return List.of();
        }

        List<BinanceKline> candles = new ArrayList<>(response.size());

        for (List<Object> row : response) {
            candles.add(mapKline(row));
        }

        return candles;
    }

    private BinanceKline mapKline(List<Object> row) {
        if (row == null || row.size() < 11) {
            throw new IllegalArgumentException(
                "Invalid Binance kline response"
            );
        }

        return new BinanceKline(
            Instant.ofEpochMilli(toLong(row.get(0))),
            toDecimal(row.get(1)),
            toDecimal(row.get(2)),
            toDecimal(row.get(3)),
            toDecimal(row.get(4)),
            toDecimal(row.get(5)),
            Instant.ofEpochMilli(toLong(row.get(6))),
            toDecimal(row.get(7)),
            toLong(row.get(8)),
            toDecimal(row.get(9)),
            toDecimal(row.get(10))
        );
    }

    private BigDecimal toDecimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(String.valueOf(value));
    }

    private void validateRequest(
        String symbol,
        String interval,
        int limit
    ) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }

        if (interval == null || interval.isBlank()) {
            throw new IllegalArgumentException("Interval is required");
        }

        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException(
                "Limit must be between 1 and 1000"
            );
        }
    }
}