package com.crypto.service;

import com.crypto.config.SentimentProperties;
import com.crypto.domain.SentimentSignal;
import com.crypto.dto.ProviderSentiment;
import com.crypto.dto.SentimentOverview;
import com.crypto.dto.SentimentRequest;
import com.crypto.dto.SentimentTextRequest;
import com.crypto.repository.SentimentSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SentimentService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final SentimentSignalRepository repository;
    private final SentimentTextAnalyzer textAnalyzer;
    private final SentimentProperties properties;
    private final SentimentProviderConfigService providerConfigService;

    @Transactional
    public SentimentSignal save(SentimentRequest request) {
        validateRange(request.score(), "score");
        validateConfidence(request.confidence());
        return persist(request.symbol(), request.score(), request.confidence(), request.source(), request.summary());
    }

    @Transactional
    public SentimentSignal analyzeAndSave(SentimentTextRequest request) {
        SentimentTextAnalyzer.Result result = textAnalyzer.analyze(request.text());
        String summary = "Text sentiment: positive=" + result.positiveMatches()
                + ", negative=" + result.negativeMatches()
                + ". " + abbreviate(request.text(), 700);
        return persist(request.symbol(), result.score(), result.confidence(), request.source(), summary);
    }

    @Transactional
    public SentimentSignal analyzeAndSave(
            String symbol,
            String provider,
            String text,
            Instant observedAt
    ) {
        SentimentTextAnalyzer.Result result = textAnalyzer.analyze(text);
        String normalizedSymbol = normalizeSymbol(symbol);
        String normalizedProvider = SentimentProviderName.normalize(provider);
        String normalizedSummary = abbreviate(text, 1000);
        Instant timestamp = observedAt == null ? Instant.now() : observedAt;
        if (repository.existsBySymbolAndSourceAndObservedAtAndSummary(
                normalizedSymbol, normalizedProvider, timestamp, normalizedSummary)) {
            return null;
        }
        return repository.save(SentimentSignal.builder()
                .symbol(normalizedSymbol)
                .score(result.score())
                .confidence(result.confidence())
                .source(normalizedProvider)
                .summary(normalizedSummary)
                .observedAt(timestamp)
                .build());
    }

    @Transactional
    public SentimentSignal saveProviderScore(
            String symbol,
            String provider,
            BigDecimal score,
            BigDecimal confidence,
            String summary,
            Instant observedAt
    ) {
        validateRange(score, "score");
        validateConfidence(confidence);
        String normalizedSymbol = normalizeSymbol(symbol);
        String normalizedProvider = SentimentProviderName.normalize(provider);
        String normalizedSummary = abbreviate(summary == null ? "" : summary, 1000);
        Instant timestamp = observedAt == null ? Instant.now() : observedAt;
        if (repository.existsBySymbolAndSourceAndObservedAtAndSummary(
                normalizedSymbol, normalizedProvider, timestamp, normalizedSummary)) {
            return null;
        }
        return repository.save(SentimentSignal.builder()
                .symbol(normalizedSymbol)
                .score(score)
                .confidence(confidence)
                .source(normalizedProvider)
                .summary(normalizedSummary)
                .observedAt(timestamp)
                .build());
    }

    @Transactional(readOnly = true)
    public SentimentOverview overview(String symbol) {
        return overviewAsOf(symbol, Instant.now());
    }

    /**
     * Historical/as-of sentiment view. No sentiment row newer than evaluatedAt
     * is visible to the caller. This keeps Administration replay deterministic
     * and prevents current/future sentiment from leaking into historical runs.
     */
    @Transactional(readOnly = true)
    public SentimentOverview overviewAsOf(String symbol, Instant evaluatedAt) {
        String normalized = normalizeSymbol(symbol);
        Instant reference = evaluatedAt == null ? Instant.now() : evaluatedAt;
        if (!properties.enabled()) {
            return new SentimentOverview(
                    normalized,
                    BigDecimal.ZERO,
                    "DISABLED",
                    0,
                    List.of(),
                    List.of()
            );
        }
        List<SentimentSignal> recent = repository
                .findTop20BySymbolAndObservedAtLessThanEqualOrderByObservedAtDesc(normalized, reference);
        Aggregation aggregation = aggregate(normalized, reference);
        return new SentimentOverview(
                normalized,
                aggregation.score(),
                label(aggregation.score()),
                aggregation.sampleCount(),
                aggregation.providers(),
                recent
        );
    }

    /**
     * Score consumed by AnalysisService. When sentiment is disabled, it safely
     * returns zero and does not query or aggregate sentiment rows.
     */
    @Transactional(readOnly = true)
    public BigDecimal currentScore(String symbol) {
        if (!properties.enabled()) {
            return BigDecimal.ZERO;
        }
        return aggregate(normalizeSymbol(symbol), Instant.now()).score();
    }

    /**
     * Kept for compatibility with existing callers. Prefer currentScore().
     */
    @Transactional(readOnly = true)
    public BigDecimal weightedScore(String symbol) {
        return currentScore(symbol);
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    private Aggregation aggregate(String symbol, Instant reference) {
        Instant now = reference == null ? Instant.now() : reference;
        Instant after = now.minus(properties.activeWindow());
        List<SentimentSignal> signals = repository
                .findBySymbolAndObservedAtAfterAndObservedAtLessThanEqualOrderByObservedAtDesc(
                        symbol, after, now);

        Map<String, List<SentimentSignal>> byProvider = new LinkedHashMap<>();
        for (SentimentSignal signal : signals) {
            String provider = SentimentProviderName.normalize(signal.getSource());
            byProvider.computeIfAbsent(provider, ignored -> new ArrayList<>()).add(signal);
        }

        for (com.crypto.domain.SentimentProviderConfig configured : providerConfigService.findAll()) {
            byProvider.computeIfAbsent(configured.getProviderCode(), ignored -> new ArrayList<>());
        }

        List<ProviderSentiment> breakdown = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;

        for (Map.Entry<String, List<SentimentSignal>> entry : byProvider.entrySet()) {
            String providerName = entry.getKey();
            com.crypto.domain.SentimentProviderConfig providerConfig = providerConfigService.require(providerName);
            ProviderAggregate provider = aggregateProvider(entry.getValue(), now);
            BigDecimal configuredWeight = providerConfig.getWeight();
            BigDecimal effectiveWeight = provider.confidence().multiply(configuredWeight, MC);

            boolean historicalReference = now.isBefore(Instant.now().minusSeconds(1));
            boolean healthyEnough = historicalReference
                    ? provider.sampleCount() > 0
                    : providerConfigService.contributes(providerConfig, now);
            if (properties.enabled() && providerConfig.isEnabled() && healthyEnough
                    && provider.sampleCount() > 0 && effectiveWeight.signum() > 0) {
                total = total.add(provider.score().multiply(effectiveWeight, MC), MC);
                denominator = denominator.add(effectiveWeight, MC);
            }

            breakdown.add(new ProviderSentiment(
                    providerName,
                    properties.enabled() && providerConfig.isEnabled(),
                    configuredWeight,
                    provider.score(),
                    provider.confidence(),
                    properties.enabled() && providerConfig.isEnabled() && healthyEnough
                            ? effectiveWeight : BigDecimal.ZERO,
                    provider.sampleCount(),
                    provider.latestObservedAt()
            ));
        }

        breakdown.sort(Comparator
                .comparing(ProviderSentiment::effectiveWeight).reversed()
                .thenComparing(ProviderSentiment::provider));

        BigDecimal score = denominator.signum() == 0
                ? BigDecimal.ZERO
                : total.divide(denominator, MC);
        return new Aggregation(score, signals.size(), breakdown);
    }

    private ProviderAggregate aggregateProvider(List<SentimentSignal> signals, Instant now) {
        if (signals.isEmpty()) {
            return new ProviderAggregate(BigDecimal.ZERO, BigDecimal.ZERO, 0, null);
        }

        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal totalSampleWeight = BigDecimal.ZERO;
        BigDecimal confidenceTotal = BigDecimal.ZERO;
        Instant latest = null;

        for (SentimentSignal signal : signals) {
            long ageMinutes = Math.max(0, Duration.between(signal.getObservedAt(), now).toMinutes());
            BigDecimal timeWeight = BigDecimal.ONE.divide(
                    BigDecimal.ONE.add(BigDecimal.valueOf(ageMinutes / 240.0), MC), MC);
            BigDecimal sampleWeight = signal.getConfidence().multiply(timeWeight, MC);

            weightedTotal = weightedTotal.add(signal.getScore().multiply(sampleWeight, MC), MC);
            totalSampleWeight = totalSampleWeight.add(sampleWeight, MC);
            confidenceTotal = confidenceTotal.add(signal.getConfidence().multiply(timeWeight, MC), MC);
            if (latest == null || signal.getObservedAt().isAfter(latest)) latest = signal.getObservedAt();
        }

        BigDecimal score = totalSampleWeight.signum() == 0
                ? BigDecimal.ZERO
                : weightedTotal.divide(totalSampleWeight, MC);
        BigDecimal confidence = confidenceTotal
                .divide(BigDecimal.valueOf(signals.size()), MC)
                .min(BigDecimal.ONE);
        return new ProviderAggregate(score, confidence, signals.size(), latest);
    }

    public String label(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(0.35)) >= 0) return "BULLISH";
        if (score.compareTo(BigDecimal.valueOf(0.10)) >= 0) return "SLIGHTLY_BULLISH";
        if (score.compareTo(BigDecimal.valueOf(-0.10)) > 0) return "NEUTRAL";
        if (score.compareTo(BigDecimal.valueOf(-0.35)) > 0) return "SLIGHTLY_BEARISH";
        return "BEARISH";
    }

    private SentimentSignal persist(String symbol, BigDecimal score, BigDecimal confidence, String source, String summary) {
        return repository.save(SentimentSignal.builder()
                .symbol(normalizeSymbol(symbol))
                .score(score)
                .confidence(confidence)
                .source(SentimentProviderName.normalize(source))
                .summary(summary)
                .observedAt(Instant.now())
                .build());
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        return symbol.trim().toUpperCase();
    }

    private void validateRange(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.valueOf(-1)) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(field + " must be between -1 and 1");
        }
    }

    private void validateConfidence(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }

    private String abbreviate(String value, int limit) {
        if (value == null) return null;
        return value.length() <= limit ? value : value.substring(0, limit - 3) + "...";
    }

    private record ProviderAggregate(
            BigDecimal score,
            BigDecimal confidence,
            int sampleCount,
            Instant latestObservedAt
    ) {}

    private record Aggregation(
            BigDecimal score,
            int sampleCount,
            List<ProviderSentiment> providers
    ) {}
}
