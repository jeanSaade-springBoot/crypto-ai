package com.crypto.execution.service;

import com.crypto.domain.Candle;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects pressure/absorption/release from closed 1m market candles without changing
 * the normal signal score or normal 1m/5m/1h BUY rules.
 *
 * IMPORTANT: this service is intentionally pure/read-only. Production and historical
 * replay call this exact service with an as-of TradeSignal timestamp, so the same
 * thresholds and state transitions are exercised in both paths with no test-only copy.
 */
@Service
@RequiredArgsConstructor
public class PressureReadinessService {

    private static final String INTERVAL = "1m";
    private static final int LOOKBACK_CANDLES = 60;
    private static final int PRIOR_BUCKETS = 6; // 30 minutes of 5m buckets
    private static final Duration RECENT_RELEASE_WINDOW = Duration.ofMinutes(10);

    private static final double SELL_ABSORPTION_MAX_BUY_RATIO = 0.30d;
    private static final double BUY_ABSORPTION_MIN_BUY_RATIO = 0.70d;
    private static final double ABNORMAL_VOLUME_MULTIPLE = 2.0d;
    private static final double ABSORPTION_MAX_PRICE_DISPLACEMENT = 0.0025d; // 0.25%
    private static final double BULLISH_RELEASE_MIN_BUY_RATIO = 0.60d;
    private static final double PRESSURE_BUILDING_MIN_BUY_RATIO = 0.60d;
    private static final double PRESSURE_NEAR_HIGH_FACTOR = 0.9975d;

    private final CandleRepository candleRepository;

    @Transactional(readOnly = true)
    public Result evaluate(TradeSignal signal) {
        if (signal == null || signal.getSymbol() == null) return Result.none();
        Instant reference = signal.getCandleOpenTime() != null ? signal.getCandleOpenTime() : signal.getGeneratedAt();
        if (reference == null) return Result.none();

        List<Candle> descending = candleRepository.findClosedCandlesAtOrBefore(
                signal.getSymbol(), INTERVAL, reference, PageRequest.of(0, LOOKBACK_CANDLES));
        if (descending == null || descending.isEmpty()) return Result.none();

        List<Candle> candles = descending.stream()
                .filter(c -> c != null && c.getOpenTime() != null && c.getClosePrice() != null)
                .sorted(Comparator.comparing(Candle::getOpenTime))
                .toList();

        List<Bucket> buckets = bucket(candles);
        if (buckets.size() <= PRIOR_BUCKETS) return Result.none();

        List<BucketState> states = new ArrayList<>();
        for (int i = PRIOR_BUCKETS; i < buckets.size(); i++) {
            Bucket current = buckets.get(i);
            List<Bucket> prior = buckets.subList(i - PRIOR_BUCKETS, i);
            Bucket previous = buckets.get(i - 1);
            states.add(classify(current, previous, prior));
        }
        if (states.isEmpty()) return Result.none();

        BucketState current = states.get(states.size() - 1);
        BucketState recentRelease = null;
        for (int i = states.size() - 1; i >= 0; i--) {
            BucketState candidate = states.get(i);
            if (Duration.between(candidate.bucketTime(), current.bucketTime()).compareTo(RECENT_RELEASE_WINDOW) > 0) break;
            if (candidate.state() == State.BULLISH_RELEASE) {
                recentRelease = candidate;
                break;
            }
        }

        boolean recentBullishRelease = recentRelease != null;
        String explanation = "Pressure state=" + current.state()
                + ", weighted taker buy=" + pct(current.weightedBuyRatio())
                + "%, volume ratio=" + fmt(current.volumeRatio()) + "x"
                + ", price response=" + pct(current.priceResponse()) + "%"
                + (recentRelease == null ? "."
                : ", recent bullish release at " + recentRelease.bucketTime()
                + " (buy=" + pct(recentRelease.weightedBuyRatio())
                + "%, volume=" + fmt(recentRelease.volumeRatio()) + "x)." );

        return new Result(current.state(), recentBullishRelease,
                recentRelease == null ? null : recentRelease.bucketTime(),
                current.weightedBuyRatio(), current.volumeRatio(), current.priceResponse(),
                current.priorHigh(), current.priorLow(), explanation);
    }

    private List<Bucket> bucket(List<Candle> candles) {
        Map<Long, List<Candle>> grouped = new LinkedHashMap<>();
        for (Candle candle : candles) {
            long bucketId = candle.getOpenTime().getEpochSecond() / 300L;
            grouped.computeIfAbsent(bucketId, ignored -> new ArrayList<>()).add(candle);
        }
        List<Bucket> result = new ArrayList<>();
        for (Map.Entry<Long, List<Candle>> entry : grouped.entrySet()) {
            List<Candle> values = entry.getValue().stream()
                    .sorted(Comparator.comparing(Candle::getOpenTime)).toList();
            Candle first = values.get(0);
            Candle last = values.get(values.size() - 1);
            BigDecimal high = values.stream().map(Candle::getHighPrice).filter(v -> v != null)
                    .max(BigDecimal::compareTo).orElse(last.getClosePrice());
            BigDecimal low = values.stream().map(Candle::getLowPrice).filter(v -> v != null)
                    .min(BigDecimal::compareTo).orElse(last.getClosePrice());
            BigDecimal volume = values.stream().map(Candle::getVolume).filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal takerBuy = values.stream().map(Candle::getTakerBuyBaseVolume).filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new Bucket(entry.getKey(), Instant.ofEpochSecond(entry.getKey() * 300L),
                    first.getOpenPrice(), high, low, last.getClosePrice(), volume, takerBuy));
        }
        return result;
    }

    private BucketState classify(Bucket current, Bucket previous, List<Bucket> prior) {
        BigDecimal priorHigh = prior.stream().map(Bucket::high).filter(v -> v != null)
                .max(BigDecimal::compareTo).orElse(current.high());
        BigDecimal priorLow = prior.stream().map(Bucket::low).filter(v -> v != null)
                .min(BigDecimal::compareTo).orElse(current.low());
        BigDecimal avgVolume = prior.stream().map(Bucket::volume).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prior.size()), 12, RoundingMode.HALF_UP);

        double weightedBuy = ratio(current.takerBuy(), current.volume());
        double volumeRatio = ratio(current.volume(), avgVolume);
        double response = ratio(current.close(), previous.close()) - 1d;

        State state;
        if (weightedBuy <= SELL_ABSORPTION_MAX_BUY_RATIO
                && volumeRatio >= ABNORMAL_VOLUME_MULTIPLE
                && response >= -ABSORPTION_MAX_PRICE_DISPLACEMENT) {
            state = State.SELL_ABSORPTION;
        } else if (weightedBuy >= BUY_ABSORPTION_MIN_BUY_RATIO
                && volumeRatio >= ABNORMAL_VOLUME_MULTIPLE
                && response <= ABSORPTION_MAX_PRICE_DISPLACEMENT) {
            state = State.BUY_ABSORPTION;
        } else if (current.close() != null && priorHigh != null
                && current.close().compareTo(priorHigh) > 0
                && weightedBuy >= BULLISH_RELEASE_MIN_BUY_RATIO) {
            state = State.BULLISH_RELEASE;
        } else if (current.close() != null && priorHigh != null
                && current.close().doubleValue() >= priorHigh.doubleValue() * PRESSURE_NEAR_HIGH_FACTOR
                && weightedBuy >= PRESSURE_BUILDING_MIN_BUY_RATIO) {
            state = State.PRESSURE_BUILDING;
        } else {
            state = State.NORMAL;
        }
        return new BucketState(current.time(), state, weightedBuy, volumeRatio, response, priorHigh, priorLow);
    }

    private double ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) return 0d;
        return numerator.divide(denominator, 12, RoundingMode.HALF_UP).doubleValue();
    }

    private String pct(double ratio) { return fmt(ratio * 100d); }
    private String fmt(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }

    public enum State {
        NORMAL,
        PRESSURE_BUILDING,
        BULLISH_RELEASE,
        SELL_ABSORPTION,
        BUY_ABSORPTION
    }

    public record Result(
            State state,
            boolean recentBullishRelease,
            Instant recentBullishReleaseAt,
            double weightedBuyRatio,
            double volumeRatio,
            double priceResponse,
            BigDecimal priorHigh,
            BigDecimal priorLow,
            String explanation
    ) {
        static Result none() {
            return new Result(State.NORMAL, false, null, 0d, 0d, 0d, null, null,
                    "No sufficient closed-candle pressure history is available.");
        }
    }

    private record Bucket(long id, Instant time, BigDecimal open, BigDecimal high, BigDecimal low,
                          BigDecimal close, BigDecimal volume, BigDecimal takerBuy) {}
    private record BucketState(Instant bucketTime, State state, double weightedBuyRatio,
                               double volumeRatio, double priceResponse,
                               BigDecimal priorHigh, BigDecimal priorLow) {}
}
