package com.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "analysis.btc-context")
public record BtcContextProperties(
        boolean enabled,
        String referenceSymbol,
        int windowSize,
        int minimumSamples,
        BigDecimal moderateCorrelation,
        BigDecimal strongCorrelation,
        BigDecimal highBeta,
        boolean vetoStrongConflict
) {
    public BtcContextProperties {
        referenceSymbol = referenceSymbol == null || referenceSymbol.isBlank() ? "BTCUSDT" : referenceSymbol.trim().toUpperCase();
        windowSize = windowSize <= 0 ? 200 : windowSize;
        minimumSamples = minimumSamples <= 0 ? 120 : minimumSamples;
        moderateCorrelation = moderateCorrelation == null ? new BigDecimal("0.40") : moderateCorrelation.abs();
        strongCorrelation = strongCorrelation == null ? new BigDecimal("0.70") : strongCorrelation.abs();
        highBeta = highBeta == null ? new BigDecimal("1.30") : highBeta.abs();
    }
}
