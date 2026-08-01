package com.crypto.dto;

import com.crypto.domain.MarketRegime;

import java.util.List;

public record MarketRegimeAssessment(
        MarketRegime regime,
        int confidence,
        List<String> evidence
) {
    public MarketRegimeAssessment {
        confidence = Math.max(0, Math.min(100, confidence));
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
