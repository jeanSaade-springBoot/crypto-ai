package com.crypto.dto;

import com.crypto.domain.DecisionAdjustmentType;
import com.crypto.domain.SignalDecision;

public record DecisionAdjustment(
        int sequence,
        String source,
        DecisionAdjustmentType type,
        SignalDecision beforeDecision,
        SignalDecision afterDecision,
        boolean entryAllowedBefore,
        boolean entryAllowedAfter,
        String reason
) {
}
