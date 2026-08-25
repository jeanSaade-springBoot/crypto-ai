package com.crypto.dto;

import com.crypto.domain.SignalDecision;

import java.util.List;

/**
 * FIX-091 / Fix 3: preserve the real computed confidence separately from the
 * effective blocked confidence shown to execution/UI consumers. The compatibility
 * confidenceScore() accessor intentionally returns effective confidence so existing
 * callers keep today's behavior while diagnostics gain the raw value and blocker.
 */
public record FinalDecisionResult(
        SignalDecision baseDecision,
        SignalDecision finalDecision,
        boolean entryAllowed,
        int rawConfidenceScore,
        int effectiveConfidenceScore,
        String primaryBlockingStage,
        List<DecisionAdjustment> adjustments,
        String explanation
) {
    public int confidenceScore() {
        return effectiveConfidenceScore;
    }
}
