package com.crypto.dto;

import com.crypto.domain.SignalDecision;

public record StrategyScoreResult(
        int trendScore,
        int volumeScore,
        int momentumScore,
        int sentimentScore,
        int fundamentalScore,
        int rawScore,
        int maximumScore,
        int normalizedScore,
        SignalDecision decision
) {}
