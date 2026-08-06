package com.crypto.service;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.client.fundamental.CoinGeckoFundamentalClient;
import com.crypto.config.FundamentalCollectionProperties;
import com.crypto.dto.FundamentalRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundamentalCollectionService {

    private final CoinGeckoFundamentalClient client;
    private final FundamentalCollectionProperties properties;
    private final CoinConfigurationService coinConfigurationService;
    private final FundamentalService fundamentalService;

    @Scheduled(fixedDelayString = "${fundamentals.collection.fixed-delay:1h}")
    public void collectConfiguredSymbols() {
        if (!properties.enabled()) {
            return;
        }

        Map<String, String> idToSymbol = configuredCoinIds();
        if (idToSymbol.isEmpty()) {
            return;
        }

        try {
            List<Map<String, Object>> rows = client.markets(
                    List.copyOf(idToSymbol.keySet())
            );

            int saved = 0;
            for (Map<String, Object> row : rows) {
                String coinId = text(row, "id");
                String symbol = idToSymbol.get(coinId);

                if (symbol == null) {
                    log.warn(
                            "Ignoring CoinGecko response for unconfigured id {}",
                            coinId
                    );
                    continue;
                }

                fundamentalService.save(new FundamentalRequest(
                        symbol,
                        decimal(row, "market_cap"),
                        decimal(row, "fully_diluted_valuation"),
                        decimal(row, "total_volume"),
                        decimal(row, "circulating_supply"),
                        decimal(row, "total_supply"),
                        decimal(row, "max_supply"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
                saved++;
            }

            log.info(
                    "Collected fundamentals for {}/{} configured symbols",
                    saved,
                    idToSymbol.size()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Fundamental collection failed: {}",
                    exception.getMessage(),
                    exception
            );
        }
    }

    private Map<String, String> configuredCoinIds() {
        Map<String, String> idToSymbol = new LinkedHashMap<>();

        for (String configured : coinConfigurationService.enabledSymbols()) {
            String symbol = configured
                    .trim()
                    .toUpperCase(Locale.ROOT);
            String coinId = properties.coinIds().get(symbol);

            if (coinId == null || coinId.isBlank()) {
                log.warn(
                        "No CoinGecko id configured for {}; fundamentals remain unavailable",
                        symbol
                );
                continue;
            }

            idToSymbol.put(coinId, symbol);
        }

        return idToSymbol;
    }

    private String text(Map<String, Object> row, String field) {
        Object value = row.get(field);
        return value == null ? null : value.toString();
    }

    private BigDecimal decimal(Map<String, Object> row, String field) {
        Object value = row.get(field);

        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException exception) {
                log.warn(
                        "Ignoring non-numeric CoinGecko field {}={}",
                        field,
                        text
                );
            }
        }

        return null;
    }
}
