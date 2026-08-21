package com.crypto.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradingStrategy;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.dto.RangeEntryLocationAssessment;
import com.crypto.dto.StrategyProfile;
import com.crypto.dto.StrategyScoreResult;
import com.crypto.dto.TrendStructureResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RangeEntryLocationServiceTest {

    private final RangeEntryLocationService service = new RangeEntryLocationService();

    @Test
    void blocksEth109885StyleRangeBuyAbove55Percent() {
        IndicatorSnapshot eth = snapshot("2391.22", "2383.292121039527", "2395.628878960473", "1.74");
        StrategyScoreResult score = score(87, SignalDecision.BUY, 17, 21);

        RangeEntryLocationAssessment result = service.evaluate(
                eth, rangeProfile(), score, structure(true));

        assertTrue(result.applicable());
        assertFalse(result.entryAllowed());
        assertEquals(new BigDecimal("64.26"), result.bollingerPositionPercent());
        assertFalse(result.expansionException());
        assertTrue(result.explanation().contains("too high"));
    }

    @Test
    void preservesStrictShibStyleStrongExpansionException() {
        // Historical SHIB range winner: high-band entry but STRONG_BUY 93 with RVOL ~2.99x.
        IndicatorSnapshot shib = snapshot("0.000004600000", "0.000004558061", "0.000004610939", "2.99");
        StrategyScoreResult score = score(93, SignalDecision.STRONG_BUY, 17, 20);

        RangeEntryLocationAssessment result = service.evaluate(
                shib, rangeProfile(), score, structure(true));

        assertTrue(result.applicable());
        assertTrue(result.entryAllowed());
        assertTrue(result.expansionException());
        assertTrue(result.bollingerPositionPercent().compareTo(new BigDecimal("55")) > 0);
    }

    @Test
    void doesNotTouchTrendFollowingEvenWhenPriceIsNearUpperBand() {
        IndicatorSnapshot doge = snapshot("0.084160", "0.083524776556", "0.084280223444", "5.18");
        StrategyProfile trend = new StrategyProfile(
                TradingStrategy.TREND_FOLLOWING, "1.0",
                30, 20, 15, 10, 10,
                90, 80, 68, 50, 35,
                true, "trend");

        RangeEntryLocationAssessment result = service.evaluate(
                doge, trend, score(88, SignalDecision.STRONG_BUY, 18, 11), structure(true));

        assertFalse(result.applicable());
        assertTrue(result.entryAllowed());
    }

    @Test
    void allowsNormalRangeBuyInLowerHalf() {
        IndicatorSnapshot indicator = snapshot("104", "100", "110", "1.20");
        RangeEntryLocationAssessment result = service.evaluate(
                indicator, rangeProfile(), score(82, SignalDecision.BUY, 15, 18), structure(false));

        assertTrue(result.applicable());
        assertTrue(result.entryAllowed());
        assertEquals(new BigDecimal("40.00"), result.bollingerPositionPercent());
        assertFalse(result.expansionException());
    }

    private StrategyProfile rangeProfile() {
        return new StrategyProfile(
                TradingStrategy.RANGE_MEAN_REVERSION, "1.0",
                15, 18, 22, 15, 15,
                88, 78, 65, 45, 30,
                true, "range");
    }

    private StrategyScoreResult score(int normalized, SignalDecision decision, int volume, int momentum) {
        return new StrategyScoreResult(11, volume, momentum, 0, 12,
                61, 70, normalized, decision);
    }

    private TrendStructureResult structure(boolean expansion) {
        return new TrendStructureResult(
                6, 2, 1, 1, 1, 1,
                true, true, true, true, false, expansion, true,
                "test", List.of("test"));
    }

    private IndicatorSnapshot snapshot(String price, String lower, String upper, String rvol) {
        BigDecimal p = new BigDecimal(price);
        BigDecimal lo = new BigDecimal(lower);
        BigDecimal up = new BigDecimal(upper);
        return new IndicatorSnapshot(
                "TESTUSDT", "1m", Instant.parse("2026-08-21T13:08:00Z"), p,
                p, p, p, p,
                new BigDecimal("56"), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                lo.add(up).divide(BigDecimal.valueOf(2)), up, lo, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal(rvol));
    }
}
