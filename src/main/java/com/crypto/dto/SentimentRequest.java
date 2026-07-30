package com.crypto.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SentimentRequest(
        @NotBlank String symbol,
        @NotNull @DecimalMin("-1.0") @DecimalMax("1.0") BigDecimal score,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
        @NotBlank String source,
        @Size(max = 1000) String summary
) {}
