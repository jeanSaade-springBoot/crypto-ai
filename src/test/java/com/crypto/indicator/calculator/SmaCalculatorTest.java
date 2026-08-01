package com.crypto.indicator.calculator;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SmaCalculatorTest {
    private final SmaCalculator calculator = new SmaCalculator();

    @Test
    void calculatesUsingOnlyMostRecentPeriod() {
        List<BigDecimal> values = IntStream.rangeClosed(1, 25)
                .mapToObj(BigDecimal::valueOf).toList();
        assertEquals(new BigDecimal("15.500000000000"), calculator.calculate(values, 20));
    }

    @Test
    void returnsNullWhenHistoryIsInsufficient() {
        assertNull(calculator.calculate(List.of(BigDecimal.ONE), 20));
    }
}
