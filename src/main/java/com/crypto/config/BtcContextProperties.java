package com.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

@ConfigurationProperties(prefix = "analysis.btc-context")
public record BtcContextProperties(
        boolean enabled,
        String referenceSymbol,
        int windowSize,
        int minimumSamples,
        BigDecimal moderateCorrelation,
        BigDecimal strongCorrelation,
        BigDecimal highBeta,
        boolean vetoStrongConflict,
        // FIX-141: max age (seconds) a BTC context candle may be before it is STALE_CONTEXT,
        // keyed by interval code. No equivalent existed before this fix - there was no
        // freshness check anywhere in BtcMarketContextService. Initial values are the
        // design's proposed defaults and are subject to explicit team review.
        Map<String, Long> freshnessMaxAgeSeconds,
        // FIX-141: whether a fresh snapshot with insufficient correlation samples (LEARNING)
        // should still block entries. Default false preserves today's pass-through behavior
        // pending the team's explicit decision (FIX-141 design, section 2, item 2).
        boolean blockLearningContext
) {
    private static final Map<String, Long> DEFAULT_FRESHNESS_MAX_AGE_SECONDS = Map.of(
            "1m", 120L,
            "5m", 600L,
            "15m", 1800L,
            "1h", 7200L,
            "4h", 28800L,
            "1d", 172800L
    );

    public BtcContextProperties {
        referenceSymbol = referenceSymbol == null || referenceSymbol.isBlank() ? "BTCUSDT" : referenceSymbol.trim().toUpperCase();
        windowSize = windowSize <= 0 ? 200 : windowSize;
        minimumSamples = minimumSamples <= 0 ? 120 : minimumSamples;
        moderateCorrelation = moderateCorrelation == null ? new BigDecimal("0.40") : moderateCorrelation.abs();
        strongCorrelation = strongCorrelation == null ? new BigDecimal("0.70") : strongCorrelation.abs();
        highBeta = highBeta == null ? new BigDecimal("1.30") : highBeta.abs();
        freshnessMaxAgeSeconds = (freshnessMaxAgeSeconds == null || freshnessMaxAgeSeconds.isEmpty())
                ? DEFAULT_FRESHNESS_MAX_AGE_SECONDS
                : Map.copyOf(freshnessMaxAgeSeconds);
    }

    /** Null means the interval is unsupported - callers must treat that as invalid, never as "no limit." */
    public Long freshnessMaxAgeSecondsFor(String interval) {
        return freshnessMaxAgeSeconds.get(interval);
    }
}
