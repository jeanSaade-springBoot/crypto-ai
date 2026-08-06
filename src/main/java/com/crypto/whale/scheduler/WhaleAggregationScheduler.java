package com.crypto.whale.scheduler;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.whale.config.WhaleProperties;
import com.crypto.whale.service.WhaleAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WhaleAggregationScheduler {
    private final WhaleProperties properties;
    private final CoinConfigurationService coinConfigurationService;
    private final WhaleAggregationService service;

    @Scheduled(fixedDelayString = "${whale.aggregation.fixed-delay-ms:300000}")
    public void aggregate() {
        if (!properties.enabled()) return;
        for (String symbol : coinConfigurationService.enabledSymbols()) service.calculateAndSave(symbol);
    }
}
