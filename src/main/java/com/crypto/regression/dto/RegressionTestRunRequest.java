package com.crypto.regression.dto;

import java.time.Instant;

public record RegressionTestRunRequest(
        String testName,
        String symbol,
        Instant startTime,
        Instant endTime,
        Boolean fix122Enabled
) {
    public RegressionTestRunRequest(String testName, String symbol, Instant startTime, Instant endTime) {
        this(testName,symbol,startTime,endTime,true);
    }
}
