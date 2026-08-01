package com.crypto.dto;

import java.math.BigDecimal;

/**
 * Explainability-only ownership snapshot. These values do not replace or
 * double-count the existing 10-point fundamental score.
 */
public record FundamentalOwnershipDetails(
        BigDecimal circulatingSupply,
        BigDecimal referenceSupply,
        BigDecimal nonCirculatingSupply,
        BigDecimal teamSupply,
        BigDecimal treasurySupply,
        BigDecimal privateInvestorSupply,
        BigDecimal lockedSupply,
        BigDecimal knownCompanyControlledSupply,
        BigDecimal publicCirculatingRatio,
        BigDecimal knownCompanyControlledRatio,
        String referenceLabel,
        String status
) {
}
