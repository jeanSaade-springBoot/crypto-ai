package com.crypto.service;

import com.crypto.config.AtrRiskProperties;
import com.crypto.domain.AtrEntryType;
import com.crypto.domain.TradingStrategy;
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

    /**
     * Builds the neutral ATR risk snapshot before strategy selection.
     */
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
        String explanation = baseExplanation(atr, atrPercent, volatilityLevel)
                + (overextended ? " Price is extended from SMA20 by "
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
                AtrEntryType.STANDARD_ENTRY,
                100,
                true,
                price,
                explanation
        );
    }

    /**
     * Converts ATR extension into a strategy-aware entry plan. Direction is preserved unless
     * extension exceeds the hard-veto level. Pullback and retracement plans keep BUY as BUY,
     * but prevent an immediate market entry until a better price is reached.
     */
    public AtrRiskAssessment applyStrategyEntryPlan(
            AtrRiskAssessment base,
            IndicatorSnapshot indicator,
            TradingStrategy strategy
    ) {
        BigDecimal distance = base.candleRangeAtrMultiple();
        Thresholds thresholds = thresholds(strategy);

        AtrEntryType entryType;
        int positionPercent;
        boolean immediateEntryAllowed;

        if (distance.compareTo(thresholds.reduced()) < 0) {
            entryType = AtrEntryType.STANDARD_ENTRY;
            positionPercent = 100;
            immediateEntryAllowed = true;
        } else if (distance.compareTo(thresholds.pullback()) < 0) {
            entryType = AtrEntryType.REDUCED_POSITION;
            positionPercent = properties.reducedPositionPercent();
            immediateEntryAllowed = true;
        } else if (distance.compareTo(thresholds.waitForRetracement()) < 0) {
            entryType = AtrEntryType.PULLBACK_ENTRY;
            positionPercent = properties.reducedPositionPercent();
            immediateEntryAllowed = false;
        } else if (distance.compareTo(thresholds.hardVeto()) < 0) {
            entryType = AtrEntryType.WAIT_FOR_RETRACEMENT;
            positionPercent = 0;
            immediateEntryAllowed = false;
        } else {
            entryType = AtrEntryType.NO_ENTRY;
            positionPercent = 0;
            immediateEntryAllowed = false;
        }

        BigDecimal retracementEntryPrice = retracementEntryPrice(indicator, base.atr(), thresholds.reduced());
        String explanation = baseExplanation(base.atr(), base.atrPercent(), base.volatilityLevel())
                + " Price is " + distance.setScale(2, RoundingMode.HALF_UP)
                + " ATR from SMA20. Strategy-aware entry type is " + entryType
                + ". Recommended position is " + positionPercent + "%"
                + (immediateEntryAllowed ? ". Immediate entry remains allowed."
                : ". Wait for price to retrace near " + retracementEntryPrice.stripTrailingZeros().toPlainString() + ".");

        return new AtrRiskAssessment(
                base.atr(),
                base.atrPercent(),
                base.stopLoss(),
                base.takeProfit(),
                base.stopDistance(),
                base.rewardDistance(),
                base.riskRewardRatio(),
                base.candleRangeAtrMultiple(),
                base.volatilityLevel(),
                entryType != AtrEntryType.STANDARD_ENTRY,
                entryType,
                positionPercent,
                immediateEntryAllowed,
                retracementEntryPrice,
                explanation
        );
    }

    private Thresholds thresholds(TradingStrategy strategy) {
        BigDecimal reduced = properties.reducedPositionMultiplier();
        BigDecimal pullback = properties.pullbackEntryMultiplier();
        BigDecimal waitForRetracement = properties.waitForRetracementMultiplier();
        BigDecimal hardVeto = properties.hardVetoMultiplier();

        if (strategy == TradingStrategy.BREAKOUT) {
            return new Thresholds(
                    reduced.add(new BigDecimal("0.50")),
                    pullback.add(new BigDecimal("0.75")),
                    waitForRetracement.add(new BigDecimal("0.75")),
                    hardVeto.add(new BigDecimal("0.75"))
            );
        }
        if (strategy == TradingStrategy.RANGE_MEAN_REVERSION) {
            return new Thresholds(
                    reduced.subtract(new BigDecimal("0.50")).max(new BigDecimal("1.00")),
                    pullback.subtract(new BigDecimal("0.75")).max(new BigDecimal("1.50")),
                    waitForRetracement.subtract(new BigDecimal("1.00")).max(new BigDecimal("2.00")),
                    hardVeto.subtract(new BigDecimal("1.25")).max(new BigDecimal("2.50"))
            );
        }
        if (strategy == TradingStrategy.DEFENSIVE || strategy == TradingStrategy.NO_TRADE) {
            return new Thresholds(
                    reduced.subtract(new BigDecimal("0.50")).max(new BigDecimal("1.00")),
                    pullback.subtract(new BigDecimal("0.50")).max(new BigDecimal("1.75")),
                    waitForRetracement.subtract(new BigDecimal("0.75")).max(new BigDecimal("2.50")),
                    hardVeto.subtract(new BigDecimal("1.00")).max(new BigDecimal("3.00"))
            );
        }
        return new Thresholds(reduced, pullback, waitForRetracement, hardVeto);
    }

    private BigDecimal retracementEntryPrice(
            IndicatorSnapshot indicator,
            BigDecimal atr,
            BigDecimal acceptableDistanceAtr
    ) {
        if (indicator.sma20() == null) {
            return indicator.latestPrice();
        }
        BigDecimal acceptableDistance = atr.multiply(acceptableDistanceAtr, MC);
        return indicator.sma20().add(acceptableDistance, MC);
    }

    private String baseExplanation(BigDecimal atr, BigDecimal atrPercent, String volatilityLevel) {
        return "ATR14 is " + atr.stripTrailingZeros().toPlainString()
                + " (" + atrPercent.setScale(2, RoundingMode.HALF_UP) + "% of price). "
                + "Volatility is " + volatilityLevel + ". Stop uses "
                + properties.stopLossMultiplier() + " ATR and target uses "
                + properties.takeProfitMultiplier() + " ATR.";
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

    private record Thresholds(
            BigDecimal reduced,
            BigDecimal pullback,
            BigDecimal waitForRetracement,
            BigDecimal hardVeto
    ) {
    }
}
