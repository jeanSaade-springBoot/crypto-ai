package com.crypto.indicator.calculator;

import com.crypto.domain.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class AtrCalculator {

    private static final int SCALE = 12;

    private static final MathContext MATH_CONTEXT =
            new MathContext(20, RoundingMode.HALF_UP);

    public BigDecimal calculate(
            List<Candle> candles,
            int period
    ) {
        if (period <= 0) {
            throw new IllegalArgumentException(
                    "ATR period must be greater than zero"
            );
        }

        if (candles == null || candles.size() < period + 1) {
            return null;
        }

        List<BigDecimal> trueRanges = new ArrayList<>();

        for (int index = 1; index < candles.size(); index++) {
            Candle current = candles.get(index);
            Candle previous = candles.get(index - 1);

            BigDecimal highLow = current.getHighPrice()
                    .subtract(current.getLowPrice())
                    .abs();

            BigDecimal highPreviousClose =
                    current.getHighPrice()
                            .subtract(previous.getClosePrice())
                            .abs();

            BigDecimal lowPreviousClose =
                    current.getLowPrice()
                            .subtract(previous.getClosePrice())
                            .abs();

            BigDecimal trueRange = highLow
                    .max(highPreviousClose)
                    .max(lowPreviousClose);

            trueRanges.add(trueRange);
        }

        if (trueRanges.size() < period) {
            return null;
        }

        BigDecimal atr = BigDecimal.ZERO;

        for (int index = 0; index < period; index++) {
            atr = atr.add(trueRanges.get(index));
        }

        atr = atr.divide(
                BigDecimal.valueOf(period),
                MATH_CONTEXT
        );

        for (int index = period;
             index < trueRanges.size();
             index++) {

            atr = atr
                    .multiply(BigDecimal.valueOf(period - 1L))
                    .add(trueRanges.get(index))
                    .divide(
                            BigDecimal.valueOf(period),
                            MATH_CONTEXT
                    );
        }

        return atr.setScale(SCALE, RoundingMode.HALF_UP);
    }
}