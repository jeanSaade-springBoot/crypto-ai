package com.crypto.service;

import com.crypto.domain.MarketRegime;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.dto.MarketRegimeAssessment;
import com.crypto.dto.TrendStructureResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketRegimeServiceTest {

    private final MarketRegimeService service = new MarketRegimeService();

    @Test
    void bullishBandAndRvolWithoutStructuralExpansionIsOnlyCandidate() {
        MarketRegimeAssessment result = service.assess(shibLikeIndicator(), structure(false, 0));

        assertThat(result.regime()).isEqualTo(MarketRegime.BREAKOUT_CANDIDATE);
        assertThat(result.evidence()).anyMatch(v -> v.contains("Structural bullish expansion is not confirmed"));
    }

    @Test
    void bullishBandAndRvolWithStructuralExpansionReceivesFullBreakout() {
        MarketRegimeAssessment result = service.assess(shibLikeIndicator(), structure(true, 1));

        assertThat(result.regime()).isEqualTo(MarketRegime.BREAKOUT);
        assertThat(result.evidence()).anyMatch(v -> v.contains("Trend structure confirms"));
    }

    private IndicatorSnapshot shibLikeIndicator() {
        return new IndicatorSnapshot(
                "SHIBUSDT", "1m", Instant.parse("2026-08-17T18:42:00Z"),
                new BigDecimal("0.000004490000"),
                new BigDecimal("0.000004479000"),
                new BigDecimal("0.000004479269"),
                new BigDecimal("0.000004474392"),
                new BigDecimal("0.000004471220"),
                new BigDecimal("59.96487166"),
                new BigDecimal("0.000000003586"),
                new BigDecimal("0.000000002899"),
                new BigDecimal("0.000000000687"),
                new BigDecimal("0.000004479000"),
                new BigDecimal("0.000004489770"),
                new BigDecimal("0.000004468230"),
                new BigDecimal("0.48091092"),
                new BigDecimal("0.000000006826"),
                new BigDecimal("508275618"),
                new BigDecimal("150495451.4"),
                new BigDecimal("3.37734871")
        );
    }

    private TrendStructureResult structure(boolean confirmed, int breakoutPreparation) {
        return new TrendStructureResult(
                4, 2, 0, 1, breakoutPreparation, 1,
                true, true, false, true, false, confirmed, true,
                "test", List.of("test")
        );
    }
}
