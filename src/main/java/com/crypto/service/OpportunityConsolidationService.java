package com.crypto.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.execution.service.ExecutionReplayScope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds execution evidence from a sequence of already-generated 1m BUY signals.
 *
 * Important architecture rule: this service NEVER creates a TradeSignal.  AnalysisService
 * remains the single source of truth for signals.  Consolidation is an execution-only path
 * that may approve the CURRENT fresh 1m BUY after several recent BUY signals have persisted.
 */
@Service
@RequiredArgsConstructor
public class OpportunityConsolidationService {

    private static final String EXECUTION_INTERVAL = "1m";
    private static final String CONFIRMATION_INTERVAL = "5m";
    private static final String TREND_INTERVAL = "1h";

    private static final Duration OPPORTUNITY_WINDOW = Duration.ofMinutes(30);
    private static final Duration FIVE_MINUTE_MAX_AGE = Duration.ofMinutes(20);
    private static final Duration ONE_HOUR_MAX_AGE = Duration.ofHours(3);

    private static final int MIN_CONSECUTIVE_BUYS = 3;
    private static final int STRONG_CONSECUTIVE_BUYS = 5;
    private static final int MIN_AVERAGE_SCORE = 75;
    private static final int MIN_AVERAGE_CONFIDENCE = 70;

    private final TradeSignalRepository signalRepository;
    @Autowired(required = false)
    private ExecutionReplayScope replayScope;

    public Assessment evaluate(TradeSignal currentSignal) {
        if (currentSignal == null || currentSignal.getGeneratedAt() == null) {
            return Assessment.reject("INVALID_SIGNAL", "Current execution signal is missing.");
        }
        if (!EXECUTION_INTERVAL.equals(currentSignal.getInterval()) || !isBullish(currentSignal.getDecision())) {
            return Assessment.reject("NOT_1M_BUY", "Opportunity consolidation only evaluates fresh 1m BUY/STRONG_BUY signals.");
        }

        TradeSignal fiveMinute = latestAtOrBefore(currentSignal, CONFIRMATION_INTERVAL);
        TradeSignal oneHour = latestAtOrBefore(currentSignal, TREND_INTERVAL);

        if (!isFresh(fiveMinute, currentSignal.getGeneratedAt(), FIVE_MINUTE_MAX_AGE)) {
            return Assessment.reject("MISSING_5M_CONTEXT", "No fresh 5m context is available for consolidation.");
        }
        if (!isFresh(oneHour, currentSignal.getGeneratedAt(), ONE_HOUR_MAX_AGE)) {
            return Assessment.reject("MISSING_1H_CONTEXT", "No fresh 1h context is available for consolidation.");
        }
        if (isBearish(fiveMinute.getDecision())) {
            return Assessment.reject("5M_BEARISH_VETO", "Consolidated BUY cancelled because 5m is " + fiveMinute.getDecision() + ".");
        }
        if (isBearish(oneHour.getDecision())) {
            return Assessment.reject("1H_BEARISH_VETO", "Consolidated BUY cancelled because 1h is " + oneHour.getDecision() + ".");
        }

        List<TradeSignal> recent = replayScope != null && replayScope.active()
                ? replayScope.recent(currentSignal.getSymbol(), EXECUTION_INTERVAL, currentSignal.getGeneratedAt(), 20)
                : signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc(currentSignal.getSymbol(), EXECUTION_INTERVAL);
        List<TradeSignal> consecutive = consecutiveFreshBuys(recent, currentSignal.getGeneratedAt());

        int count = consecutive.size();
        if (count < MIN_CONSECUTIVE_BUYS) {
            return Assessment.building(
                    count,
                    averageScore(consecutive),
                    averageConfidence(consecutive),
                    firstScore(consecutive),
                    currentSignal.getTotalScore(),
                    fiveMinute.getDecision(),
                    oneHour.getDecision(),
                    "BUILDING",
                    "Bullish opportunity is building: " + count + "/" + MIN_CONSECUTIVE_BUYS
                            + " consecutive fresh 1m BUY signals."
            );
        }

        int averageScore = averageScore(consecutive);
        int averageConfidence = averageConfidence(consecutive);
        int firstScore = firstScore(consecutive);
        int latestScore = currentSignal.getTotalScore();
        boolean scoreStableOrImproving = latestScore >= firstScore - 3;

        if (averageScore < MIN_AVERAGE_SCORE || averageConfidence < MIN_AVERAGE_CONFIDENCE || !scoreStableOrImproving) {
            return Assessment.rejectDetailed(
                    "QUALITY_DECAY",
                    count,
                    averageScore,
                    averageConfidence,
                    firstScore,
                    latestScore,
                    fiveMinute.getDecision(),
                    oneHour.getDecision(),
                    "Persistent BUY signals exist, but quality is not stable enough. Average score=" + averageScore
                            + ", average confidence=" + averageConfidence + ", first/latest score=" + firstScore + "/" + latestScore + "."
            );
        }

        // Fully neutral 5m + 1h needs stronger persistence; this avoids treating repeated 1m noise as confirmation.
        if (fiveMinute.getDecision() == SignalDecision.NEUTRAL
                && oneHour.getDecision() == SignalDecision.NEUTRAL
                && count < STRONG_CONSECUTIVE_BUYS) {
            return Assessment.building(
                    count,
                    averageScore,
                    averageConfidence,
                    firstScore,
                    latestScore,
                    fiveMinute.getDecision(),
                    oneHour.getDecision(),
                    "BUILDING_STRONGER_CONFIRMATION",
                    "1m BUY persistence is valid, but both 5m and 1h are NEUTRAL. Waiting for "
                            + STRONG_CONSECUTIVE_BUYS + " consecutive BUY signals or improving higher-timeframe context."
            );
        }

        int positionPercent = consolidatedPositionPercent(count, fiveMinute.getDecision(), oneHour.getDecision());
        String state = count >= STRONG_CONSECUTIVE_BUYS ? "CONFIRMED" : "BUILDING_CONFIDENCE";
        String explanation = "Consolidated opportunity approved from the current signal #" + currentSignal.getId()
                + ": " + count + " consecutive fresh 1m BUY signals, average score=" + averageScore
                + ", average confidence=" + averageConfidence + ", score path=" + firstScore + "→" + latestScore
                + ", 5m=" + fiveMinute.getDecision() + ", 1h=" + oneHour.getDecision()
                + ". Reduced execution size=" + positionPercent + "%. No second trade signal was generated.";

        return Assessment.allow(
                positionPercent,
                count,
                averageScore,
                averageConfidence,
                firstScore,
                latestScore,
                fiveMinute.getDecision(),
                oneHour.getDecision(),
                state,
                explanation
        );
    }

    private List<TradeSignal> consecutiveFreshBuys(List<TradeSignal> recent, Instant reference) {
        List<TradeSignal> result = new ArrayList<>();
        Instant cutoff = reference.minus(OPPORTUNITY_WINDOW);
        for (TradeSignal signal : recent) {
            if (signal.getGeneratedAt() == null || signal.getGeneratedAt().isAfter(reference)) {
                continue;
            }
            if (signal.getGeneratedAt().isBefore(cutoff)) {
                break;
            }
            if (!isBullish(signal.getDecision())) {
                break;
            }
            result.add(signal);
        }
        return result;
    }

    private int consolidatedPositionPercent(int count, SignalDecision fiveMinute, SignalDecision oneHour) {
        int percent = count >= STRONG_CONSECUTIVE_BUYS ? 50 : 25;
        if (fiveMinute == SignalDecision.WATCH) percent += 10;
        if (isBullish(fiveMinute)) percent += 20;
        if (oneHour == SignalDecision.WATCH) percent += 5;
        if (isBullish(oneHour)) percent += 10;
        return Math.min(75, percent);
    }

    private int averageScore(List<TradeSignal> signals) {
        if (signals.isEmpty()) return 0;
        return (int) Math.round(signals.stream().mapToInt(TradeSignal::getTotalScore).average().orElse(0));
    }

    private int averageConfidence(List<TradeSignal> signals) {
        if (signals.isEmpty()) return 0;
        return (int) Math.round(signals.stream().mapToInt(TradeSignal::getConfidenceScore).average().orElse(0));
    }

    private int firstScore(List<TradeSignal> signals) {
        return signals.isEmpty() ? 0 : signals.get(signals.size() - 1).getTotalScore();
    }

    private TradeSignal latestAtOrBefore(TradeSignal executionSignal, String interval) {
        return (replayScope != null && replayScope.active()
                ? replayScope.latestAtOrBefore(executionSignal.getSymbol(), interval, executionSignal.getGeneratedAt())
                : signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        executionSignal.getSymbol(), interval, executionSignal.getGeneratedAt()))
                .orElse(null);
    }

    private boolean isFresh(TradeSignal signal, Instant reference, Duration maximumAge) {
        return signal != null
                && signal.getGeneratedAt() != null
                && !signal.getGeneratedAt().isAfter(reference)
                && !signal.getGeneratedAt().isBefore(reference.minus(maximumAge));
    }

    private boolean isBullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private boolean isBearish(SignalDecision decision) {
        return decision == SignalDecision.SELL || decision == SignalDecision.STRONG_SELL;
    }

    public record Assessment(
            boolean allowed,
            String code,
            String state,
            String explanation,
            int positionPercent,
            int consecutiveBuyCount,
            int averageScore,
            int averageConfidence,
            int firstScore,
            int latestScore,
            SignalDecision fiveMinuteDecision,
            SignalDecision oneHourDecision
    ) {
        static Assessment allow(int positionPercent, int count, int averageScore, int averageConfidence,
                                int firstScore, int latestScore, SignalDecision fiveMinute, SignalDecision oneHour,
                                String state, String explanation) {
            return new Assessment(true, "CONSOLIDATED_BUY", state, explanation,
                    Math.max(1, Math.min(75, positionPercent)), count, averageScore, averageConfidence,
                    firstScore, latestScore, fiveMinute, oneHour);
        }

        static Assessment building(int count, int averageScore, int averageConfidence,
                                   int firstScore, int latestScore, SignalDecision fiveMinute, SignalDecision oneHour,
                                   String state, String explanation) {
            return new Assessment(false, "OPPORTUNITY_BUILDING", state, explanation, 0,
                    count, averageScore, averageConfidence, firstScore, latestScore, fiveMinute, oneHour);
        }

        static Assessment reject(String code, String explanation) {
            return new Assessment(false, code, "CANCELLED", explanation, 0,
                    0, 0, 0, 0, 0, null, null);
        }

        static Assessment rejectDetailed(String code, int count, int averageScore, int averageConfidence,
                                         int firstScore, int latestScore, SignalDecision fiveMinute,
                                         SignalDecision oneHour, String explanation) {
            return new Assessment(false, code, "WEAKENING", explanation, 0,
                    count, averageScore, averageConfidence, firstScore, latestScore, fiveMinute, oneHour);
        }
    }
}
