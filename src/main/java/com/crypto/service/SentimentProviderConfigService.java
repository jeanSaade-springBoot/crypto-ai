package com.crypto.service;

import com.crypto.config.SentimentProperties;
import com.crypto.domain.SentimentProviderConfig;
import com.crypto.dto.SentimentProviderUpdateRequest;
import com.crypto.repository.SentimentProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SentimentProviderConfigService implements CommandLineRunner {

    private final SentimentProviderConfigRepository repository;
    private final SentimentProperties properties;

    private static final Map<String, Seed> SEEDS = new LinkedHashMap<>();
    static {
        SEEDS.put("CRYPTOPANIC", new Seed("CryptoPanic", "CRYPTOPANIC_API_KEY", 300));
        SEEDS.put("NEWS_API", new Seed("NewsAPI", "NEWS_API_KEY", 300));
        SEEDS.put("REDDIT", new Seed("Reddit", "REDDIT_ACCESS_TOKEN", 600));
        SEEDS.put("X", new Seed("X / Twitter", "X_BEARER_TOKEN", 300));
        SEEDS.put("FEAR_GREED", new Seed("Fear & Greed", null, 900));
        SEEDS.put("BINANCE_ANNOUNCEMENT", new Seed("Binance Announcements", null, 300));
        SEEDS.put("WHALE_ALERT", new Seed("Whale Alert", "WHALE_ALERT_API_KEY", 300));
        SEEDS.put("MANUAL_NEWS", new Seed("Manual News", null, 300));
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (Map.Entry<String, Seed> entry : SEEDS.entrySet()) {
            String code = entry.getKey();
            if (repository.findByProviderCodeIgnoreCase(code).isPresent()) continue;
            String propertyKey = code.toLowerCase();
            SentimentProperties.Provider configured = properties.provider(propertyKey);
            Seed seed = entry.getValue();
            repository.save(SentimentProviderConfig.builder()
                    .providerCode(code)
                    .displayName(seed.displayName())
                    .enabled(configured.enabled())
                    .weight(configured.weight())
                    .collectionIntervalSeconds(seed.defaultIntervalSeconds())
                    .lastStatus("NEVER_RUN")
                    .apiKeyEnvVar(seed.apiKeyEnvVar())
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public List<SentimentProviderConfig> findAll() {
        return repository.findAllByOrderByDisplayNameAsc();
    }

    @Transactional(readOnly = true)
    public SentimentProviderConfig require(String provider) {
        return repository.findByProviderCodeIgnoreCase(SentimentProviderName.normalize(provider))
                .orElseThrow(() -> new IllegalArgumentException("Unknown sentiment provider: " + provider));
    }

    @Transactional
    public SentimentProviderConfig update(String provider, SentimentProviderUpdateRequest request) {
        SentimentProviderConfig config = require(provider);
        if (request.enabled() != null) config.setEnabled(request.enabled());
        if (request.weight() != null) config.setWeight(request.weight().max(BigDecimal.ZERO));
        if (request.collectionIntervalSeconds() != null) {
            config.setCollectionIntervalSeconds(Math.max(60, request.collectionIntervalSeconds()));
        }
        return repository.save(config);
    }

    @Transactional
    public void recordResult(String provider, String status, String message, boolean success) {
        SentimentProviderConfig config = require(provider);
        Instant now = Instant.now();
        config.setLastCollectionAt(now);
        if (success) config.setLastSuccessAt(now);
        config.setLastStatus(status);
        config.setLastMessage(message == null ? null : message.substring(0, Math.min(1000, message.length())));
        repository.save(config);
    }

    @Transactional(readOnly = true)
    public boolean enabled(String provider) {
        return properties.enabled() && require(provider).isEnabled();
    }


    public boolean apiKeyConfigured(String provider) {
        String key = properties.provider(SentimentProviderName.normalize(provider).toLowerCase()).apiKey();
        SentimentProviderConfig config = require(provider);
        return config.getApiKeyEnvVar() == null || (key != null && !key.isBlank());
    }

    public boolean masterEnabled() {
        return properties.enabled();
    }

    public ProviderHealth health(SentimentProviderConfig config, Instant now) {
        if (!properties.enabled() || !config.isEnabled()) {
            return new ProviderHealth("DISABLED", false, 0, "Provider is disabled");
        }
        Instant success = config.getLastSuccessAt();
        if (success == null) {
            String state = "NEVER_RUN".equals(config.getLastStatus()) ? "STALE" : "DOWN";
            return new ProviderHealth(state, false, -1,
                    "No successful collection has been recorded");
        }
        java.time.Duration age = java.time.Duration.between(success, now);
        long hours = Math.max(0, age.toHours());
        if (age.compareTo(properties.health().downAfter()) >= 0) {
            return new ProviderHealth("DOWN", false, hours,
                    "No successful collection for " + hours + " hours");
        }
        if (age.compareTo(properties.health().staleAfter()) >= 0) {
            return new ProviderHealth("STALE", false, hours,
                    "Latest successful data is " + hours + " hours old");
        }
        if ("FAILED".equals(config.getLastStatus())) {
            return new ProviderHealth("DEGRADED", true, hours,
                    "Latest attempt failed; recent successful data is still active");
        }
        return new ProviderHealth("HEALTHY", true, hours, "Provider is collecting normally");
    }

    public boolean contributes(SentimentProviderConfig config, Instant now) {
        return health(config, now).contributing();
    }

    public record ProviderHealth(String status, boolean contributing, long hoursSinceSuccess, String message) {}


    private record Seed(String displayName, String apiKeyEnvVar, long defaultIntervalSeconds) {}
}
