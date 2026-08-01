package com.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "analysis.derivatives-positioning")
public record DerivativesPositioningProperties(
        boolean enabled,
        String baseUrl,
        int fundingHistoryLimit,
        int openInterestHistoryLimit,
        BigDecimal moderateFundingRate,
        BigDecimal extremeFundingRate,
        BigDecimal openInterestChangeThreshold,
        BigDecimal strongOpenInterestChangeThreshold,
        int minimumFundingSamples,
        boolean vetoExtremeCrowding
) {
    public DerivativesPositioningProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://fapi.binance.com" : baseUrl;
        fundingHistoryLimit = fundingHistoryLimit <= 0 ? 100 : Math.min(fundingHistoryLimit, 1000);
        openInterestHistoryLimit = openInterestHistoryLimit <= 1 ? 3 : Math.min(openInterestHistoryLimit, 30);
        moderateFundingRate = positive(moderateFundingRate, "0.0005");
        extremeFundingRate = positive(extremeFundingRate, "0.0010");
        openInterestChangeThreshold = positive(openInterestChangeThreshold, "2.0");
        strongOpenInterestChangeThreshold = positive(strongOpenInterestChangeThreshold, "5.0");
        minimumFundingSamples = minimumFundingSamples <= 0 ? 20 : minimumFundingSamples;
    }

    private static BigDecimal positive(BigDecimal value, String fallback) {
        return value == null || value.signum() <= 0 ? new BigDecimal(fallback) : value.abs();
    }
}
