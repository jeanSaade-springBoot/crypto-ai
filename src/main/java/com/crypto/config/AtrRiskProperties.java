package com.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.atr-risk")
public record AtrRiskProperties(
        int period,
        BigDecimal stopLossMultiplier,
        BigDecimal takeProfitMultiplier,
        BigDecimal lowVolatilityPercent,
        BigDecimal highVolatilityPercent,
        BigDecimal extremeVolatilityPercent,
        BigDecimal overextensionMultiplier,
        BigDecimal minimumStopPercent,
        BigDecimal maximumStopPercent,
        BigDecimal reducedPositionMultiplier,
        BigDecimal pullbackEntryMultiplier,
        BigDecimal waitForRetracementMultiplier,
        BigDecimal hardVetoMultiplier,
        int reducedPositionPercent
) {
    public AtrRiskProperties {
        period = period <= 0 ? 14 : period;
        stopLossMultiplier = positiveOrDefault(stopLossMultiplier, "2.0");
        takeProfitMultiplier = positiveOrDefault(takeProfitMultiplier, "3.0");
        lowVolatilityPercent = positiveOrDefault(lowVolatilityPercent, "0.50");
        highVolatilityPercent = positiveOrDefault(highVolatilityPercent, "2.50");
        extremeVolatilityPercent = positiveOrDefault(extremeVolatilityPercent, "5.00");
        overextensionMultiplier = positiveOrDefault(overextensionMultiplier, "2.50");
        minimumStopPercent = positiveOrDefault(minimumStopPercent, "0.50");
        maximumStopPercent = positiveOrDefault(maximumStopPercent, "8.00");
        reducedPositionMultiplier = positiveOrDefault(reducedPositionMultiplier, "2.00");
        pullbackEntryMultiplier = positiveOrDefault(pullbackEntryMultiplier, "2.75");
        waitForRetracementMultiplier = positiveOrDefault(waitForRetracementMultiplier, "3.50");
        hardVetoMultiplier = positiveOrDefault(hardVetoMultiplier, "4.50");
        reducedPositionPercent = reducedPositionPercent <= 0 || reducedPositionPercent > 100
                ? 60 : reducedPositionPercent;
    }

    private static BigDecimal positiveOrDefault(BigDecimal value, String fallback) {
        return value == null || value.signum() <= 0 ? new BigDecimal(fallback) : value;
    }
}
