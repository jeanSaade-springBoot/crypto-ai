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
import com.crypto.execution.service.ExecutionReplayScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
    // FIX-140: count missing/present saved BTC context without altering entry permission.
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private SignalContextMetrics contextMetrics;
    private final BtcContextProperties properties;
    @Autowired(required = false)
    private ExecutionReplayScope replayScope;

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
                    "BTC market context is disabled by configuration.", null, null, null);
        }

        if (normalizedSymbol.equals(properties.referenceSymbol())) {
            return result(BtcRelationshipType.NOT_APPLICABLE, BtcContextStatus.NOT_APPLICABLE,
                    decisionAfterConfluence, confluenceEntryAllowed, interval, decisionAfterConfluence, null,
                    BigDecimal.ONE, BigDecimal.ONE, 0, ZERO, true, evaluatedAt, null,
                    "This is the configured BTC reference asset; correlation filtering is not applicable.",
                    null, null, null);
        }

        // FIX-141: fetch and validate the BTC snapshot BEFORE the LEARNING short-circuit, and
        // before it is used by the main decision path below - not after either one. Previously
        // there was no freshness check anywhere in this method; a stale or missing BTC signal
        // fell through unguarded in both branches.
        Duration duration = intervalDuration(interval);
        TradeSignal btcSignal = null;
        if (duration != null) {
            Instant candleOpenTimeBound = evaluatedAt.minus(duration);
            if (replayScope != null && replayScope.active()) {
                btcSignal = replayScope
                        .latestClosedAtOrBefore(properties.referenceSymbol(), interval, evaluatedAt, candleOpenTimeBound)
                        .orElse(null);
            } else {
                btcSignal = tradeSignalRepository
                        .findTopBySymbolAndIntervalAndCandleOpenTimeLessThanEqualAndGeneratedAtLessThanEqualOrderByCandleOpenTimeDesc(
                                properties.referenceSymbol(), interval, candleOpenTimeBound, evaluatedAt)
                        .orElse(null);
            }
        }
        Freshness freshness = freshness(btcSignal, interval, duration, evaluatedAt);

        // FIX-141 review correction: record() alone counts snapshot presence, so a stale
        // snapshot counted identically to a fresh one. recordFreshness() makes degradation
        // visible as its own outcome, separate from simple presence/absence - and reports the
        // specific outcome (STALE vs UNSUPPORTED_INTERVAL/MISSING_SIGNAL/UNKNOWN_CANDLE_TIME/
        // FUTURE_DATED/NO_THRESHOLD_CONFIGURED/FRESH) rather than collapsing every invalid case
        // into a single "stale" bucket.
        if (contextMetrics != null) {
            contextMetrics.record("BTC", interval, 1, btcSignal == null ? 0 : 1);
            contextMetrics.recordFreshness("BTC", interval, freshness.outcome());
        }

        if (!freshness.fresh()) {
            // Blocks regardless of correlation sample size/LEARNING - a hard fail-closed gate.
            // FIX-141 review correction: the expensive relationship() computation below is
            // skipped entirely on this path now, so no correlation/beta/sampleSize is
            // available here - report null/null/0 rather than computing it just to discard it.
            return result(BtcRelationshipType.UNAVAILABLE, BtcContextStatus.STALE_CONTEXT,
                    decisionAfterConfluence, false, interval, null, null,
                    null, null, 0, ZERO, false,
                    evaluatedAt, btcSignal == null ? null : btcSignal.getGeneratedAt(),
                    freshness.reason(), freshness.candleCloseTime(), freshness.ageSeconds(), freshness.thresholdSeconds());
        }

        // FIX-141 review correction: relationship() is a candle-fetch + correlation computation
        // and must run only after the freshness gate passes, so stale/missing context fails
        // fast without paying for it.
        RelationshipMetrics metrics = relationship(normalizedSymbol, interval, evaluatedAt);

        if (metrics.sampleSize() < properties.minimumSamples()) {
            // Freshness already validated above. Preserve existing pass-through behavior unless
            // the team has explicitly opted into blocking fresh-but-learning context (config).
            boolean learningEntryAllowed = properties.blockLearningContext() ? false : confluenceEntryAllowed;
            return result(BtcRelationshipType.LEARNING, BtcContextStatus.LEARNING,
                    decisionAfterConfluence, learningEntryAllowed, interval, null, null,
                    metrics.correlation(), metrics.beta(), metrics.sampleSize(), ZERO, false, evaluatedAt,
                    btcSignal == null ? null : btcSignal.getGeneratedAt(),
                    "BTC relationship is still learning: " + metrics.sampleSize() + "/"
                            + properties.minimumSamples() + " aligned return samples. Context is fresh; "
                            + (properties.blockLearningContext()
                                    ? "entries are blocked pending relationship confirmation."
                                    : "no BTC veto was applied."),
                    freshness.candleCloseTime(), freshness.ageSeconds(), freshness.thresholdSeconds());
        }

        BtcRelationshipType relationshipType = classify(metrics.correlation());
        BigDecimal influence = influence(metrics.correlation());
        boolean stable = metrics.sampleSize() >= properties.minimumSamples()
                && metrics.correlation() != null;

        if (btcSignal == null) {
            // Freshness validation already requires a non-null signal with a known candle time
            // to pass; this branch is unreachable in practice but kept as a defensive fallback.
            // UNAVAILABLE is historically non-authoritative (pass-through), unlike STALE_CONTEXT
            // above which is a real, evidenced veto - preserve that distinction here too.
            return result(relationshipType, BtcContextStatus.UNAVAILABLE,
                    decisionAfterConfluence, confluenceEntryAllowed, interval, null, null,
                    metrics.correlation(), metrics.beta(), metrics.sampleSize(), influence, stable,
                    evaluatedAt, null,
                    "The BTC relationship was measured, but no BTC signal snapshot was available for this interval at signal creation time.",
                    null, null, freshness.thresholdSeconds());
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
                metrics.sampleSize(), influence, stable, evaluatedAt, btcSignal.getGeneratedAt(), explanation,
                freshness.candleCloseTime(), freshness.ageSeconds(), freshness.thresholdSeconds());
    }

    /** FIX-141: close time = candleOpenTime + interval duration, never generatedAt.
     * Missing signal, null candle time, unsupported interval, a null generatedAt, a close time
     * after evaluationTime, or a generatedAt after evaluationTime are all invalid - never
     * "fresh by default." A null generatedAt and a future-dated generatedAt are reported as
     * distinct outcomes (UNKNOWN_GENERATED_AT vs FUTURE_DATED) even though both block the same
     * way. Stale when age >= threshold (threshold itself is invalid/unsupported when duration is
     * null, since there is nothing to compare age against). */
    private Freshness freshness(TradeSignal btcSignal, String interval, Duration duration, Instant evaluatedAt) {
        if (duration == null) {
            return new Freshness(false, "BTC context interval \"" + interval + "\" is not a supported freshness interval.",
                    null, null, null, "UNSUPPORTED_INTERVAL");
        }
        Long thresholdSeconds = properties.freshnessMaxAgeSecondsFor(interval);
        if (btcSignal == null) {
            return new Freshness(false,
                    "No BTC signal snapshot was available for this interval at or before the evaluation time.",
                    null, null, thresholdSeconds, "MISSING_SIGNAL");
        }
        Instant candleOpenTime = btcSignal.getCandleOpenTime();
        if (candleOpenTime == null) {
            return new Freshness(false,
                    "The selected BTC signal has no candle open time; context cannot be validated as fresh.",
                    null, null, thresholdSeconds, "UNKNOWN_CANDLE_TIME");
        }
        Instant candleCloseTime = candleOpenTime.plus(duration);
        // Review correction: a null generatedAt is an unknown generation time, not a future
        // one - it was previously folded into FUTURE_DATED, which misreports a missing
        // timestamp as a timestamp that is known and simply wrong. Both still block entry;
        // only the reported outcome/reason differs, for accurate metrics and diagnostics.
        if (btcSignal.getGeneratedAt() == null) {
            return new Freshness(false,
                    "The selected BTC signal has no generation timestamp; context cannot be validated as fresh.",
                    candleCloseTime, null, thresholdSeconds, "UNKNOWN_GENERATED_AT");
        }
        if (candleCloseTime.isAfter(evaluatedAt) || btcSignal.getGeneratedAt().isAfter(evaluatedAt)) {
            return new Freshness(false,
                    "The selected BTC signal or candle is future-dated relative to the evaluation time.",
                    candleCloseTime, null, thresholdSeconds, "FUTURE_DATED");
        }
        long ageSeconds = Duration.between(candleCloseTime, evaluatedAt).getSeconds();
        if (thresholdSeconds == null) {
            return new Freshness(false,
                    "No freshness threshold is configured for this interval.",
                    candleCloseTime, ageSeconds, null, "NO_THRESHOLD_CONFIGURED");
        }
        if (ageSeconds >= thresholdSeconds) {
            return new Freshness(false,
                    "BTC context is " + ageSeconds + "s old (threshold " + thresholdSeconds
                            + "s); a recently generated signal for an old candle still counts as stale.",
                    candleCloseTime, ageSeconds, thresholdSeconds, "STALE");
        }
        return new Freshness(true, null, candleCloseTime, ageSeconds, thresholdSeconds, "FRESH");
    }

    // FIX-141 review correction: outcome distinguishes WHY context failed freshness, not just
    // that it did. STALE (age >= threshold) is an ordinary, expected degradation mode; the
    // others (unsupported interval, missing signal, unknown candle time, future-dated, no
    // threshold configured) are each a different kind of problem - a misconfiguration or a
    // data-quality defect upstream - and folding them all into "stale" would hide that from
    // metrics/alerting. recordFreshness() reports this value directly, one series per outcome.
    private record Freshness(boolean fresh, String reason, Instant candleCloseTime, Long ageSeconds,
                              Long thresholdSeconds, String outcome) {}

    /** FIX-141: candle length by interval code, used to derive close time from open time. */
    static Duration intervalDuration(String interval) {
        if (interval == null) return null;
        return switch (interval) {
            case "1m" -> Duration.ofMinutes(1);
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "1h" -> Duration.ofHours(1);
            case "4h" -> Duration.ofHours(4);
            case "1d" -> Duration.ofDays(1);
            default -> null;
        };
    }

    private RelationshipMetrics relationship(String symbol, String interval, Instant evaluatedAt) {
        int candleLimit = Math.max(properties.windowSize() + 1, properties.minimumSamples() + 1);
        List<Candle> assetCandles;
        List<Candle> btcCandles;
        if (replayScope != null && replayScope.active()) {
            assetCandles = candleRepository.findClosedCandlesClosedAtOrBefore(
                    symbol, interval, evaluatedAt, PageRequest.of(0, candleLimit));
            btcCandles = candleRepository.findClosedCandlesClosedAtOrBefore(
                    properties.referenceSymbol(), interval, evaluatedAt, PageRequest.of(0, candleLimit));
        } else {
            assetCandles = candleRepository.findClosedCandles(symbol, interval, PageRequest.of(0, candleLimit));
            btcCandles = candleRepository.findClosedCandles(properties.referenceSymbol(), interval, PageRequest.of(0, candleLimit));
        }

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
            String explanation,
            Instant contextCandleCloseTime,
            Long contextAgeSeconds,
            Long freshnessThresholdSeconds
    ) {
        return new BtcMarketContextResult(relationshipType, status, finalDecision, entryAllowed,
                btcInterval, btcDecision, btcTrendScore, correlation, beta, sampleSize,
                influence, stable, evaluatedAt, btcSignalGeneratedAt, explanation,
                contextCandleCloseTime, contextAgeSeconds, freshnessThresholdSeconds);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private record AlignedPrice(Instant time, BigDecimal assetPrice, BigDecimal btcPrice) {}
    private record RelationshipMetrics(BigDecimal correlation, BigDecimal beta, int sampleSize) {}
}
