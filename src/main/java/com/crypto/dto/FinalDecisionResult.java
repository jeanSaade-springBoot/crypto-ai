package com.crypto.dto;

import com.crypto.domain.SignalDecision;

import java.util.List;

public record FinalDecisionResult(
        SignalDecision baseDecision,
        SignalDecision finalDecision,
        boolean entryAllowed,
        int confidenceScore,
        List<DecisionAdjustment> adjustments,
        String explanation
) {
}
