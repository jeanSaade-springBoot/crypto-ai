package com.crypto.whale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "whale")
public record WhaleProperties(
        boolean enabled,
        Api api,
        Collection collection,
        Evaluation evaluation,
        Learning learning,
        Aggregation aggregation
) {
    public WhaleProperties {
        api = api == null ? Api.defaults() : api;
        collection = collection == null ? Collection.defaults() : collection;
        evaluation = evaluation == null ? Evaluation.defaults() : evaluation;
        learning = learning == null ? Learning.defaults() : learning;
        aggregation = aggregation == null ? Aggregation.defaults() : aggregation;
    }

    public record Api(String baseUrl, String apiKey, String transactionsPath) {
        public static Api defaults() { return new Api("https://api.whale-alert.io/v1", "", "/transactions"); }
    }

    public record Collection(long fixedDelayMs, BigDecimal minimumUsdValue, int limit) {
        public Collection {
            fixedDelayMs = fixedDelayMs <= 0 ? 300_000L : fixedDelayMs;
            minimumUsdValue = minimumUsdValue == null ? new BigDecimal("1000000") : minimumUsdValue;
            limit = limit <= 0 ? 100 : Math.min(limit, 1000);
        }
        public static Collection defaults() { return new Collection(300_000L, new BigDecimal("1000000"), 100); }
    }

    public record Evaluation(long fixedDelayMs, String priceInterval, List<String> horizons, BigDecimal inconclusiveMovePercent) {
        public Evaluation {
            fixedDelayMs = fixedDelayMs <= 0 ? 60_000L : fixedDelayMs;
            priceInterval = priceInterval == null || priceInterval.isBlank() ? "1m" : priceInterval;
            horizons = horizons == null || horizons.isEmpty() ? List.of("1h", "4h", "24h") : List.copyOf(horizons);
            inconclusiveMovePercent = inconclusiveMovePercent == null ? new BigDecimal("0.003") : inconclusiveMovePercent;
        }
        public static Evaluation defaults() { return new Evaluation(60_000L, "1m", List.of("1h", "4h", "24h"), new BigDecimal("0.003")); }
    }

    public record Learning(BigDecimal initialWeight, BigDecimal minimumWeight, BigDecimal maximumWeight,
                           BigDecimal smoothingFactor, int priorSampleSize) {
        public Learning {
            initialWeight = initialWeight == null ? new BigDecimal("0.15") : initialWeight;
            minimumWeight = minimumWeight == null ? new BigDecimal("0.05") : minimumWeight;
            maximumWeight = maximumWeight == null ? BigDecimal.ONE : maximumWeight;
            smoothingFactor = smoothingFactor == null ? new BigDecimal("0.20") : smoothingFactor;
            priorSampleSize = priorSampleSize <= 0 ? 20 : priorSampleSize;
        }
        public static Learning defaults() { return new Learning(new BigDecimal("0.15"), new BigDecimal("0.05"), BigDecimal.ONE, new BigDecimal("0.20"), 20); }
    }

    public record Aggregation(long fixedDelayMs, Duration activeWindow, String horizon, BigDecimal providerConfidenceFloor) {
        public Aggregation {
            fixedDelayMs = fixedDelayMs <= 0 ? 300_000L : fixedDelayMs;
            activeWindow = activeWindow == null ? Duration.ofHours(2) : activeWindow;
            horizon = horizon == null || horizon.isBlank() ? "4h" : horizon;
            providerConfidenceFloor = providerConfidenceFloor == null ? new BigDecimal("0.20") : providerConfidenceFloor;
        }
        public static Aggregation defaults() { return new Aggregation(300_000L, Duration.ofHours(2), "4h", new BigDecimal("0.20")); }
    }
}
