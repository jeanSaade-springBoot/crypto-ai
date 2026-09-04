package com.crypto.client.binance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.client.binance.dto.BinanceKline;
import com.crypto.client.binance.dto.BinanceOrderBook;

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

    public BinanceOrderBook getOrderBook(String symbol, int limit) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Depth limit must be between 1 and 1000");
        }

        BinanceOrderBook response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v3/depth")
                .queryParam("symbol", symbol.toUpperCase())
                .queryParam("limit", limit)
                .build())
            .retrieve()
            .body(BinanceOrderBook.class);

        if (response == null) {
            throw new IllegalStateException("Empty Binance order-book response for " + symbol);
        }
        return response;
    }


    /**
     * FIX-11T: read the symbol's current Binance minimum executable notional directly
     * from exchangeInfo.  Near-TP uses this only as a minimum-order guard; it does not
     * introduce a separate quantity-rounding model. NOTIONAL is preferred when present,
     * with MIN_NOTIONAL retained for symbols that still expose the older filter.
     */
    public BigDecimal getMinimumExecutableNotional(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }

        Map<String, Object> response = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v3/exchangeInfo")
                .queryParam("symbol", symbol.toUpperCase())
                .build())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });

        if (response == null) {
            throw new IllegalStateException("Empty Binance exchangeInfo response for " + symbol);
        }
        Object symbolsValue = response.get("symbols");
        if (!(symbolsValue instanceof List<?> symbols) || symbols.isEmpty() || !(symbols.get(0) instanceof Map<?, ?> symbolInfo)) {
            throw new IllegalStateException("Binance exchangeInfo did not contain symbol rules for " + symbol);
        }
        Object filtersValue = symbolInfo.get("filters");
        if (!(filtersValue instanceof List<?> filters)) {
            throw new IllegalStateException("Binance exchangeInfo did not contain filters for " + symbol);
        }

        BigDecimal minNotional = null;
        for (Object filterValue : filters) {
            if (!(filterValue instanceof Map<?, ?> filter)) continue;
            String filterType = String.valueOf(filter.get("filterType"));
            if (!"NOTIONAL".equals(filterType) && !"MIN_NOTIONAL".equals(filterType)) continue;
            Object raw = filter.get("minNotional");
            if (raw == null) continue;
            BigDecimal candidate = new BigDecimal(String.valueOf(raw));
            if (candidate.signum() <= 0) continue;
            if ("NOTIONAL".equals(filterType)) return candidate;
            minNotional = candidate;
        }
        if (minNotional != null) return minNotional;
        throw new IllegalStateException("Binance exchangeInfo did not expose a minimum notional for " + symbol);
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