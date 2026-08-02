package com.crypto.service;

import com.crypto.config.AnalysisScoringProperties;
import com.crypto.dto.IndicatorSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisServiceTrendPriceLocationTest {

    private final AnalysisService service = new AnalysisService(
            null, null, null, null, null, null, null, null,
            new AnalysisScoringProperties(null, null, null),
            null, null, null, null, null, null, null, null
    );

    @Test
    void awardsPartialEma200CreditForExactBnbSnapshot() {
        IndicatorSnapshot bnb = snapshot(
                "577.460000000000",
                "580.110500000000",
                "577.762195357511",
                "2.408987717247"
        );

        assertEquals(1, service.scoreTrendPriceLocation(bnb));
    }

    @Test
    void awardsNoEma200CreditWhenPriceIsMoreThanQuarterAtrBelow() {
        IndicatorSnapshot snapshot = snapshot("576.00", "580.00", "577.00", "2.00");

        assertEquals(0, service.scoreTrendPriceLocation(snapshot));
    }

    @Test
    void keepsFullEma200CreditWhenPriceIsAboveEma200() {
        IndicatorSnapshot snapshot = snapshot("578.00", "580.00", "577.00", "2.00");

        assertEquals(2, service.scoreTrendPriceLocation(snapshot));
    }

    private IndicatorSnapshot snapshot(
            String price,
            String sma20,
            String ema200,
            String atr14
    ) {
        return new IndicatorSnapshot(
                "BNBUSDT",
                "1h",
                Instant.parse("2026-08-02T01:00:00Z"),
                new BigDecimal(price),
                new BigDecimal(sma20),
                new BigDecimal("579.393873306152"),
                new BigDecimal("582.373974044408"),
                new BigDecimal(ema200),
                new BigDecimal("40.40681803"),
                new BigDecimal("-3.118603639844"),
                new BigDecimal("-2.999593550849"),
                new BigDecimal("-0.119010088995"),
                new BigDecimal("580.110500000000"),
                new BigDecimal("590.697277555045"),
                new BigDecimal("569.523722444955"),
                new BigDecimal("3.64991758"),
                new BigDecimal(atr14),
                new BigDecimal("3278.887000000000"),
                new BigDecimal("4649.400300000000"),
                new BigDecimal("0.70522794")
        );
    }
}
