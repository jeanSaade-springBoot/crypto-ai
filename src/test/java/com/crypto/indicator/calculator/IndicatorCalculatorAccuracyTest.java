package com.crypto.indicator.calculator;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IndicatorCalculatorAccuracyTest {
    @Test
    void emaOfConstantSeriesEqualsConstant() {
        EmaCalculator calculator = new EmaCalculator();
        List<BigDecimal> values = IntStream.range(0, 250)
                .mapToObj(i -> new BigDecimal("100"))
                .toList();
        assertEquals(new BigDecimal("100.000000000000"), calculator.calculate(values, 200));
    }

    @Test
    void rsiOfOnlyGainsIsOneHundred() {
        RsiCalculator calculator = new RsiCalculator();
        List<BigDecimal> values = IntStream.rangeClosed(1, 30)
                .mapToObj(BigDecimal::valueOf).toList();
        assertEquals(new BigDecimal("100.00000000"), calculator.calculate(values, 14));
    }
}
