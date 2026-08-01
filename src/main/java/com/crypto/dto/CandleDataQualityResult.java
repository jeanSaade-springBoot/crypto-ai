package com.crypto.dto;

import java.util.List;

public record CandleDataQualityResult(
        boolean valid,
        int expectedCandles,
        int actualCandles,
        int missingCandles,
        int invalidCandles,
        List<String> warnings
) {
}
