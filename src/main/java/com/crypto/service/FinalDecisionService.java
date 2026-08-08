package com.crypto.service;

import com.crypto.domain.BtcContextStatus;
import com.crypto.domain.ConfluenceStatus;
import com.crypto.domain.DecisionAdjustmentType;
import com.crypto.domain.LiquidityContextStatus;
import com.crypto.domain.SignalDecision;
import com.crypto.dto.AtrRiskAssessment;
import com.crypto.dto.BtcMarketContextResult;
import com.crypto.dto.DecisionAdjustment;
import com.crypto.dto.FinalDecisionResult;
import com.crypto.dto.MarketContextSnapshot;
import com.crypto.dto.MarketRegimeAssessment;
import com.crypto.dto.MultiTimeframeConfluenceResult;
import com.crypto.dto.OrderBookLiquidityResult;
import com.crypto.dto.StrategyProfile;
import com.crypto.dto.DerivativesPositioningResult;
import com.crypto.domain.DerivativesPositioningStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class FinalDecisionService {

    public FinalDecisionResult decide(
            SignalDecision baseDecision,
            SignalDecision atrAdjustedDecision,
            AtrRiskAssessment atrRisk,
            StrategyProfile strategy,
            MarketRegimeAssessment regime,
            MarketContextSnapshot context,
            MultiTimeframeConfluenceResult confluence,
            BtcMarketContextResult btcContext,
            DerivativesPositioningResult derivatives,
            OrderBookLiquidityResult liquidity
    ) {
        List<DecisionAdjustment> path = new ArrayList<>();
        SignalDecision current = baseDecision;
        boolean entryAllowed = strategy.entryAllowed();
        int sequence = 1;

        path.add(new DecisionAdjustment(
                sequence++, "STRATEGY_SCORE", DecisionAdjustmentType.BASE,
                baseDecision, baseDecision, true, entryAllowed,
                "The selected " + strategy.strategy() + " profile produced the isolated decision."
        ));

        if (!context.dataValid()) {
            SignalDecision before = current;
            current = safeNeutral(current);
            entryAllowed = false;
            path.add(new DecisionAdjustment(
                    sequence++, "DATA_QUALITY", DecisionAdjustmentType.VETO,
                    before, current, true, false,
                    "Required market data was incomplete or invalid; new entry was blocked."
            ));
        } else {
            path.add(new DecisionAdjustment(
                    sequence++, "DATA_QUALITY", DecisionAdjustmentType.PASS,
                    current, current, entryAllowed, entryAllowed,
                    "Required technical and contextual market data passed validation."
            ));
        }

        if (atrAdjustedDecision != baseDecision) {
            SignalDecision before = current;
            current = atrAdjustedDecision;
            entryAllowed = false;
            path.add(new DecisionAdjustment(
                    sequence++, "ATR_RISK", DecisionAdjustmentType.VETO,
                    before, current, true, false,
                    atrRisk.explanation()
            ));
        } else if (!atrRisk.immediateEntryAllowed() && isBullish(current)) {
            // Preserve the BUY recommendation, but do not execute at the current price.
            // The dashboard exposes PULLBACK_ENTRY or WAIT_FOR_RETRACEMENT.
            boolean entryBefore = entryAllowed;
            entryAllowed = false;
            path.add(new DecisionAdjustment(
                    sequence++, "ATR_RISK", DecisionAdjustmentType.DOWNGRADE,
                    current, current, entryBefore, false,
                    atrRisk.explanation()
            ));
        } else {
            path.add(new DecisionAdjustment(
                    sequence++, "ATR_RISK", DecisionAdjustmentType.PASS,
                    current, current, entryAllowed, entryAllowed,
                    atrRisk.explanation()
            ));
        }

        current = appendExternalAdjustment(path, sequence++, "MULTI_TIMEFRAME", current,
                confluence.finalDecision(), entryAllowed,
                entryAllowed && confluence.entryAllowed(), confluence.explanation(),
                confluence.status() == ConfluenceStatus.UNAVAILABLE);
        entryAllowed = entryAllowed && confluence.entryAllowed();

        current = appendExternalAdjustment(path, sequence++, "BTC_CONTEXT", current,
                btcContext.finalDecision(), entryAllowed,
                entryAllowed && btcContext.entryAllowed(), btcContext.explanation(),
                btcContext.contextStatus() == BtcContextStatus.UNAVAILABLE
                        || btcContext.contextStatus() == BtcContextStatus.LEARNING);
        entryAllowed = entryAllowed && btcContext.entryAllowed();

        current = appendExternalAdjustment(path, sequence++, "DERIVATIVES_POSITIONING", current,
                derivatives.finalDecision(), entryAllowed,
                entryAllowed && derivatives.entryAllowed(), derivatives.explanation(),
                derivatives.status() == DerivativesPositioningStatus.UNAVAILABLE
                        || derivatives.status() == DerivativesPositioningStatus.LEARNING
                        || derivatives.status() == DerivativesPositioningStatus.NOT_APPLICABLE);
        entryAllowed = entryAllowed && derivatives.entryAllowed();

        current = appendExternalAdjustment(path, sequence, "ORDER_BOOK", current,
                liquidity.finalDecision(), entryAllowed,
                entryAllowed && liquidity.entryAllowed(), liquidity.explanation(),
                liquidity.status() == LiquidityContextStatus.UNAVAILABLE
                        || liquidity.status() == LiquidityContextStatus.LEARNING);
        entryAllowed = entryAllowed && liquidity.entryAllowed();

        if (!strategy.entryAllowed() && isBullish(current)) {
            current = SignalDecision.WATCH;
            entryAllowed = false;
        }

        int confidence = confidence(regime, context, confluence, btcContext, derivatives, liquidity, entryAllowed);
        String explanation = "FinalDecisionService applied " + path.size()
                + " ordered checks. Confidence " + confidence + "/100. Entry "
                + (entryAllowed ? "allowed" : "blocked") + ".";

        return new FinalDecisionResult(
                baseDecision,
                current,
                entryAllowed,
                confidence,
                List.copyOf(path),
                explanation
        );
    }

    private SignalDecision appendExternalAdjustment(
            List<DecisionAdjustment> path,
            int sequence,
            String source,
            SignalDecision current,
            SignalDecision serviceDecision,
            boolean entryBefore,
            boolean entryAfter,
            String reason,
            boolean unavailable
    ) {
        SignalDecision after = serviceDecision == null ? current : serviceDecision;
        DecisionAdjustmentType type;
        if (unavailable) {
            type = DecisionAdjustmentType.UNAVAILABLE;
        } else if (after != current || (entryBefore && !entryAfter)) {
            type = entryBefore && !entryAfter ? DecisionAdjustmentType.VETO : DecisionAdjustmentType.DOWNGRADE;
        } else {
            type = DecisionAdjustmentType.PASS;
        }
        path.add(new DecisionAdjustment(
                sequence, source, type, current, after,
                entryBefore, entryAfter, reason
        ));
        return after;
    }

    private int confidence(
            MarketRegimeAssessment regime,
            MarketContextSnapshot context,
            MultiTimeframeConfluenceResult confluence,
            BtcMarketContextResult btc,
            DerivativesPositioningResult derivatives,
            OrderBookLiquidityResult liquidity,
            boolean entryAllowed
    ) {
        double value = regime.confidence() * 0.25;
        value += (context.dataValid() ? 100 : 0) * 0.15;
        value += confluenceConfidence(confluence.status()) * 0.20;
        value += btcConfidence(btc.contextStatus(), btc.stable()) * 0.15;
        value += liquidityConfidence(liquidity.status(), liquidity.observations()) * 0.12;
        value += derivativesConfidence(derivatives) * 0.08;
        value += sentimentConfidence(context) * 0.10;
        int result = (int) Math.round(value);
        if (!entryAllowed) {
            result = Math.min(result, 49);
        }
        return Math.max(0, Math.min(100, result));
    }

    private int confluenceConfidence(ConfluenceStatus status) {
        return switch (status) {
            case STRONG_AGREEMENT -> 100;
            case AGREEMENT -> 90;
            case MIXED -> 60;
            case CONFLICT -> 30;
            case STRONG_CONFLICT -> 10;
            case UNAVAILABLE -> 40;
        };
    }

    private int btcConfidence(BtcContextStatus status, boolean stable) {
        int base = switch (status) {
            case CONFIRMED -> 95;
            case NOT_APPLICABLE -> 85;
            case NEUTRAL -> 75;
            case LEARNING -> 50;
            case CONFLICT -> 30;
            case STRONG_CONFLICT -> 10;
            case UNAVAILABLE -> 40;
        };
        return stable ? base : Math.max(0, base - 10);
    }

    private int liquidityConfidence(LiquidityContextStatus status, int observations) {
        int base = switch (status) {
            case BULLISH_SUPPORT -> 95;
            case BALANCED, DISABLED -> 85;
            case LEARNING -> 55;
            case UNAVAILABLE -> 40;
            case BEARISH_PRESSURE, STOP_EXPOSED -> 35;
            case TARGET_BLOCKED, THIN_LIQUIDITY -> 15;
            case WALL_WEAKENING -> 65;
        };
        if (observations <= 0 && status != LiquidityContextStatus.DISABLED) {
            return Math.min(base, 40);
        }
        return base;
    }

    private int derivativesConfidence(DerivativesPositioningResult result) {
        int base = switch (result.status()) {
            case FRESH_LONG_BUILDUP, FRESH_SHORT_BUILDUP -> 95;
            case HEALTHY_BULLISH, HEALTHY_BEARISH, BALANCED, NOT_APPLICABLE -> 80;
            case SHORT_COVERING, LONG_LIQUIDATION, LOW_CONVICTION -> 55;
            case LONGS_CROWDED, SHORTS_CROWDED -> 25;
            case LEARNING -> 50;
            case UNAVAILABLE -> 40;
        };
        return Math.max(0, Math.min(100, base + result.confidenceAdjustment()));
    }

    private int sentimentConfidence(MarketContextSnapshot context) {
        if (!context.sentimentEnabled()) {
            return 70;
        }
        BigDecimal coverage = context.sentimentCoverage();
        if (coverage == null) {
            return 40;
        }
        BigDecimal percent = coverage.compareTo(BigDecimal.ONE) <= 0
                ? coverage.multiply(BigDecimal.valueOf(100))
                : coverage;
        return percent.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private SignalDecision safeNeutral(SignalDecision decision) {
        return isBullish(decision) ? SignalDecision.WATCH : SignalDecision.NEUTRAL;
    }

    private boolean isBullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }
}
