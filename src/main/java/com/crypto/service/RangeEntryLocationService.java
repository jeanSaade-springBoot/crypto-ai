package com.crypto.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradingStrategy;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.dto.RangeEntryLocationAssessment;
import com.crypto.dto.StrategyProfile;
import com.crypto.dto.StrategyScoreResult;
import com.crypto.dto.TrendStructureResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * FIX-036 - surgical RANGE_MEAN_REVERSION location protection.
 *
 * Production incident reproduced by ETH signal #109885 (21 Aug 2026): the engine
 * correctly found bullish momentum/volume, but because RANGE_MEAN_REVERSION weights
 * those components strongly it produced BUY 87 while price was already ~64% of the
 * way from the lower to the upper Bollinger band. The position then stopped out.
 *
 * Historical regression showed the same high-range pattern in 5/6 losing/winning
 * range trades. Therefore this service does NOT touch trend-following or breakout
 * entries. It simply prevents an ordinary range BUY above 55% of the Bollinger
 * envelope. A deliberately strict expansion exception preserves the historical
 * SHIB-style case where a true STRONG_BUY transition was effectively escaping the
 * range rather than mean-reverting inside it.
 */
@Service
public class RangeEntryLocationService {

    static final BigDecimal MAX_NORMAL_RANGE_BUY_POSITION_PERCENT = new BigDecimal("55.00");
    static final BigDecimal MIN_EXPANSION_EXCEPTION_RVOL = new BigDecimal("2.00");
    static final int MIN_EXPANSION_EXCEPTION_SCORE = 90;

    public RangeEntryLocationAssessment evaluate(
            IndicatorSnapshot indicator,
            StrategyProfile profile,
            StrategyScoreResult score,
            TrendStructureResult trendStructure
    ) {
        if (indicator == null || profile == null || score == null
                || profile.strategy() != TradingStrategy.RANGE_MEAN_REVERSION
                || !isBullish(score.decision())) {
            return RangeEntryLocationAssessment.notApplicable();
        }

        BigDecimal price = indicator.latestPrice();
        BigDecimal lower = indicator.bollingerLower();
        BigDecimal upper = indicator.bollingerUpper();
        if (price == null || lower == null || upper == null || upper.compareTo(lower) <= 0) {
            // Missing/invalid Bollinger geometry must not silently create a new veto.
            // Existing DATA_QUALITY checks remain authoritative for unavailable data.
            return new RangeEntryLocationAssessment(true, true, null, false,
                    "RANGE_MEAN_REVERSION location guard could not calculate Bollinger position; existing data-quality controls remain authoritative.");
        }

        BigDecimal positionPercent = price.subtract(lower)
                .divide(upper.subtract(lower), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        if (positionPercent.compareTo(MAX_NORMAL_RANGE_BUY_POSITION_PERCENT) <= 0) {
            return new RangeEntryLocationAssessment(true, true, positionPercent, false,
                    "RANGE_MEAN_REVERSION BUY is inside the preferred lower/middle range zone at "
                            + positionPercent + "% of the Bollinger range (limit "
                            + MAX_NORMAL_RANGE_BUY_POSITION_PERCENT + "%). Entry remains allowed.");
        }

        boolean expansionException = score.decision() == SignalDecision.STRONG_BUY
                && score.normalizedScore() >= MIN_EXPANSION_EXCEPTION_SCORE
                && percent(score.volumeScore(), profile.volumeMaximum()) >= 85
                && percent(score.momentumScore(), profile.momentumMaximum()) >= 80
                && trendStructure != null
                && trendStructure.bullishExpansionConfirmed()
                && indicator.relativeVolume() != null
                && indicator.relativeVolume().compareTo(MIN_EXPANSION_EXCEPTION_RVOL) >= 0;

        if (expansionException) {
            return new RangeEntryLocationAssessment(true, true, positionPercent, true,
                    "Price is high inside the Bollinger range at " + positionPercent
                            + "%, but a strict expansion-transition exception passed: STRONG_BUY score >= "
                            + MIN_EXPANSION_EXCEPTION_SCORE + ", strong volume/momentum, bullish expansion structure and RVOL >= "
                            + MIN_EXPANSION_EXCEPTION_RVOL + "x. Entry remains allowed as a transition case, not an ordinary range mean-reversion entry.");
        }

        return new RangeEntryLocationAssessment(true, false, positionPercent, false,
                "RANGE_MEAN_REVERSION BUY is too high inside the Bollinger range at "
                        + positionPercent + "% (normal entry limit "
                        + MAX_NORMAL_RANGE_BUY_POSITION_PERCENT
                        + "%). Bullish momentum/volume may remain visible in the score, but a new range entry is blocked until price location improves or a strict expansion transition is confirmed.");
    }

    private boolean isBullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private int percent(int value, int maximum) {
        if (maximum <= 0) return 0;
        return (int) Math.round(value * 100.0 / maximum);
    }
}
