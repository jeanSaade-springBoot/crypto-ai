package com.crypto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SentimentTextRequest(
        @NotBlank String symbol,
        @NotBlank @Size(max = 30) String source,
        @NotBlank @Size(max = 4000) String text
) {
}
