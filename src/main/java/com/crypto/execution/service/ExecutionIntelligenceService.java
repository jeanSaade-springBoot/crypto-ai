package com.crypto.execution.service;

import com.crypto.config.TradingProperties;
import com.crypto.domain.LiquidityContextStatus;
import com.crypto.domain.MarketRegime;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.domain.TradingStrategy;
import com.crypto.execution.domain.ExecutionOpportunity;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.service.OpportunityConsolidationService;
import com.crypto.service.TradeExecutionValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Single execution-decision layer between TradeSignal generation and wallet execution.
 *
 * AnalysisService remains the only producer of TradeSignal rows. This service never creates
 * or mutates a market signal. It decides whether the CURRENT 1m signal should be executed,
 * accumulated as evidence, held, or rejected, and records one auditable opportunity lifecycle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionIntelligenceService {

    private static final String EXECUTION_INTERVAL = "1m";
    private static final String CONFIRMATION_INTERVAL = "5m";
    private static final String TREND_INTERVAL = "1h";
    private static final Duration EVIDENCE_WINDOW = Duration.ofMinutes(30);
    private static final Duration FIVE_MINUTE_MAX_AGE = Duration.ofMinutes(20);
    private static final Duration ONE_HOUR_MAX_AGE = Duration.ofHours(3);
    private static final Duration SETUP_WAKEUP_1M_MAX_AGE = Duration.ofMinutes(2);

    private static final int WATCH_EVIDENCE_MIN_SCORE = 60;
    private static final int WATCH_EVIDENCE_MIN_CONFIDENCE = 60;
    private static final int MIN_EVIDENCE_SCORE = 7;
    private static final int STRONG_EVIDENCE_SCORE = 11;
    private static final int WEAK_SELL_EVIDENCE_PENALTY = 2;
    private static final int OPPORTUNITY_HEALTH_START = 50;
    private static final int OPPORTUNITY_HEALTH_MIN_TO_KEEP = 20;
    private static final int EVIDENCE_MOMENTUM_WINDOW = 6;
    private static final int EVIDENCE_MOMENTUM_CAP = 25;

    // Deferred continuation: a prior quality BUY that was blocked only by ATR can
    // remain actionable when the market confirms continuation instead of delivering
    // the requested pullback. This route is intentionally reduced-size and must use
    // the CURRENT signal risk plan; it never reuses the old BUY target/stop.
    private static final Duration DEFERRED_BUY_LOOKBACK = Duration.ofMinutes(30);
    private static final int DEFERRED_MIN_HEALTH = 65;
    private static final int DEFERRED_MIN_CURRENT_SCORE = 60;
    private static final int DEFERRED_MIN_CURRENT_CONFIDENCE = 65;
    private static final int DEFERRED_MIN_5M_SCORE = 65;
    private static final int DEFERRED_MIN_5M_CONFIDENCE = 70;
    private static final int DEFERRED_MIN_EVIDENCE_MOMENTUM = -10;
    private static final int DEFERRED_POSITION_PERCENT = 30;

    // Higher-timeframe transition entry: when a fresh 1h BUY confirms a recovering market,
    // a supportive 1m WATCH/BUY plus a fresh 5m WATCH/BUY can use a strictly reduced
    // position even when ATR is asking for a pullback. This prevents a strong transition
    // from being blocked for hours while still refusing bearish or low-health setups.
    private static final int TRANSITION_MIN_HEALTH = 80;
    private static final int TRANSITION_MIN_CURRENT_SCORE = 60;
    private static final int TRANSITION_MIN_CURRENT_CONFIDENCE = 65;
    private static final int TRANSITION_MIN_5M_SCORE = 65;
    private static final int TRANSITION_MIN_5M_CONFIDENCE = 65;
    private static final int TRANSITION_MIN_1H_SCORE = 75;
    private static final int TRANSITION_POSITION_PERCENT = 25;

    // Exceptional independent-strength probe. This is a global, symbol-agnostic
    // exception for rare 1m reversal impulses that are technically exceptional but
    // are vetoed only by BTC context. It never changes the technical score and it
    // never bypasses bearish 5m/1h context or the other hard risk gates.
    private static final int EXCEPTIONAL_PROBE_MIN_SCORE = 88;
    private static final int EXCEPTIONAL_PROBE_MIN_TREND = 17;
    private static final int EXCEPTIONAL_PROBE_MIN_MOMENTUM = 13;
    private static final int EXCEPTIONAL_PROBE_MIN_VOLUME = 15;
    private static final int EXCEPTIONAL_PROBE_MIN_SCORE_JUMP = 40;
    private static final int EXCEPTIONAL_PROBE_MAX_PRIOR_SCORE = 40;
    private static final Duration EXCEPTIONAL_PROBE_REVERSAL_LOOKBACK = Duration.ofMinutes(5);
    private static final int EXCEPTIONAL_PROBE_BTC_SELL_PERCENT = 15;
    private static final int EXCEPTIONAL_PROBE_BTC_STRONG_SELL_PERCENT = 10;

    // Pressure-probe side path. NORMAL BUY/MTF logic remains authoritative and is
    // evaluated first. This route exists only for WATCH/NEUTRAL 1m states after a
    // completed candle-level sequence proves: bullish burst -> rejection -> higher-low
    // retest -> pressure rebuild. It never rewrites a TradeSignal, never changes score
    // thresholds, never upgrades a final decision, and never grants normal/full size.
    //
    // Regression anchors:
    //  - SOLUSDT 2026-08-17 ~00:43-01:00 UTC: first burst rejected, retest held above
    //    the 74.43 structural low, buyers rebuilt, then the normal 5m BUY arrived later.
    //  - The earlier isolated 00:28 burst must NOT be enough by itself.
    //  - Existing SHIB pressure-probe behavior remains covered by the same generic path.
    private static final Duration PRESSURE_RECENT_5M_SETUP_LOOKBACK = Duration.ofMinutes(25);
    private static final int PRESSURE_PROBE_MIN_1M_SCORE = 65;
    private static final int PRESSURE_PROBE_MIN_1M_CONFIDENCE = 65;
    private static final int PRESSURE_PROBE_MIN_1M_TREND = 12;
    private static final int PRESSURE_PROBE_MIN_1M_VOLUME = 14;
    private static final int PRESSURE_PROBE_MIN_1M_MOMENTUM = 12;
    private static final int PRESSURE_PROBE_MIN_5M_SETUP_SCORE = 65;
    private static final int PRESSURE_PROBE_MIN_5M_SETUP_VOLUME = 14;
    private static final int PRESSURE_PROBE_MIN_5M_SETUP_MOMENTUM = 12;
    private static final int PRESSURE_PROBE_POSITION_PERCENT = 15;

    // Reversal retracement entry. ATR remains authoritative: an overextended BUY is
    // still not chased. Instead, a high-quality BUY may stay actionable for a short
    // window and execute only if price returns close to ATR's own retracement level
    // while the latest available 5m momentum remains constructive. This is a separate
    // execution route; it does not change normal BUY scoring or ATR thresholds.
    private static final Duration RETRACEMENT_SETUP_LOOKBACK = Duration.ofMinutes(12);
    private static final int RETRACEMENT_MIN_ORIGIN_SCORE = 78;
    private static final int RETRACEMENT_MIN_ORIGIN_TREND = 20;
    private static final int RETRACEMENT_MIN_ORIGIN_VOLUME = 16;
    private static final int RETRACEMENT_MIN_5M_MOMENTUM = 13;
    private static final int RETRACEMENT_MIN_5M_MACD_SCORE = 7;
    private static final int RETRACEMENT_MIN_5M_RSI_SCORE = 5;
    private static final double RETRACEMENT_TARGET_TOLERANCE_ATR = 0.35d;
    private static final double RETRACEMENT_MAX_OVERSHOOT_ATR = 0.75d;
    private static final int RETRACEMENT_POSITION_PERCENT = 20;
    // Post-bearish guard for BALANCED_EARLY only. Normal BUY paths are untouched.
    // Historical regression reason: ETHUSDT Trade #2 on 2026-08-11 17:15 KSA
    // entered at 1889.56 through BALANCED_EARLY only minutes after 5m SELL context,
    // while opportunity health was 38/100 and evidence was 0/7. The position exited
    // five minutes later at 1887.74. Keep health/evidence as hard shortcut-quality
    // requirements so future scenarios cannot silently reintroduce this failure mode.
    // If this guard conflicts with another proven scenario, compare that replay against
    // this regression case before weakening or removing these thresholds.
    // A single 5m WATCH immediately after a fresh 5m SELL is not enough to re-open risk.
    private static final Duration BALANCED_EARLY_BEARISH_LOOKBACK = Duration.ofMinutes(20);
    private static final int BALANCED_EARLY_POST_BEARISH_MIN_HEALTH = 60;
    private static final int BALANCED_EARLY_POST_BEARISH_MIN_EVIDENCE = 4;
    private static final int BALANCED_EARLY_POST_BEARISH_MIN_ENTRY_QUALITY = 60;
    private static final int BALANCED_EARLY_POST_BEARISH_REQUIRED_5M_RECOVERY = 2;
    private static final int BALANCED_EARLY_POST_BEARISH_MAX_POSITION = 25;
    private static final int RETRACEMENT_MIN_CURRENT_HEALTH = 45;
    private static final int RETRACEMENT_MIN_CURRENT_ENTRY_QUALITY = 50;
    private static final double RETRACEMENT_MIN_CURRENT_RR = 1.15d;

    // Breakout -> retracement handoff. Historical regression reason:
    // ETHUSDT 2026-08-11 11:54-12:00 KSA. The engine correctly detected a
    // BREAKOUT at 1880.20 with strong volume and ATR correctly refused to chase,
    // requesting a retracement near 1879.22. Price then reached that zone, ATR
    // returned to STANDARD_ENTRY, trend was 24/30 and momentum 15/15, but the
    // ordinary TREND_FOLLOWING score fell to WATCH because pullback-candle volume
    // cooled to 6/20. That forgot the volume confirmation already proven by the
    // breakout. Preserve the breakout thesis for a short window and revalidate the
    // retracement instead of requiring a second volume explosion. If this route
    // conflicts with another proven scenario, compare that scenario against this
    // exact regression before weakening these guards. Normal BUY scoring, ATR, MTF
    // and SELL logic remain unchanged.
    private static final Duration BREAKOUT_RETRACEMENT_LOOKBACK = Duration.ofMinutes(12);
    private static final int BREAKOUT_RETRACEMENT_MIN_ORIGIN_SCORE = 70;
    private static final int BREAKOUT_RETRACEMENT_MIN_ORIGIN_TREND = 15;
    private static final int BREAKOUT_RETRACEMENT_MIN_ORIGIN_VOLUME = 15;
    private static final int BREAKOUT_RETRACEMENT_MIN_ORIGIN_MOMENTUM = 10;
    private static final int BREAKOUT_RETRACEMENT_MIN_CURRENT_SCORE = 60;
    private static final int BREAKOUT_RETRACEMENT_MIN_CURRENT_CONFIDENCE = 65;
    private static final int BREAKOUT_RETRACEMENT_MIN_CURRENT_TREND = 18;
    private static final int BREAKOUT_RETRACEMENT_MIN_CURRENT_MOMENTUM = 13;
    private static final int BREAKOUT_RETRACEMENT_MIN_HEALTH = 60;
    private static final int BREAKOUT_RETRACEMENT_MIN_5M_MOMENTUM = 13;
    private static final int BREAKOUT_RETRACEMENT_MIN_ENTRY_QUALITY = 50;
    private static final double BREAKOUT_RETRACEMENT_MIN_RR = 1.15d;
    private static final double BREAKOUT_RETRACEMENT_TARGET_TOLERANCE_ATR = 0.40d;
    private static final double BREAKOUT_RETRACEMENT_MAX_OVERSHOOT_ATR = 0.80d;
    private static final int BREAKOUT_RETRACEMENT_POSITION_PERCENT = 30;
    private static final int BREAKOUT_RETRACEMENT_BEARISH_1H_POSITION_PERCENT = 20;

    // Progressive Position Building. These are portfolio-allocation stages, not
    // confidence shortcuts: a scout requires excellent price quality, confirmation
    // requires stronger evidence, and the final add requires trend continuation.
    private static final int SCOUT_TARGET_PERCENT = 20;
    private static final int CONFIRMATION_TARGET_PERCENT = 50;
    private static final int TREND_TARGET_PERCENT = 100;
    private static final int SCOUT_MIN_SIGNAL_SCORE = 60;
    private static final int SCOUT_MIN_CONFIDENCE = 75;
    private static final int SCOUT_MIN_ENTRY_QUALITY = 80;
    private static final int CONFIRMATION_MIN_ENTRY_QUALITY = 65;
    private static final int TREND_MIN_ENTRY_QUALITY = 60;
    private static final int CHASE_ENTRY_CUTOFF = 50;

    // Symbol-agnostic opportunity health weights. Higher timeframes carry more
    // information and signal quality scales every contribution.
    private static final int HEALTH_1M_BUY = 15;
    private static final int HEALTH_1M_WATCH = 5;
    private static final int HEALTH_1M_SELL = -15;
    private static final int HEALTH_1M_STRONG_SELL = -30;
    private static final int HEALTH_1M_NEUTRAL = -2;
    private static final int HEALTH_5M_BUY = 25;
    private static final int HEALTH_5M_WATCH = 10;
    private static final int HEALTH_5M_SELL = -25;
    private static final int HEALTH_5M_STRONG_SELL = -35;
    private static final int HEALTH_1H_BUY = 40;
    private static final int HEALTH_1H_WATCH = 15;
    private static final int HEALTH_1H_SELL = -40;
    private static final int HEALTH_1H_STRONG_SELL = -50;

    private final TradingProperties properties;
    private final TradeExecutionValidationService validationService;
    private final OpportunityConsolidationService consolidationService;
    private final TradeSignalRepository signalRepository;
    private final ExecutionOpportunityRepository opportunityRepository;
    private final PressureReadinessService pressureReadinessService;
    private final RecoveryTransitionService recoveryTransitionService;
    @Autowired(required = false)
    private ExecutionReplayScope replayScope;

    @Transactional
    public ExecutionDecision evaluateBuy(TradeSignal signal) {
        return evaluateBuy(signal, 0, "NONE");
    }

    /**
     * FIX-014 / XRPUSDT 2026-08-19:
     * A fresh 5m BUY transition may wake an already-existing, unexecuted BUY opportunity
     * whose latest 1m timing signal was deferred only by local ATR extension. The 5m signal
     * NEVER becomes wallet execution authority: it only asks the existing SETUP_TIMEFRAME_ATR
     * policy to re-evaluate the latest fresh 1m state. 1h WATCH/BUY authority, current 1m
     * non-bearish timing, all hard risk gates, the existing 5m ATR plan, and Entry Quality
     * remain mandatory. Normal 1m BUY behavior and all existing HTF/ATR routes are unchanged.
     */
    @Transactional
    public SetupWakeupEvaluation evaluateSetupTimeframeWakeup(
            TradeSignal fiveMinuteTrigger, int currentAllocationPercent) {
        if (currentAllocationPercent > 0 || fiveMinuteTrigger == null || fiveMinuteTrigger.getGeneratedAt() == null
                || !CONFIRMATION_INTERVAL.equals(fiveMinuteTrigger.getInterval())
                || !isBullish(fiveMinuteTrigger.getDecision())
                || !fiveMinuteTrigger.isFinalEntryAllowed()
                || !fiveMinuteTrigger.isAtrImmediateEntryAllowed()) {
            return SetupWakeupEvaluation.none();
        }

        // Only a real 5m transition can wake the opportunity. Repeated BUY candles remain
        // context-only so this hook cannot create duplicate execution pressure.
        TradeSignal previousFive = previousSignalBefore(
                fiveMinuteTrigger.getSymbol(), CONFIRMATION_INTERVAL, fiveMinuteTrigger.getGeneratedAt()).orElse(null);
        if (previousFive != null && isBullish(previousFive.getDecision())) {
            return SetupWakeupEvaluation.none();
        }

        ExecutionOpportunity opportunity = currentOpportunity(
                fiveMinuteTrigger.getSymbol(), List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .filter(o -> o.getExecutedAt() == null)
                .orElse(null);
        if (opportunity == null) return SetupWakeupEvaluation.none();

        TradeSignal current1m = latestSignalAtOrBefore(
                fiveMinuteTrigger.getSymbol(), EXECUTION_INTERVAL, fiveMinuteTrigger.getGeneratedAt()).orElse(null);
        if (current1m == null || current1m.getGeneratedAt() == null
                || Duration.between(current1m.getGeneratedAt(), fiveMinuteTrigger.getGeneratedAt())
                        .compareTo(SETUP_WAKEUP_1M_MAX_AGE) > 0) {
            return SetupWakeupEvaluation.none();
        }

        // This hook is only for the already-proven SETUP_TIMEFRAME_ATR gap: current 1m timing
        // must still be deferred by ATR. A normal immediate 1m BUY continues through evaluateBuy().
        if (current1m.isAtrImmediateEntryAllowed()
                || isBearish(current1m.getDecision())
                || isBearish(current1m.getOriginalDecision())
                || !current1m.isFinalEntryAllowed()) {
            return SetupWakeupEvaluation.none();
        }

        TradeSignal oneHour = latestSignalAtOrBefore(
                fiveMinuteTrigger.getSymbol(), TREND_INTERVAL, fiveMinuteTrigger.getGeneratedAt()).orElse(null);
        if (oneHour == null || oneHour.getGeneratedAt() == null
                || Duration.between(oneHour.getGeneratedAt(), fiveMinuteTrigger.getGeneratedAt()).compareTo(ONE_HOUR_MAX_AGE) > 0
                || !(oneHour.getDecision() == SignalDecision.WATCH || isBullish(oneHour.getDecision()))
                || isBearish(oneHour.getOriginalDecision())) {
            return SetupWakeupEvaluation.none();
        }

        HardRiskBlock hardBlock = nonAtrHardRiskBlock(current1m);
        if (hardBlock.blocked()) return SetupWakeupEvaluation.none();
        if (current1m.getLatestPrice() == null || current1m.getStopLoss() == null || current1m.getTakeProfit() == null
                || current1m.getStopLoss().signum() <= 0 || current1m.getTakeProfit().signum() <= 0) {
            return SetupWakeupEvaluation.none();
        }

        Evidence evidence = evidence(current1m);
        boolean supportiveCurrent = isSupportiveCurrentSignal(current1m);
        boolean healthyNeutralTiming = current1m.getDecision() == SignalDecision.NEUTRAL
                && Math.max(evidence.opportunityHealth(), opportunity.getOpportunityHealth()) >= 60
                && Math.max(evidence.evidenceScore(), opportunity.getEvidenceScore()) >= MIN_EVIDENCE_SCORE;
        if (!supportiveCurrent && !healthyNeutralTiming) return SetupWakeupEvaluation.none();

        if (isBearish(fiveMinuteTrigger.getOriginalDecision())
                || !fiveMinuteTrigger.isStrategyEntryAllowed()
                || !fiveMinuteTrigger.isBtcContextEntryAllowed()
                || !fiveMinuteTrigger.isDerivativesEntryAllowed()
                || !fiveMinuteTrigger.isLiquidityEntryAllowed()) {
            return SetupWakeupEvaluation.none();
        }

        ExecutionDecision decision = setupTimeframeAtrAuthorityDecision(current1m, evidence, fiveMinuteTrigger);
        EntryQuality quality = assessEntryQuality(current1m, fiveMinuteTrigger.getAtrAtSignal());
        decision = applyInitialEntryQualityGuard(decision, quality);
        if (!decision.allowed()) return new SetupWakeupEvaluation(current1m, decision);

        String explanation = decision.explanation()
                + " FIX-014 wake-up: fresh 5m BUY transition " + fiveMinuteTrigger.getId()
                + " re-evaluated existing opportunity " + opportunity.getId()
                + " using latest 1m signal " + current1m.getId()
                + "; 5m remains context/setup authority only and does not execute independently.";
        ExecutionDecision wakeupDecision = ExecutionDecision.allow(
                decision.source(), "SETUP_TIMEFRAME_WAKEUP", decision.positionPercent(), explanation, decision.evidence());
        saveOpportunity(current1m, evidence, "CONFIRMED", wakeupDecision.source(),
                wakeupDecision.positionPercent(), wakeupDecision.code(), wakeupDecision.explanation());
        return new SetupWakeupEvaluation(current1m, wakeupDecision);
    }

    /**
     * FIX-023 / ETHUSDT 2026-08-20 18:11-18:24 KSA:
     * A fresh 5m BUY transition may wake an already-live, unexecuted opportunity after
     * liquidity/confirmation improves. This is deliberately narrower than granting 5m
     * direct wallet authority: the actual execution price/risk plan remains the latest
     * fresh 1m signal, which must still be supportive, immediately executable, and free
     * of hard risk vetoes. The configured 5m/1h execution profile is revalidated here,
     * so HTF authority is preserved rather than bypassed.
     */
    @Transactional
    public SetupWakeupEvaluation evaluateConfirmedSetupWakeup(
            TradeSignal fiveMinuteTrigger, int currentAllocationPercent) {
        if (currentAllocationPercent > 0 || fiveMinuteTrigger == null || fiveMinuteTrigger.getGeneratedAt() == null
                || !CONFIRMATION_INTERVAL.equals(fiveMinuteTrigger.getInterval())
                || !isBullish(fiveMinuteTrigger.getDecision())
                || !fiveMinuteTrigger.isFinalEntryAllowed()
                || !fiveMinuteTrigger.isAtrImmediateEntryAllowed()
                || !fiveMinuteTrigger.isLiquidityEntryAllowed()) {
            return SetupWakeupEvaluation.none();
        }

        TradeSignal previousFive = previousSignalBefore(
                fiveMinuteTrigger.getSymbol(), CONFIRMATION_INTERVAL, fiveMinuteTrigger.getGeneratedAt()).orElse(null);
        if (previousFive != null && isBullish(previousFive.getDecision())) {
            return SetupWakeupEvaluation.none();
        }

        ExecutionOpportunity opportunity = currentOpportunity(
                fiveMinuteTrigger.getSymbol(), List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .filter(o -> o.getExecutedAt() == null)
                .orElse(null);
        if (opportunity == null) return SetupWakeupEvaluation.none();

        TradeSignal current1m = latestSignalAtOrBefore(
                fiveMinuteTrigger.getSymbol(), EXECUTION_INTERVAL, fiveMinuteTrigger.getGeneratedAt()).orElse(null);
        if (current1m == null || current1m.getGeneratedAt() == null
                || Duration.between(current1m.getGeneratedAt(), fiveMinuteTrigger.getGeneratedAt())
                        .compareTo(SETUP_WAKEUP_1M_MAX_AGE) > 0
                || !isSupportiveCurrentSignal(current1m)
                || !current1m.isFinalEntryAllowed()
                || !current1m.isAtrImmediateEntryAllowed()) {
            return SetupWakeupEvaluation.none();
        }

        HardRiskBlock hardBlock = nonAtrHardRiskBlock(current1m);
        if (hardBlock.blocked()) return SetupWakeupEvaluation.none();

        TradeExecutionValidationService.ValidationResult authority =
                validationService.validateBuyContext(fiveMinuteTrigger);
        if (!authority.allowed()) return SetupWakeupEvaluation.none();

        Evidence evidence = evidence(current1m);
        EntryQuality quality = assessEntryQuality(current1m, fiveMinuteTrigger.getAtrAtSignal());
        int basePercent = Math.min(25, authority.positionPercent());
        ExecutionDecision decision = applyInitialEntryQualityGuard(
                ExecutionDecision.allow(
                        "SETUP_CONFIRMATION_WAKEUP",
                        "FRESH_5M_CONFIRMATION",
                        basePercent,
                        "Fresh 5m BUY transition reactivated an existing unexecuted opportunity after the prior blocker cleared. "
                                + "Execution still uses latest 1m signal #" + current1m.getId()
                                + " at " + current1m.getLatestPrice() + "; 5m trigger #" + fiveMinuteTrigger.getId()
                                + " remains confirmation authority only. HTF profile=" + authority.code() + ".",
                        evidence),
                quality);
        if (!decision.allowed()) return new SetupWakeupEvaluation(current1m, decision);

        saveOpportunity(current1m, evidence, "CONFIRMED", decision.source(),
                decision.positionPercent(), decision.code(), decision.explanation());
        return new SetupWakeupEvaluation(current1m, decision);
    }

    @Transactional
    public ExecutionDecision evaluateBuy(TradeSignal signal, int currentAllocationPercent, String currentStage) {
        if (signal == null || signal.getGeneratedAt() == null) {
            return ExecutionDecision.reject("INVALID_SIGNAL", "Signal is missing required execution data.");
        }
        if (!EXECUTION_INTERVAL.equals(signal.getInterval())) {
            return ExecutionDecision.observe("CONTEXT_ONLY", "Only fresh 1m signals may trigger a BUY execution.");
        }

        if (isBearish(signal.getDecision())) {
            Evidence evidence = evidence(signal);
            if (isHardBearishReversal(signal, evidence)) {
                saveOpportunity(signal, evidence, "CANCELLED", "OPPORTUNITY_MEMORY", 0,
                        "BEARISH_REVERSAL",
                        "BUY opportunity cancelled because bearish evidence is strong or confirmed by 5m/1h context.");
                return ExecutionDecision.reject("BEARISH_REVERSAL",
                        "Current bearish evidence is strong enough to invalidate the BUY opportunity.", evidence);
            }

            if (evidence.opportunityHealth() < OPPORTUNITY_HEALTH_MIN_TO_KEEP) {
                saveOpportunity(signal, evidence, "CANCELLED", "OPPORTUNITY_MEMORY", 0,
                        "OPPORTUNITY_HEALTH_EXHAUSTED",
                        "BUY opportunity health fell below the minimum after bearish/aging penalties.");
                return ExecutionDecision.reject("OPPORTUNITY_HEALTH_EXHAUSTED",
                        "Opportunity memory decayed below the safe minimum and was cancelled.", evidence);
            }

            saveOpportunity(signal, evidence, "WEAKENING", "OPPORTUNITY_MEMORY", 0,
                    "SOFT_BEARISH_INTERRUPTION",
                    "A brief 1m bearish interruption reduced opportunity evidence instead of erasing it. "
                            + "Health=" + evidence.opportunityHealth() + "/100, evidence=" + evidence.evidenceScore()
                            + ". A new supportive signal is required before execution.");
            return ExecutionDecision.weakening("SOFT_BEARISH_INTERRUPTION",
                    "Bearish 1m evidence weakened the opportunity but did not invalidate supportive 5m/1h context.", evidence);
        }

        // Non-ATR safety vetoes remain absolute except for one deliberately tiny,
        // fully-auditable BTC-only exception. An exceptional independent-strength
        // reversal may take a 10-15% probe when BTC is bearish, but only if the
        // asset's own 5m/1h context is no longer bearish and every other hard gate
        // plus ATR immediate-entry permission still passes.
        HardRiskBlock nonAtrHardBlock = nonAtrHardRiskBlock(signal);
        if (nonAtrHardBlock.blocked()) {
            Evidence evidence = evidence(signal);
            ExecutionDecision exceptionalProbe = exceptionalStrengthProbeDecision(signal, evidence, nonAtrHardBlock);
            if (exceptionalProbe != null) {
                EntryQuality probeQuality = assessEntryQuality(signal);
                exceptionalProbe = applyExceptionalProbeEntryQualityGuard(exceptionalProbe, probeQuality);
                saveOpportunity(signal, evidence,
                        exceptionalProbe.allowed() ? "CONFIRMED" : exceptionalProbe.state(),
                        exceptionalProbe.source(), exceptionalProbe.positionPercent(),
                        exceptionalProbe.code(), exceptionalProbe.explanation());
                return exceptionalProbe;
            }
            saveOpportunity(signal, evidence, "BLOCKED", "HARD_RISK", 0,
                    nonAtrHardBlock.code(), nonAtrHardBlock.explanation());
            return ExecutionDecision.reject(nonAtrHardBlock.code(), nonAtrHardBlock.explanation(), evidence);
        }

        if (signal.getLatestPrice() == null || signal.getStopLoss() == null || signal.getTakeProfit() == null
                || signal.getStopLoss().signum() <= 0 || signal.getTakeProfit().signum() <= 0) {
            Evidence evidence = evidence(signal);
            saveOpportunity(signal, evidence, "BLOCKED", "HARD_RISK", 0,
                    "MISSING_RISK_PLAN", "Execution requires a fresh entry price, stop loss, and take-profit plan.");
            return ExecutionDecision.reject("MISSING_RISK_PLAN",
                    "Execution requires a fresh entry price, stop loss, and take-profit plan.", evidence);
        }

        // Evaluate deferred continuation BEFORE the ordinary ATR immediate-entry veto.
        // This route exists specifically for a previous analytically valid BUY that was
        // deferred by ATR waiting for a pullback. It still requires a current risk plan,
        // supportive 1m/5m/1h context, healthy opportunity state and >= 1:1 current R/R.
        Evidence evidence = evidence(signal);
        EntryQuality entryQuality = assessEntryQuality(signal);

        ExecutionDecision progressive = progressivePositionDecision(
                signal, evidence, entryQuality, currentAllocationPercent, currentStage);
        if (progressive != null) {
            saveOpportunity(signal, evidence,
                    progressive.allowed() ? "CONFIRMED" : progressive.state(),
                    progressive.source(), progressive.positionPercent(),
                    progressive.code(), progressive.explanation());
            return progressive;
        }

        // When a wallet position is already open, only an explicit progressive add
        // may increase exposure. Normal initial-entry routes are not re-run.
        if (currentAllocationPercent > 0) {
            return ExecutionDecision.observe(
                    "POSITION_BUILDING_HOLD",
                    "An open position already exists at " + currentAllocationPercent
                            + "% allocation. No additional stage qualified on this signal.",
                    evidence);
        }

        // Breakout retracement handoff runs before the generic reversal retracement route.
        // It remembers a recent BREAKOUT/WAIT_FOR_RETRACEMENT setup, so the pullback
        // candle does not have to recreate the breakout's volume spike. Production and
        // replay both reach this exact method through the shared evaluateBuy path.
        ExecutionDecision breakoutRetracement = breakoutRetracementDecision(signal, evidence);
        if (breakoutRetracement != null) {
            saveOpportunity(signal, evidence,
                    breakoutRetracement.allowed() ? "CONFIRMED" : breakoutRetracement.state(),
                    breakoutRetracement.source(), breakoutRetracement.positionPercent(),
                    breakoutRetracement.code(), breakoutRetracement.explanation());
            return breakoutRetracement;
        }

        // ATR retracement handoff: preserve a recent, high-quality BUY that ATR refused
        // to chase and execute only when price actually returns to ATR's own requested
        // retracement zone. The current 1m candle may be WATCH/NEUTRAL during the pullback;
        // the original thesis is revalidated with as-of 5m context instead of requiring
        // another BUY after price has already started moving again.
        ExecutionDecision retracement = reversalRetracementDecision(signal, evidence);
        if (retracement != null) {
            saveOpportunity(signal, evidence,
                    retracement.allowed() ? "CONFIRMED" : retracement.state(),
                    retracement.source(), retracement.positionPercent(),
                    retracement.code(), retracement.explanation());
            return retracement;
        }

        ExecutionDecision deferred = deferredContinuationDecision(signal, evidence);
        if (deferred != null) {
            deferred = applyInitialEntryQualityGuard(deferred, entryQuality);
            saveOpportunity(signal, evidence,
                    deferred.allowed() ? "CONFIRMED" : deferred.state(),
                    deferred.source(), deferred.positionPercent(),
                    deferred.code(), deferred.explanation());
            return deferred;
        }

        // A fresh 1h BUY plus supportive 5m/1m transition can justify a strictly reduced
        // entry even when ATR is still extended. This is intentionally evaluated before
        // the ordinary 5m-BUY ATR fallback because the 5m may still be WATCH during the
        // first minutes of a genuine higher-timeframe reversal.
        ExecutionDecision transition = higherTimeframeTransitionDecision(signal, evidence);
        if (transition != null) {
            TradeSignal five = latestAtOrBefore(signal, CONFIRMATION_INTERVAL, FIVE_MINUTE_MAX_AGE);
            EntryQuality transitionQuality = assessEntryQuality(signal, five == null ? signal.getAtrAtSignal() : five.getAtrAtSignal());
            transition = applyTransitionEntryQualityGuard(transition, transitionQuality);
            saveOpportunity(signal, evidence,
                    transition.allowed() ? "CONFIRMED" : transition.state(),
                    transition.source(), transition.positionPercent(),
                    transition.code(), transition.explanation());
            return transition;
        }

        // A valid 5m BUY owns setup-level ATR risk. The 1m ATR still controls timing,
        // but it must not permanently kill a fresh 5m setup whose own ATR explicitly
        // permits immediate/reduced entry. If a fresh 1h context exists and is bearish,
        // it still vetoes this route. Missing 1h context does not manufacture a veto.
        TradeSignal setupAtrSignal = setupTimeframeAtrAuthoritySignal(signal, evidence);
        if (setupAtrSignal != null) {
            ExecutionDecision setupAtrAuthority = setupTimeframeAtrAuthorityDecision(signal, evidence, setupAtrSignal);
            // Entry-quality ATR extension must use the same setup timeframe that owns
            // ATR risk. Using the much smaller 1m ATR here can falsely label a valid
            // 5m setup as a 9-10 ATR chase even when the 5m risk plan permits entry.
            EntryQuality setupEntryQuality = assessEntryQuality(signal, setupAtrSignal.getAtrAtSignal());
            setupAtrAuthority = applyInitialEntryQualityGuard(setupAtrAuthority, setupEntryQuality);
            saveOpportunity(signal, evidence,
                    setupAtrAuthority.allowed() ? "CONFIRMED" : setupAtrAuthority.state(),
                    setupAtrAuthority.source(), setupAtrAuthority.positionPercent(),
                    setupAtrAuthority.code(), setupAtrAuthority.explanation());
            return setupAtrAuthority;
        }

        // For every remaining non-continuation path, ATR immediate-entry permission remains mandatory.
        if (!signal.isAtrImmediateEntryAllowed()) {
            saveOpportunity(signal, evidence, "BLOCKED", "HARD_RISK", 0,
                    "ATR_ENTRY_BLOCKED", "ATR risk controls do not allow immediate entry.");
            return ExecutionDecision.reject("ATR_ENTRY_BLOCKED",
                    "ATR risk controls do not allow immediate entry.", evidence);
        }

        // Fast path: a normal fresh BUY can execute immediately using the configured profile.
        if (isDirectBuyCandidate(signal)) {
            TradeExecutionValidationService.ValidationResult validation = validationService.validateBuy(signal);
            if (validation.allowed()) {
                ExecutionDecision postBearishGuard = balancedEarlyPostBearishGuard(signal, evidence, entryQuality, validation);
                if (postBearishGuard != null) {
                    saveOpportunity(signal, evidence,
                            postBearishGuard.allowed() ? "CONFIRMED" : postBearishGuard.state(),
                            postBearishGuard.source(), postBearishGuard.positionPercent(),
                            postBearishGuard.code(), postBearishGuard.explanation());
                    return postBearishGuard;
                }

                // FIX-041 / ACEUSDT 2026-08-22 13:28 KSA:
                // BALANCED_EARLY is an early-entry profile by definition. If the independent
                // Entry Quality model already classifies the price as LATE_ENTRY, executing
                // this route would contradict its purpose and can turn a well-detected trend
                // into a chase near exhaustion. Keep every other proven initial-entry route
                // unchanged: setup-timeframe ATR authority, confirmation wake-up, accumulated
                // evidence, recovery probes and normal non-BALANCED_EARLY BUYs still use the
                // existing quality guard. Production and Replay share this exact method.
                if ("BALANCED_EARLY".equals(validation.code())
                        && "LATE_ENTRY".equals(entryQuality.classification())) {
                    ExecutionDecision lateBalancedEarly = ExecutionDecision.building(
                            "BALANCED_EARLY_LATE_ENTRY_BLOCKED",
                            "BALANCED_EARLY requires an actually early price. Entry Quality is "
                                    + entryQuality.score() + "/100 (" + entryQuality.classification()
                                    + "), so the opportunity stays alive for a better price or fresh "
                                    + "confirmation instead of opening a new late-stage position.",
                            evidence);
                    saveOpportunity(signal, evidence, lateBalancedEarly.state(),
                            "OPPORTUNITY_MEMORY", 0, lateBalancedEarly.code(), lateBalancedEarly.explanation());
                    return lateBalancedEarly;
                }

                ExecutionDecision guarded = applyInitialEntryQualityGuard(
                        ExecutionDecision.allow(
                                "IMMEDIATE_VALIDATION",
                                validation.code(),
                                validation.positionPercent(),
                                validation.explanation(),
                                evidence
                        ),
                        entryQuality);
                saveOpportunity(signal, evidence,
                        guarded.allowed() ? "CONFIRMED" : guarded.state(),
                        guarded.source(), guarded.positionPercent(),
                        guarded.code(), guarded.explanation());
                return guarded;
            }

            // Preserve the existing BUY-only persistence model as the first consolidation route.
            OpportunityConsolidationService.Assessment consolidated = consolidationService.evaluate(signal);
            if (consolidated.allowed()) {
                ExecutionDecision guarded = applyInitialEntryQualityGuard(
                        ExecutionDecision.allow(
                                "CONSOLIDATED_BUY",
                                consolidated.code(),
                                consolidated.positionPercent(),
                                consolidated.explanation(),
                                evidence
                        ),
                        entryQuality);
                saveOpportunity(signal, evidence,
                        guarded.allowed() ? "CONFIRMED" : guarded.state(),
                        guarded.source(), guarded.positionPercent(),
                        guarded.code(), guarded.explanation());
                return guarded;
            }
        }

        // EARLY-ENTRY PATH (separate from the normal BUY path).
        // It is reached only after every existing normal/special initial-entry route above
        // had the opportunity to act. A valid normal BUY therefore always keeps priority.
        // The pressure service reads closed candles as-of this signal timestamp; replay calls
        // this exact production method and therefore exercises the exact same logic.
        ExecutionDecision pressureProbe = pressureProbeDecision(signal, evidence);
        if (pressureProbe != null) {
            pressureProbe = applyInitialEntryQualityGuard(pressureProbe, entryQuality);
            saveOpportunity(signal, evidence,
                    pressureProbe.allowed() ? "CONFIRMED" : pressureProbe.state(),
                    pressureProbe.source(), pressureProbe.positionPercent(),
                    pressureProbe.code(), pressureProbe.explanation());
            return pressureProbe;
        }

        // FIX-026 / ENAUSDT 2026-08-20 12:55-13:04 KSA:
        // The market can transition from a recently bearish 1m state into absorption and
        // then a confirmed recovery before the ordinary strategy label leaves RANGE. The
        // historical ENA regression moved from STRONG_SELL 15 -> WATCH 75 while three
        // CLOSED candles printed 85.26%, 87.47% and 73.91% taker-buy pressure and rising
        // closes. Waiting for a later conventional BUY gave away the early edge. This route
        // does NOT lower BUY thresholds or rewrite RANGE: it allows only a 25% recovery probe
        // after (a) a recent bearish state, (b) a closed-candle absorption/recovery sequence,
        // (c) strong current technical recovery, and (d) all existing ATR/context hard gates.
        // RecoveryTransitionService selects candles by close_time <= signal.generated_at, so
        // Production and Proven/Replay cannot inspect the still-open or future candle.
        ExecutionDecision recoveryTransition = recoveryTransitionDecision(signal, evidence);
        if (recoveryTransition != null) {
            recoveryTransition = applyInitialEntryQualityGuard(recoveryTransition, entryQuality);
            saveOpportunity(signal, evidence,
                    recoveryTransition.allowed() ? "CONFIRMED" : recoveryTransition.state(),
                    recoveryTransition.source(), recoveryTransition.positionPercent(),
                    recoveryTransition.code(), recoveryTransition.explanation());
            return recoveryTransition;
        }

        // Intelligent evidence path: BUY and strong WATCH observations can build one opportunity.
        ExecutionDecision accumulated = applyInitialEntryQualityGuard(
                accumulatedDecision(signal, evidence), entryQuality);
        saveOpportunity(signal, evidence,
                accumulated.allowed() ? "CONFIRMED" : accumulated.state(),
                accumulated.source(), accumulated.positionPercent(),
                accumulated.code(), accumulated.explanation());
        return accumulated;
    }

    @Transactional
    public void markExecuted(TradeSignal signal, ExecutionDecision decision) {
        if (signal == null || decision == null || !decision.allowed()) return;
        currentOpportunity(signal.getSymbol(), List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .ifPresent(opportunity -> {
                    boolean stillBuilding = "SCOUT_ENTRY".equals(decision.source())
                            || "PRESSURE_PROBE_ENTRY".equals(decision.source())
                            || "CONFIRMATION_ADD".equals(decision.source());
                    opportunity.setStatus(stillBuilding ? "BUILDING" : "EXECUTED");
                    opportunity.setExecutionSource(decision.source());
                    opportunity.setRecommendedPositionPercent(decision.positionPercent());
                    opportunity.setDecisionCode(decision.code());
                    opportunity.setDecisionExplanation(decision.explanation());
                    opportunity.setLatestSignal(signal);
                    opportunity.setLastEvidenceAt(signal.getGeneratedAt());
                    if (!stillBuilding) {
                        opportunity.setExecutedAt(Instant.now());
                    }
                    opportunity.setUpdatedAt(Instant.now());
                    saveOpportunityEntity(opportunity);
                });
    }


    /**
     * FIX-020 / ENAUSDT 2026-08-20:
     * A fully closed position is an immutable evidence boundary. Scout/probe/confirmation
     * opportunities intentionally remain BUILDING while the SAME position is open, but once
     * that position reaches any terminal exit (TP, SL, SIGNAL_SELL, profit lock, manual close),
     * its pre-exit evidence may never finance a brand-new position. Replay calls this exact
     * method too, so production and Proven/Regression share the same lifecycle semantics.
     */
    @Transactional
    public void completePositionOpportunity(String symbol, TradeSignal exitSignal, String closeReason) {
        if (symbol == null || symbol.isBlank()) return;
        currentOpportunity(symbol, List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .ifPresent(opportunity -> {
                    opportunity.setStatus("COMPLETED");
                    if (exitSignal != null) {
                        opportunity.setLatestSignal(exitSignal);
                        if (exitSignal.getGeneratedAt() != null) {
                            opportunity.setLastEvidenceAt(exitSignal.getGeneratedAt());
                        }
                    }
                    opportunity.setDecisionCode("POSITION_CLOSED_EVIDENCE_BOUNDARY");
                    opportunity.setDecisionExplanation(
                            "Position lifecycle completed"
                                    + (closeReason == null || closeReason.isBlank() ? "" : " via " + closeReason)
                                    + "; pre-exit opportunity evidence is consumed and cannot justify a new position.");
                    opportunity.setUpdatedAt(Instant.now());
                    saveOpportunityEntity(opportunity);
                });
    }



    /**
     * Entry quality is deliberately independent from signal quality. A market can become
     * more certain while simultaneously becoming a worse price to enter.
     */
    public EntryQuality assessEntryQuality(TradeSignal current) {
        return assessEntryQuality(current, current == null ? null : current.getAtrAtSignal());
    }

    private EntryQuality assessEntryQuality(TradeSignal current, BigDecimal authoritativeAtr) {
        if (current == null || current.getGeneratedAt() == null || current.getLatestPrice() == null
                || current.getLatestPrice().signum() <= 0) {
            return new EntryQuality(0, "UNKNOWN", 0d, 0d, 0d, 0L);
        }

        Instant cutoff = current.getGeneratedAt().minus(EVIDENCE_WINDOW);
        List<TradeSignal> recent = recentSignals(current.getSymbol(), EXECUTION_INTERVAL, current.getGeneratedAt());

        BigDecimal reference = current.getLatestPrice();
        for (TradeSignal s : recent) {
            if (s.getGeneratedAt() == null || s.getGeneratedAt().isAfter(current.getGeneratedAt())) continue;
            if (s.getGeneratedAt().isBefore(cutoff)) break;
            if (s.getLatestPrice() != null && s.getLatestPrice().signum() > 0
                    && s.getLatestPrice().compareTo(reference) < 0) {
                reference = s.getLatestPrice();
            }
        }

        double expansionPercent = current.getLatestPrice().subtract(reference)
                .divide(reference, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();

        double atrExtension = 0d;
        BigDecimal atrForExtension = authoritativeAtr != null && authoritativeAtr.signum() > 0
                ? authoritativeAtr
                : current.getAtrAtSignal();
        if (atrForExtension != null && atrForExtension.signum() > 0) {
            atrExtension = current.getLatestPrice().subtract(reference).max(BigDecimal.ZERO)
                    .divide(atrForExtension, 8, RoundingMode.HALF_UP).doubleValue();
        }

        long ageMinutes = currentOpportunity(current.getSymbol(), List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .map(o -> o.getStartedAt() == null ? 0L
                        : Math.max(0L, Duration.between(o.getStartedAt(), current.getGeneratedAt()).toMinutes()))
                .orElse(0L);

        double rr = currentRewardRisk(current);
        int score = 100;

        if (expansionPercent > 8d) score -= 50;
        else if (expansionPercent > 5d) score -= 35;
        else if (expansionPercent > 3d) score -= 22;
        else if (expansionPercent > 1.5d) score -= 10;

        if (atrExtension > 6d) score -= 30;
        else if (atrExtension > 4d) score -= 20;
        else if (atrExtension > 2.5d) score -= 10;

        if (ageMinutes > 60) score -= 15;
        else if (ageMinutes > 40) score -= 10;
        else if (ageMinutes > 20) score -= 5;

        if (rr <= 0d || rr < 1d) score -= 30;
        else if (rr < 1.25d) score -= 15;
        else if (rr >= 2d) score += 5;

        if ("HIGH_VOLATILITY".equals(String.valueOf(current.getMarketRegime()))) score -= 10;
        if ("BREAKOUT".equals(String.valueOf(current.getMarketRegime()))
                && expansionPercent <= 3d && atrExtension <= 2.5d) score += 5;

        score = Math.max(0, Math.min(100, score));
        String classification = score >= 85 ? "EXCELLENT_ENTRY"
                : score >= 70 ? "GOOD_ENTRY"
                : score >= 55 ? "ACCEPTABLE_ENTRY"
                : score >= CHASE_ENTRY_CUTOFF ? "LATE_ENTRY"
                : "CHASE_ENTRY";

        return new EntryQuality(score, classification, expansionPercent, atrExtension, rr, ageMinutes);
    }

    private ExecutionDecision progressivePositionDecision(
            TradeSignal current,
            Evidence e,
            EntryQuality q,
            int currentAllocationPercent,
            String currentStage) {

        TradeSignal five = latestAtOrBefore(current, CONFIRMATION_INTERVAL, FIVE_MINUTE_MAX_AGE);
        TradeSignal oneHour = latestAtOrBefore(current, TREND_INTERVAL, ONE_HOUR_MAX_AGE);
        boolean fiveSupportive = five != null
                && (five.getDecision() == SignalDecision.WATCH || isBullish(five.getDecision()))
                && five.getTotalScore() >= 60 && five.getConfidenceScore() >= 65;
        boolean oneHourSupportive = oneHour != null
                && (oneHour.getDecision() == SignalDecision.WATCH || isBullish(oneHour.getDecision()));

        if (currentAllocationPercent <= 0) {
            boolean scout = isSupportiveCurrentSignal(current)
                    && current.isAtrImmediateEntryAllowed()
                    && current.getTotalScore() >= SCOUT_MIN_SIGNAL_SCORE
                    && current.getConfidenceScore() >= SCOUT_MIN_CONFIDENCE
                    && q.score() >= SCOUT_MIN_ENTRY_QUALITY
                    && q.rewardRisk() >= 1.25d
                    && e.opportunityHealth() >= 55
                    && e.evidenceMomentum() >= -5
                    && fiveSupportive
                    && oneHourSupportive;
            if (scout) {
                return ExecutionDecision.allow(
                        "SCOUT_ENTRY",
                        "EXCELLENT_PRICE_SCOUT",
                        SCOUT_TARGET_PERCENT,
                        "Progressive Position Building opened a " + SCOUT_TARGET_PERCENT
                                + "% scout because signal quality is supportive while entry quality is "
                                + q.score() + "/100 (" + q.classification() + "). Price expansion="
                                + format(q.expansionPercent()) + "%, ATR extension=" + format(q.atrExtension())
                                + ", current R/R=" + format(q.rewardRisk())
                                + ". Capital remains deliberately small until confirmation improves.",
                        e);
            }
            return null;
        }

        if (!isSupportiveCurrentSignal(current) || !fiveSupportive || !oneHourSupportive
                || q.classification().equals("CHASE_ENTRY")) {
            return null;
        }

        if (currentAllocationPercent < CONFIRMATION_TARGET_PERCENT) {
            boolean evidenceConfirmed = e.evidenceScore() >= MIN_EVIDENCE_SCORE
                    && e.averageScore() >= 65
                    && e.averageConfidence() >= 65;
            boolean directConfirmed = isBullish(current.getDecision())
                    && current.getTotalScore() >= properties.minimumBuyScore();
            if ((evidenceConfirmed || directConfirmed)
                    && current.isAtrImmediateEntryAllowed()
                    && e.opportunityHealth() >= 60
                    && q.score() >= CONFIRMATION_MIN_ENTRY_QUALITY
                    && q.rewardRisk() >= 1.15d) {
                int add = CONFIRMATION_TARGET_PERCENT - currentAllocationPercent;
                return ExecutionDecision.allow(
                        "CONFIRMATION_ADD",
                        "PROGRESSIVE_CONFIRMATION_ADD",
                        add,
                        "Progressive Position Building added " + add
                                + "% after the scout was confirmed. Entry quality=" + q.score()
                                + "/100, evidence=" + e.evidenceScore()
                                + ", health=" + e.opportunityHealth()
                                + ", 5m=" + e.fiveMinute() + ", 1h=" + e.oneHour() + ".",
                        e);
            }
        }

        if (currentAllocationPercent >= CONFIRMATION_TARGET_PERCENT
                && currentAllocationPercent < TREND_TARGET_PERCENT) {
            boolean strongCurrent = isBullish(current.getDecision())
                    || (current.getDecision() == SignalDecision.WATCH
                    && current.getTotalScore() >= 72
                    && current.getConfidenceScore() >= 75);
            boolean strongFive = five != null
                    && (isBullish(five.getDecision()) || five.getDecision() == SignalDecision.WATCH)
                    && five.getTotalScore() >= 65
                    && five.getConfidenceScore() >= 70;
            if (strongCurrent && strongFive
                    && current.isAtrImmediateEntryAllowed()
                    && e.opportunityHealth() >= 70
                    && e.evidenceMomentum() >= 0
                    && q.score() >= TREND_MIN_ENTRY_QUALITY
                    && q.rewardRisk() >= 1.20d) {
                int add = TREND_TARGET_PERCENT - currentAllocationPercent;
                return ExecutionDecision.allow(
                        "TREND_ADD",
                        "PROGRESSIVE_TREND_ADD",
                        add,
                        "Progressive Position Building completed the position with a " + add
                                + "% trend add only after confirmation strengthened. Entry quality="
                                + q.score() + "/100, health=" + e.opportunityHealth()
                                + ", evidence momentum=" + e.evidenceMomentum()
                                + ", current R/R=" + format(q.rewardRisk()) + ".",
                        e);
            }
        }
        return null;
    }

    private ExecutionDecision applyInitialEntryQualityGuard(ExecutionDecision decision, EntryQuality q) {
        if (decision == null || !decision.allowed()) return decision;
        if (q.score() < CHASE_ENTRY_CUTOFF) {
            return ExecutionDecision.building(
                    "CHASE_ENTRY_BLOCKED",
                    "The market signal is valid, but Entry Quality is only " + q.score()
                            + "/100 (" + q.classification() + "). Price has already expanded "
                            + format(q.expansionPercent()) + "% from the recent opportunity base and "
                            + format(q.atrExtension()) + " ATR. The engine will not buy a late-stage chase.",
                    decision.evidence());
        }

        int cap = q.score() >= 85 ? 50 : q.score() >= 70 ? 40 : 25;
        int reduced = Math.min(decision.positionPercent(), cap);
        if (reduced == decision.positionPercent()) return decision;

        return ExecutionDecision.allow(
                decision.source(),
                decision.code(),
                reduced,
                decision.explanation() + " Entry Quality " + q.score() + "/100 ("
                        + q.classification() + ") capped the initial allocation at " + reduced
                        + "% so confirmation can add later instead of committing full size at once.",
                decision.evidence());
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }


    private ExecutionDecision exceptionalStrengthProbeDecision(
            TradeSignal current, Evidence e, HardRiskBlock block) {
        if (current == null || block == null || !block.blocked()) return null;
        if (!"BTC_CONTEXT_BLOCKED".equals(block.code())) return null;

        // BTC is allowed to reduce exposure here, not to manufacture extra score.
        // Every other risk authority remains mandatory.
        if (!current.isStrategyEntryAllowed()
                || !current.isDerivativesEntryAllowed()
                || !current.isLiquidityEntryAllowed()
                || !current.isAtrImmediateEntryAllowed()) return null;
        if (current.getLatestPrice() == null || current.getStopLoss() == null || current.getTakeProfit() == null
                || current.getStopLoss().signum() <= 0 || current.getTakeProfit().signum() <= 0) return null;

        if (current.getOriginalDecision() != SignalDecision.STRONG_BUY) return null;
        if (current.getDecision() != SignalDecision.WATCH && !isBullish(current.getDecision())) return null;
        if (current.getTotalScore() < EXCEPTIONAL_PROBE_MIN_SCORE
                || current.getTrendScore() < EXCEPTIONAL_PROBE_MIN_TREND
                || current.getMomentumScore() < EXCEPTIONAL_PROBE_MIN_MOMENTUM
                || current.getVolumeScore() < EXCEPTIONAL_PROBE_MIN_VOLUME) return null;

        // This route is specifically a reversal probe, not a generic high-score bypass.
        // Require a fresh bearish -> exceptional bullish acceleration on the same 1m stream.
        TradeSignal prior = recentBearishReversalAnchor(current);
        if (prior == null) return null;
        SignalDecision priorEffective = strongerDecision(prior.getDecision(), prior.getOriginalDecision());
        int scoreJump = current.getTotalScore() - prior.getTotalScore();
        if (scoreJump < EXCEPTIONAL_PROBE_MIN_SCORE_JUMP) return null;

        TradeSignal five = latestAtOrBefore(current, CONFIRMATION_INTERVAL, FIVE_MINUTE_MAX_AGE);
        TradeSignal oneHour = latestAtOrBefore(current, TREND_INTERVAL, ONE_HOUR_MAX_AGE);
        if (five == null || oneHour == null) return null;
        if (isBearish(five.getDecision()) || isBearish(five.getOriginalDecision())
                || isBearish(oneHour.getDecision()) || isBearish(oneHour.getOriginalDecision())) return null;

        SignalDecision btcDecision = current.getBtcContextDecision();
        int percent = btcDecision == SignalDecision.STRONG_SELL
                ? EXCEPTIONAL_PROBE_BTC_STRONG_SELL_PERCENT
                : EXCEPTIONAL_PROBE_BTC_SELL_PERCENT;

        return ExecutionDecision.allow(
                "EXCEPTIONAL_STRENGTH_PROBE",
                "BTC_CONFLICT_REDUCED_PROBE",
                percent,
                "High-conviction reversal qualified for a reduced BTC-conflict probe: prior="
                        + priorEffective + " " + prior.getTotalScore() + " -> current="
                        + current.getOriginalDecision() + " " + current.getTotalScore()
                        + " (score jump +" + scoreJump + "), trend=" + current.getTrendScore()
                        + ", momentum=" + current.getMomentumScore() + ", volume=" + current.getVolumeScore()
                        + ", 1m=" + current.getDecision() + ", 5m=" + five.getDecision()
                        + ", 1h=" + oneHour.getDecision() + ", BTC=" + (btcDecision == null ? "BEARISH" : btcDecision)
                        + ". BTC weakness reduces exposure to " + percent
                        + "% instead of increasing the technical score or bypassing asset-timeframe confirmation.",
                e
        );
    }

    private ExecutionDecision applyExceptionalProbeEntryQualityGuard(ExecutionDecision decision, EntryQuality q) {
        if (decision == null || !decision.allowed()) return decision;
        // A probe is specifically for early reversal participation, but it must still
        // have a valid reward/risk plan and may not be an obvious late-stage chase.
        if (q.rewardRisk() <= 0d || q.score() < 40) {
            return ExecutionDecision.building(
                    "EXCEPTIONAL_PROBE_PRICE_QUALITY_BLOCKED",
                    "Exceptional technical strength is present, but probe entry quality is only " + q.score()
                            + "/100 (" + q.classification() + "), expansion=" + format(q.expansionPercent())
                            + "%, ATR extension=" + format(q.atrExtension()) + ", R/R=" + format(q.rewardRisk()) + ".",
                    decision.evidence());
        }
        return decision;
    }

    private ExecutionDecision higherTimeframeTransitionDecision(TradeSignal current, Evidence e) {
        if (current == null || current.isAtrImmediateEntryAllowed()) return null;
        if (!isSupportiveCurrentSignal(current)) return null;
        if (current.getTotalScore() < TRANSITION_MIN_CURRENT_SCORE
                || current.getConfidenceScore() < TRANSITION_MIN_CURRENT_CONFIDENCE) return null;
        if (e.opportunityHealth() < TRANSITION_MIN_HEALTH) return null;

        TradeSignal five = latestAtOrBefore(current, CONFIRMATION_INTERVAL, FIVE_MINUTE_MAX_AGE);
        TradeSignal oneHour = latestAtOrBefore(current, TREND_INTERVAL, ONE_HOUR_MAX_AGE);
        if (five == null || oneHour == null) return null;

        boolean fiveSupportive = (five.getDecision() == SignalDecision.WATCH || isBullish(five.getDecision()))
                && five.getTotalScore() >= TRANSITION_MIN_5M_SCORE
                && five.getConfidenceScore() >= TRANSITION_MIN_5M_CONFIDENCE;
        boolean strongOneHour = isBullish(oneHour.getDecision())
                && oneHour.getTotalScore() >= TRANSITION_MIN_1H_SCORE;
        if (!fiveSupportive || !strongOneHour) return null;
        if (isBearish(current.getOriginalDecision()) || isBearish(five.getOriginalDecision())
                || isBearish(oneHour.getOriginalDecision())) return null;
        if (!five.isStrategyEntryAllowed() || !five.isBtcContextEntryAllowed()
                || !five.isDerivativesEntryAllowed() || !five.isLiquidityEntryAllowed()) return null;

        int percent = TRANSITION_POSITION_PERCENT;
        if (five.getAtrRecommendedPositionPercent() > 0) {
            percent = Math.min(percent, five.getAtrRecommendedPositionPercent());
        }
        percent = Math.max(15, percent);

        return ExecutionDecision.allow(
                "HTF_TRANSITION",
                "HTF_TRANSITION_REDUCED_ENTRY",
                percent,
                "Fresh higher-timeframe transition confirmed: 1h=" + oneHour.getDecision()
                        + " (score=" + oneHour.getTotalScore() + "), 5m=" + five.getDecision()
                        + " (score=" + five.getTotalScore() + ", confidence=" + five.getConfidenceScore() + ")"
                        + ", current 1m=" + current.getDecision()
                        + ", opportunity health=" + e.opportunityHealth() + "/100. "
                        + "ATR is extended, so execution is deliberately reduced to " + percent
                        + "% rather than rejecting the transition outright. Hard strategy/BTC/derivatives/liquidity vetoes remain absolute.",
                e
        );
    }

    private ExecutionDecision applyTransitionEntryQualityGuard(ExecutionDecision decision, EntryQuality q) {
        if (decision == null || !decision.allowed()) return decision;
        // The transition route is specifically for a fast reversal where ATR can lag. We still
        // reject an extreme chase, but use a lower floor than the ordinary entry-quality guard.
        if (q.score() < 40 || q.rewardRisk() <= 0d) {
            return ExecutionDecision.building(
                    "TRANSITION_CHASE_BLOCKED",
                    "Higher-timeframe transition is valid, but price quality is too poor even for a reduced entry: "
                            + q.score() + "/100 (" + q.classification() + "), expansion="
                            + format(q.expansionPercent()) + "%, ATR extension=" + format(q.atrExtension())
                            + ", R/R=" + format(q.rewardRisk()) + ".",
                    decision.evidence());
        }
        int cap = q.score() >= 70 ? TRANSITION_POSITION_PERCENT : 20;
        int reduced = Math.min(decision.positionPercent(), cap);
        if (reduced == decision.positionPercent()) return decision;
        return ExecutionDecision.allow(decision.source(), decision.code(), reduced,
                decision.explanation() + " Entry Quality " + q.score() + "/100 capped the transition allocation at "
                        + reduced + "%.", decision.evidence());
    }

    private TradeSignal setupTimeframeAtrAuthoritySignal(TradeSignal current, Evidence e) {
        if (current.isAtrImmediateEntryAllowed()) return null;

        // A neutral 1m candle is timing-neutral, not a bearish veto. A fresh 5m BUY
        // may still own setup-level ATR risk when opportunity evidence is already healthy.
        // Explicit bearish 1m information remains a veto for this fallback.
        if (isBearish(current.getDecision()) || isBearish(current.getOriginalDecision())) return null;
        boolean supportiveCurrent = isSupportiveCurrentSignal(current);
        boolean healthyNeutralTiming = current.getDecision() == SignalDecision.NEUTRAL
                && e.opportunityHealth() >= 60
                && e.evidenceScore() >= MIN_EVIDENCE_SCORE;
        if (!supportiveCurrent && !healthyNeutralTiming) return null;

        TradeSignal five = latestAtOrBefore(current, CONFIRMATION_INTERVAL, FIVE_MINUTE_MAX_AGE);
        if (five == null || !isBullish(five.getDecision())) return null;
        if (!five.isFinalEntryAllowed() || !five.isAtrImmediateEntryAllowed()) return null;

        TradeSignal oneHour = latestAtOrBefore(current, TREND_INTERVAL, ONE_HOUR_MAX_AGE);
        if (oneHour != null && isBearish(oneHour.getDecision())) return null;
        return five;
    }

    private ExecutionDecision setupTimeframeAtrAuthorityDecision(
            TradeSignal current, Evidence e, TradeSignal five) {
        int fiveRecommended = five.getAtrRecommendedPositionPercent() > 0
                ? five.getAtrRecommendedPositionPercent()
                : DEFERRED_POSITION_PERCENT;
        int percent = Math.min(DEFERRED_POSITION_PERCENT, fiveRecommended);

        return ExecutionDecision.allow(
                "SETUP_TIMEFRAME_ATR",
                "REDUCED_POSITION_ALLOWED",
                percent,
                "The current 1m signal is ATR-extended, but the fresh 5m BUY remains "
                        + "the setup-risk authority and its ATR plan explicitly allows immediate entry at "
                        + fiveRecommended + "%. Execution is therefore allowed at a conservative "
                        + percent + "% allocation instead of permanently rejecting the opportunity. "
                        + "A neutral 1m candle is treated as timing-neutral; explicit bearish 1m/1h context still vetoes. "
                        + "Entry Quality is evaluated with the authoritative 5m ATR so a tiny 1m ATR cannot manufacture "
                        + "a false late-stage chase classification.",
                e
        );
    }

    private ExecutionDecision breakoutRetracementDecision(TradeSignal current, Evidence e) {
        if (current == null || current.getGeneratedAt() == null || current.getLatestPrice() == null) return null;
        if (isBearish(current.getDecision()) || isBearish(current.getOriginalDecision())) return null;
        if (!current.isAtrImmediateEntryAllowed() || !current.isFinalEntryAllowed()) return null;
        if (current.getTotalScore() < BREAKOUT_RETRACEMENT_MIN_CURRENT_SCORE
                || current.getConfidenceScore() < BREAKOUT_RETRACEMENT_MIN_CURRENT_CONFIDENCE
                || current.getTrendScore() < BREAKOUT_RETRACEMENT_MIN_CURRENT_TREND
                || current.getMomentumScore() < BREAKOUT_RETRACEMENT_MIN_CURRENT_MOMENTUM) return null;
        if (e.opportunityHealth() < BREAKOUT_RETRACEMENT_MIN_HEALTH) return null;

        TradeSignal origin = recentBreakoutRetracementOrigin(current);
        if (origin == null || origin.getAtrRetracementEntryPrice() == null
                || origin.getAtrAtSignal() == null || origin.getAtrAtSignal().signum() <= 0) return null;

        BigDecimal target = origin.getAtrRetracementEntryPrice();
        BigDecimal atr = origin.getAtrAtSignal();
        BigDecimal upper = target.add(atr.multiply(BigDecimal.valueOf(BREAKOUT_RETRACEMENT_TARGET_TOLERANCE_ATR)));
        BigDecimal lower = target.subtract(atr.multiply(BigDecimal.valueOf(BREAKOUT_RETRACEMENT_MAX_OVERSHOOT_ATR)));
        BigDecimal price = current.getLatestPrice();
        if (price.compareTo(upper) > 0 || price.compareTo(lower) < 0) return null;

        EntryQuality currentQuality = assessEntryQuality(current);
        if (currentQuality.score() < BREAKOUT_RETRACEMENT_MIN_ENTRY_QUALITY
                || currentQuality.rewardRisk() < BREAKOUT_RETRACEMENT_MIN_RR) return null;

        TradeSignal five = latestAtOrBefore(current, CONFIRMATION_INTERVAL, FIVE_MINUTE_MAX_AGE);
        if (five == null || isBearish(five.getDecision()) || isBearish(five.getOriginalDecision())) return null;
        if (!(five.getDecision() == SignalDecision.WATCH || isBullish(five.getDecision())
                || five.getOriginalDecision() == SignalDecision.WATCH || isBullish(five.getOriginalDecision()))) return null;
        if (five.getMomentumScore() < BREAKOUT_RETRACEMENT_MIN_5M_MOMENTUM) return null;

        // A bearish 1h may be the old trend the breakout is trying to reverse. It is
        // allowed only when it already existed at the breakout origin and has not been
        // replaced by a fresh/worsening bearish snapshot after that origin.
        TradeSignal originOneHour = latestAtOrBefore(origin, TREND_INTERVAL, ONE_HOUR_MAX_AGE);
        TradeSignal oneHour = latestAtOrBefore(current, TREND_INTERVAL, ONE_HOUR_MAX_AGE);
        boolean oneHourBearish = oneHour != null
                && (isBearish(oneHour.getDecision()) || isBearish(oneHour.getOriginalDecision()));
        if (oneHourBearish) {
            boolean bearishAtOrigin = originOneHour != null
                    && (isBearish(originOneHour.getDecision()) || isBearish(originOneHour.getOriginalDecision()));
            boolean freshBearishAfterOrigin = oneHour.getGeneratedAt() != null
                    && oneHour.getGeneratedAt().isAfter(origin.getGeneratedAt());
            if (!bearishAtOrigin || freshBearishAfterOrigin || isOneHourBearishWorsening(oneHour, current)) return null;
        }

        int percent = oneHourBearish
                ? BREAKOUT_RETRACEMENT_BEARISH_1H_POSITION_PERCENT
                : BREAKOUT_RETRACEMENT_POSITION_PERCENT;

        return ExecutionDecision.allow(
                "BREAKOUT_RETRACEMENT",
                "BREAKOUT_RETRACEMENT_ENTRY",
                percent,
                "A recent BREAKOUT was deliberately deferred by ATR instead of being chased, and price has now "
                        + "returned to the requested retracement zone. origin=" + origin.getLatestPrice()
                        + ", target=" + target + ", current=" + price
                        + ". Breakout confirmation remains valid (score=" + origin.getTotalScore()
                        + ", trend=" + origin.getTrendScore() + ", momentum=" + origin.getMomentumScore()
                        + ", volume=" + origin.getVolumeScore() + "). Current pullback quality remains supportive "
                        + "(score=" + current.getTotalScore() + ", confidence=" + current.getConfidenceScore()
                        + ", trend=" + current.getTrendScore() + ", momentum=" + current.getMomentumScore()
                        + ", health=" + e.opportunityHealth() + ", 5m=" + five.getDecision()
                        + ", 5m momentum=" + five.getMomentumScore() + ", Entry Quality=" + currentQuality.score()
                        + "/100, R/R=" + format(currentQuality.rewardRisk()) + "). Current-candle volume is not "
                        + "required to repeat the breakout spike; the original breakout already supplied that evidence. "
                        + "Execute only a reduced " + percent + "% position; normal BUY and SELL logic are unchanged.",
                e);
    }

    private TradeSignal recentBreakoutRetracementOrigin(TradeSignal current) {
        Instant cutoff = current.getGeneratedAt().minus(BREAKOUT_RETRACEMENT_LOOKBACK);
        return recentSignals(current.getSymbol(), EXECUTION_INTERVAL, current.getGeneratedAt()).stream()
                .filter(s -> s != null && s.getGeneratedAt() != null)
                .filter(s -> s.getGeneratedAt().isBefore(current.getGeneratedAt())
                        && !s.getGeneratedAt().isBefore(cutoff))
                .filter(s -> s.getSelectedStrategy() == TradingStrategy.BREAKOUT
                        && s.getMarketRegime() == MarketRegime.BREAKOUT)
                .filter(s -> "WAIT_FOR_RETRACEMENT".equals(s.getAtrEntryType()))
                .filter(s -> !s.isAtrImmediateEntryAllowed())
                .filter(s -> s.getAtrRetracementEntryPrice() != null
                        && s.getAtrAtSignal() != null && s.getAtrAtSignal().signum() > 0)
                .filter(s -> s.getTotalScore() >= BREAKOUT_RETRACEMENT_MIN_ORIGIN_SCORE
                        && s.getTrendScore() >= BREAKOUT_RETRACEMENT_MIN_ORIGIN_TREND
                        && s.getVolumeScore() >= BREAKOUT_RETRACEMENT_MIN_ORIGIN_VOLUME
                        && s.getMomentumScore() >= BREAKOUT_RETRACEMENT_MIN_ORIGIN_MOMENTUM)
                .filter(s -> s.isStrategyEntryAllowed() && s.isBtcContextEntryAllowed()
                        && s.isDerivativesEntryAllowed() && s.isLiquidityEntryAllowed())
                .max(java.util.Comparator.comparing(TradeSignal::getGeneratedAt))
                .orElse(null);
    }

    private ExecutionDecision reversalRetracementDecision(TradeSignal current, Evidence e) {
        if (current == null || current.getGeneratedAt() == null || current.getLatestPrice() == null) return null;
        if (isBearish(current.getDecision()) || isBearish(current.getOriginalDecision())) return null;

        TradeSignal origin = recentAtrRetracementOrigin(current);
        if (origin == null || origin.getAtrRetracementEntryPrice() == null
                || origin.getAtrAtSignal() == null || origin.getAtrAtSignal().signum() <= 0) return null;

        BigDecimal target = origin.getAtrRetracementEntryPrice();
        BigDecimal atr = origin.getAtrAtSignal();
        BigDecimal upper = target.add(atr.multiply(BigDecimal.valueOf(RETRACEMENT_TARGET_TOLERANCE_ATR)));
        BigDecimal lower = target.subtract(atr.multiply(BigDecimal.valueOf(RETRACEMENT_MAX_OVERSHOOT_ATR)));
        BigDecimal price = current.getLatestPrice();
        if (price.compareTo(upper) > 0 || price.compareTo(lower) < 0) return null;

        // The retracement route may preserve an old high-quality thesis, but it may not
        // resurrect an opportunity whose current health/risk economics have already decayed.
        if (e.opportunityHealth() < RETRACEMENT_MIN_CURRENT_HEALTH) return null;
        if (current.getStopLoss() == null || current.getTakeProfit() == null
                || current.getStopLoss().signum() <= 0 || current.getTakeProfit().signum() <= 0) return null;
        EntryQuality currentQuality = assessEntryQuality(current);
        if (currentQuality.score() < RETRACEMENT_MIN_CURRENT_ENTRY_QUALITY
                || currentQuality.rewardRisk() < RETRACEMENT_MIN_CURRENT_RR) return null;

        TradeSignal five = latestAtOrBefore(current, CONFIRMATION_INTERVAL, FIVE_MINUTE_MAX_AGE);
        if (five == null || isBearish(five.getDecision()) || isBearish(five.getOriginalDecision())) return null;
        if (five.getMomentumScore() < RETRACEMENT_MIN_5M_MOMENTUM
                || five.getMacdScore() < RETRACEMENT_MIN_5M_MACD_SCORE
                || five.getRsiScore() < RETRACEMENT_MIN_5M_RSI_SCORE) return null;

        // Preserve the distinction between an OLD bearish 1h regime that the reversal is
        // trying to turn, and a NEW bearish 1h signal that appeared after the origin BUY.
        // A newly bearish 1h invalidates the pending setup. A pre-existing bearish 1h may
        // remain only if its severity/score structure is not worsening, and it caps size.
        TradeSignal originOneHour = latestAtOrBefore(origin, TREND_INTERVAL, ONE_HOUR_MAX_AGE);
        TradeSignal oneHour = latestAtOrBefore(current, TREND_INTERVAL, ONE_HOUR_MAX_AGE);
        boolean currentOneHourBearish = oneHour != null
                && (isBearish(oneHour.getDecision()) || isBearish(oneHour.getOriginalDecision()));
        if (currentOneHourBearish) {
            boolean bearishWasPresentAtOrigin = originOneHour != null
                    && (isBearish(originOneHour.getDecision()) || isBearish(originOneHour.getOriginalDecision()));
            boolean newBearishSnapshotAfterOrigin = oneHour.getGeneratedAt() != null
                    && oneHour.getGeneratedAt().isAfter(origin.getGeneratedAt());
            if (!bearishWasPresentAtOrigin || newBearishSnapshotAfterOrigin
                    || isOneHourBearishWorsening(oneHour, current)) return null;
        }

        if (!current.isStrategyEntryAllowed() || !current.isDerivativesEntryAllowed()
                || !current.isLiquidityEntryAllowed() || !current.isBtcContextEntryAllowed()) return null;

        int percent = RETRACEMENT_POSITION_PERCENT;
        if (oneHour != null && (isBearish(oneHour.getDecision()) || isBearish(oneHour.getOriginalDecision()))) {
            percent = 15;
        }

        return ExecutionDecision.allow(
                "REVERSAL_RETRACEMENT",
                "ATR_RETRACEMENT_REACHED",
                percent,
                "ATR correctly deferred the original BUY instead of chasing it. Price has now returned to the "
                        + "ATR retracement zone: origin=" + origin.getLatestPrice()
                        + ", target=" + target + ", current=" + price
                        + ". Original quality remained high (score=" + origin.getTotalScore()
                        + ", trend=" + origin.getTrendScore() + ", volume=" + origin.getVolumeScore()
                        + ") and the latest as-of 5m context is constructive (decision=" + five.getDecision()
                        + ", momentum=" + five.getMomentumScore() + ", MACD score=" + five.getMacdScore()
                        + ", RSI score=" + five.getRsiScore() + "). "
                        + (oneHour == null ? "No fresh 1h replay context is available; no bullish 1h state was invented. "
                        : "1h=" + oneHour.getDecision() + " and is a pre-existing, non-worsening context. ")
                        + "Current opportunity health=" + e.opportunityHealth()
                        + ", Entry Quality=" + currentQuality.score() + "/100, current R/R=" + format(currentQuality.rewardRisk()) + ". "
                        + "Execute only a reduced " + percent + "% retracement position; normal BUY logic is unchanged.",
                e);
    }

    private TradeSignal recentAtrRetracementOrigin(TradeSignal current) {
        Instant cutoff = current.getGeneratedAt().minus(RETRACEMENT_SETUP_LOOKBACK);
        return recentSignals(current.getSymbol(), EXECUTION_INTERVAL, current.getGeneratedAt()).stream()
                .filter(s -> s != null && s.getGeneratedAt() != null)
                .filter(s -> s.getGeneratedAt().isBefore(current.getGeneratedAt())
                        && !s.getGeneratedAt().isBefore(cutoff))
                .filter(s -> isBullish(s.getOriginalDecision()) || isBullish(s.getDecision()))
                .filter(s -> s.getTotalScore() >= RETRACEMENT_MIN_ORIGIN_SCORE
                        && s.getTrendScore() >= RETRACEMENT_MIN_ORIGIN_TREND
                        && s.getVolumeScore() >= RETRACEMENT_MIN_ORIGIN_VOLUME)
                .filter(s -> !s.isAtrImmediateEntryAllowed() && s.isAtrOverextended())
                .filter(s -> s.getAtrRetracementEntryPrice() != null && s.getAtrAtSignal() != null)
                .filter(s -> s.isStrategyEntryAllowed() && s.isDerivativesEntryAllowed() && s.isLiquidityEntryAllowed())
                .max(java.util.Comparator.comparing(TradeSignal::getGeneratedAt))
                .orElse(null);
    }

    private boolean isOneHourBearishWorsening(TradeSignal latest, TradeSignal current) {
        if (latest == null || current == null) return false;
        List<TradeSignal> hours = recentSignals(current.getSymbol(), TREND_INTERVAL, current.getGeneratedAt()).stream()
                .filter(s -> s.getGeneratedAt() != null && s.getGeneratedAt().isBefore(latest.getGeneratedAt()))
                .toList();
        TradeSignal previous = hours.isEmpty() ? null : hours.get(0);
        if (previous == null) return false;

        SignalDecision latestEffective = strongerDecision(latest.getDecision(), latest.getOriginalDecision());
        SignalDecision previousEffective = strongerDecision(previous.getDecision(), previous.getOriginalDecision());
        boolean severityWorsened = bearishSeverity(latestEffective) > bearishSeverity(previousEffective);
        return severityWorsened
                || latest.getTotalScore() < previous.getTotalScore()
                || latest.getTrendScore() < previous.getTrendScore()
                || latest.getMomentumScore() < previous.getMomentumScore();
    }

    private int bearishSeverity(SignalDecision decision) {
        if (decision == SignalDecision.STRONG_SELL) return 2;
        if (decision == SignalDecision.SELL) return 1;
        return 0;
    }

    private ExecutionDecision deferredContinuationDecision(TradeSignal current, Evidence e) {
        if (!isSupportiveCurrentSignal(current)) return null;
        // Do not require finalEntryAllowed/atrImmediateEntryAllowed here. The aggregate final
        // entry flag may be false solely because ATR requested a pullback; that is exactly
        // the condition this continuation route is designed to reassess. All non-ATR hard
        // vetoes were already enforced before this method is called.
        if (current.getTotalScore() < DEFERRED_MIN_CURRENT_SCORE
                || current.getConfidenceScore() < DEFERRED_MIN_CURRENT_CONFIDENCE) return null;
        if (e.opportunityHealth() < DEFERRED_MIN_HEALTH
                || e.evidenceMomentum() < DEFERRED_MIN_EVIDENCE_MOMENTUM) return null;
        if (e.fiveMinute() == null || e.oneHour() == null
                || isBearish(e.fiveMinute()) || isBearish(e.oneHour())) return null;

        TradeSignal five = latestAtOrBefore(current, CONFIRMATION_INTERVAL, FIVE_MINUTE_MAX_AGE);
        if (five == null || !(five.getDecision() == SignalDecision.WATCH || isBullish(five.getDecision()))
                || five.getTotalScore() < DEFERRED_MIN_5M_SCORE
                || five.getConfidenceScore() < DEFERRED_MIN_5M_CONFIDENCE) return null;
        if (!(e.oneHour() == SignalDecision.WATCH || isBullish(e.oneHour()))) return null;

        TradeSignal deferredBuy = priorAtrDeferredBuy(current);
        if (deferredBuy == null) return null;

        double rewardRisk = currentRewardRisk(current);
        if (rewardRisk < 1.0d) {
            return ExecutionDecision.building(
                    "CONTINUATION_RISK_REWARD_LOW",
                    "A prior ATR-deferred BUY exists and continuation is confirmed, but the current reward/risk is only "
                            + String.format(java.util.Locale.ROOT, "%.2f", rewardRisk)
                            + ". The engine will not chase a continuation with less than 1:1 current reward/risk.",
                    e);
        }

        int percent = Math.min(DEFERRED_POSITION_PERCENT,
                current.getAtrRecommendedPositionPercent() > 0
                        ? current.getAtrRecommendedPositionPercent()
                        : DEFERRED_POSITION_PERCENT);
        percent = Math.max(20, percent);

        return ExecutionDecision.allow(
                "DEFERRED_CONTINUATION",
                "BREAKOUT_CONTINUATION_ENTRY",
                percent,
                "A prior 1m BUY was deferred only by ATR pullback logic, but the pullback did not arrive. "
                        + "Fresh continuation is now supported by current 1m evidence, 5m=" + e.fiveMinute()
                        + " (score=" + five.getTotalScore() + ", confidence=" + five.getConfidenceScore() + ")"
                        + ", 1h=" + e.oneHour() + ", opportunity health=" + e.opportunityHealth()
                        + ", evidence momentum=" + e.evidenceMomentum() + ". "
                        + "Execution uses the current stop/target and a reduced " + percent + "% position.",
                e);
    }

    private TradeSignal priorAtrDeferredBuy(TradeSignal current) {
        Instant cutoff = current.getGeneratedAt().minus(DEFERRED_BUY_LOOKBACK);
        return recentSignals(current.getSymbol(), EXECUTION_INTERVAL, current.getGeneratedAt()).stream()
                .filter(s -> s.getGeneratedAt() != null
                        && s.getGeneratedAt().isBefore(current.getGeneratedAt())
                        && !s.getGeneratedAt().isBefore(cutoff))
                .filter(s -> isBullish(s.getDecision()) || isBullish(s.getOriginalDecision()))
                .filter(s -> s.getTotalScore() >= properties.minimumBuyScore())
                .filter(s -> !s.isAtrImmediateEntryAllowed())
                .filter(s -> s.isStrategyEntryAllowed()
                        && s.isBtcContextEntryAllowed()
                        && s.isDerivativesEntryAllowed()
                        && s.isLiquidityEntryAllowed())
                .filter(s -> {
                    String type = s.getAtrEntryType();
                    return "PULLBACK_ENTRY".equals(type) || "WAIT_FOR_RETRACEMENT".equals(type);
                })
                .findFirst()
                .orElse(null);
    }

    private double currentRewardRisk(TradeSignal signal) {
        if (signal.getLatestPrice() == null || signal.getStopLoss() == null || signal.getTakeProfit() == null) return 0d;
        java.math.BigDecimal risk = signal.getLatestPrice().subtract(signal.getStopLoss()).abs();
        java.math.BigDecimal reward = signal.getTakeProfit().subtract(signal.getLatestPrice());
        if (risk.signum() <= 0 || reward.signum() <= 0) return 0d;
        return reward.divide(risk, 8, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Small exploratory entry for a proven pressure-reversal sequence while normal MTF
     * confirmation is still lagging. This is intentionally NOT a BUY-promotion method.
     *
     * <p>Normal direct BUY validation already ran before this method. Therefore a BUY
     * that belongs to the existing normal path can never use PRESSURE_PROBE_ENTRY as a
     * fallback around normal validation. The probe is restricted to WATCH/NEUTRAL.</p>
     *
     * <p>A bearish 1h label is allowed only because the exposure is capped at 15% and
     * PressureReadinessService must prove a completed burst/rejection/higher-low/rebuild
     * sequence using only candles closed as-of the current signal timestamp. This does
     * not weaken the normal 1h veto for normal-sized trades.</p>
     */

    /**
     * FIX-026 recovery-transition probe. Normal direct BUY and the proven pressure-probe
     * route already had priority before this method. This is intentionally a small,
     * state-transition exception for WATCH/NEUTRAL only; it never converts a bearish
     * current signal and it never bypasses a hard risk/context veto.
     */
    private ExecutionDecision recoveryTransitionDecision(TradeSignal current, Evidence evidence) {
        if (current == null || current.getGeneratedAt() == null) return null;
        if (current.getDecision() != SignalDecision.WATCH && current.getDecision() != SignalDecision.NEUTRAL) return null;
        if (current.getTotalScore() < 72 || current.getConfidenceScore() < 68
                || current.getTrendScore() < 20 || current.getMomentumScore() < 14) return null;

        // Keep all production hard authorities intact. The ENA case had every one of these
        // gates open; this probe must not manufacture permission when ATR, liquidity, BTC,
        // derivatives, confluence, strategy or FinalDecision has already vetoed entry.
        if (!current.isFinalEntryAllowed() || !current.isStrategyEntryAllowed()
                || !current.isConfluenceEntryAllowed() || !current.isAtrImmediateEntryAllowed()
                || !current.isBtcContextEntryAllowed() || !current.isLiquidityEntryAllowed()
                || !current.isDerivativesEntryAllowed()) return null;

        Instant bearishCutoff = current.getGeneratedAt().minus(Duration.ofMinutes(10));
        TradeSignal recentBearish = recentSignals(current.getSymbol(), EXECUTION_INTERVAL, current.getGeneratedAt()).stream()
                .filter(s -> s != null && s.getGeneratedAt() != null && s.getGeneratedAt().isBefore(current.getGeneratedAt()))
                .filter(s -> !s.getGeneratedAt().isBefore(bearishCutoff))
                .filter(s -> isBearish(strongerDecision(s.getDecision(), s.getOriginalDecision())))
                .filter(s -> s.getTotalScore() <= 35)
                .findFirst().orElse(null);
        if (recentBearish == null) return null;

        TradeSignal five = latestAtOrBefore(current, CONFIRMATION_INTERVAL, FIVE_MINUTE_MAX_AGE);
        TradeSignal oneHour = latestAtOrBefore(current, TREND_INTERVAL, ONE_HOUR_MAX_AGE);
        if (five == null || oneHour == null) return null;
        SignalDecision fiveDecision = strongerDecision(five.getDecision(), five.getOriginalDecision());
        SignalDecision oneHourDecision = strongerDecision(oneHour.getDecision(), oneHour.getOriginalDecision());
        if (isBearish(fiveDecision) || isBearish(oneHourDecision)) return null;

        RecoveryTransitionService.Result recovery = recoveryTransitionService.evaluate(current);
        if (recovery == null || !recovery.probeReady()) return null;

        return ExecutionDecision.allow(
                "RECOVERY_TRANSITION_ENTRY",
                "ABSORPTION_RECOVERY_PROBE",
                25,
                "Recovery-transition probe approved after recent bearish signal #" + recentBearish.getId()
                        + " (score=" + recentBearish.getTotalScore() + ") recovered into current "
                        + current.getDecision() + " " + current.getTotalScore()
                        + " (trend=" + current.getTrendScore() + ", momentum=" + current.getMomentumScore()
                        + "). " + recovery.explanation() + " 5m=" + fiveDecision
                        + ", 1h=" + oneHourDecision + ". Exposure is capped at 25% until ordinary confirmation adds.",
                evidence);
    }

    private ExecutionDecision pressureProbeDecision(TradeSignal current, Evidence e) {
        if (current == null || current.getGeneratedAt() == null) return null;

        // Preserve normal BUY authority. A bullish final decision belongs to the normal
        // path and must never fall back to this probe if normal validation rejects it.
        if (isBullish(current.getDecision()) || isBearish(current.getDecision())) return null;
        if (current.getDecision() != SignalDecision.WATCH && current.getDecision() != SignalDecision.NEUTRAL) return null;

        if (current.getTotalScore() < PRESSURE_PROBE_MIN_1M_SCORE
                || current.getConfidenceScore() < PRESSURE_PROBE_MIN_1M_CONFIDENCE
                || current.getTrendScore() < PRESSURE_PROBE_MIN_1M_TREND
                || current.getVolumeScore() < PRESSURE_PROBE_MIN_1M_VOLUME
                || current.getMomentumScore() < PRESSURE_PROBE_MIN_1M_MOMENTUM) return null;

        // These are existing production safety authorities; the pressure path cannot
        // manufacture permission when another context/risk layer already vetoed entry.
        if (!current.isFinalEntryAllowed()
                || !current.isStrategyEntryAllowed()
                || !current.isAtrImmediateEntryAllowed()
                || !current.isBtcContextEntryAllowed()
                || !current.isLiquidityEntryAllowed()
                || !current.isDerivativesEntryAllowed()) return null;

        PressureReadinessService.Result pressure = pressureReadinessService.evaluate(current);
        if (pressure == null || !pressure.probeReady()) return null;

        TradeSignal latestFive = latestAtOrBefore(current, CONFIRMATION_INTERVAL, FIVE_MINUTE_MAX_AGE);
        TradeSignal oneHour = latestAtOrBefore(current, TREND_INTERVAL, ONE_HOUR_MAX_AGE);
        if (latestFive == null || oneHour == null) return null;

        SignalDecision latestFiveDecision = strongerDecision(latestFive.getDecision(), latestFive.getOriginalDecision());
        SignalDecision oneHourDecision = strongerDecision(oneHour.getDecision(), oneHour.getOriginalDecision());

        // A fresh 5m STRONG_SELL is too hostile even for a probe. A plain 5m SELL can
        // represent the retest only when a recent 5m WATCH/BUY BREAKOUT already proved
        // the first bullish attempt. This is the SOL sequence: setup -> retest -> rebuild.
        if (latestFiveDecision == SignalDecision.STRONG_SELL) return null;

        Instant setupCutoff = current.getGeneratedAt().minus(PRESSURE_RECENT_5M_SETUP_LOOKBACK);
        TradeSignal recentFiveMinuteSetup = recentSignals(current.getSymbol(), CONFIRMATION_INTERVAL, current.getGeneratedAt()).stream()
                .filter(s -> s != null && s.getGeneratedAt() != null)
                .filter(s -> !s.getGeneratedAt().isAfter(current.getGeneratedAt())
                        && !s.getGeneratedAt().isBefore(setupCutoff))
                .filter(s -> {
                    SignalDecision d = strongerDecision(s.getDecision(), s.getOriginalDecision());
                    return d == SignalDecision.WATCH || isBullish(d);
                })
                .filter(s -> s.getSelectedStrategy() == TradingStrategy.BREAKOUT)
                .filter(s -> s.getTotalScore() >= PRESSURE_PROBE_MIN_5M_SETUP_SCORE)
                .filter(s -> s.getVolumeScore() >= PRESSURE_PROBE_MIN_5M_SETUP_VOLUME)
                .filter(s -> s.getMomentumScore() >= PRESSURE_PROBE_MIN_5M_SETUP_MOMENTUM)
                .findFirst()
                .orElse(null);
        if (recentFiveMinuteSetup == null) return null;

        // The probe may coexist with a bearish 1h because it is explicitly counter-trend
        // discovery risk, not normal confirmation. We still require a fresh 1h object so
        // the conflict is known/auditable rather than silently treated as unavailable.
        if (oneHourDecision == null) return null;

        return ExecutionDecision.allow(
                "PRESSURE_PROBE_ENTRY",
                "ABSORPTION_RETEST_REBUILD_PROBE",
                PRESSURE_PROBE_POSITION_PERCENT,
                "Small pressure probe approved without changing the normal BUY path. "
                        + pressure.explanation()
                        + " Current 1m=" + current.getDecision() + " " + current.getTotalScore()
                        + " (trend=" + current.getTrendScore() + ", volume=" + current.getVolumeScore()
                        + ", momentum=" + current.getMomentumScore() + "), latest 5m="
                        + latestFiveDecision + " " + latestFive.getTotalScore()
                        + ", prior 5m setup #" + recentFiveMinuteSetup.getId() + "="
                        + strongerDecision(recentFiveMinuteSetup.getDecision(), recentFiveMinuteSetup.getOriginalDecision())
                        + " " + recentFiveMinuteSetup.getTotalScore() + " " + recentFiveMinuteSetup.getSelectedStrategy()
                        + ", 1h=" + oneHourDecision + " " + oneHour.getTotalScore()
                        + ". Exposure is capped at " + PRESSURE_PROBE_POSITION_PERCENT
                        + "% until the existing progressive/normal confirmation path adds or exits.",
                e);
    }

    private ExecutionDecision accumulatedDecision(TradeSignal current, Evidence e) {
        if (!isSupportiveCurrentSignal(current)) {
            return ExecutionDecision.observe("NO_BULLISH_EVIDENCE",
                    "Current 1m signal does not add BUY/WATCH evidence.", e);
        }
        if (e.fiveMinute() == null || e.oneHour() == null) {
            return ExecutionDecision.building("MISSING_CONTEXT",
                    "Opportunity is building, but fresh 5m/1h context is not yet available.", e);
        }
        if (isBearish(e.fiveMinute()) || isBearish(e.oneHour())) {
            return ExecutionDecision.reject("HIGHER_TIMEFRAME_BEARISH",
                    "Opportunity cancelled because 5m or 1h context is bearish.", e);
        }
        if (e.opportunityHealth() < 40) {
            return ExecutionDecision.building("OPPORTUNITY_RECOVERING",
                    "Bullish evidence is returning, but opportunity health is only "
                            + e.opportunityHealth() + "/100 after recent bearish/aging penalties.", e);
        }
        if (e.evidenceScore() < MIN_EVIDENCE_SCORE) {
            return ExecutionDecision.building("EVIDENCE_BUILDING",
                    "Bullish evidence is accumulating: score " + e.evidenceScore() + "/" + MIN_EVIDENCE_SCORE
                            + " from " + e.buyCount() + " BUY and " + e.watchCount() + " WATCH observations.", e);
        }
        if (e.buyCount() == 0 && e.watchCount() < 5) {
            return ExecutionDecision.building("WATCH_ONLY_BUILDING",
                    "WATCH evidence is persistent but has not yet produced a BUY signal; continuing to observe.", e);
        }

        // WATCH-only accumulation is intentionally allowed to keep building across symbols,
        // but old WATCH observations must not manufacture a fresh BUY when the current
        // higher-timeframe context is only 1h WATCH + 5m NEUTRAL. In that state there is
        // no fresh setup confirmation: keep the opportunity memory, but wait for either a
        // 5m WATCH/BUY or a genuine 1h BUY before committing capital. Existing routes with
        // real BUY evidence are unchanged, as are HTF_TRANSITION / IMMEDIATE_VALIDATION.
        if (e.buyCount() == 0
                && !isBullish(e.oneHour())
                && e.fiveMinute() != SignalDecision.WATCH
                && !isBullish(e.fiveMinute())) {
            return ExecutionDecision.building("WATCH_ONLY_NEEDS_FRESH_CONFIRMATION",
                    "Accumulated WATCH evidence remains valid, but there is still no BUY observation and fresh "
                            + "higher-timeframe confirmation is missing (5m=" + e.fiveMinute()
                            + ", 1h=" + e.oneHour() + "). Wait for 5m WATCH/BUY or 1h BUY before entry.", e);
        }
        if (e.averageScore() < 65 || e.averageConfidence() < 65) {
            return ExecutionDecision.building("EVIDENCE_QUALITY_LOW",
                    "Evidence persists, but average quality is still low: score=" + e.averageScore()
                            + ", confidence=" + e.averageConfidence() + ".", e);
        }

        // FIX-021 / BICOUSDT + ETHUSDT:
        // Accumulated evidence is memory, not a separate execution authority. It must obey
        // the SAME configured 5m/1h profile that a normal direct BUY would face. This closes
        // the contradiction where BALANCED rejected 5m WATCH + 1h NEUTRAL for a fresh BUY,
        // but ACCUMULATED_EVIDENCE later entered with that same or weaker HTF state.
        TradeExecutionValidationService.ValidationResult authority = validationService.validateBuyContext(current);
        if (!authority.allowed()) {
            return ExecutionDecision.building("ACCUMULATED_AUTHORITY_WAIT",
                    "Accumulated evidence remains stored, but current HTF execution authority is insufficient: "
                            + authority.code() + " - " + authority.explanation(), e);
        }

        int percent = e.evidenceScore() >= STRONG_EVIDENCE_SCORE ? 50 : 25;
        if (e.buyCount() >= 2) percent += 10;
        if (e.fiveMinute() == SignalDecision.WATCH) percent += 5;
        if (isBullish(e.fiveMinute())) percent += 15;
        if (e.oneHour() == SignalDecision.WATCH) percent += 5;
        if (isBullish(e.oneHour())) percent += 10;
        // Never size accumulated evidence above the authority granted by the configured
        // direct-BUY profile. Historical evidence may reduce uncertainty, not increase HTF authority.
        percent = Math.min(Math.min(75, percent), authority.positionPercent());

        return ExecutionDecision.allow(
                "ACCUMULATED_EVIDENCE",
                "OPPORTUNITY_CONFIRMED",
                percent,
                "Execution Intelligence approved the current signal from accumulated fresh evidence: "
                        + e.buyCount() + " BUY, " + e.watchCount() + " WATCH, evidence score=" + e.evidenceScore()
                        + ", average score=" + e.averageScore() + ", average confidence=" + e.averageConfidence()
                        + ", 5m=" + e.fiveMinute() + ", 1h=" + e.oneHour()
                        + ", HTF authority=" + authority.code()
                        + ". No second trade signal was generated.",
                e
        );
    }


    /**
     * BALANCED_EARLY deliberately bypasses accumulated evidence in normal conditions.
     * After a fresh 5m SELL/STRONG_SELL, however, it must prove that the bearish phase
     * actually recovered before that shortcut is available again. This guard changes
     * only BALANCED_EARLY; accumulated evidence, normal BUY, retracement, transition,
     * ATR and SELL logic remain unchanged.
     */
    private ExecutionDecision balancedEarlyPostBearishGuard(
            TradeSignal current, Evidence evidence, EntryQuality entryQuality,
            TradeExecutionValidationService.ValidationResult validation) {
        if (validation == null || !validation.allowed() || !"BALANCED_EARLY".equals(validation.code())) return null;
        if (current == null || current.getGeneratedAt() == null) return null;

        List<TradeSignal> fiveMinuteHistory = recentSignals(current.getSymbol(), CONFIRMATION_INTERVAL, current.getGeneratedAt()).stream()
                .filter(s -> s != null && s.getGeneratedAt() != null && !s.getGeneratedAt().isAfter(current.getGeneratedAt()))
                .sorted(java.util.Comparator.comparing(TradeSignal::getGeneratedAt).reversed())
                .toList();

        Instant cutoff = current.getGeneratedAt().minus(BALANCED_EARLY_BEARISH_LOOKBACK);
        TradeSignal lastBearishFive = fiveMinuteHistory.stream()
                .filter(s -> !s.getGeneratedAt().isBefore(cutoff))
                .filter(s -> isBearish(strongerDecision(s.getDecision(), s.getOriginalDecision())))
                .findFirst()
                .orElse(null);
        if (lastBearishFive == null) return null; // existing BALANCED_EARLY behavior stays untouched

        int consecutiveRecoveredFive = 0;
        for (TradeSignal five : fiveMinuteHistory) {
            if (!five.getGeneratedAt().isAfter(lastBearishFive.getGeneratedAt())) break;
            SignalDecision decision = strongerDecision(five.getDecision(), five.getOriginalDecision());
            if (decision == SignalDecision.WATCH || isBullish(decision)) consecutiveRecoveredFive++;
            else break;
        }

        boolean healthOk = evidence.opportunityHealth() >= BALANCED_EARLY_POST_BEARISH_MIN_HEALTH;
        boolean evidenceOk = evidence.evidenceScore() >= BALANCED_EARLY_POST_BEARISH_MIN_EVIDENCE;
        boolean recoveryOk = consecutiveRecoveredFive >= BALANCED_EARLY_POST_BEARISH_REQUIRED_5M_RECOVERY;
        boolean qualityOk = entryQuality != null && entryQuality.score() >= BALANCED_EARLY_POST_BEARISH_MIN_ENTRY_QUALITY;

        if (!healthOk || !evidenceOk || !recoveryOk || !qualityOk) {
            return ExecutionDecision.building(
                    "BALANCED_EARLY_POST_BEARISH_RECOVERY_REQUIRED",
                    "BALANCED_EARLY is paused after a fresh 5m bearish confirmation. "
                            + "Require health>=" + BALANCED_EARLY_POST_BEARISH_MIN_HEALTH
                            + ", evidence>=" + BALANCED_EARLY_POST_BEARISH_MIN_EVIDENCE
                            + ", " + BALANCED_EARLY_POST_BEARISH_REQUIRED_5M_RECOVERY
                            + " consecutive recovered 5m WATCH/BUY states, and Entry Quality>="
                            + BALANCED_EARLY_POST_BEARISH_MIN_ENTRY_QUALITY
                            + ". Actual health=" + evidence.opportunityHealth()
                            + ", evidence=" + evidence.evidenceScore()
                            + ", recovered5m=" + consecutiveRecoveredFive
                            + ", entryQuality=" + (entryQuality == null ? 0 : entryQuality.score()) + ".",
                    evidence);
        }

        int reduced = Math.min(validation.positionPercent(), BALANCED_EARLY_POST_BEARISH_MAX_POSITION);
        return ExecutionDecision.allow(
                "IMMEDIATE_VALIDATION",
                "BALANCED_EARLY_POST_BEARISH_RECOVERED",
                reduced,
                "BALANCED_EARLY recovered safely after recent 5m bearish context: health="
                        + evidence.opportunityHealth() + ", evidence=" + evidence.evidenceScore()
                        + ", recovered5m=" + consecutiveRecoveredFive + ", Entry Quality="
                        + entryQuality.score() + ". Initial exposure capped at " + reduced
                        + "% so normal progressive confirmation can add later.",
                evidence);
    }

    private boolean isDirectBuyCandidate(TradeSignal signal) {
        return signal.isFinalEntryAllowed()
                && signal.isAtrImmediateEntryAllowed()
                && signal.getTotalScore() >= properties.minimumBuyScore()
                && isBullish(signal.getDecision());
    }

    private boolean isSupportiveCurrentSignal(TradeSignal signal) {
        if (isBullish(signal.getDecision()) || isBullish(signal.getOriginalDecision())) return true;
        return signal.getDecision() == SignalDecision.WATCH
                && signal.getTotalScore() >= WATCH_EVIDENCE_MIN_SCORE
                && signal.getConfidenceScore() >= WATCH_EVIDENCE_MIN_CONFIDENCE;
    }

    private HardRiskBlock nonAtrHardRiskBlock(TradeSignal signal) {
        if (!signal.isStrategyEntryAllowed()) {
            return HardRiskBlock.block("STRATEGY_ENTRY_BLOCKED", "Selected market strategy does not allow entry.");
        }
        if (!signal.isBtcContextEntryAllowed()) {
            return HardRiskBlock.block("BTC_CONTEXT_BLOCKED", "BTC context issued an entry veto.");
        }
        if (!signal.isDerivativesEntryAllowed()) {
            return HardRiskBlock.block("DERIVATIVES_BLOCKED", "Funding/open-interest positioning issued an entry veto.");
        }
        // With wall-lifecycle intelligence, TARGET_BLOCKED should only remain false-entry-allowed
        // when the wall is genuinely strong/relevant. Weakening walls no longer reach this hard veto.
        if (!signal.isLiquidityEntryAllowed()
                && signal.getLiquidityStatus() == LiquidityContextStatus.TARGET_BLOCKED) {
            return HardRiskBlock.block("STRONG_TARGET_WALL",
                    "A strong nearby target-side wall is still active; execution remains blocked until fresh order-book evidence changes.");
        }
        return HardRiskBlock.none();
    }

    private Evidence evidence(TradeSignal current) {
        Instant cutoff = current.getGeneratedAt().minus(EVIDENCE_WINDOW);
        List<TradeSignal> recent = recentSignals(current.getSymbol(), EXECUTION_INTERVAL, current.getGeneratedAt());

        int buy = 0, watch = 0, neutral = 0, bearish = 0, evidenceScore = 0;
        int scoreTotal = 0, confidenceTotal = 0, qualityCount = 0;
        int health = OPPORTUNITY_HEALTH_START;
        Instant lastBearishAt = null;
        Instant oldestObservedAt = null;
        List<Long> signalIds = new ArrayList<>();
        List<TradeSignal> observedSequence = new ArrayList<>();

        for (TradeSignal signal : recent) {
            if (signal.getGeneratedAt() == null || signal.getGeneratedAt().isAfter(current.getGeneratedAt())) continue;
            if (signal.getGeneratedAt().isBefore(cutoff)) break;

            if (oldestObservedAt == null || signal.getGeneratedAt().isBefore(oldestObservedAt)) {
                oldestObservedAt = signal.getGeneratedAt();
            }

            observedSequence.add(signal);

            SignalDecision decision = signal.getDecision();
            SignalDecision original = signal.getOriginalDecision();
            SignalDecision effective = strongerDecision(decision, original);

            if (effective == SignalDecision.STRONG_SELL) {
                bearish++;
                health += scaledHealthContribution(HEALTH_1M_STRONG_SELL, signal);
                if (lastBearishAt == null) lastBearishAt = signal.getGeneratedAt();
                break; // a strong bearish 1m event is still a true memory boundary
            }

            if (effective == SignalDecision.SELL) {
                bearish++;
                evidenceScore -= WEAK_SELL_EVIDENCE_PENALTY;
                health += scaledHealthContribution(HEALTH_1M_SELL, signal);
                if (lastBearishAt == null) lastBearishAt = signal.getGeneratedAt();
                continue;
            }

            if (isBullish(effective)) {
                buy++;
                evidenceScore += 3;
                health += scaledHealthContribution(HEALTH_1M_BUY, signal);
                if (signal.getId() != null) {
                    signalIds.add(signal.getId());
                }
            } else if (decision == SignalDecision.WATCH
                    && signal.getTotalScore() >= WATCH_EVIDENCE_MIN_SCORE
                    && signal.getConfidenceScore() >= WATCH_EVIDENCE_MIN_CONFIDENCE) {
                watch++;
                evidenceScore += 1;
                health += scaledHealthContribution(HEALTH_1M_WATCH, signal);
                if (signal.getId() != null) {
                    signalIds.add(signal.getId());
                }
            } else if (decision == SignalDecision.NEUTRAL) {
                neutral++;
                health += HEALTH_1M_NEUTRAL;
            }

            if (decision == SignalDecision.WATCH || isBullish(decision)) {
                scoreTotal += signal.getTotalScore();
                confidenceTotal += signal.getConfidenceScore();
                qualityCount++;
            }
        }

        int evidenceMomentum = calculateEvidenceMomentum(observedSequence);
        health += evidenceMomentum;

        TradeSignal five = latestAtOrBefore(current, CONFIRMATION_INTERVAL, FIVE_MINUTE_MAX_AGE);
        TradeSignal one = latestAtOrBefore(current, TREND_INTERVAL, ONE_HOUR_MAX_AGE);

        // Higher timeframe evidence actively restores or weakens opportunity health.
        // This is deliberately generic: no symbol-specific values are used.
        health += timeframeHealthContribution(five, CONFIRMATION_INTERVAL);
        health += timeframeHealthContribution(one, TREND_INTERVAL);

        long ageMinutes = oldestObservedAt == null ? 0
                : Math.max(0, Duration.between(oldestObservedAt, current.getGeneratedAt()).toMinutes());
        // Slow decay: fresh bullish evidence should be able to recover faster than age destroys it.
        health -= Math.min(6, (int) (ageMinutes / 10));
        evidenceScore = Math.max(0, evidenceScore);
        health = Math.max(0, Math.min(100, health));

        int previousHealth = currentOpportunity(current.getSymbol(), List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .map(ExecutionOpportunity::getOpportunityHealth)
                .orElse(OPPORTUNITY_HEALTH_START);
        int healthMomentum = health - previousHealth;

        return new Evidence(
                buy + watch + neutral + bearish,
                buy,
                watch,
                neutral,
                bearish,
                evidenceScore,
                health,
                healthMomentum,
                evidenceMomentum,
                qualityCount == 0 ? 0 : Math.round((float) scoreTotal / qualityCount),
                qualityCount == 0 ? 0 : Math.round((float) confidenceTotal / qualityCount),
                five == null ? null : five.getDecision(),
                one == null ? null : one.getDecision(),
                lastBearishAt,
                signalIds.stream().filter(Objects::nonNull).toList()
        );
    }

    private int calculateEvidenceMomentum(List<TradeSignal> observedDescending) {
        if (observedDescending == null || observedDescending.size() < 2) return 0;

        List<TradeSignal> sequence = new ArrayList<>(observedDescending);
        Collections.reverse(sequence); // oldest -> newest
        if (sequence.size() > EVIDENCE_MOMENTUM_WINDOW) {
            sequence = new ArrayList<>(sequence.subList(sequence.size() - EVIDENCE_MOMENTUM_WINDOW, sequence.size()));
        }

        int momentum = 0;
        for (int i = 1; i < sequence.size(); i++) {
            TradeSignal previous = sequence.get(i - 1);
            TradeSignal current = sequence.get(i);
            int previousStrength = decisionStrength(strongerDecision(previous.getDecision(), previous.getOriginalDecision()));
            int currentStrength = decisionStrength(strongerDecision(current.getDecision(), current.getOriginalDecision()));
            int delta = currentStrength - previousStrength;
            int recencyWeight = Math.min(3, i);
            momentum += delta * 3 * recencyWeight;
        }

        TradeSignal latest = sequence.get(sequence.size() - 1);
        int latestStrength = decisionStrength(strongerDecision(latest.getDecision(), latest.getOriginalDecision()));
        momentum += latestStrength * 2;
        return Math.max(-EVIDENCE_MOMENTUM_CAP, Math.min(EVIDENCE_MOMENTUM_CAP, momentum));
    }

    private int decisionStrength(SignalDecision decision) {
        if (decision == null) return 0;
        return switch (decision) {
            case STRONG_BUY -> 2;
            case BUY, WATCH -> 1;
            case NEUTRAL -> 0;
            case SELL -> -1;
            case STRONG_SELL -> -2;
        };
    }

    private int timeframeHealthContribution(TradeSignal signal, String interval) {
        if (signal == null) return 0;
        SignalDecision decision = strongerDecision(signal.getDecision(), signal.getOriginalDecision());
        int base;
        if (CONFIRMATION_INTERVAL.equals(interval)) {
            base = switch (decision) {
                case BUY, STRONG_BUY -> HEALTH_5M_BUY;
                case WATCH -> HEALTH_5M_WATCH;
                case SELL -> HEALTH_5M_SELL;
                case STRONG_SELL -> HEALTH_5M_STRONG_SELL;
                default -> 0;
            };
        } else {
            base = switch (decision) {
                case BUY, STRONG_BUY -> HEALTH_1H_BUY;
                case WATCH -> HEALTH_1H_WATCH;
                case SELL -> HEALTH_1H_SELL;
                case STRONG_SELL -> HEALTH_1H_STRONG_SELL;
                default -> 0;
            };
        }
        return scaledHealthContribution(base, signal);
    }

    private int scaledHealthContribution(int base, TradeSignal signal) {
        if (base == 0 || signal == null) return 0;
        int quality = Math.round((signal.getTotalScore() + signal.getConfidenceScore()) / 2.0f);
        double factor = quality >= 80 ? 1.25 : quality >= 70 ? 1.0 : quality >= 60 ? 0.8 : 0.6;
        return (int) Math.round(base * factor);
    }

    /**
     * Downstream execution must respect FinalDecisionService as the authority.
     * originalDecision is retained for audit/explainability only and is used
     * solely as a null fallback for legacy rows.
     */
    private SignalDecision strongerDecision(SignalDecision decision, SignalDecision original) {
        return decision != null ? decision : original;
    }

    private boolean isHardBearishReversal(TradeSignal current, Evidence evidence) {
        if (current.getDecision() == SignalDecision.STRONG_SELL) {
            return true;
        }
        return isBearish(evidence.fiveMinute()) || isBearish(evidence.oneHour());
    }


    private TradeSignal recentBearishReversalAnchor(TradeSignal current) {
        if (current == null || current.getGeneratedAt() == null) return null;
        Instant cutoff = current.getGeneratedAt().minus(EXCEPTIONAL_PROBE_REVERSAL_LOOKBACK);
        return recentSignals(current.getSymbol(), EXECUTION_INTERVAL, current.getGeneratedAt()).stream()
                .filter(signal -> signal != null && signal.getGeneratedAt() != null)
                .filter(signal -> signal.getGeneratedAt().isBefore(current.getGeneratedAt())
                        && !signal.getGeneratedAt().isBefore(cutoff))
                .filter(signal -> isBearish(strongerDecision(signal.getDecision(), signal.getOriginalDecision())))
                .filter(signal -> signal.getTotalScore() <= EXCEPTIONAL_PROBE_MAX_PRIOR_SCORE)
                .max(java.util.Comparator.comparing(TradeSignal::getGeneratedAt))
                .orElse(null);
    }

    private TradeSignal latestAtOrBefore(TradeSignal current, String interval, Duration maxAge) {
        TradeSignal result = latestSignalAtOrBefore(current.getSymbol(), interval, current.getGeneratedAt()).orElse(null);
        if (result == null || result.getGeneratedAt() == null
                || result.getGeneratedAt().isBefore(current.getGeneratedAt().minus(maxAge))) {
            return null;
        }
        return result;
    }

    private void saveOpportunity(TradeSignal signal, Evidence evidence, String status, String source,
                                 int positionPercent, String code, String explanation) {
        Instant now = Instant.now();
        ExecutionOpportunity opportunity = currentOpportunity(signal.getSymbol(), List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .orElseGet(() -> ExecutionOpportunity.builder()
                        .symbol(signal.getSymbol())
                        .direction("BUY")
                        .startedAt(signal.getGeneratedAt())
                        .createdAt(now)
                        .build());

        opportunity.setStatus(normalizeStatus(status));
        opportunity.setLastEvidenceAt(signal.getGeneratedAt());
        opportunity.setLatestSignal(signal);
        opportunity.setEvidenceCount(evidence.observationCount());
        opportunity.setBuyCount(evidence.buyCount());
        opportunity.setWatchCount(evidence.watchCount());
        opportunity.setNeutralCount(evidence.neutralCount());
        opportunity.setBearishCount(evidence.bearishCount());
        opportunity.setEvidenceScore(evidence.evidenceScore());
        opportunity.setOpportunityHealth(evidence.opportunityHealth());
        opportunity.setHealthMomentum(evidence.healthMomentum());
        opportunity.setEvidenceMomentum(evidence.evidenceMomentum());
        opportunity.setLastBearishAt(evidence.lastBearishAt());
        opportunity.setAverageSignalScore(evidence.averageScore());
        opportunity.setAverageConfidence(evidence.averageConfidence());
        opportunity.setFiveMinuteDecision(evidence.fiveMinute() == null ? null : evidence.fiveMinute().name());
        opportunity.setOneHourDecision(evidence.oneHour() == null ? null : evidence.oneHour().name());
        opportunity.setExecutionSource(source);
        opportunity.setRecommendedPositionPercent(positionPercent);
        opportunity.setDecisionCode(code);
        opportunity.setDecisionExplanation(explanation);
        opportunity.setUpdatedAt(now);
        saveOpportunityEntity(opportunity);
    }

    private void closeOpportunity(TradeSignal signal, String status, String code, String explanation) {
        currentOpportunity(signal.getSymbol(), List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .ifPresent(opportunity -> {
                    opportunity.setStatus(status);
                    opportunity.setLatestSignal(signal);
                    opportunity.setLastEvidenceAt(signal.getGeneratedAt());
                    opportunity.setDecisionCode(code);
                    opportunity.setDecisionExplanation(explanation);
                    opportunity.setUpdatedAt(Instant.now());
                    saveOpportunityEntity(opportunity);
                });
    }

    private List<TradeSignal> recentSignals(String symbol, String interval, Instant reference) {
        if (replayScope != null && replayScope.active()) return replayScope.recent(symbol, interval, reference, 20);
        return signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc(symbol, interval);
    }

    private java.util.Optional<TradeSignal> latestSignalAtOrBefore(String symbol, String interval, Instant reference) {
        if (replayScope != null && replayScope.active()) return replayScope.latestAtOrBefore(symbol, interval, reference);
        return signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(symbol, interval, reference);
    }

    private java.util.Optional<TradeSignal> previousSignalBefore(String symbol, String interval, Instant reference) {
        if (replayScope != null && replayScope.active()) return replayScope.previousBefore(symbol, interval, reference);
        return signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanOrderByGeneratedAtDesc(symbol, interval, reference);
    }

    private java.util.Optional<ExecutionOpportunity> currentOpportunity(String symbol, List<String> statuses) {
        if (replayScope != null && replayScope.active()) return replayScope.currentOpportunity(symbol, statuses);
        return opportunityRepository.findTopBySymbolAndDirectionAndStatusInOrderByUpdatedAtDesc(symbol, "BUY", statuses);
    }

    private ExecutionOpportunity saveOpportunityEntity(ExecutionOpportunity opportunity) {
        if (replayScope != null && replayScope.active()) return replayScope.saveOpportunity(opportunity);
        return opportunityRepository.save(opportunity);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "BUILDING";
        return switch (status) {
            case "CONFIRMED", "BLOCKED", "WEAKENING", "CANCELLED", "EXECUTED", "COMPLETED" -> status;
            default -> "BUILDING";
        };
    }

    private boolean isBullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private boolean isBearish(SignalDecision decision) {
        return decision == SignalDecision.SELL || decision == SignalDecision.STRONG_SELL;
    }

    public record SetupWakeupEvaluation(TradeSignal executionSignal, ExecutionDecision decision) {
        public static SetupWakeupEvaluation none() { return new SetupWakeupEvaluation(null, null); }
        public boolean present() { return executionSignal != null && decision != null; }
    }

    public record ExecutionDecision(
            boolean allowed,
            String source,
            String code,
            String state,
            int positionPercent,
            String explanation,
            Evidence evidence
    ) {
        public static ExecutionDecision allow(String source, String code, int positionPercent,
                                              String explanation, Evidence evidence) {
            return new ExecutionDecision(true, source, code, "CONFIRMED",
                    Math.max(1, Math.min(100, positionPercent)), explanation, evidence);
        }

        public static ExecutionDecision reject(String code, String explanation) {
            return reject(code, explanation, Evidence.empty());
        }

        public static ExecutionDecision reject(String code, String explanation, Evidence evidence) {
            return new ExecutionDecision(false, "HARD_RISK", code, "BLOCKED", 0, explanation, evidence);
        }

        public static ExecutionDecision building(String code, String explanation, Evidence evidence) {
            return new ExecutionDecision(false, "ACCUMULATED_EVIDENCE", code, "BUILDING", 0, explanation, evidence);
        }

        public static ExecutionDecision weakening(String code, String explanation, Evidence evidence) {
            return new ExecutionDecision(false, "OPPORTUNITY_MEMORY", code, "WEAKENING", 0, explanation, evidence);
        }

        public static ExecutionDecision observe(String code, String explanation) {
            return observe(code, explanation, Evidence.empty());
        }

        public static ExecutionDecision observe(String code, String explanation, Evidence evidence) {
            return new ExecutionDecision(false, "OBSERVE", code, "BUILDING", 0, explanation, evidence);
        }
    }

    public record Evidence(
            int observationCount,
            int buyCount,
            int watchCount,
            int neutralCount,
            int bearishCount,
            int evidenceScore,
            int opportunityHealth,
            int healthMomentum,
            int evidenceMomentum,
            int averageScore,
            int averageConfidence,
            SignalDecision fiveMinute,
            SignalDecision oneHour,
            Instant lastBearishAt,
            List<Long> supportingSignalIds
    ) {
        static Evidence empty() {
            return new Evidence(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, List.of());
        }
    }

    public record EntryQuality(
            int score,
            String classification,
            double expansionPercent,
            double atrExtension,
            double rewardRisk,
            long opportunityAgeMinutes
    ) {}

    private record HardRiskBlock(boolean blocked, String code, String explanation) {
        static HardRiskBlock none() { return new HardRiskBlock(false, "NONE", ""); }
        static HardRiskBlock block(String code, String explanation) {
            return new HardRiskBlock(true, code, explanation);
        }
    }
}
