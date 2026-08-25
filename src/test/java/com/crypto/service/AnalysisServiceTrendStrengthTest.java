package com.crypto.service;

import com.crypto.config.AnalysisScoringProperties;
import com.crypto.dto.IndicatorSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisServiceTrendStrengthTest {

    private final AnalysisService service = new AnalysisService(
            null, null, null, null, null, null, null, null,
            new AnalysisScoringProperties(null, null, null),
            // FIX-091: RegimeStateService and EntryAuthorityService were added to AnalysisService.
            // These focused scoring tests do not exercise either dependency, so keep them null.
            null, null,
            null, null, null, null, null, null, null, null, null, null
    );

    @Test
    void givesOnlyEarlyWarningCreditForBnbOneHourRecoveryCandle() {
        IndicatorSnapshot previous = snapshot(
                "2026-08-02T00:00:00Z",
                "579.597438917326",
                "582.574539128656",
                "-0.401548184002"
        );
        IndicatorSnapshot current = snapshot(
                "2026-08-02T01:00:00Z",
                "579.393873306152",
                "582.373974044408",
                "-0.119010088995"
        );

        // Histogram improved, but the EMA gap was not yet narrowing and both EMAs still fell.
        assertEquals(1, service.scoreTrendStrength(current, previous));
    }

    @Test
    void recognizesConfirmedBnbTransitionAfterNextClosedHour() {
        IndicatorSnapshot previous = snapshot(
                "2026-08-02T01:00:00Z",
                "579.393873306152",
                "582.373974044408",
                "-0.119010088995"
        );
        IndicatorSnapshot current = snapshot(
                "2026-08-02T02:00:00Z",
                "579.575409181756",
                "582.331863567508",
                "0.336724571748"
        );

        // Gap narrowing (2), EMA20 rising (1), histogram improving (1), histogram positive (1).
        assertEquals(5, service.scoreTrendStrength(current, previous));
    }

    @Test
    void givesNoTransitionCreditWhenBearishTrendKeepsDeteriorating() {
        IndicatorSnapshot previous = snapshot(
                "2026-08-01T23:00:00Z",
                "580.130853540202",
                "582.902885211009",
                "-0.477215881524"
        );
        IndicatorSnapshot current = snapshot(
                "2026-08-02T00:00:00Z",
                "579.597438917326",
                "582.574539128656",
                "-0.401548184002"
        );

        // Histogram improved, but the EMA gap widened and both EMAs fell.
        assertEquals(1, service.scoreTrendStrength(current, previous));
    }

    private IndicatorSnapshot snapshot(
            String candleOpenTime,
            String ema20,
            String ema50,
            String macdHistogram
    ) {
        return new IndicatorSnapshot(
                "BNBUSDT",
                "1h",
                Instant.parse(candleOpenTime),
                new BigDecimal("577.460000000000"),
                new BigDecimal("580.110500000000"),
                new BigDecimal(ema20),
                new BigDecimal(ema50),
                new BigDecimal("577.762195357511"),
                new BigDecimal("40.40681803"),
                new BigDecimal("-3.118603639844"),
                new BigDecimal("-2.999593550849"),
                new BigDecimal(macdHistogram),
                new BigDecimal("580.110500000000"),
                new BigDecimal("590.697277555045"),
                new BigDecimal("569.523722444955"),
                new BigDecimal("3.64991758"),
                new BigDecimal("2.408987717247"),
                new BigDecimal("3278.887000000000"),
                new BigDecimal("4649.400300000000"),
                new BigDecimal("0.70522794")
        );
    }
}
