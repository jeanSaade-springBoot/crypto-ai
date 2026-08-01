package com.crypto.service;

import com.crypto.domain.MarketFundamental;
import com.crypto.dto.FundamentalScoreResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FundamentalScoringServiceTest {

    private final FundamentalScoringService service = new FundamentalScoringService();

    @Test
    void healthyFdvAndSupplyShouldScoreStrongly() {
        MarketFundamental fundamental = MarketFundamental.builder()
                .marketCap(new BigDecimal("30000000000"))
                .fullyDilutedValuation(new BigDecimal("32000000000"))
                .volume24h(new BigDecimal("4000000000"))
                .circulatingSupply(new BigDecimal("900000000"))
                .maxSupply(new BigDecimal("1000000000"))
                .tier1ExchangeCount(3)
                .exchangeCount(8)
                .build();

        FundamentalScoreResult result = service.score(fundamental);

        assertEquals(10, result.total());
        assertEquals("LOW", result.riskLevel());
    }

    @Test
    void lowMarketCapToFdvRatioShouldExposeDilutionRisk() {
        MarketFundamental fundamental = MarketFundamental.builder()
                .marketCap(new BigDecimal("300000000"))
                .fullyDilutedValuation(new BigDecimal("5000000000"))
                .volume24h(new BigDecimal("3000000"))
                .circulatingSupply(new BigDecimal("100000000"))
                .maxSupply(new BigDecimal("1000000000"))
                .tier1ExchangeCount(0)
                .exchangeCount(1)
                .build();

        FundamentalScoreResult result = service.score(fundamental);

        assertEquals(0, result.total());
        assertEquals("HIGH", result.riskLevel());
    }

    @Test
    void missingFundamentalDataShouldRemainNeutral() {
        FundamentalScoreResult result = service.score(null);

        assertEquals(5, result.total());
        assertEquals("UNKNOWN", result.riskLevel());
    }
}
