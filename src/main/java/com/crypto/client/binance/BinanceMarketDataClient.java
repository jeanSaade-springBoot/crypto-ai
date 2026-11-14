package com.crypto.client.binance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.client.binance.dto.BinanceKline;
import com.crypto.client.binance.dto.BinanceOrderBook;

@Component
public class BinanceMarketDataClient {

    private final RestClient restClient;
    private final RestClient timeClient;
    private final RestClient reconciliationClient;
    private final BinanceMarketDataProperties properties;
    private final BinanceRestAdmissionGate admissionGate;

    public BinanceMarketDataClient(
        RestClient.Builder restClientBuilder,
        BinanceMarketDataProperties properties,
        BinanceRestAdmissionGate admissionGate
    ) {
        this.properties = properties;
        this.admissionGate = admissionGate;
        int requestTimeoutMs=Math.max(1_000,properties.getRequestTimeoutSeconds()*1_000);
        var marketFactory=new SimpleClientHttpRequestFactory();
        marketFactory.setConnectTimeout(requestTimeoutMs);marketFactory.setReadTimeout(requestTimeoutMs);
        this.restClient = restClientBuilder
            .baseUrl(properties.getBaseUrl()).requestFactory(marketFactory)
            .build();
        // Clock sampling must not delay application startup for the general
        // historical-request timeout. This client is used only for /api/v3/time.
        int clockTimeoutMs=Math.max(1_000,Math.min(5_000,properties.getRequestTimeoutSeconds()*1_000));
        var clockFactory=new SimpleClientHttpRequestFactory();
        clockFactory.setConnectTimeout(clockTimeoutMs);clockFactory.setReadTimeout(clockTimeoutMs);
        this.timeClient=restClientBuilder.baseUrl(properties.getBaseUrl()).requestFactory(clockFactory).build();
        // Recovery is bounded by the drain window.  It must not spend the
        // general 30-second historical-request timeout per candidate.
        var recoveryFactory=new SimpleClientHttpRequestFactory();
        recoveryFactory.setConnectTimeout(clockTimeoutMs);recoveryFactory.setReadTimeout(clockTimeoutMs);
        this.reconciliationClient=restClientBuilder.baseUrl(properties.getBaseUrl()).requestFactory(recoveryFactory).build();
    }

    public List<BinanceKline> getKlines(
        String symbol,
        String interval,
        int limit
    ) {
        validateRequest(symbol, interval, limit);

        List<List<Object>> response = request(() -> restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v3/klines")
                .queryParam("symbol", symbol.toUpperCase())
                .queryParam("interval", interval)
                .queryParam("limit", limit)
                .build())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            }));

        if (response == null || response.isEmpty()) {
            return List.of();
        }

        List<BinanceKline> candles = new ArrayList<>(response.size());

        for (List<Object> row : response) {
            candles.add(mapKline(row));
        }

        return candles;
    }

    /** Exact historical lookup used only by the local reconciliation fallback.
     * The kline payload does not carry symbol/interval, so caller context is
     * authoritative and returned open/close timestamps are validated by the
     * reconciliation service. */
    public List<BinanceKline> getKlines(String symbol, String interval, Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null || endTime.isBefore(startTime))
            throw new IllegalArgumentException("Valid kline time range is required");
        validateRequest(symbol, interval, 2);
        List<List<Object>> response = request(() -> reconciliationClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/v3/klines")
                .queryParam("symbol", symbol.toUpperCase())
                .queryParam("interval", interval)
                .queryParam("startTime", startTime.toEpochMilli())
                .queryParam("endTime", endTime.toEpochMilli())
                .queryParam("limit", 2).build())
            .retrieve().body(new ParameterizedTypeReference<>() {}));
        if (response == null || response.isEmpty()) return List.of();
        List<BinanceKline> candles = new ArrayList<>(response.size());
        for (List<Object> row : response) candles.add(mapKline(row));
        return candles;
    }

    /** Bounded probe used only to distinguish a pre-listing identity from a
     * transient empty exact-range response. */
    public List<BinanceKline> getKlinesAtOrAfter(String symbol, String interval, Instant startTime) {
        if(startTime==null)throw new IllegalArgumentException("startTime is required");
        validateRequest(symbol,interval,1);
        List<List<Object>> response=request(()->reconciliationClient.get().uri(uriBuilder->uriBuilder.path("/api/v3/klines")
            .queryParam("symbol",symbol.toUpperCase()).queryParam("interval",interval)
            .queryParam("startTime",startTime.toEpochMilli()).queryParam("limit",1).build())
            .retrieve().body(new ParameterizedTypeReference<>() {}));
        if(response==null||response.isEmpty())return List.of();
        return response.stream().map(this::mapKline).toList();
    }

    /** Exchange time is required when deciding whether a REST kline is final. */
    public Instant getServerTime() {
        Map<String,Object> response=request(()->timeClient.get().uri("/api/v3/time").retrieve()
            .body(new ParameterizedTypeReference<>() {}));
        if(response==null || !(response.get("serverTime") instanceof Number serverTime))
            throw new IllegalStateException("Binance time response did not contain serverTime");
        return Instant.ofEpochMilli(serverTime.longValue());
    }

    public BinanceOrderBook getOrderBook(String symbol, int limit) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Depth limit must be between 1 and 1000");
        }

        BinanceOrderBook response = request(() -> restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v3/depth")
                .queryParam("symbol", symbol.toUpperCase())
                .queryParam("limit", limit)
                .build())
            .retrieve()
            .body(BinanceOrderBook.class));

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

        Map<String, Object> response = request(() -> restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v3/exchangeInfo")
                .queryParam("symbol", symbol.toUpperCase())
                .build())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            }));

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

    private <T> T request(Supplier<T> call) {
        admissionGate.admit();
        try { return call.get(); }
        catch (RuntimeException failure) { admissionGate.recordFailure(failure); throw failure; }
    }
}
