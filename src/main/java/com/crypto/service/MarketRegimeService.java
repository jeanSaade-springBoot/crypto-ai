package com.crypto.service;

import com.crypto.domain.MarketRegime;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.dto.MarketRegimeAssessment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarketRegimeService {

    private static final BigDecimal HIGH_ATR_PERCENT = BigDecimal.valueOf(4);
    private static final BigDecimal HIGH_BANDWIDTH = BigDecimal.valueOf(12);
    private static final BigDecimal STRONG_GAP_PERCENT = BigDecimal.valueOf(1.5);
    private static final BigDecimal WEAK_GAP_PERCENT = BigDecimal.valueOf(0.5);
    private static final BigDecimal BREAKOUT_VOLUME = BigDecimal.valueOf(1.5);

    public MarketRegime classify(IndicatorSnapshot indicator) {
        return assess(indicator).regime();
    }

    public MarketRegimeAssessment assess(IndicatorSnapshot indicator) {
        if (!valid(indicator)) {
            return new MarketRegimeAssessment(MarketRegime.UNKNOWN, 0,
                    List.of("Required indicator values are unavailable"));
        }

        List<String> evidence = new ArrayList<>();
        BigDecimal emaGap = percentDistance(indicator.ema20(), indicator.ema50()).abs();
        BigDecimal atrPercent = indicator.atr14() == null
                ? BigDecimal.ZERO
                : indicator.atr14().abs()
                    .divide(indicator.latestPrice().abs(), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        BigDecimal bandwidth = indicator.bollingerBandwidth() == null
                ? BigDecimal.ZERO
                : indicator.bollingerBandwidth().abs();
        BigDecimal relativeVolume = indicator.relativeVolume() == null
                ? BigDecimal.ZERO : indicator.relativeVolume();

        boolean alignedUp = indicator.ema20().compareTo(indicator.ema50()) > 0
                && indicator.ema50().compareTo(indicator.ema200()) > 0;
        boolean alignedDown = indicator.ema20().compareTo(indicator.ema50()) < 0
                && indicator.ema50().compareTo(indicator.ema200()) < 0;
        boolean aboveUpper = indicator.bollingerUpper() != null
                && indicator.latestPrice().compareTo(indicator.bollingerUpper()) >= 0;
        boolean belowLower = indicator.bollingerLower() != null
                && indicator.latestPrice().compareTo(indicator.bollingerLower()) <= 0;
        boolean volumeExpansion = relativeVolume.compareTo(BREAKOUT_VOLUME) >= 0;

        if ((aboveUpper || belowLower) && volumeExpansion) {
            evidence.add(aboveUpper ? "Price broke above the upper Bollinger band" : "Price broke below the lower Bollinger band");
            evidence.add("Relative volume confirms expansion at " + relativeVolume.setScale(2, RoundingMode.HALF_UP) + "x");
            return new MarketRegimeAssessment(MarketRegime.BREAKOUT,
                    confidence(70, emaGap, atrPercent, relativeVolume), evidence);
        }

        if (atrPercent.compareTo(HIGH_ATR_PERCENT) >= 0 || bandwidth.compareTo(HIGH_BANDWIDTH) >= 0) {
            evidence.add("ATR is " + atrPercent.setScale(2, RoundingMode.HALF_UP) + "% of price");
            evidence.add("Bollinger bandwidth is " + bandwidth.setScale(2, RoundingMode.HALF_UP));
            return new MarketRegimeAssessment(MarketRegime.HIGH_VOLATILITY,
                    confidence(65, emaGap, atrPercent, relativeVolume), evidence);
        }

        if (alignedUp && emaGap.compareTo(STRONG_GAP_PERCENT) >= 0) {
            evidence.add("EMA20 > EMA50 > EMA200");
            evidence.add("EMA20/EMA50 separation is " + emaGap.setScale(2, RoundingMode.HALF_UP) + "%");
            return new MarketRegimeAssessment(MarketRegime.STRONG_UPTREND,
                    confidence(75, emaGap, atrPercent, relativeVolume), evidence);
        }
        if (alignedDown && emaGap.compareTo(STRONG_GAP_PERCENT) >= 0) {
            evidence.add("EMA20 < EMA50 < EMA200");
            evidence.add("EMA20/EMA50 separation is " + emaGap.setScale(2, RoundingMode.HALF_UP) + "%");
            return new MarketRegimeAssessment(MarketRegime.STRONG_DOWNTREND,
                    confidence(75, emaGap, atrPercent, relativeVolume), evidence);
        }
        if (alignedUp || (indicator.ema20().compareTo(indicator.ema50()) > 0
                && emaGap.compareTo(WEAK_GAP_PERCENT) >= 0)) {
            evidence.add("Short-term EMA structure is bullish but not strongly separated");
            return new MarketRegimeAssessment(MarketRegime.WEAK_UPTREND,
                    confidence(58, emaGap, atrPercent, relativeVolume), evidence);
        }
        if (alignedDown || (indicator.ema20().compareTo(indicator.ema50()) < 0
                && emaGap.compareTo(WEAK_GAP_PERCENT) >= 0)) {
            evidence.add("Short-term EMA structure is bearish but not strongly separated");
            return new MarketRegimeAssessment(MarketRegime.WEAK_DOWNTREND,
                    confidence(58, emaGap, atrPercent, relativeVolume), evidence);
        }

        evidence.add("EMA separation is limited");
        evidence.add("Price is rotating inside the Bollinger structure");
        return new MarketRegimeAssessment(MarketRegime.RANGE,
                confidence(65, BigDecimal.ONE.subtract(emaGap.min(BigDecimal.ONE)),
                        BigDecimal.ZERO, BigDecimal.ONE), evidence);
    }

    private boolean valid(IndicatorSnapshot i) {
        return i != null && i.latestPrice() != null && i.latestPrice().signum() > 0
                && i.ema20() != null && i.ema50() != null && i.ema200() != null;
    }

    private int confidence(int base, BigDecimal emaGap, BigDecimal atrPercent, BigDecimal relativeVolume) {
        int value = base;
        value += Math.min(10, emaGap.multiply(BigDecimal.valueOf(2)).intValue());
        value += Math.min(8, relativeVolume.max(BigDecimal.ZERO).intValue());
        if (atrPercent.compareTo(BigDecimal.valueOf(8)) > 0) value -= 8;
        return Math.max(0, Math.min(95, value));
    }

    private BigDecimal percentDistance(BigDecimal value, BigDecimal reference) {
        if (value == null || reference == null || reference.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.subtract(reference)
                .divide(reference.abs(), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
