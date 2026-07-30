package com.crypto.whale.service;

import com.crypto.whale.config.WhaleProperties;
import com.crypto.whale.domain.*;
import com.crypto.whale.repository.WhaleActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WhaleEvaluationService {
    private static final MathContext MC = MathContext.DECIMAL64;
    private final WhaleActivityRepository repository;
    private final WhalePriceService priceService;
    private final WhaleWeightCalculator weightCalculator;
    private final WhaleProperties properties;

    @Transactional
    public int evaluateDueActivities() {
        List<WhaleActivity> due = repository.findByEvaluationResultAndEvaluationDueAtLessThanEqualOrderByEvaluationDueAtAsc(
                WhaleEvaluationResult.PENDING, Instant.now(), PageRequest.of(0, 500));
        int completed = 0;
        for (WhaleActivity activity : due) {
            try {
                evaluate(activity);
                completed++;
            } catch (RuntimeException ex) {
                activity.setEvaluationResult(WhaleEvaluationResult.FAILED);
                activity.setEvaluatedAt(Instant.now());
                activity.setUpdatedAt(Instant.now());
                repository.save(activity);
            }
        }
        return completed;
    }

    private void evaluate(WhaleActivity activity) {
        BigDecimal currentPrice = priceService.latestPrice(activity.getSymbol());
        BigDecimal marketReturn = currentPrice.subtract(activity.getPriceAtSignal())
                .divide(activity.getPriceAtSignal(), 10, RoundingMode.HALF_UP);
        WhaleEvaluationResult result = determineResult(activity.getTransactionScore(), marketReturn);
        BigDecimal quality = quality(activity.getTransactionScore(), marketReturn, result);

        List<WhaleActivity> history = repository.findByWalletAddressAndSymbolAndEvaluationHorizonAndEvaluationResultIn(
                activity.getWalletAddress(), activity.getSymbol(), activity.getEvaluationHorizon(),
                List.of(WhaleEvaluationResult.CORRECT, WhaleEvaluationResult.INCORRECT, WhaleEvaluationResult.INCONCLUSIVE));

        long correct = history.stream().filter(row -> row.getEvaluationResult() == WhaleEvaluationResult.CORRECT).count();
        long incorrect = history.stream().filter(row -> row.getEvaluationResult() == WhaleEvaluationResult.INCORRECT).count();
        long inconclusive = history.stream().filter(row -> row.getEvaluationResult() == WhaleEvaluationResult.INCONCLUSIVE).count();
        if (result == WhaleEvaluationResult.CORRECT) correct++;
        else if (result == WhaleEvaluationResult.INCORRECT) incorrect++;
        else inconclusive++;
        long total = correct + incorrect + inconclusive;
        long conclusive = correct + incorrect;
        BigDecimal accuracy = conclusive == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(correct)
                .divide(BigDecimal.valueOf(conclusive), 8, RoundingMode.HALF_UP);

        BigDecimal qualitySum = history.stream().map(WhaleActivity::getPredictionQuality)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        long qualityCount = history.stream().filter(row -> row.getPredictionQuality() != null).count();
        BigDecimal averageQuality = result == WhaleEvaluationResult.INCONCLUSIVE
                ? (qualityCount == 0 ? BigDecimal.ZERO : qualitySum.divide(BigDecimal.valueOf(qualityCount), 8, RoundingMode.HALF_UP))
                : qualitySum.add(quality).divide(BigDecimal.valueOf(qualityCount + 1), 8, RoundingMode.HALF_UP);
        BigDecimal learnedWeight = weightCalculator.calculate(conclusive, accuracy, averageQuality, activity.getWhaleLearnedWeight());

        activity.setPriceAtEvaluation(currentPrice);
        activity.setMarketReturn(marketReturn);
        activity.setEvaluationResult(result);
        activity.setPredictionQuality(quality);
        activity.setEvaluatedAt(Instant.now());
        activity.setWhaleTotalSignals(total);
        activity.setWhaleCorrectSignals(correct);
        activity.setWhaleIncorrectSignals(incorrect);
        activity.setWhaleInconclusiveSignals(inconclusive);
        activity.setWhaleAccuracy(accuracy);
        activity.setWhaleAverageQuality(averageQuality);
        activity.setWhaleLearnedWeight(learnedWeight);
        activity.setUpdatedAt(Instant.now());
        repository.save(activity);
    }

    private WhaleEvaluationResult determineResult(BigDecimal signal, BigDecimal marketReturn) {
        if (marketReturn.abs().compareTo(properties.evaluation().inconclusiveMovePercent()) < 0 || signal.signum() == 0) {
            return WhaleEvaluationResult.INCONCLUSIVE;
        }
        return signal.signum() == marketReturn.signum() ? WhaleEvaluationResult.CORRECT : WhaleEvaluationResult.INCORRECT;
    }

    private BigDecimal quality(BigDecimal signal, BigDecimal marketReturn, WhaleEvaluationResult result) {
        if (result == WhaleEvaluationResult.INCONCLUSIVE) return BigDecimal.ZERO;
        BigDecimal magnitude = marketReturn.abs().divide(new BigDecimal("0.05"), MC).min(BigDecimal.ONE);
        return magnitude.multiply(signal.abs(), MC).max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
}
