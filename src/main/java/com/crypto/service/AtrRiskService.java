package com.crypto.service;

import com.crypto.config.AtrRiskProperties;
import com.crypto.dto.AtrRiskAssessment;
import com.crypto.dto.IndicatorSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class AtrRiskService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final AtrRiskProperties properties;

    public AtrRiskAssessment assess(IndicatorSnapshot indicator) {
        BigDecimal price = requirePositive(indicator.latestPrice(), "Latest price");
        BigDecimal atr = requirePositive(indicator.atr14(), "ATR14");

        BigDecimal atrPercent = atr.divide(price, 10, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED);

        BigDecimal configuredStopDistance = atr.multiply(properties.stopLossMultiplier(), MC);
        BigDecimal minimumStopDistance = price.multiply(properties.minimumStopPercent(), MC)
                .divide(ONE_HUNDRED, MC);
        BigDecimal maximumStopDistance = price.multiply(properties.maximumStopPercent(), MC)
                .divide(ONE_HUNDRED, MC);

        BigDecimal stopDistance = configuredStopDistance.max(minimumStopDistance).min(maximumStopDistance);
        BigDecimal rewardDistance = atr.multiply(properties.takeProfitMultiplier(), MC);

        BigDecimal stopLoss = price.subtract(stopDistance, MC).max(BigDecimal.ZERO);
        BigDecimal takeProfit = price.add(rewardDistance, MC);
        BigDecimal riskRewardRatio = rewardDistance.divide(stopDistance, 6, RoundingMode.HALF_UP);

        BigDecimal distanceFromSma = indicator.sma20() == null
                ? BigDecimal.ZERO
                : price.subtract(indicator.sma20()).abs();
        BigDecimal distanceAtrMultiple = distanceFromSma.divide(atr, 6, RoundingMode.HALF_UP);
        boolean overextended = distanceAtrMultiple.compareTo(properties.overextensionMultiplier()) >= 0;

        String volatilityLevel = volatilityLevel(atrPercent);
        String explanation = "ATR14 is " + atr.stripTrailingZeros().toPlainString()
                + " (" + atrPercent.setScale(2, RoundingMode.HALF_UP) + "% of price). "
                + "Volatility is " + volatilityLevel + ". Stop uses "
                + properties.stopLossMultiplier() + " ATR and target uses "
                + properties.takeProfitMultiplier() + " ATR."
                + (overextended ? " Price is overextended from SMA20 by "
                + distanceAtrMultiple.setScale(2, RoundingMode.HALF_UP) + " ATR." : "");

        return new AtrRiskAssessment(
                atr,
                atrPercent,
                stopLoss,
                takeProfit,
                stopDistance,
                rewardDistance,
                riskRewardRatio,
                distanceAtrMultiple,
                volatilityLevel,
                overextended,
                explanation
        );
    }

    private String volatilityLevel(BigDecimal atrPercent) {
        if (atrPercent.compareTo(properties.extremeVolatilityPercent()) >= 0) return "EXTREME";
        if (atrPercent.compareTo(properties.highVolatilityPercent()) >= 0) return "HIGH";
        if (atrPercent.compareTo(properties.lowVolatilityPercent()) < 0) return "LOW";
        return "NORMAL";
    }

    private BigDecimal requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException(label + " must be available and greater than zero");
        }
        return value;
    }
}
