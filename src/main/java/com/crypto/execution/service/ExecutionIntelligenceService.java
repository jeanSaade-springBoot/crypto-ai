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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Transactional
    public ExecutionDecision evaluateBuy(TradeSignal signal) {
        if (signal == null || signal.getGeneratedAt() == null) {
            return ExecutionDecision.reject("INVALID_SIGNAL", "Signal is missing required execution data.");
        }
        if (!EXECUTION_INTERVAL.equals(signal.getInterval())) {
            return ExecutionDecision.observe("CONTEXT_ONLY", "Only fresh 1m signals may trigger a BUY execution.");
        }

        if (isBearish(signal.getDecision()) || isBearish(signal.getOriginalDecision())) {
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
        ExecutionDecision deferred = deferredContinuationDecision(signal, evidence);
        if (deferred != null) {
            saveOpportunity(signal, evidence,
                    deferred.allowed() ? "CONFIRMED" : deferred.state(),
                    deferred.source(), deferred.positionPercent(),
                    deferred.code(), deferred.explanation());
            return deferred;
        }

        // For every non-continuation path, ATR immediate-entry permission remains mandatory.
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
                saveOpportunity(signal, evidence, "CONFIRMED", "IMMEDIATE_VALIDATION",
                        validation.positionPercent(), validation.code(), validation.explanation());
                return ExecutionDecision.allow(
                        "IMMEDIATE_VALIDATION",
                        validation.code(),
                        validation.positionPercent(),
                        validation.explanation(),
                        evidence
                );
            }

            // Preserve the existing BUY-only persistence model as the first consolidation route.
            OpportunityConsolidationService.Assessment consolidated = consolidationService.evaluate(signal);
            if (consolidated.allowed()) {
                saveOpportunity(signal, evidence, "CONFIRMED", "CONSOLIDATED_BUY",
                        consolidated.positionPercent(), consolidated.code(), consolidated.explanation());
                return ExecutionDecision.allow(
                        "CONSOLIDATED_BUY",
                        consolidated.code(),
                        consolidated.positionPercent(),
                        consolidated.explanation(),
                        evidence
                );
            }
        }

        // Intelligent evidence path: BUY and strong WATCH observations can build one opportunity.
        ExecutionDecision accumulated = accumulatedDecision(signal, evidence);
        saveOpportunity(signal, evidence,
                accumulated.allowed() ? "CONFIRMED" : accumulated.state(),
                accumulated.source(), accumulated.positionPercent(),
                accumulated.code(), accumulated.explanation());
        return accumulated;
    }

    @Transactional
    public void markExecuted(TradeSignal signal, ExecutionDecision decision) {
        if (signal == null || decision == null || !decision.allowed()) return;
        opportunityRepository.findTopBySymbolAndDirectionAndStatusInOrderByUpdatedAtDesc(
                        signal.getSymbol(), "BUY", List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .ifPresent(opportunity -> {
                    opportunity.setStatus("EXECUTED");
                    opportunity.setExecutionSource(decision.source());
                    opportunity.setRecommendedPositionPercent(decision.positionPercent());
                    opportunity.setDecisionCode(decision.code());
                    opportunity.setDecisionExplanation(decision.explanation());
                    opportunity.setLatestSignal(signal);
                    opportunity.setLastEvidenceAt(signal.getGeneratedAt());
                    opportunity.setExecutedAt(Instant.now());
                    opportunity.setUpdatedAt(Instant.now());
                    opportunityRepository.save(opportunity);
                });
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
        return signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc(
                        current.getSymbol(), EXECUTION_INTERVAL).stream()
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
        List<TradeSignal> recent = signalRepository
                .findTop20BySymbolAndIntervalOrderByGeneratedAtDesc(current.getSymbol(), EXECUTION_INTERVAL);

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
                signalIds.add(signal.getId());
            } else if (decision == SignalDecision.WATCH
                    && signal.getTotalScore() >= WATCH_EVIDENCE_MIN_SCORE
                    && signal.getConfidenceScore() >= WATCH_EVIDENCE_MIN_CONFIDENCE) {
                watch++;
                evidenceScore += 1;
                health += scaledHealthContribution(HEALTH_1M_WATCH, signal);
                signalIds.add(signal.getId());
            } else if (decision == SignalDecision.NEUTRAL) {
                neutral++;
                health += HEALTH_1M_NEUTRAL;
            }

            if (decision == SignalDecision.WATCH || isBullish(decision) || isBullish(original)) {
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

        int previousHealth = opportunityRepository
                .findTopBySymbolAndDirectionAndStatusInOrderByUpdatedAtDesc(
                        current.getSymbol(), "BUY", List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
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
                List.copyOf(signalIds)
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

    private SignalDecision strongerDecision(SignalDecision decision, SignalDecision original) {
        if (decision == SignalDecision.STRONG_SELL || original == SignalDecision.STRONG_SELL) return SignalDecision.STRONG_SELL;
        if (decision == SignalDecision.SELL || original == SignalDecision.SELL) return SignalDecision.SELL;
        if (decision == SignalDecision.STRONG_BUY || original == SignalDecision.STRONG_BUY) return SignalDecision.STRONG_BUY;
        if (decision == SignalDecision.BUY || original == SignalDecision.BUY) return SignalDecision.BUY;
        if (decision == SignalDecision.WATCH || original == SignalDecision.WATCH) return SignalDecision.WATCH;
        return decision == null ? original : decision;
    }

    private boolean isHardBearishReversal(TradeSignal current, Evidence evidence) {
        if (current.getDecision() == SignalDecision.STRONG_SELL
                || current.getOriginalDecision() == SignalDecision.STRONG_SELL) {
            return true;
        }
        return isBearish(evidence.fiveMinute()) || isBearish(evidence.oneHour());
    }

    private TradeSignal latestAtOrBefore(TradeSignal current, String interval, Duration maxAge) {
        TradeSignal result = signalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        current.getSymbol(), interval, current.getGeneratedAt())
                .orElse(null);
        if (result == null || result.getGeneratedAt() == null
                || result.getGeneratedAt().isBefore(current.getGeneratedAt().minus(maxAge))) {
            return null;
        }
        return result;
    }

    private void saveOpportunity(TradeSignal signal, Evidence evidence, String status, String source,
                                 int positionPercent, String code, String explanation) {
        Instant now = Instant.now();
        ExecutionOpportunity opportunity = opportunityRepository
                .findTopBySymbolAndDirectionAndStatusInOrderByUpdatedAtDesc(
                        signal.getSymbol(), "BUY", List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
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
        opportunityRepository.save(opportunity);
    }

    private void closeOpportunity(TradeSignal signal, String status, String code, String explanation) {
        opportunityRepository.findTopBySymbolAndDirectionAndStatusInOrderByUpdatedAtDesc(
                        signal.getSymbol(), "BUY", List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .ifPresent(opportunity -> {
                    opportunity.setStatus(status);
                    opportunity.setLatestSignal(signal);
                    opportunity.setLastEvidenceAt(signal.getGeneratedAt());
                    opportunity.setDecisionCode(code);
                    opportunity.setDecisionExplanation(explanation);
                    opportunity.setUpdatedAt(Instant.now());
                    opportunityRepository.save(opportunity);
                });
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

    private record HardRiskBlock(boolean blocked, String code, String explanation) {
        static HardRiskBlock none() { return new HardRiskBlock(false, "NONE", ""); }
        static HardRiskBlock block(String code, String explanation) {
            return new HardRiskBlock(true, code, explanation);
        }
    }
}
