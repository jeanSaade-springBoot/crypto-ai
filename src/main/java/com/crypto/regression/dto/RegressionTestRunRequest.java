package com.crypto.regression.dto;

import java.time.Instant;

public record RegressionTestRunRequest(
        String testName,
        String symbol,
        Instant startTime,
        Instant endTime
) {
}
