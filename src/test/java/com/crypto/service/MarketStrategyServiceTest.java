package com.crypto.service;

import com.crypto.config.DynamicStrategyProperties;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradingStrategy;
import com.crypto.dto.StrategyProfile;
import com.crypto.dto.StrategyScoreResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketStrategyServiceTest {

    private final DynamicStrategyProperties properties = new DynamicStrategyProperties();
    private final MarketStrategyService service = new MarketStrategyService(properties);

    @Test
    void promotesHighQualityEarlyBreakoutWithoutLoweringGlobalBreakoutThreshold() {
        StrategyProfile breakout = new StrategyProfile(
                TradingStrategy.BREAKOUT, "1.0",
                20, 30, 20, 10, 5,
                90, 80, 65, 45, 30,
                true, "test");

        StrategyScoreResult result = service.score(
                breakout,
                20, 15, 14, 4, 6,
                true, true);

        assertThat(result.normalizedScore()).isBetween(75, 79);
        assertThat(result.decision()).isEqualTo(SignalDecision.BUY);
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
}
