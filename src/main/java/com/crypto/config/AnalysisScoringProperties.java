package com.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "analysis.scoring")
public record AnalysisScoringProperties(
        Trend trend,
        Momentum momentum,
        DataQuality dataQuality
) {
    public AnalysisScoringProperties {
        trend = trend == null ? Trend.defaults() : trend;
        momentum = momentum == null ? Momentum.defaults() : momentum;
        dataQuality = dataQuality == null ? DataQuality.defaults() : dataQuality;
    }

    public record Trend(
            BigDecimal emaGapVeryWeak,
            BigDecimal emaGapWeak,
            BigDecimal emaGapModerate,
            BigDecimal emaGapGood,
            BigDecimal emaGapStrong,
            BigDecimal emaGapOptimal,
            BigDecimal emaGapOverextended,
            BigDecimal priceEma200Weak,
            BigDecimal priceEma200Moderate,
            BigDecimal priceEma200Strong,
            BigDecimal priceEma200Optimal,
            BigDecimal priceEma200Overextended,
            BigDecimal sma20Weak,
            BigDecimal sma20Moderate,
            BigDecimal sma20Optimal,
            BigDecimal sma20Overextended,
            BigDecimal emaSeparationAtrWeak,
            BigDecimal emaSeparationAtrModerate,
            BigDecimal emaSeparationAtrStrong
    ) {
        static Trend defaults() {
            return new Trend(
                    bd("0.25"), bd("0.50"), bd("1.00"), bd("2.00"), bd("3.00"), bd("5.00"), bd("8.00"),
                    bd("1.00"), bd("3.00"), bd("6.00"), bd("12.00"), bd("20.00"),
                    bd("0.25"), bd("1.00"), bd("3.00"), bd("6.00"),
                    bd("0.50"), bd("1.00"), bd("3.00")
            );
        }
    }

    public record Momentum(
            BigDecimal rsiOversold,
            BigDecimal rsiWeak,
            BigDecimal rsiRecovering,
            BigDecimal rsiBullish,
            BigDecimal rsiStrong,
            BigDecimal rsiHot,
            BigDecimal rsiOverbought,
            BigDecimal macdAtrWeak,
            BigDecimal macdAtrModerate,
            BigDecimal macdAtrStrong
    ) {
        static Momentum defaults() {
            return new Momentum(
                    bd("30"), bd("40"), bd("45"), bd("50"), bd("60"), bd("68"), bd("72"),
                    bd("2"), bd("5"), bd("10")
            );
        }
    }

    public record DataQuality(int minimumCandles, int maximumMissingCandles, boolean blockOnInvalidOhlc) {
        static DataQuality defaults() {
            return new DataQuality(210, 0, true);
        }
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
