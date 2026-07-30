package com.crypto.indicator.calculator;

import com.crypto.indicator.model.BollingerBandsResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

@Component
public class BollingerBandsCalculator {

    private static final int PRICE_SCALE = 12;
    private static final int PERCENTAGE_SCALE = 8;

    private static final MathContext MATH_CONTEXT =
            new MathContext(20, RoundingMode.HALF_UP);

    private final SmaCalculator smaCalculator;

    public BollingerBandsCalculator(
            SmaCalculator smaCalculator
    ) {
        this.smaCalculator = smaCalculator;
    }

    public BollingerBandsResult calculate(
            List<BigDecimal> values,
            int period,
            BigDecimal standardDeviationMultiplier
    ) {
        if (values == null || values.size() < period) {
            return null;
        }

        if (standardDeviationMultiplier == null
                || standardDeviationMultiplier.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Standard deviation multiplier must be positive"
            );
        }

        BigDecimal middle = smaCalculator.calculate(values, period);

        int startIndex = values.size() - period;
        BigDecimal squaredDifferenceSum = BigDecimal.ZERO;

        for (int index = startIndex; index < values.size(); index++) {
            BigDecimal difference =
                    values.get(index).subtract(middle);

            squaredDifferenceSum = squaredDifferenceSum.add(
                    difference.multiply(difference, MATH_CONTEXT)
            );
        }

        BigDecimal variance = squaredDifferenceSum.divide(
                BigDecimal.valueOf(period),
                MATH_CONTEXT
        );

        BigDecimal standardDeviation =
                sqrt(variance, MATH_CONTEXT);

        BigDecimal bandDistance = standardDeviation.multiply(
                standardDeviationMultiplier,
                MATH_CONTEXT
        );

        BigDecimal upper = middle.add(bandDistance)
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        BigDecimal lower = middle.subtract(bandDistance)
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        BigDecimal bandwidth = null;

        if (middle.compareTo(BigDecimal.ZERO) != 0) {
            bandwidth = upper
                    .subtract(lower)
                    .divide(middle, MATH_CONTEXT)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(
                            PERCENTAGE_SCALE,
                            RoundingMode.HALF_UP
                    );
        }

        return new BollingerBandsResult(
                middle.setScale(
                        PRICE_SCALE,
                        RoundingMode.HALF_UP
                ),
                upper,
                lower,
                bandwidth
        );
    }

    private BigDecimal sqrt(
            BigDecimal value,
            MathContext mathContext
    ) {
        if (value.signum() < 0) {
            throw new ArithmeticException(
                    "Cannot calculate square root of negative value"
            );
        }

        if (value.signum() == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal estimate = new BigDecimal(
                Math.sqrt(value.doubleValue()),
                mathContext
        );

        BigDecimal two = BigDecimal.valueOf(2);

        for (int iteration = 0; iteration < 20; iteration++) {
            estimate = estimate.add(
                            value.divide(estimate, mathContext)
                    )
                    .divide(two, mathContext);
        }

        return estimate;
    }
}