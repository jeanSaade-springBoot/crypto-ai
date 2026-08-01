package com.crypto.dto;

import com.crypto.domain.TradingStrategy;

public record StrategyProfile(
        TradingStrategy strategy,
        String version,
        int trendMaximum,
        int volumeMaximum,
        int momentumMaximum,
        int sentimentMaximum,
        int fundamentalMaximum,
        int strongBuyThreshold,
        int buyThreshold,
        int watchThreshold,
        int neutralThreshold,
        int sellThreshold,
        boolean entryAllowed,
        String explanation
) {
    public int maximum(boolean sentimentEnabled) {
        return trendMaximum + volumeMaximum + momentumMaximum
                + fundamentalMaximum + (sentimentEnabled ? sentimentMaximum : 0);
    }
}
