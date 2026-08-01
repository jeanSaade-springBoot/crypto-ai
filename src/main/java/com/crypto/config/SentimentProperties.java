package com.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "sentiment")
public record SentimentProperties(
        boolean enabled,
        Duration activeWindow,
        Scheduler scheduler,
        Health health,
        Map<String, Provider> providers
) {
    public SentimentProperties {
        activeWindow = activeWindow == null ? Duration.ofHours(24) : activeWindow;
        scheduler = scheduler == null ? Scheduler.defaults() : scheduler;
        health = health == null ? Health.defaults() : health;
        providers = providers == null ? new LinkedHashMap<>() : providers;
    }

    public Provider provider(String name) {
        if (name == null) {
            return Provider.defaults();
        }
        Provider provider = providers.get(name.toLowerCase());
        return provider == null ? Provider.defaults() : provider;
    }

    public boolean providerEnabled(String name) {
        return enabled && provider(name).enabled();
    }

    public record Scheduler(
            boolean enabled,
            long fixedDelayMs
    ) {
        public Scheduler {
            fixedDelayMs = fixedDelayMs <= 0 ? 300_000L : fixedDelayMs;
        }

        public static Scheduler defaults() {
            return new Scheduler(false, 300_000L);
        }
    }


    public record Health(
            Duration staleAfter,
            Duration downAfter
    ) {
        public Health {
            staleAfter = staleAfter == null ? Duration.ofHours(2) : staleAfter;
            downAfter = downAfter == null ? Duration.ofHours(6) : downAfter;
            if (downAfter.compareTo(staleAfter) < 0) downAfter = staleAfter;
        }

        public static Health defaults() {
            return new Health(Duration.ofHours(2), Duration.ofHours(6));
        }
    }

    public record Provider(
            boolean enabled,
            BigDecimal weight,
            String apiKey,
            String baseUrl
    ) {
        public Provider {
            weight = weight == null || weight.signum() < 0 ? BigDecimal.ZERO : weight;
        }

        public static Provider defaults() {
            return new Provider(false, BigDecimal.ONE, null, null);
        }
    }
}
