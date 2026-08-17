package com.crypto.service;

import com.crypto.config.DynamicStrategyProperties;
import com.crypto.domain.MarketRegime;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradingStrategy;
import com.crypto.dto.MarketRegimeAssessment;
import com.crypto.dto.StrategyProfile;
import com.crypto.dto.StrategyScoreResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketStrategyServiceTest {

    private final DynamicStrategyProperties properties = new DynamicStrategyProperties();
    private final MarketStrategyService service = new MarketStrategyService(properties);

    @Test
    void promotesExactEarlyBreakoutShapeWithoutLoweringNormalBuyThreshold() {
        StrategyProfile breakout = new StrategyProfile(
                TradingStrategy.BREAKOUT, "1.0",
                20, 30, 20, 10, 5,
                90, 80, 65, 45, 30,
                true, "test");

        // Mirrors the BNB case: 12/20 trend, 24/30 volume, 19/20 momentum,
        // normalized 72. Normal scoring remains WATCH because BUY is still 80.
        StrategyScoreResult watch = new StrategyScoreResult(
                12, 24, 19, 3, 3, 61, 85, 72, SignalDecision.WATCH);
        StrategyScoreResult promoted = service.promoteEarlyBreakout(breakout, watch, true, true);

        assertThat(breakout.buyThreshold()).isEqualTo(80);
        assertThat(promoted.decision()).isEqualTo(SignalDecision.BUY);
    }

    @Test
    void doesNotPromoteEarlyBreakoutWhenAtrOrHigherTimeframeBlocks() {
        StrategyProfile breakout = new StrategyProfile(
                TradingStrategy.BREAKOUT, "1.0",
                20, 30, 20, 10, 5,
                90, 80, 65, 45, 30,
                true, "test");
        StrategyScoreResult watch = new StrategyScoreResult(
                12, 24, 19, 3, 3, 61, 85, 72, SignalDecision.WATCH);

        assertThat(service.promoteEarlyBreakout(breakout, watch, false, true).decision())
                .isEqualTo(SignalDecision.WATCH);
        assertThat(service.promoteEarlyBreakout(breakout, watch, true, false).decision())
                .isEqualTo(SignalDecision.WATCH);
    }

    @Test
    void keepsBreakoutAsWatchWhenMomentumIsNotStrongEnough() {
        StrategyProfile breakout = new StrategyProfile(
                TradingStrategy.BREAKOUT, "1.0",
                20, 30, 20, 10, 5,
                90, 80, 65, 45, 30,
                true, "test");

        StrategyScoreResult result = service.score(
                breakout,
                20, 17, 10, 4, 6,
                true, true);

        assertThat(result.normalizedScore()).isBetween(65, 79);
        assertThat(result.decision()).isEqualTo(SignalDecision.WATCH);
    }

    @Test
    void capsUnconfirmedBreakoutCandidateBuyToWatchBeforePromotion() {
        StrategyScoreResult buy83 = new StrategyScoreResult(
                14, 24, 20, 0, 0, 58, 70, 83, SignalDecision.BUY);
        MarketRegimeAssessment candidate = new MarketRegimeAssessment(
                MarketRegime.BREAKOUT_CANDIDATE, 73, java.util.List.of("test"));

        StrategyScoreResult constrained = service.constrainBreakoutCandidate(candidate, buy83);

        assertThat(constrained.normalizedScore()).isEqualTo(83);
        assertThat(constrained.decision()).isEqualTo(SignalDecision.WATCH);
    }

}
