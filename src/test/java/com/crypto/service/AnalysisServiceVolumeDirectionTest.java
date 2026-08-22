package com.crypto.service;

import com.crypto.config.AnalysisScoringProperties;
import com.crypto.dto.IndicatorSnapshot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisServiceVolumeDirectionTest {

    private final AnalysisService service = new AnalysisService(
            null, null, null, null, null, null, null, null,
            new AnalysisScoringProperties(null, null, null),
            null, null, null, null, null, null, null, null, null, null
    );

    @Test
    void highRvolOnFallingPriceDoesNotBecomeBullishVolumeConfirmation() throws Exception {
        IndicatorSnapshot previous = snapshot(
                "2026-08-08T14:50:00Z", "608.960000000000", "601.440500000000",
                "1.014381189386", "0.52387065"
        );
        IndicatorSnapshot current = snapshot(
                "2026-08-08T14:55:00Z", "603.280000000000", "601.803500000000",
                "0.448933719149", "5.58420364"
        );

        Object result = score(current, previous);

        // Price fell sharply from the previous close. The RVOL spike is therefore
        // not bullish participation and must not receive bullish RVOL/confirmation points.
        assertEquals(0, component(result, "relativeVolume"));
        assertEquals(0, component(result, "volumeSma20"));
        assertEquals(6, component(result, "bollinger"));
        assertEquals(6, component(result, "total"));
    }

    @Test
    void highRvolOnAdvancingPriceStillReceivesBullishVolumeCredit() throws Exception {
        IndicatorSnapshot previous = snapshot(
                "2026-08-08T13:50:00Z", "597.780000000000", "596.222500000000",
                "0.119662456024", "1.57446834"
        );
        IndicatorSnapshot current = snapshot(
                "2026-08-08T13:55:00Z", "598.180000000000", "596.336500000000",
                "0.181639964547", "4.09881017"
        );

        Object result = score(current, previous);

        assertEquals(8, component(result, "relativeVolume"));
        assertEquals(6, component(result, "volumeSma20"));
    }

    private Object score(IndicatorSnapshot current, IndicatorSnapshot previous) throws Exception {
        Method method = AnalysisService.class.getDeclaredMethod(
                "bandsVolumeScore", IndicatorSnapshot.class, IndicatorSnapshot.class);
        method.setAccessible(true);
        return method.invoke(service, current, previous);
    }

    private int component(Object result, String methodName) throws Exception {
        Method method = result.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (Integer) method.invoke(result);
    }

    private IndicatorSnapshot snapshot(
            String candleOpenTime,
            String price,
            String sma20,
            String macdHistogram,
            String relativeVolume
    ) {
        return new IndicatorSnapshot(
                "BNBUSDT",
                "5m",
                Instant.parse(candleOpenTime),
                new BigDecimal(price),
                new BigDecimal(sma20),
                new BigDecimal("603.249766356835"),
                new BigDecimal("599.585662847155"),
                new BigDecimal("595.444152197777"),
                new BigDecimal("53.51518116"),
                new BigDecimal("3.129456498940"),
                new BigDecimal("2.680522779791"),
                new BigDecimal(macdHistogram),
                new BigDecimal("601.803500000000"),
                new BigDecimal("612.660500092106"),
                new BigDecimal("590.946499907894"),
                new BigDecimal("3.60815452"),
                new BigDecimal("2.148441930020"),
                new BigDecimal("9387.091000000000"),
                new BigDecimal("1681.008000000000"),
                new BigDecimal(relativeVolume)
        );
    }
}
