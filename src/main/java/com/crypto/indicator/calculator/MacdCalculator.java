package com.crypto.indicator.calculator;

import com.crypto.indicator.model.MacdResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class MacdCalculator {

    private static final MathContext MC =
            new MathContext(20, RoundingMode.HALF_UP);

    private final EmaCalculator emaCalculator;

    public MacdCalculator(EmaCalculator emaCalculator) {
        this.emaCalculator = emaCalculator;
    }

    public MacdResult calculate(List<BigDecimal> closes) {
        if (closes == null || closes.size() < 35) {
            return null;
        }

        BigDecimal ema12 = emaCalculator.calculate(closes, 12);
        BigDecimal ema26 = emaCalculator.calculate(closes, 26);
        BigDecimal macd = ema12.subtract(ema26, MC);

        List<BigDecimal> macdSeries = new ArrayList<>();

        for (int index = 26; index <= closes.size(); index++) {
            List<BigDecimal> window =
                    closes.subList(0, index);

            BigDecimal currentEma12 =
                    emaCalculator.calculate(window, 12);

            BigDecimal currentEma26 =
                    emaCalculator.calculate(window, 26);

            macdSeries.add(
                    currentEma12.subtract(currentEma26, MC)
            );
        }

        BigDecimal signal =
                emaCalculator.calculate(macdSeries, 9);

        BigDecimal histogram =
                macd.subtract(signal, MC);

        return new MacdResult(
                macd,
                signal,
                histogram
        );
    }
}