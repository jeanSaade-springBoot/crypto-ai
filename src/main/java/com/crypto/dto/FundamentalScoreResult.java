package com.crypto.dto;

import java.util.List;

public record FundamentalScoreResult(
        int total,
        int maximum,
        List<FundamentalComponentScore> components,
        String riskLevel,
        FundamentalOwnershipDetails ownership
) {
}
