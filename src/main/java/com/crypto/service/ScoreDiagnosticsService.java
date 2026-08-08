package com.crypto.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.domain.TradingStrategy;
import com.crypto.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScoreDiagnosticsService {

    private static final int TREND_MAXIMUM = 25;
    private static final int VOLUME_MAXIMUM = 20;
    private static final int MOMENTUM_MAXIMUM = 15;
    private static final int SENTIMENT_MAXIMUM = 15;
    private static final int FUNDAMENTAL_MAXIMUM = 10;

    private final TradeSignalRepository tradeSignalRepository;
    private volatile DiagnosticsCacheEntry diagnosticsCache;

    @Transactional(readOnly = true)
    public Map<String, Object> last24Hours() {
        Instant now = Instant.now();
        DiagnosticsCacheEntry cached = diagnosticsCache;
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        Instant from = now.minus(Duration.ofHours(24));
        List<TradeSignal> signals = tradeSignalRepository
                .findByGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(from);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from);
        result.put("to", Instant.now());
        result.put("signalCount", signals.size());
        if (signals.isEmpty()) {
            result.put("warnings", List.of("No signals were generated during the last 24 hours."));
            result.put("score", Map.of());
            result.put("categories", List.of());
            result.put("originalDecisions", Map.of());
            result.put("finalDecisions", Map.of());
            result.put("strategies", List.of());
            result.put("symbolIntervals", List.of());
            Map<String, Object> immutable = Map.copyOf(result);
            diagnosticsCache = new DiagnosticsCacheEntry(now.plusSeconds(60), immutable);
            return immutable;
        }

        int minimum = signals.stream().mapToInt(TradeSignal::getTotalScore).min().orElse(0);
        int maximum = signals.stream().mapToInt(TradeSignal::getTotalScore).max().orElse(0);
        double average = signals.stream().mapToInt(TradeSignal::getTotalScore).average().orElse(0);
        long normalizationMismatches = signals.stream().filter(this::normalizationMismatch).count();

        result.put("score", Map.of(
                "minimum", minimum,
                "maximum", maximum,
                "average", round(average),
                "normalizationMismatches", normalizationMismatches,
                "buckets", scoreBuckets(signals)
        ));
        result.put("categories", categoryDiagnostics(signals));
        result.put("originalDecisions", decisionCounts(signals, true));
        result.put("finalDecisions", decisionCounts(signals, false));
        result.put("strategies", strategyDiagnostics(signals));
        result.put("symbolIntervals", symbolIntervalDiagnostics(signals));
        result.put("warnings", warnings(signals, average, maximum, normalizationMismatches));
        Map<String, Object> immutable = Map.copyOf(result);
        diagnosticsCache = new DiagnosticsCacheEntry(now.plusSeconds(60), immutable);
        return immutable;
    }

    private record DiagnosticsCacheEntry(Instant expiresAt, Map<String, Object> value) {}

    private List<Map<String, Object>> categoryDiagnostics(List<TradeSignal> signals) {
        return List.of(
                category("Trend", average(signals, TradeSignal::getTrendScore), TREND_MAXIMUM),
                category("Volume", average(signals, TradeSignal::getVolumeScore), VOLUME_MAXIMUM),
                category("Momentum", average(signals, TradeSignal::getMomentumScore), MOMENTUM_MAXIMUM),
                category("Sentiment", average(signals, TradeSignal::getSentimentScore), SENTIMENT_MAXIMUM),
                category("Fundamentals", average(signals, TradeSignal::getFundamentalScore), FUNDAMENTAL_MAXIMUM)
        );
    }

    private Map<String, Object> category(String name, double average, int maximum) {
        double utilization = maximum == 0 ? 0 : average * 100.0 / maximum;
        return Map.of(
                "name", name,
                "average", round(average),
                "maximum", maximum,
                "utilizationPercent", round(utilization),
                "status", utilization < 25 ? "CRITICAL" : utilization < 40 ? "LOW" : "NORMAL"
        );
    }

    private Map<String, Long> decisionCounts(List<TradeSignal> signals, boolean original) {
        Map<SignalDecision, Long> counts = new EnumMap<>(SignalDecision.class);
        for (TradeSignal signal : signals) {
            SignalDecision decision = original ? signal.getOriginalDecision() : signal.getDecision();
            counts.merge(decision, 1L, Long::sum);
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (SignalDecision decision : SignalDecision.values()) {
            result.put(decision.name(), counts.getOrDefault(decision, 0L));
        }
        return result;
    }

    private List<Map<String, Object>> strategyDiagnostics(List<TradeSignal> signals) {
        Map<TradingStrategy, List<TradeSignal>> groups = new EnumMap<>(TradingStrategy.class);
        signals.forEach(signal -> groups.computeIfAbsent(signal.getSelectedStrategy(), ignored -> new ArrayList<>()).add(signal));
        List<Map<String, Object>> result = new ArrayList<>();
        groups.forEach((strategy, items) -> result.add(Map.of(
                "strategy", strategy.name(),
                "count", items.size(),
                "averageScore", round(items.stream().mapToInt(TradeSignal::getTotalScore).average().orElse(0)),
                "buyCount", items.stream().filter(s -> isBuy(s.getOriginalDecision())).count(),
                "finalBuyCount", items.stream().filter(s -> isBuy(s.getDecision())).count()
        )));
        result.sort((a, b) -> Integer.compare((Integer) b.get("count"), (Integer) a.get("count")));
        return result;
    }

    private List<Map<String, Object>> symbolIntervalDiagnostics(List<TradeSignal> signals) {
        Map<String, List<TradeSignal>> groups = new LinkedHashMap<>();
        signals.forEach(signal -> groups.computeIfAbsent(signal.getSymbol() + "|" + signal.getInterval(), ignored -> new ArrayList<>()).add(signal));
        List<Map<String, Object>> result = new ArrayList<>();
        groups.forEach((key, items) -> {
            String[] parts = key.split("\\|", 2);
            result.add(Map.of(
                    "symbol", parts[0],
                    "interval", parts[1],
                    "count", items.size(),
                    "averageScore", round(items.stream().mapToInt(TradeSignal::getTotalScore).average().orElse(0)),
                    "minimumScore", items.stream().mapToInt(TradeSignal::getTotalScore).min().orElse(0),
                    "maximumScore", items.stream().mapToInt(TradeSignal::getTotalScore).max().orElse(0),
                    "buyCount", items.stream().filter(s -> isBuy(s.getOriginalDecision())).count()
            ));
        });
        result.sort((a, b) -> Double.compare((Double) a.get("averageScore"), (Double) b.get("averageScore")));
        return result;
    }

    private List<Map<String, Object>> scoreBuckets(List<TradeSignal> signals) {
        String[] labels = {"0-29", "30-44", "45-59", "60-74", "75-84", "85-100"};
        long[] counts = new long[labels.length];
        for (TradeSignal signal : signals) {
            int score = signal.getTotalScore();
            int index = score >= 85 ? 5 : score >= 75 ? 4 : score >= 60 ? 3 : score >= 45 ? 2 : score >= 30 ? 1 : 0;
            counts[index]++;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < labels.length; index++) {
            result.add(Map.of("bucket", labels[index], "count", counts[index]));
        }
        return result;
    }

    private List<String> warnings(List<TradeSignal> signals, double average, int maximum, long mismatches) {
        List<String> warnings = new ArrayList<>();
        long buys = signals.stream().filter(signal -> isBuy(signal.getOriginalDecision())).count();
        double buyRate = buys * 100.0 / signals.size();
        if (average < 40) warnings.add("Average normalized score is below 40; the base scoring model is strongly bearish.");
        if (maximum < 85) warnings.add("No signal reached the STRONG_BUY threshold during this period.");
        if (buyRate < 2) warnings.add("Isolated BUY/STRONG_BUY rate is below 2%; inspect category contributions before changing safety vetoes.");
        if (mismatches > 0) warnings.add(mismatches + " signals do not match rawScore / maximumAvailableScore normalization.");
        categoryDiagnostics(signals).stream()
                .filter(category -> ((Double) category.get("utilizationPercent")) < 30)
                .forEach(category -> warnings.add(category.get("name") + " utilization is below 30% of its maximum."));
        return warnings;
    }

    private boolean normalizationMismatch(TradeSignal signal) {
        if (signal.getMaximumAvailableScore() <= 0) return signal.getTotalScore() != 0;
        int expected = (int) Math.round(signal.getRawScore() * 100.0 / signal.getMaximumAvailableScore());
        return Math.abs(expected - signal.getTotalScore()) > 1;
    }

    private boolean isBuy(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private double average(List<TradeSignal> signals, IntGetter getter) {
        return signals.stream().mapToInt(getter::get).average().orElse(0);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @FunctionalInterface
    private interface IntGetter {
        int get(TradeSignal signal);
    }
}
