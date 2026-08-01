package com.crypto.dto;

import com.crypto.domain.ConfluenceStatus;
import com.crypto.domain.SignalDecision;

import java.time.Instant;
import java.util.List;

public record MultiTimeframeConfluenceResult(
        ConfluenceStatus status,
        SignalDecision originalDecision,
        SignalDecision finalDecision,
        boolean entryAllowed,
        String higherInterval,
        SignalDecision higherTimeframeDecision,
        Integer higherTimeframeTrendScore,
        Instant evaluatedAt,
        Instant higherSignalGeneratedAt,
        List<String> reasons
) {
    public String explanation() {
        return reasons == null || reasons.isEmpty()
                ? "No higher-timeframe context was available."
                : String.join(" ", reasons);
    }
}
