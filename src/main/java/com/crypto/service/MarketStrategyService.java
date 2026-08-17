package com.crypto.service;

import com.crypto.config.DynamicStrategyProperties;
import com.crypto.domain.MarketRegime;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradingStrategy;
import com.crypto.dto.MarketRegimeAssessment;
import com.crypto.dto.MarketContextSnapshot;
import com.crypto.dto.StrategyProfile;
import com.crypto.dto.StrategyScoreResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketStrategyService {

    private static final int BASE_TREND_MAX = 25;
    private static final int BASE_VOLUME_MAX = 20;
    private static final int BASE_MOMENTUM_MAX = 15;
    private static final int BASE_SENTIMENT_MAX = 15;
    private static final int BASE_FUNDAMENTAL_MAX = 10;

    private final DynamicStrategyProperties properties;

    public StrategyProfile select(MarketRegimeAssessment assessment) {
        return select(assessment, null);
    }

    public StrategyProfile select(MarketRegimeAssessment assessment, MarketContextSnapshot context) {
        if (!properties.isEnabled()) {
            return profile(TradingStrategy.TREND_FOLLOWING, properties.getTrendFollowing(), true,
                    "Dynamic strategy selection is disabled; using the default profile");
        }
        if (context != null && !context.dataValid()) {
            return profile(TradingStrategy.NO_TRADE, properties.getDefensive(), false,
                    "Required market data is invalid or incomplete; new entries are disabled");
        }
        if (context != null && context.liquidityRisky()) {
            return profile(TradingStrategy.DEFENSIVE, properties.getDefensive(), true,
                    "Current order-book liquidity risk selected the defensive profile");
        }
        if (context != null && (context.derivativesStatus() == com.crypto.domain.DerivativesPositioningStatus.LONGS_CROWDED
                || context.derivativesStatus() == com.crypto.domain.DerivativesPositioningStatus.SHORTS_CROWDED)) {
            return profile(TradingStrategy.DEFENSIVE, properties.getDefensive(), true,
                    "Extreme futures positioning selected the defensive profile");
        }
        if (context != null && context.sentimentEnabled()
                && context.sentimentCoverage().compareTo(java.math.BigDecimal.valueOf(0.50)) < 0) {
            return profile(TradingStrategy.DEFENSIVE, properties.getDefensive(), true,
                    "Less than half of enabled sentiment providers are contributing; defensive profile selected");
        }
        if (context != null && context.higherTimeframeStatus() == com.crypto.domain.ConfluenceStatus.STRONG_CONFLICT) {
            return profile(TradingStrategy.DEFENSIVE, properties.getDefensive(), true,
                    "Strong higher-timeframe conflict selected the defensive profile");
        }
        if (context != null
                && context.btcContextStatus() == com.crypto.domain.BtcContextStatus.STRONG_CONFLICT
                && context.btcInfluenceFactor() != null
                && context.btcInfluenceFactor().compareTo(java.math.BigDecimal.valueOf(0.70)) >= 0) {
            return profile(TradingStrategy.DEFENSIVE, properties.getDefensive(), true,
                    "Strong BTC conflict for a highly correlated asset selected the defensive profile");
        }
        if (assessment.confidence() < properties.getMinimumRegimeConfidence()) {
            return profile(TradingStrategy.DEFENSIVE, properties.getDefensive(), true,
                    "Regime confidence is below the configured minimum; defensive profile selected");
        }

        return switch (assessment.regime()) {
            case STRONG_UPTREND, WEAK_UPTREND, STRONG_DOWNTREND, WEAK_DOWNTREND ->
                    profile(TradingStrategy.TREND_FOLLOWING, properties.getTrendFollowing(), true,
                            "Directional market structure selected the trend-following profile");
            case RANGE -> profile(TradingStrategy.RANGE_MEAN_REVERSION, properties.getRangeMeanReversion(), true,
                    "Range conditions selected the mean-reversion profile");
            case BREAKOUT -> profile(TradingStrategy.BREAKOUT, properties.getBreakout(), true,
                    "Price expansion, volume and structural confirmation selected the breakout profile");
            case BREAKOUT_CANDIDATE -> profile(TradingStrategy.BREAKOUT, properties.getBreakout(), true,
                    "Price expansion and volume detected an early breakout candidate; structural confirmation is still pending");
            case HIGH_VOLATILITY, LOW_LIQUIDITY -> profile(TradingStrategy.DEFENSIVE, properties.getDefensive(), true,
                    "Risk conditions selected the defensive profile");
            case UNKNOWN -> profile(TradingStrategy.NO_TRADE, properties.getDefensive(), false,
                    "Market regime is unknown; new entries are disabled");
        };
    }

    public StrategyScoreResult score(
            StrategyProfile profile,
            int baseTrend,
            int baseVolume,
            int baseMomentum,
            int baseSentiment,
            int baseFundamentals,
            boolean sentimentAvailable,
            boolean fundamentalAvailable
    ) {
        int trend = scale(baseTrend, BASE_TREND_MAX, profile.trendMaximum());
        int volume = scale(baseVolume, BASE_VOLUME_MAX, profile.volumeMaximum());
        int momentum = scale(baseMomentum, BASE_MOMENTUM_MAX, profile.momentumMaximum());
        int sentiment = sentimentAvailable
                ? scale(baseSentiment, BASE_SENTIMENT_MAX, profile.sentimentMaximum()) : 0;
        int fundamentals = fundamentalAvailable
                ? scale(baseFundamentals, BASE_FUNDAMENTAL_MAX, profile.fundamentalMaximum()) : 0;
        int raw = trend + volume + momentum + sentiment + fundamentals;
        int maximum = profile.maximum(sentimentAvailable, fundamentalAvailable);
        int normalized = maximum <= 0 ? 0 : (int) Math.round(raw * 100.0 / maximum);
        SignalDecision decision = decision(normalized, profile);

        if (!profile.entryAllowed() && (decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY)) {
            decision = SignalDecision.WATCH;
        }
        return new StrategyScoreResult(trend, volume, momentum, sentiment, fundamentals,
                raw, maximum, normalized, decision);
    }


    /**
     * A BREAKOUT_CANDIDATE may use breakout weights for diagnostics, but it cannot
     * become an ordinary full BUY solely through score normalization. It must first
     * pass the existing early-breakout promotion path.
     */
    public StrategyScoreResult constrainBreakoutCandidate(
            MarketRegimeAssessment assessment,
            StrategyScoreResult score
    ) {
        if (assessment == null || score == null
                || assessment.regime() != MarketRegime.BREAKOUT_CANDIDATE) {
            return score;
        }
        if (score.decision() != SignalDecision.BUY && score.decision() != SignalDecision.STRONG_BUY) {
            return score;
        }
        return new StrategyScoreResult(
                score.trendScore(), score.volumeScore(), score.momentumScore(),
                score.sentimentScore(), score.fundamentalScore(), score.rawScore(),
                score.maximumScore(), score.normalizedScore(), SignalDecision.WATCH);
    }

    /**
     * Promotes only a technically strong early BREAKOUT WATCH. The normal BUY threshold stays
     * untouched (80 by configuration). ATR and higher-timeframe context are required here so
     * the promotion cannot bypass entry-risk or 1m/5m/1h safety checks.
     */
    public StrategyScoreResult promoteEarlyBreakout(
            StrategyProfile profile,
            StrategyScoreResult score,
            MarketContextSnapshot context,
            com.crypto.dto.AtrRiskAssessment atrRisk,
            MarketRegimeAssessment regimeAssessment,
            com.crypto.dto.TrendStructureResult trendStructure
    ) {
        if (profile == null || score == null || profile.strategy() != TradingStrategy.BREAKOUT
                || score.decision() != SignalDecision.WATCH) {
            return score;
        }

        boolean higherTimeframeSafe = context != null
                && context.higherTimeframeStatus() != com.crypto.domain.ConfluenceStatus.UNAVAILABLE
                && context.higherTimeframeStatus() != com.crypto.domain.ConfluenceStatus.CONFLICT
                && context.higherTimeframeStatus() != com.crypto.domain.ConfluenceStatus.STRONG_CONFLICT;
        boolean atrAllowsEntry = atrRisk != null && atrRisk.immediateEntryAllowed();

        // An unconfirmed candidate needs at least genuine breakout preparation
        // (compression or equivalent structure evidence) before the early-probe
        // promotion is allowed. This specifically prevents a tiny Bollinger poke
        // plus RVOL from jumping directly to BUY.
        if (regimeAssessment != null
                && regimeAssessment.regime() == MarketRegime.BREAKOUT_CANDIDATE
                && (trendStructure == null || trendStructure.breakoutPreparationScore() <= 0)) {
            return score;
        }

        return promoteEarlyBreakout(profile, score, higherTimeframeSafe, atrAllowsEntry);
    }

    StrategyScoreResult promoteEarlyBreakout(
            StrategyProfile profile,
            StrategyScoreResult score,
            boolean higherTimeframeSafe,
            boolean atrAllowsEntry
    ) {
        if (profile == null || score == null || profile.strategy() != TradingStrategy.BREAKOUT
                || score.decision() != SignalDecision.WATCH) {
            return score;
        }
        boolean strongTechnicalBreakout = score.normalizedScore() >= 70
                && percent(score.trendScore(), profile.trendMaximum()) >= 60
                && percent(score.volumeScore(), profile.volumeMaximum()) >= 80
                && percent(score.momentumScore(), profile.momentumMaximum()) >= 85;
        if (!strongTechnicalBreakout || !higherTimeframeSafe || !atrAllowsEntry || !profile.entryAllowed()) {
            return score;
        }
        return new StrategyScoreResult(
                score.trendScore(), score.volumeScore(), score.momentumScore(),
                score.sentimentScore(), score.fundamentalScore(), score.rawScore(),
                score.maximumScore(), score.normalizedScore(), SignalDecision.BUY);
    }

    private int percent(int score, int maximum) {
        return maximum <= 0 ? 0 : (int) Math.round(score * 100.0 / maximum);
    }

    private StrategyProfile profile(TradingStrategy strategy, DynamicStrategyProperties.Profile p,
                                    boolean entryAllowed, String explanation) {
        return new StrategyProfile(strategy, properties.getVersion(),
                p.getTrendMaximum(), p.getVolumeMaximum(), p.getMomentumMaximum(),
                p.getSentimentMaximum(), p.getFundamentalMaximum(),
                p.getStrongBuyThreshold(), p.getBuyThreshold(), p.getWatchThreshold(),
                p.getNeutralThreshold(), p.getSellThreshold(), entryAllowed, explanation);
    }

    private int scale(int score, int oldMaximum, int newMaximum) {
        if (oldMaximum <= 0 || newMaximum <= 0) return 0;
        return Math.max(0, Math.min(newMaximum,
                (int) Math.round(score * (double) newMaximum / oldMaximum)));
    }

    private SignalDecision decision(int score, StrategyProfile p) {
        if (score >= p.strongBuyThreshold()) return SignalDecision.STRONG_BUY;
        if (score >= p.buyThreshold()) return SignalDecision.BUY;
        if (score >= p.watchThreshold()) return SignalDecision.WATCH;
        if (score >= p.neutralThreshold()) return SignalDecision.NEUTRAL;
        if (score >= p.sellThreshold()) return SignalDecision.SELL;
        return SignalDecision.STRONG_SELL;
    }
}
