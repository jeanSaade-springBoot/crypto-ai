package com.crypto.service;

import com.crypto.config.BtcContextProperties;
import com.crypto.domain.BtcContextStatus;
import com.crypto.domain.BtcRelationshipType;
import com.crypto.domain.Candle;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.BtcMarketContextResult;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BtcMarketContextService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);

    private final CandleRepository candleRepository;
    private final TradeSignalRepository tradeSignalRepository;
    private final BtcContextProperties properties;

    @Transactional(readOnly = true)
    public BtcMarketContextResult evaluate(
            String symbol,
            String interval,
            SignalDecision decisionAfterConfluence,
            boolean confluenceEntryAllowed,
            Instant evaluationTime
    ) {
        Instant evaluatedAt = evaluationTime == null ? Instant.now() : evaluationTime;
        String normalizedSymbol = symbol.trim().toUpperCase();

        if (!properties.enabled()) {
            return result(BtcRelationshipType.UNAVAILABLE, BtcContextStatus.UNAVAILABLE,
                    decisionAfterConfluence, confluenceEntryAllowed, interval, null, null,
                    null, null, 0, ZERO, false, evaluatedAt, null,
                    "BTC market context is disabled by configuration.");
        }

        if (normalizedSymbol.equals(properties.referenceSymbol())) {
            return result(BtcRelationshipType.NOT_APPLICABLE, BtcContextStatus.NOT_APPLICABLE,
                    decisionAfterConfluence, confluenceEntryAllowed, interval, decisionAfterConfluence, null,
                    BigDecimal.ONE, BigDecimal.ONE, 0, ZERO, true, evaluatedAt, null,
                    "This is the configured BTC reference asset; correlation filtering is not applicable.");
        }

        RelationshipMetrics metrics = relationship(normalizedSymbol, interval);
        if (metrics.sampleSize() < properties.minimumSamples()) {
            return result(BtcRelationshipType.LEARNING, BtcContextStatus.LEARNING,
                    decisionAfterConfluence, confluenceEntryAllowed, interval, null, null,
                    metrics.correlation(), metrics.beta(), metrics.sampleSize(), ZERO, false, evaluatedAt, null,
                    "BTC relationship is still learning: " + metrics.sampleSize() + "/"
                            + properties.minimumSamples() + " aligned return samples. No BTC veto was applied.");
        }

        BtcRelationshipType relationshipType = classify(metrics.correlation());
        BigDecimal influence = influence(metrics.correlation());
        boolean stable = metrics.sampleSize() >= properties.minimumSamples()
                && metrics.correlation() != null;

        TradeSignal btcSignal = tradeSignalRepository
                .findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                        properties.referenceSymbol(), interval, evaluatedAt)
                .orElse(null);

        if (btcSignal == null) {
            return result(relationshipType, BtcContextStatus.UNAVAILABLE,
                    decisionAfterConfluence, confluenceEntryAllowed, interval, null, null,
                    metrics.correlation(), metrics.beta(), metrics.sampleSize(), influence, stable,
                    evaluatedAt, null,
                    "The BTC relationship was measured, but no BTC signal snapshot was available for this interval at signal creation time.");
        }

        boolean btcBullish = isBullishContext(btcSignal);
        boolean btcBearish = isBearishContext(btcSignal);
        boolean altBullish = isBullish(decisionAfterConfluence);
        boolean positive = metrics.correlation().compareTo(BigDecimal.ZERO) > 0;
        boolean negative = metrics.correlation().compareTo(BigDecimal.ZERO) < 0;
        boolean strongRelationship = metrics.correlation().abs().compareTo(properties.strongCorrelation()) >= 0;

        BtcContextStatus status = BtcContextStatus.NEUTRAL;
        SignalDecision finalDecision = decisionAfterConfluence;
        boolean entryAllowed = confluenceEntryAllowed;
        String explanation;

        if (relationshipType == BtcRelationshipType.WEAK) {
            explanation = "The measured BTC relationship is weak, so BTC direction did not change this signal.";
        } else if (altBullish && ((positive && btcBearish) || (negative && btcBullish))) {
            status = strongRelationship ? BtcContextStatus.STRONG_CONFLICT : BtcContextStatus.CONFLICT;
            if (strongRelationship) {
                finalDecision = SignalDecision.WATCH;
                if (properties.vetoStrongConflict()) {
                    entryAllowed = false;
                }
            }
            explanation = positive
                    ? "The asset currently follows BTC, but its bullish setup conflicts with a bearish BTC trend."
                    : "The asset currently moves inversely to BTC, but its bullish setup conflicts with a bullish BTC trend.";
        } else if (altBullish && ((positive && btcBullish) || (negative && btcBearish))) {
            status = BtcContextStatus.CONFIRMED;
            explanation = positive
                    ? "The asset currently follows BTC and BTC confirms the bullish setup."
                    : "The asset currently moves inversely to BTC and bearish BTC direction confirms the bullish setup.";
        } else {
            explanation = "BTC context is available, but it did not require a decision adjustment for this signal.";
        }

        if (metrics.beta() != null && metrics.beta().abs().compareTo(properties.highBeta()) >= 0) {
            explanation += " Beta is elevated, so BTC moves may be amplified in this asset.";
        }

        return result(relationshipType, status, finalDecision, entryAllowed, interval,
                btcSignal.getDecision(), btcSignal.getTrendScore(), metrics.correlation(), metrics.beta(),
                metrics.sampleSize(), influence, stable, evaluatedAt, btcSignal.getGeneratedAt(), explanation);
    }

    private RelationshipMetrics relationship(String symbol, String interval) {
        int candleLimit = Math.max(properties.windowSize() + 1, properties.minimumSamples() + 1);
        List<Candle> assetCandles = candleRepository.findClosedCandles(symbol, interval, PageRequest.of(0, candleLimit));
        List<Candle> btcCandles = candleRepository.findClosedCandles(properties.referenceSymbol(), interval, PageRequest.of(0, candleLimit));

        Map<Instant, BigDecimal> btcByOpenTime = new HashMap<>();
        for (Candle candle : btcCandles) {
            btcByOpenTime.put(candle.getOpenTime(), candle.getClosePrice());
        }

        List<AlignedPrice> aligned = assetCandles.stream()
                .filter(candle -> btcByOpenTime.containsKey(candle.getOpenTime()))
                .map(candle -> new AlignedPrice(candle.getOpenTime(), candle.getClosePrice(), btcByOpenTime.get(candle.getOpenTime())))
                .sorted(Comparator.comparing(AlignedPrice::time))
                .toList();

        List<Double> assetReturns = new ArrayList<>();
        List<Double> btcReturns = new ArrayList<>();
        for (int index = 1; index < aligned.size(); index++) {
            AlignedPrice previous = aligned.get(index - 1);
            AlignedPrice current = aligned.get(index);
            if (previous.assetPrice().signum() <= 0 || previous.btcPrice().signum() <= 0
                    || current.assetPrice().signum() <= 0 || current.btcPrice().signum() <= 0) {
                continue;
            }
            assetReturns.add(Math.log(current.assetPrice().doubleValue() / previous.assetPrice().doubleValue()));
            btcReturns.add(Math.log(current.btcPrice().doubleValue() / previous.btcPrice().doubleValue()));
        }

        if (assetReturns.size() < 2) {
            return new RelationshipMetrics(null, null, assetReturns.size());
        }

        double assetMean = assetReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
        double btcMean = btcReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
        double covariance = 0d;
        double assetVariance = 0d;
        double btcVariance = 0d;
        for (int index = 0; index < assetReturns.size(); index++) {
            double assetDelta = assetReturns.get(index) - assetMean;
            double btcDelta = btcReturns.get(index) - btcMean;
            covariance += assetDelta * btcDelta;
            assetVariance += assetDelta * assetDelta;
            btcVariance += btcDelta * btcDelta;
        }

        if (assetVariance == 0d || btcVariance == 0d) {
            return new RelationshipMetrics(null, null, assetReturns.size());
        }

        double correlation = covariance / Math.sqrt(assetVariance * btcVariance);
        double beta = covariance / btcVariance;
        return new RelationshipMetrics(decimal(correlation), decimal(beta), assetReturns.size());
    }

    private BtcRelationshipType classify(BigDecimal correlation) {
        if (correlation == null) return BtcRelationshipType.UNAVAILABLE;
        if (correlation.compareTo(properties.strongCorrelation()) >= 0) return BtcRelationshipType.STRONG_POSITIVE;
        if (correlation.compareTo(properties.moderateCorrelation()) >= 0) return BtcRelationshipType.MODERATE_POSITIVE;
        if (correlation.compareTo(properties.strongCorrelation().negate()) <= 0) return BtcRelationshipType.STRONG_NEGATIVE;
        if (correlation.compareTo(properties.moderateCorrelation().negate()) <= 0) return BtcRelationshipType.MODERATE_NEGATIVE;
        return BtcRelationshipType.WEAK;
    }

    private BigDecimal influence(BigDecimal correlation) {
        if (correlation == null) return ZERO;
        BigDecimal absolute = correlation.abs();
        if (absolute.compareTo(properties.moderateCorrelation()) < 0) return ZERO;
        if (absolute.compareTo(new BigDecimal("0.55")) < 0) return new BigDecimal("0.25");
        if (absolute.compareTo(properties.strongCorrelation()) < 0) return new BigDecimal("0.50");
        if (absolute.compareTo(new BigDecimal("0.85")) < 0) return new BigDecimal("0.75");
        return BigDecimal.ONE;
    }

    private boolean isBullishContext(TradeSignal signal) {
        return isBullish(signal.getDecision()) || signal.getTrendScore() >= 15;
    }

    private boolean isBearishContext(TradeSignal signal) {
        return signal.getDecision() == SignalDecision.SELL
                || signal.getDecision() == SignalDecision.STRONG_SELL
                || signal.getTrendScore() <= 10;
    }

    private boolean isBullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private BtcMarketContextResult result(
            BtcRelationshipType relationshipType,
            BtcContextStatus status,
            SignalDecision finalDecision,
            boolean entryAllowed,
            String btcInterval,
            SignalDecision btcDecision,
            Integer btcTrendScore,
            BigDecimal correlation,
            BigDecimal beta,
            int sampleSize,
            BigDecimal influence,
            boolean stable,
            Instant evaluatedAt,
            Instant btcSignalGeneratedAt,
            String explanation
    ) {
        return new BtcMarketContextResult(relationshipType, status, finalDecision, entryAllowed,
                btcInterval, btcDecision, btcTrendScore, correlation, beta, sampleSize,
                influence, stable, evaluatedAt, btcSignalGeneratedAt, explanation);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private record AlignedPrice(Instant time, BigDecimal assetPrice, BigDecimal btcPrice) {}
    private record RelationshipMetrics(BigDecimal correlation, BigDecimal beta, int sampleSize) {}
}
