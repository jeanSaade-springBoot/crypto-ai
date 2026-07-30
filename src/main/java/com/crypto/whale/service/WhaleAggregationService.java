package com.crypto.whale.service;

import com.crypto.service.SentimentProviderName;
import com.crypto.service.SentimentService;
import com.crypto.whale.config.WhaleProperties;
import com.crypto.whale.domain.*;
import com.crypto.whale.dto.WhaleSentimentResult;
import com.crypto.whale.repository.WhaleActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WhaleAggregationService {
    private static final MathContext MC = MathContext.DECIMAL64;
    private final WhaleActivityRepository repository;
    private final WhaleProperties properties;
    private final SentimentService sentimentService;

    @Transactional
    public WhaleSentimentResult calculateAndSave(String symbol) {
        Instant now = Instant.now();
        WhaleEvaluationHorizon horizon = WhaleEvaluationHorizon.fromCode(properties.aggregation().horizon());
        List<WhaleActivity> activities = repository.findBySymbolAndEvaluationHorizonAndObservedAtAfterOrderByObservedAtDesc(
                symbol.toUpperCase(), horizon, now.minus(properties.aggregation().activeWindow()));
        BigDecimal contribution = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalUsd = BigDecimal.ZERO;
        for (WhaleActivity activity : activities) {
            BigDecimal recency = recency(activity.getObservedAt(), now);
            BigDecimal learnedWeight = latestWeight(activity);
            BigDecimal effectiveWeight = learnedWeight
                    .multiply(activity.getTransactionConfidence(), MC)
                    .multiply(recency, MC);
            contribution = contribution.add(activity.getTransactionScore().multiply(effectiveWeight, MC), MC);
            totalWeight = totalWeight.add(effectiveWeight, MC);
            totalUsd = totalUsd.add(activity.getUsdValue(), MC);
        }
        BigDecimal score = totalWeight.signum() == 0 ? BigDecimal.ZERO : contribution.divide(totalWeight, MC)
                .max(BigDecimal.ONE.negate()).min(BigDecimal.ONE);
        BigDecimal confidence = activities.isEmpty() ? BigDecimal.ZERO
                : totalWeight.divide(BigDecimal.valueOf(activities.size()), MC)
                .max(properties.aggregation().providerConfidenceFloor()).min(BigDecimal.ONE);
        String summary = "Whale activity: " + activities.size() + " records, USD " + totalUsd.toPlainString();
        if (!activities.isEmpty()) {
            sentimentService.saveProviderScore(symbol, SentimentProviderName.WHALE_ALERT.name(), score, confidence, summary, now);
        }
        return new WhaleSentimentResult(symbol, score, confidence, activities.size(), totalUsd, summary);
    }

    private BigDecimal latestWeight(WhaleActivity activity) {
        return repository.findFirstByWalletAddressAndSymbolAndEvaluationHorizonAndEvaluationResultInOrderByEvaluatedAtDesc(
                        activity.getWalletAddress(), activity.getSymbol(), activity.getEvaluationHorizon(),
                        List.of(WhaleEvaluationResult.CORRECT, WhaleEvaluationResult.INCORRECT, WhaleEvaluationResult.INCONCLUSIVE))
                .map(WhaleActivity::getWhaleLearnedWeight)
                .orElse(activity.getWhaleLearnedWeight());
    }

    private BigDecimal recency(Instant observedAt, Instant now) {
        long ageMinutes = Math.max(0, Duration.between(observedAt, now).toMinutes());
        double windowMinutes = Math.max(1, properties.aggregation().activeWindow().toMinutes());
        return BigDecimal.valueOf(Math.max(0.05, 1.0 - ageMinutes / windowMinutes));
    }
}
