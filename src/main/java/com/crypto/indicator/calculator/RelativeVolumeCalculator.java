package com.crypto.indicator.calculator;

import com.crypto.domain.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

@Component
public class RelativeVolumeCalculator {

    private static final MathContext MC =
            new MathContext(20, RoundingMode.HALF_UP);

    public BigDecimal calculate(
            List<Candle> candles,
            int lookback
    ) {
        if (lookback <= 0) {
            throw new IllegalArgumentException(
                    "Relative-volume lookback must be positive"
            );
        }

        if (candles == null
                || candles.size() < lookback + 1) {
            return null;
        }

        Candle latest = candles.get(candles.size() - 1);

        int startIndex =
                candles.size() - lookback - 1;

        int endIndex =
                candles.size() - 1;

        BigDecimal totalPreviousVolume =
                BigDecimal.ZERO;

        for (int index = startIndex;
             index < endIndex;
             index++) {

            totalPreviousVolume =
                    totalPreviousVolume.add(
                            candles.get(index).getVolume(),
                            MC
                    );
        }

        BigDecimal averagePreviousVolume =
                totalPreviousVolume.divide(
                        BigDecimal.valueOf(lookback),
                        MC
                );

        if (averagePreviousVolume.signum() == 0) {
            return BigDecimal.ZERO;
        }

        return latest.getVolume().divide(
                averagePreviousVolume,
                MC
        );
    }
}