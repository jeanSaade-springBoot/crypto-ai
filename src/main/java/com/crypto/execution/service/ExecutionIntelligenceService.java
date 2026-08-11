package com.crypto.execution.service;

import com.crypto.config.TradingProperties;
import com.crypto.domain.LiquidityContextStatus;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
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
    @Autowired(required = false)
    private ExecutionReplayScope replayScope;

    @Transactional
    public ExecutionDecision evaluateBuy(TradeSignal signal) {
        return evaluateBuy(signal, 0, "NONE");
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

        // Non-ATR safety vetoes remain absolute. ATR is intentionally handled later so an
        // ATR-deferred BUY can be reconsidered by the continuation engine when fresh
        // multi-timeframe evidence confirms the move and the CURRENT risk/reward remains sound.
        HardRiskBlock nonAtrHardBlock = nonAtrHardRiskBlock(signal);
        if (nonAtrHardBlock.blocked()) {
            Evidence evidence = evidence(signal);
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

        ExecutionDecision deferred = deferredContinuationDecision(signal, evidence);
        if (deferred != null) {
            deferred = applyInitialEntryQualityGuard(deferred, entryQuality);
            saveOpportunity(signal, evidence,
                    deferred.allowed() ? "CONFIRMED" : deferred.state(),
                    deferred.source(), deferred.positionPercent(),
                    deferred.code(), deferred.explanation());
            return deferred;
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
        if (e.averageScore() < 65 || e.averageConfidence() < 65) {
            return ExecutionDecision.building("EVIDENCE_QUALITY_LOW",
                    "Evidence persists, but average quality is still low: score=" + e.averageScore()
                            + ", confidence=" + e.averageConfidence() + ".", e);
        }

        int percent = e.evidenceScore() >= STRONG_EVIDENCE_SCORE ? 50 : 25;
        if (e.buyCount() >= 2) percent += 10;
        if (e.fiveMinute() == SignalDecision.WATCH) percent += 5;
        if (isBullish(e.fiveMinute())) percent += 15;
        if (e.oneHour() == SignalDecision.WATCH) percent += 5;
        if (isBullish(e.oneHour())) percent += 10;
        percent = Math.min(75, percent);

        return ExecutionDecision.allow(
                "ACCUMULATED_EVIDENCE",
                "OPPORTUNITY_CONFIRMED",
                percent,
                "Execution Intelligence approved the current signal from accumulated fresh evidence: "
                        + e.buyCount() + " BUY, " + e.watchCount() + " WATCH, evidence score=" + e.evidenceScore()
                        + ", average score=" + e.averageScore() + ", average confidence=" + e.averageConfidence()
                        + ", 5m=" + e.fiveMinute() + ", 1h=" + e.oneHour()
                        + ". No second trade signal was generated.",
                e
        );
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
            case "CONFIRMED", "BLOCKED", "WEAKENING", "CANCELLED", "EXECUTED" -> status;
            default -> "BUILDING";
        };
    }

    private boolean isBullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private boolean isBearish(SignalDecision decision) {
        return decision == SignalDecision.SELL || decision == SignalDecision.STRONG_SELL;
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
