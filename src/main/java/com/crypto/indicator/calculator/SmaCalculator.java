package com.crypto.indicator.calculator;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class SmaCalculator {

    private static final int SCALE = 12;

    public BigDecimal calculate(
            List<BigDecimal> values,
            int period
    ) {
        validatePeriod(period);

        if (values == null || values.size() < period) {
            return null;
        }

        int startIndex = values.size() - period;

        BigDecimal sum = BigDecimal.ZERO;

        for (int index = startIndex; index < values.size(); index++) {
            BigDecimal value = values.get(index);

            if (value == null) {
                throw new IllegalArgumentException(
                        "SMA values cannot contain null"
                );
            }

            sum = sum.add(value);
        }

        return sum.divide(
                BigDecimal.valueOf(period),
                SCALE,
                RoundingMode.HALF_UP
        );
    }

    private void validatePeriod(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException(
                    "SMA period must be greater than zero"
            );
        }
    }
}