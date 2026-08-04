package com.crypto.dto;

import java.util.List;

public record TrendStructureResult(
        int score,
        int marketStructureScore,
        int pullbackQualityScore,
        int ema20RespectScore,
        int breakoutPreparationScore,
        int continuationScore,
        boolean higherHigh,
        boolean higherLow,
        boolean healthyPullback,
        boolean ema20Respected,
        boolean compressionDetected,
        boolean continuationSupported,
        String explanation,
        List<String> evidence
) {
    public static TrendStructureResult unavailable(String reason) {
        return new TrendStructureResult(
                0, 0, 0, 0, 0, 0,
                false, false, false, false, false, false,
                reason, List.of(reason)
        );
    }
}
