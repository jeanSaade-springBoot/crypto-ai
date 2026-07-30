package com.crypto.service;

import java.util.Locale;

public enum SentimentProviderName {
    CRYPTOPANIC,
    NEWS_API,
    REDDIT,
    X,
    FEAR_GREED,
    BINANCE_ANNOUNCEMENT,
    WHALE_ALERT,
    MANUAL_NEWS,
    MANUAL;

    public static String normalize(String source) {
        if (source == null || source.isBlank()) {
            return MANUAL.name();
        }
        String normalized = source.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (normalized.startsWith("CRYPTOPANIC")) return CRYPTOPANIC.name();
        if (normalized.startsWith("NEWSAPI") || normalized.startsWith("NEWS_API")) return NEWS_API.name();
        if (normalized.startsWith("REDDIT")) return REDDIT.name();
        if (normalized.equals("TWITTER") || normalized.startsWith("X_")) return X.name();
        if (normalized.startsWith("FEAR") || normalized.startsWith("ALTERNATIVE_ME")) return FEAR_GREED.name();
        if (normalized.startsWith("BINANCE")) return BINANCE_ANNOUNCEMENT.name();
        if (normalized.startsWith("WHALE")) return WHALE_ALERT.name();
        if (normalized.startsWith("MANUAL_NEWS")) return MANUAL_NEWS.name();
        return normalized;
    }
}
