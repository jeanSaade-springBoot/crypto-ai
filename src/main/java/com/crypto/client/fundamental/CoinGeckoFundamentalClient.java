package com.crypto.client.fundamental;

import com.crypto.config.FundamentalCollectionProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class CoinGeckoFundamentalClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> MARKET_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final FundamentalCollectionProperties properties;

    public CoinGeckoFundamentalClient(
            RestClient.Builder builder,
            FundamentalCollectionProperties properties
    ) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .build();
    }

    public List<Map<String, Object>> markets(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> response = restClient.get()
                .uri(uri -> uri
                        .path("/coins/markets")
                        .queryParam("vs_currency", properties.vsCurrency())
                        .queryParam("ids", String.join(",", ids))
                        .queryParam("sparkline", false)
                        .build())
                .retrieve()
                .body(MARKET_LIST_TYPE);

        return response == null ? List.of() : List.copyOf(response);
    }
}
