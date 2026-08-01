package com.crypto.client.binance;

import com.crypto.client.binance.dto.BinanceFundingRate;
import com.crypto.client.binance.dto.BinanceOpenInterestPoint;
import com.crypto.config.DerivativesPositioningProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class BinanceFuturesMarketDataClient {
    private final RestClient restClient;

    public BinanceFuturesMarketDataClient(RestClient.Builder builder, DerivativesPositioningProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    public List<BinanceFundingRate> fundingHistory(String symbol, int limit) {
        List<BinanceFundingRate> result = restClient.get()
                .uri(uri -> uri.path("/fapi/v1/fundingRate")
                        .queryParam("symbol", symbol.toUpperCase())
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    public List<BinanceOpenInterestPoint> openInterestHistory(String symbol, String period, int limit) {
        List<BinanceOpenInterestPoint> result = restClient.get()
                .uri(uri -> uri.path("/futures/data/openInterestHist")
                        .queryParam("symbol", symbol.toUpperCase())
                        .queryParam("period", period)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }
}
