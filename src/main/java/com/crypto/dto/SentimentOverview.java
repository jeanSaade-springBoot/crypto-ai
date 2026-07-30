package com.crypto.dto;

import com.crypto.domain.SentimentSignal;

import java.math.BigDecimal;
import java.util.List;

public record SentimentOverview(
        String symbol,
        BigDecimal weightedScore,
        String label,
        int sampleCount,
        List<ProviderSentiment> providers,
        List<SentimentSignal> recentSignals
) {
}
