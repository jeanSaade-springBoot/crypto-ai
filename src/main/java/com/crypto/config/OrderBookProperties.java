package com.crypto.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis.order-book")
public record OrderBookProperties(
        boolean enabled,
        int depthLimit,
        BigDecimal rangePercent,
        long snapshotIntervalMs,
        int historySize,
        int minimumObservations,
        BigDecimal moderateImbalance,
        BigDecimal strongImbalance,
        BigDecimal wallSizeMultiplier,
        BigDecimal wallPriceTolerancePercent,
        boolean vetoStrongConflict,
        Map<String, IntervalPolicy> intervals
) {
    public OrderBookProperties {
        depthLimit = depthLimit <= 0 ? 100 : Math.min(depthLimit, 1000);
        rangePercent = positiveOrDefault(rangePercent, new BigDecimal("2.0"));
        snapshotIntervalMs = snapshotIntervalMs <= 0 ? 5000L : snapshotIntervalMs;
        historySize = historySize <= 0 ? 1000 : historySize;
        minimumObservations = minimumObservations <= 0 ? 5 : minimumObservations;
        moderateImbalance = positiveOrDefault(moderateImbalance, new BigDecimal("0.20"));
        strongImbalance = positiveOrDefault(strongImbalance, new BigDecimal("0.40"));
        wallSizeMultiplier = positiveOrDefault(wallSizeMultiplier, new BigDecimal("4.0"));
        wallPriceTolerancePercent = positiveOrDefault(wallPriceTolerancePercent, new BigDecimal("0.15"));
        intervals = intervals == null || intervals.isEmpty()
                ? defaultPolicies()
                : Map.copyOf(intervals);
    }

    public IntervalPolicy policyFor(String interval) {
        IntervalPolicy policy = intervals.get(interval);
        if (policy != null) {
            return policy.normalized(minimumObservations);
        }
        return new IntervalPolicy(300L, minimumObservations, 60L, BigDecimal.ONE, false)
                .normalized(minimumObservations);
    }

    public long maximumWindowSeconds() {
        return intervals.values().stream()
                .map(IntervalPolicy::windowSeconds)
                .filter(value -> value != null && value > 0)
                .mapToLong(Long::longValue)
                .max()
                .orElse(3600L);
    }

    private static Map<String, IntervalPolicy> defaultPolicies() {
        Map<String, IntervalPolicy> defaults = new LinkedHashMap<>();
        defaults.put("1m", new IntervalPolicy(60L, 6, 20L, new BigDecimal("1.00"), true));
        defaults.put("5m", new IntervalPolicy(300L, 24, 60L, new BigDecimal("0.80"), true));
        defaults.put("15m", new IntervalPolicy(900L, 60, 180L, new BigDecimal("0.60"), true));
        defaults.put("1h", new IntervalPolicy(3600L, 120, 900L, new BigDecimal("0.30"), false));
        defaults.put("4h", new IntervalPolicy(14400L, 180, 1800L, new BigDecimal("0.15"), false));
        defaults.put("1d", new IntervalPolicy(21600L, 180, 3600L, BigDecimal.ZERO, false));
        return Map.copyOf(defaults);
    }

    private static BigDecimal positiveOrDefault(BigDecimal value, BigDecimal fallback) {
        return value == null || value.signum() <= 0 ? fallback : value;
    }

    public record IntervalPolicy(
            Long windowSeconds,
            Integer minimumObservations,
            Long minimumWallPersistenceSeconds,
            BigDecimal influence,
            Boolean allowVeto
    ) {
        public IntervalPolicy normalized(int defaultMinimumObservations) {
            long normalizedWindow = windowSeconds == null || windowSeconds <= 0 ? 300L : windowSeconds;
            int normalizedObservations = minimumObservations == null || minimumObservations <= 0
                    ? defaultMinimumObservations : minimumObservations;
            long normalizedPersistence = minimumWallPersistenceSeconds == null
                    || minimumWallPersistenceSeconds <= 0 ? 60L : minimumWallPersistenceSeconds;
            BigDecimal normalizedInfluence = influence == null ? BigDecimal.ONE
                    : influence.max(BigDecimal.ZERO).min(BigDecimal.ONE);
            return new IntervalPolicy(normalizedWindow, normalizedObservations,
                    normalizedPersistence, normalizedInfluence, Boolean.TRUE.equals(allowVeto));
        }
    }
}
