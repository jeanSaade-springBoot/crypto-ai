package com.crypto.regression.dto;

import java.time.Instant;

/**
 * FIX-065: one persistent replay/investigation case. Times are UTC Instants at the API boundary;
 * the Administration UI accepts KSA timestamps and converts them before posting.
 */
public record RegressionInvestigationCaseRequest(
        String caseName,
        String symbol,
        Instant startTime,
        Instant endTime,
        Long walletId,
        String expectedAction,
        String notes
) {}
