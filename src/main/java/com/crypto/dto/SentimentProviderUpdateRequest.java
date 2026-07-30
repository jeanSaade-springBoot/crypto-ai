package com.crypto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record SentimentProviderUpdateRequest(
        Boolean enabled,
        @DecimalMin("0.0") BigDecimal weight,
        @Min(60) Long collectionIntervalSeconds
) {
}
