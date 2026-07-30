package com.crypto.indicator.calculator;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

@Component
public class RsiCalculator {

    private static final int SCALE = 8;

    private static final MathContext MATH_CONTEXT =
            new MathContext(20, RoundingMode.HALF_UP);

    public BigDecimal calculate(
            List<BigDecimal> closePrices,
            int period
    ) {
        if (period <= 0) {
            throw new IllegalArgumentException(
                    "RSI period must be greater than zero"
            );
        }

        if (closePrices == null || closePrices.size() < period + 1) {
            return null;
        }

        BigDecimal totalGain = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;

        for (int index = 1; index <= period; index++) {
            BigDecimal change = closePrices.get(index)
                    .subtract(closePrices.get(index - 1));

            if (change.signum() > 0) {
                totalGain = totalGain.add(change);
            } else {
                totalLoss = totalLoss.add(change.abs());
            }
        }

        BigDecimal periodValue = BigDecimal.valueOf(period);

        BigDecimal averageGain = totalGain.divide(
                periodValue,
                MATH_CONTEXT
        );

        BigDecimal averageLoss = totalLoss.divide(
                periodValue,
                MATH_CONTEXT
        );

        for (int index = period + 1;
             index < closePrices.size();
             index++) {

            BigDecimal change = closePrices.get(index)
                    .subtract(closePrices.get(index - 1));

            BigDecimal gain = change.signum() > 0
                    ? change
                    : BigDecimal.ZERO;

            BigDecimal loss = change.signum() < 0
                    ? change.abs()
                    : BigDecimal.ZERO;

            averageGain = averageGain
                    .multiply(BigDecimal.valueOf(period - 1L))
                    .add(gain)
                    .divide(periodValue, MATH_CONTEXT);

            averageLoss = averageLoss
                    .multiply(BigDecimal.valueOf(period - 1L))
                    .add(loss)
                    .divide(periodValue, MATH_CONTEXT);
        }

        if (averageLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100)
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }

        if (averageGain.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal relativeStrength =
                averageGain.divide(averageLoss, MATH_CONTEXT);

        BigDecimal rsi = BigDecimal.valueOf(100)
                .subtract(
                        BigDecimal.valueOf(100)
                                .divide(
                                        BigDecimal.ONE.add(relativeStrength),
                                        MATH_CONTEXT
                                )
                );

        return rsi.setScale(SCALE, RoundingMode.HALF_UP);
    }
}