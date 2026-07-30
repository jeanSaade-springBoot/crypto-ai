package com.crypto.whale.scheduler;

import com.crypto.service.SentimentProviderConfigService;
import com.crypto.service.SentimentProviderName;
import com.crypto.whale.client.WhaleApiClient;
import com.crypto.whale.config.WhaleProperties;
import com.crypto.whale.service.WhaleTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhaleCollectionScheduler {
    private final WhaleProperties properties;
    private final WhaleApiClient apiClient;
    private final WhaleTransactionService transactionService;
    private final SentimentProviderConfigService providerConfigService;

    @Scheduled(fixedDelayString = "${whale.collection.fixed-delay-ms:300000}")
    public void collect() {
        if (!properties.enabled() || !providerConfigService.enabled(SentimentProviderName.WHALE_ALERT.name())) return;
        try {
            int inserted = apiClient.findRecentTransactions(Instant.now().minusSeconds(900)).stream()
                    .mapToInt(transactionService::process).sum();
            providerConfigService.recordResult(SentimentProviderName.WHALE_ALERT.name(), "SUCCESS",
                    "Saved " + inserted + " whale evaluation rows", true);
        } catch (RuntimeException ex) {
            log.error("Whale collection failed", ex);
            providerConfigService.recordResult(SentimentProviderName.WHALE_ALERT.name(), "FAILED", ex.getMessage(), false);
        }
    }
}
