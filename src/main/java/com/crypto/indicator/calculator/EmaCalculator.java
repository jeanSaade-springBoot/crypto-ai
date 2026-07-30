package com.crypto.indicator.calculator;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

@Component
public class EmaCalculator {

    private static final int SCALE = 12;

    private static final MathContext MATH_CONTEXT =
            new MathContext(20, RoundingMode.HALF_UP);

    public BigDecimal calculate(
            List<BigDecimal> values,
            int period
    ) {
        validatePeriod(period);

        if (values == null || values.size() < period) {
            return null;
        }

        BigDecimal initialSma = calculateInitialSma(values, period);

        BigDecimal multiplier = BigDecimal.valueOf(2)
                .divide(
                        BigDecimal.valueOf(period + 1L),
                        MATH_CONTEXT
                );

        BigDecimal oneMinusMultiplier =
                BigDecimal.ONE.subtract(multiplier, MATH_CONTEXT);

        BigDecimal ema = initialSma;

        for (int index = period; index < values.size(); index++) {
            BigDecimal currentValue = values.get(index);

            if (currentValue == null) {
                throw new IllegalArgumentException(
                        "EMA values cannot contain null"
                );
            }

            ema = currentValue
                    .multiply(multiplier, MATH_CONTEXT)
                    .add(
                            ema.multiply(
                                    oneMinusMultiplier,
                                    MATH_CONTEXT
                            ),
                            MATH_CONTEXT
                    );
        }

        return ema.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateInitialSma(
            List<BigDecimal> values,
            int period
    ) {
        BigDecimal sum = BigDecimal.ZERO;

        for (int index = 0; index < period; index++) {
            BigDecimal value = values.get(index);

            if (value == null) {
                throw new IllegalArgumentException(
                        "EMA values cannot contain null"
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
                    "EMA period must be greater than zero"
            );
        }
    }
}