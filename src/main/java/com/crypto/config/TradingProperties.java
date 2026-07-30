package com.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "trading")
public record TradingProperties(
        List<String> symbols,
        List<String> intervals,
        int candleLimit,
        int minimumBuyScore,
        BigDecimal riskPerTradePercent,
        BigDecimal maxDailyLossPercent,
        int maxOpenPositions,
        BigDecimal paperAccountBalance,
        boolean scheduledAnalysisEnabled,
        long analysisDelayMs
) {
    public TradingProperties {
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
        intervals = intervals == null || intervals.isEmpty()
                ? List.of("1m", "5m", "1h")
                : List.copyOf(intervals);
    }
}
