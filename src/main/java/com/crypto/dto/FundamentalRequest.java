package com.crypto.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record FundamentalRequest(
        @NotBlank String symbol,
        BigDecimal marketCap,
        BigDecimal fullyDilutedValuation,
        BigDecimal volume24h,
        BigDecimal circulatingSupply,
        BigDecimal totalSupply,
        BigDecimal maxSupply,
        Integer tier1ExchangeCount,
        Integer exchangeCount,
        BigDecimal teamSupply,
        BigDecimal treasurySupply,
        BigDecimal privateInvestorSupply,
        BigDecimal lockedSupply
) {}
