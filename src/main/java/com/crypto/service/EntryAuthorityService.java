package com.crypto.service;

import com.crypto.domain.BtcContextStatus;
import com.crypto.domain.LiquidityContextStatus;
import com.crypto.domain.MarketRegime;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradingStrategy;
import com.crypto.dto.*;
import com.crypto.execution.domain.EntryAuthority;
import com.crypto.execution.domain.EntryAuthorityDecision;
import com.crypto.execution.service.ExecutionReplayScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * FIX-091 / Fix 5: Replay-only RANGE -> BREAKOUT transition authority.
 * It reuses existing structural evidence and only grants a maximum probe size.
 * It never executes a trade and never bypasses ExecutionIntelligenceService.validateBuy().
 */
@Service
public class EntryAuthorityService {
    private final ExecutionReplayScope replayScope;
    private final int minScore;
    private final BigDecimal minRvol;
    private final int probePercent;

    public EntryAuthorityService(ExecutionReplayScope replayScope,
                                 @Value("${analysis.breakout-transition.min-score:80}") int minScore,
                                 @Value("${analysis.breakout-transition.min-rvol:1.50}") BigDecimal minRvol,
                                 @Value("${analysis.breakout-transition.probe-percent:25}") int probePercent) {
        this.replayScope = replayScope;
        this.minScore = minScore;
        this.minRvol = minRvol;
        this.probePercent = Math.max(20, Math.min(30, probePercent));
    }

    public EntryAuthorityDecision evaluate(IndicatorSnapshot indicator,
                                           RegimeStateService.Decision regimeState,
                                           StrategyProfile profile,
                                           StrategyScoreResult score,
                                           TrendStructureResult structure,
                                           AtrRiskAssessment atr,
                                           MultiTimeframeConfluenceResult confluence,
                                           BtcMarketContextResult btc,
                                           OrderBookLiquidityResult liquidity) {
        if (!replayScope.active() || indicator == null || regimeState == null || profile == null || score == null) {
            return EntryAuthorityDecision.normal();
        }
        boolean rangeBoundary = regimeState.confirmedRegime() == MarketRegime.RANGE
                && profile.strategy() == TradingStrategy.RANGE_MEAN_REVERSION;
        if (!rangeBoundary) return EntryAuthorityDecision.normal();

        boolean bullishDecision = score.decision() == SignalDecision.BUY || score.decision() == SignalDecision.STRONG_BUY;
        boolean structural = bullishDecision
                && score.normalizedScore() >= minScore
                && structure != null && structure.bullishExpansionConfirmed() && structure.continuationSupported()
                && indicator.relativeVolume() != null && indicator.relativeVolume().compareTo(minRvol) >= 0
                && indicator.macdHistogram() != null && indicator.macdHistogram().signum() > 0
                && indicator.ema20() != null && indicator.latestPrice().compareTo(indicator.ema20()) > 0
                && atr != null && atr.immediateEntryAllowed();
        if (!structural) return EntryAuthorityDecision.normal();

        boolean htfSafe = confluence != null && confluence.entryAllowed()
                && confluence.higherTimeframeDecision() != SignalDecision.SELL
                && confluence.higherTimeframeDecision() != SignalDecision.STRONG_SELL;
        boolean btcSafe = btc != null && btc.entryAllowed()
                && btc.contextStatus() != BtcContextStatus.CONFLICT
                && btc.contextStatus() != BtcContextStatus.STRONG_CONFLICT;
        boolean liquiditySafe = liquidity != null && liquidity.entryAllowed()
                && liquidity.status() != LiquidityContextStatus.UNAVAILABLE
                && liquidity.status() != LiquidityContextStatus.LEARNING
                && liquidity.status() != LiquidityContextStatus.INSUFFICIENT_DATA_HOLD
                && liquidity.status() != LiquidityContextStatus.TARGET_BLOCKED
                && liquidity.status() != LiquidityContextStatus.THIN_LIQUIDITY;
        boolean safetyComplete = htfSafe && btcSafe && liquiditySafe;

        String explanation = "Verified RANGE->BREAKOUT structural transition candidate: score="
                + score.normalizedScore() + ", RVOL=" + indicator.relativeVolume()
                + ", bullishExpansionConfirmed=true, continuationSupported=true, MACD histogram="
                + indicator.macdHistogram() + ". Probe authority is capped at " + probePercent
                + "%; validateBuy remains mandatory. Safety complete=" + safetyComplete + ".";
        return new EntryAuthorityDecision(EntryAuthority.TRANSITION_PROBE, probePercent, true,
                safetyComplete, safetyComplete ? "BREAKOUT_TRANSITION_PROBE" : "BREAKOUT_TRANSITION_SAFETY_INCOMPLETE",
                explanation);
    }
}
