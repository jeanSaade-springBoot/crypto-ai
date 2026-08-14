package com.crypto.service;

import com.crypto.domain.ConfluenceStatus;
import com.crypto.domain.MarketRegime;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.domain.TradingStrategy;
import com.crypto.dto.MultiTimeframeConfluenceResult;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.execution.service.ExecutionReplayScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MultiTimeframeConfluenceService {

    private static final int BULLISH_TREND_MINIMUM = 15;
    private static final int STRONG_BULLISH_TREND_MINIMUM = 20;
    private static final int BEARISH_TREND_MAXIMUM = 10;
    private static final int STRONG_BEARISH_TREND_MAXIMUM = 6;

    private final TradeSignalRepository tradeSignalRepository;
    @Autowired(required = false)
    private ExecutionReplayScope replayScope;

    /** Generic pre-strategy context evaluation. */
    public MultiTimeframeConfluenceResult evaluate(
            String symbol,
            String interval,
            SignalDecision currentDecision,
            Instant evaluationTime
    ) {
        return evaluate(symbol, interval, currentDecision, evaluationTime, null);
    }

    /** Strategy-aware final evaluation. */
    public MultiTimeframeConfluenceResult evaluate(
            String symbol,
            String interval,
            SignalDecision currentDecision,
            Instant evaluationTime,
            TradingStrategy strategy
    ) {
        Instant snapshotTime = evaluationTime == null ? Instant.now() : evaluationTime;
        List<String> higherIntervals = higherIntervals(interval);
        if (higherIntervals.isEmpty()) {
            return unavailable(currentDecision, snapshotTime,
                    "No higher-timeframe mapping is configured for " + interval + ".");
        }

        List<TradeSignal> contexts = higherIntervals.stream()
                .map(higher -> contextAtOrBefore(symbol, higher, snapshotTime))
                .filter(signal -> signal != null && isFresh(signal, snapshotTime))
                .toList();

        if (contexts.isEmpty()) {
            return unavailable(currentDecision, snapshotTime,
                    "No recent closed higher-timeframe signal was available at signal creation time; "
                            + "the isolated decision was preserved with reduced confidence.");
        }

        TradeSignal strongestContext = contexts.stream()
                .max((left, right) -> Integer.compare(contextStrength(left), contextStrength(right)))
                .orElseThrow();

        ContextCounts counts = count(contexts);
        TradingStrategy activeStrategy = strategy == null ? TradingStrategy.TREND_FOLLOWING : strategy;
        Evaluation evaluation = switch (activeStrategy) {
            case TREND_FOLLOWING -> trendFollowing(currentDecision, contexts, counts);
            case RANGE_MEAN_REVERSION -> rangeMeanReversion(currentDecision, contexts, counts);
            case BREAKOUT -> breakout(currentDecision, contexts, counts);
            case DEFENSIVE -> defensive(currentDecision, contexts, counts);
            case NO_TRADE -> noTrade(currentDecision);
        };

        List<String> reasons = new ArrayList<>(evaluation.reasons());
        reasons.add("Strategy-aware confluence used " + activeStrategy + ".");
        reasons.add(contextSummary(contexts));

        return new MultiTimeframeConfluenceResult(
                evaluation.status(),
                currentDecision,
                evaluation.finalDecision(),
                evaluation.entryAllowed(),
                strongestContext.getInterval(),
                strongestContext.getDecision(),
                strongestContext.getTrendScore(),
                snapshotTime,
                strongestContext.getGeneratedAt(),
                List.copyOf(reasons)
        );
    }

    private TradeSignal contextAtOrBefore(String symbol, String interval, Instant snapshotTime) {
        if (replayScope != null && replayScope.active()) {
            return replayScope.latestAtOrBefore(symbol, interval, snapshotTime).orElse(null);
        }
        return tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        symbol, interval, snapshotTime)
                .orElse(null);
    }

    private Evaluation trendFollowing(
            SignalDecision decision,
            List<TradeSignal> contexts,
            ContextCounts counts
    ) {
        if (isBullish(decision) && hasHardBearishOpposition(contexts)) {
            return veto(ConfluenceStatus.STRONG_CONFLICT, SignalDecision.WATCH,
                    "Trend-following long entry was vetoed by explicit strongly bearish higher-timeframe opposition.");
        }
        if (isBullish(decision) && hasExplicitBearishOpposition(contexts)) {
            return preserve(ConfluenceStatus.CONFLICT, decision, true,
                    "A bearish higher-timeframe decision reduced confidence, but did not veto the lower-timeframe BUY.");
        }
        if (isBullish(decision) && hasNeutralWeakStructure(contexts)) {
            return preserve(ConfluenceStatus.CONFLICT, decision, true,
                    "Higher-timeframe direction is NEUTRAL/WATCH with weak structure; confidence was reduced without blocking the BUY.");
        }
        if (isBearish(decision) && counts.strongBullish() > 0) {
            return preserve(ConfluenceStatus.STRONG_CONFLICT, SignalDecision.NEUTRAL, true,
                    "Bearish signal was neutralized by strongly bullish higher-timeframe structure.");
        }
        if (isBearish(decision) && counts.bullish() > 0) {
            return preserve(ConfluenceStatus.CONFLICT, SignalDecision.NEUTRAL, true,
                    "Bearish signal conflicts with bullish higher-timeframe structure.");
        }
        return agreementOrMixed(decision, contexts, counts,
                "Trend-following setup is aligned with higher-timeframe direction.",
                "Higher-timeframe direction is mixed; no trend-following adjustment was applied.");
    }


    private boolean hasHardBearishOpposition(List<TradeSignal> contexts) {
        return contexts.stream().anyMatch(signal ->
                signal.getDecision() == SignalDecision.STRONG_SELL
                        || (signal.getDecision() == SignalDecision.SELL
                        && signal.getTrendScore() <= STRONG_BEARISH_TREND_MAXIMUM));
    }

    private boolean hasExplicitBearishOpposition(List<TradeSignal> contexts) {
        return contexts.stream().anyMatch(signal -> signal.getDecision() == SignalDecision.SELL);
    }

    private boolean hasNeutralWeakStructure(List<TradeSignal> contexts) {
        return contexts.stream().anyMatch(signal ->
                (signal.getDecision() == SignalDecision.NEUTRAL
                        || signal.getDecision() == SignalDecision.WATCH)
                        && signal.getTrendScore() <= BEARISH_TREND_MAXIMUM);
    }

    private Evaluation rangeMeanReversion(
            SignalDecision decision,
            List<TradeSignal> contexts,
            ContextCounts counts
    ) {
        boolean higherRange = contexts.stream().anyMatch(this::isRangeContext);
        if (higherRange && (isBullish(decision) || isBearish(decision))) {
            return preserve(ConfluenceStatus.AGREEMENT, decision, true,
                    "Higher timeframe is ranging, which supports the mean-reversion strategy.");
        }
        if (isBullish(decision) && counts.strongBearish() > 0) {
            return veto(ConfluenceStatus.STRONG_CONFLICT, SignalDecision.WATCH,
                    "Mean-reversion BUY was blocked because the higher timeframe is strongly bearish, not ranging.");
        }
        if (isBearish(decision) && counts.strongBullish() > 0) {
            return preserve(ConfluenceStatus.STRONG_CONFLICT, SignalDecision.NEUTRAL, true,
                    "Mean-reversion SELL was neutralized by a strongly bullish higher timeframe.");
        }
        if ((isBullish(decision) && counts.bearish() > 0)
                || (isBearish(decision) && counts.bullish() > 0)) {
            return preserve(ConfluenceStatus.MIXED, decision, true,
                    "Moderate higher-timeframe opposition was treated as context, not a veto, for mean reversion.");
        }
        return agreementOrMixed(decision, contexts, counts,
                "Higher-timeframe context does not oppose the range setup.",
                "Higher-timeframe context is neutral for the range strategy.");
    }

    private Evaluation breakout(
            SignalDecision decision,
            List<TradeSignal> contexts,
            ContextCounts counts
    ) {
        if (isBullish(decision) && counts.strongBearish() > 0) {
            return veto(ConfluenceStatus.STRONG_CONFLICT, SignalDecision.WATCH,
                    "Bullish breakout was vetoed by strongly bearish higher-timeframe structure.");
        }
        if (isBearish(decision) && counts.strongBullish() > 0) {
            return preserve(ConfluenceStatus.STRONG_CONFLICT, SignalDecision.NEUTRAL, true,
                    "Bearish breakout conflicts with strongly bullish higher-timeframe structure.");
        }
        if (isBullish(decision) && counts.bullish() == 0) {
            return veto(ConfluenceStatus.MIXED, SignalDecision.WATCH,
                    "Breakout strategy requires at least one bullish higher-timeframe confirmation.");
        }
        if (isBearish(decision) && counts.bearish() == 0) {
            return preserve(ConfluenceStatus.MIXED, SignalDecision.NEUTRAL, true,
                    "Bearish breakout lacks higher-timeframe confirmation.");
        }
        return agreementOrMixed(decision, contexts, counts,
                "Breakout direction is supported by higher-timeframe structure.",
                "Breakout context is mixed; entry remains subject to volume and liquidity checks.");
    }

    private Evaluation defensive(
            SignalDecision decision,
            List<TradeSignal> contexts,
            ContextCounts counts
    ) {
        if (isBullish(decision)) {
            boolean allBullish = counts.bullish() == contexts.size();
            if (!allBullish) {
                return veto(counts.bearish() > 0 ? ConfluenceStatus.CONFLICT : ConfluenceStatus.MIXED,
                        SignalDecision.WATCH,
                        "Defensive strategy requires all available higher timeframes to confirm a bullish entry.");
            }
        }
        if (isBearish(decision) && counts.bullish() > 0) {
            return preserve(ConfluenceStatus.CONFLICT, SignalDecision.NEUTRAL, true,
                    "Defensive strategy neutralized a bearish decision because higher timeframes are not aligned.");
        }
        return agreementOrMixed(decision, contexts, counts,
                "Defensive strategy received full higher-timeframe agreement.",
                "Defensive strategy found insufficient directional evidence.");
    }

    private Evaluation noTrade(SignalDecision decision) {
        SignalDecision safe = isBullish(decision) ? SignalDecision.WATCH : SignalDecision.NEUTRAL;
        return veto(ConfluenceStatus.UNAVAILABLE, safe,
                "NO_TRADE strategy does not permit a new entry regardless of confluence.");
    }

    private Evaluation agreementOrMixed(
            SignalDecision decision,
            List<TradeSignal> contexts,
            ContextCounts counts,
            String agreementReason,
            String mixedReason
    ) {
        boolean allAgree = (isBullish(decision) && counts.bullish() == contexts.size())
                || (isBearish(decision) && counts.bearish() == contexts.size());
        if (allAgree) {
            return preserve(contexts.size() > 1
                            ? ConfluenceStatus.STRONG_AGREEMENT : ConfluenceStatus.AGREEMENT,
                    decision, true, agreementReason);
        }
        return preserve(ConfluenceStatus.MIXED, decision, true, mixedReason);
    }

    private Evaluation veto(ConfluenceStatus status, SignalDecision decision, String reason) {
        return new Evaluation(status, decision, false, List.of(reason));
    }

    private Evaluation preserve(
            ConfluenceStatus status,
            SignalDecision decision,
            boolean entryAllowed,
            String reason
    ) {
        return new Evaluation(status, decision, entryAllowed, List.of(reason));
    }

    private MultiTimeframeConfluenceResult unavailable(
            SignalDecision decision,
            Instant evaluatedAt,
            String reason
    ) {
        return new MultiTimeframeConfluenceResult(
                ConfluenceStatus.UNAVAILABLE,
                decision,
                decision,
                true,
                null,
                null,
                null,
                evaluatedAt,
                null,
                List.of(reason)
        );
    }

    private List<String> higherIntervals(String interval) {
        return switch (interval) {
            case "1m" -> List.of("5m", "1h");
            case "5m" -> List.of("15m", "1h");
            case "15m" -> List.of("1h", "4h");
            case "1h" -> List.of("4h", "1d");
            case "4h" -> List.of("1d");
            default -> List.of();
        };
    }

    private boolean isFresh(TradeSignal signal, Instant evaluationTime) {
        if (signal.getGeneratedAt() == null) return false;
        Duration maximumAge = switch (signal.getInterval()) {
            case "5m" -> Duration.ofMinutes(20);
            case "15m" -> Duration.ofMinutes(45);
            case "1h" -> Duration.ofHours(3);
            case "4h" -> Duration.ofHours(10);
            case "1d" -> Duration.ofDays(2);
            default -> Duration.ofHours(1);
        };
        return !signal.getGeneratedAt().isAfter(evaluationTime)
                && signal.getGeneratedAt().isAfter(evaluationTime.minus(maximumAge));
    }

    private ContextCounts count(List<TradeSignal> contexts) {
        return new ContextCounts(
                (int) contexts.stream().filter(this::isBullishContext).count(),
                (int) contexts.stream().filter(this::isBearishContext).count(),
                (int) contexts.stream().filter(this::isStrongBullishContext).count(),
                (int) contexts.stream().filter(this::isStrongBearishContext).count()
        );
    }

    private boolean isRangeContext(TradeSignal signal) {
        return signal.getMarketRegime() == MarketRegime.RANGE
                || (signal.getTrendScore() > BEARISH_TREND_MAXIMUM
                && signal.getTrendScore() < BULLISH_TREND_MINIMUM
                && (signal.getDecision() == SignalDecision.NEUTRAL
                || signal.getDecision() == SignalDecision.WATCH));
    }

    private boolean isBullishContext(TradeSignal signal) {
        return isBullish(signal.getDecision()) || signal.getTrendScore() >= BULLISH_TREND_MINIMUM;
    }

    private boolean isStrongBullishContext(TradeSignal signal) {
        return signal.getDecision() == SignalDecision.STRONG_BUY
                || signal.getTrendScore() >= STRONG_BULLISH_TREND_MINIMUM;
    }

    private boolean isBearishContext(TradeSignal signal) {
        return isBearish(signal.getDecision()) || signal.getTrendScore() <= BEARISH_TREND_MAXIMUM;
    }

    private boolean isStrongBearishContext(TradeSignal signal) {
        return signal.getDecision() == SignalDecision.STRONG_SELL
                || signal.getTrendScore() <= STRONG_BEARISH_TREND_MAXIMUM;
    }

    private boolean isBullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private boolean isBearish(SignalDecision decision) {
        return decision == SignalDecision.SELL || decision == SignalDecision.STRONG_SELL;
    }

    private int contextStrength(TradeSignal signal) {
        return Math.abs(signal.getTrendScore() - 13);
    }

    private String contextSummary(List<TradeSignal> contexts) {
        return "Higher timeframes: " + contexts.stream()
                .map(signal -> signal.getInterval() + "=" + signal.getDecision()
                        + " (trend " + signal.getTrendScore() + "/25, regime "
                        + signal.getMarketRegime() + ")")
                .reduce((left, right) -> left + ", " + right)
                .orElse("none") + ".";
    }

    private record ContextCounts(int bullish, int bearish, int strongBullish, int strongBearish) {}
    private record Evaluation(
            ConfluenceStatus status,
            SignalDecision finalDecision,
            boolean entryAllowed,
            List<String> reasons
    ) {}
}
