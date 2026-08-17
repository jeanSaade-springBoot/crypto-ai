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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Detects a very specific early-reversal sequence from CLOSED 1m candles.
 *
 * <p>This service does not score a TradeSignal and never changes the normal BUY path.
 * Its only purpose is to tell ExecutionIntelligenceService whether a small exploratory
 * pressure probe is justified while the normal 1m/5m/1h confirmation path is still
 * catching up.</p>
 *
 * <p>The detector deliberately requires a sequence, not one impressive candle:</p>
 * <pre>
 * meaningful bullish burst
 *      -> rejection / aggressive selling
 *      -> retest that holds above the pre-burst structural low
 *      -> repeated bullish pressure rebuild
 *      -> price reclaim
 * </pre>
 *
 * <p>Production and Proven/Regression call this exact class. Candles are selected by
 * close_time <= signal.generated_at, which is critical: replay cannot see a candle that
 * had not closed yet at the historical decision timestamp.</p>
 */
@Service
@RequiredArgsConstructor
public class PressureReadinessService {

    private static final String INTERVAL = "1m";
    private static final int LOOKBACK_CANDLES = 75;

    // Generic, normalized guards. These are intentionally based on ratios/structure,
    // not SOL prices or a hard-coded historical timestamp.
    private static final int PRE_BURST_STRUCTURE_MIN_CANDLES = 10;
    private static final int PRE_BURST_STRUCTURE_MAX_CANDLES = 25;
    private static final int MAX_BURST_PAIR_GAP = 1;
    private static final double BURST_WEIGHTED_BUY_MIN = 0.70d;
    private static final double BURST_SINGLE_CANDLE_BUY_MIN = 0.74d;
    private static final double BURST_MIN_PRICE_ADVANCE = 0.0015d;          // 0.15%
    private static final double BURST_MIN_VOLUME_MULTIPLE = 2.00d;

    private static final int RETEST_MAX_CANDLES_AFTER_BURST = 12;
    private static final double RETEST_SELL_DOMINANCE_MAX_BUY = 0.30d;
    private static final double RETEST_MIN_PULLBACK_FROM_BURST_HIGH = 0.0010d; // 0.10%
    private static final double RETEST_MIN_HIGHER_LOW = 0.0005d;              // 0.05%

    private static final int REBUILD_MAX_CANDLES = 10;
    private static final double REBUILD_BUY_MIN = 0.58d;
    private static final double REBUILD_STRONG_BUY_MIN = 0.70d;
    private static final int REBUILD_MIN_BUY_CANDLES = 3;
    private static final int REBUILD_MIN_STRONG_BUY_CANDLES = 1;
    private static final double REBUILD_MIN_RECOVERY_FROM_RETEST = 0.0008d;   // 0.08%

    private final CandleRepository candleRepository;

    @Transactional(readOnly = true)
    public Result evaluate(TradeSignal signal) {
        if (signal == null || signal.getSymbol() == null || signal.getGeneratedAt() == null) {
            return Result.none("Signal timestamp/symbol unavailable.");
        }

        List<Candle> descending = candleRepository.findClosedCandlesClosedAtOrBefore(
                signal.getSymbol(), INTERVAL, signal.getGeneratedAt(), PageRequest.of(0, LOOKBACK_CANDLES));
        if (descending == null || descending.size() < PRE_BURST_STRUCTURE_MIN_CANDLES + 6) {
            return Result.none("Insufficient closed 1m candle history for pressure-sequence evaluation.");
        }

        List<Candle> candles = descending.stream()
                .filter(this::usable)
                .sorted(Comparator.comparing(Candle::getOpenTime))
                .toList();
        if (candles.size() < PRE_BURST_STRUCTURE_MIN_CANDLES + 6) {
            return Result.none("Insufficient usable closed 1m candle history for pressure-sequence evaluation.");
        }

        // Search newest-to-oldest so the most recent complete transition owns the probe.
        for (int burstStart = candles.size() - 3; burstStart >= PRE_BURST_STRUCTURE_MIN_CANDLES; burstStart--) {
            int burstEnd = burstStart + MAX_BURST_PAIR_GAP;
            if (burstEnd >= candles.size()) continue;

            Candle first = candles.get(burstStart);
            Candle second = candles.get(burstEnd);
            int structureStart = Math.max(0, burstStart - PRE_BURST_STRUCTURE_MAX_CANDLES);
            List<Candle> prior = candles.subList(structureStart, burstStart);
            if (prior.size() < PRE_BURST_STRUCTURE_MIN_CANDLES) continue;

            Burst burst = burst(first, second, prior);
            if (!burst.qualifies()) continue;

            int retestSearchEnd = Math.min(candles.size() - 1, burstEnd + RETEST_MAX_CANDLES_AFTER_BURST);
            Retest retest = retest(candles, burstEnd + 1, retestSearchEnd, burst, prior);
            if (!retest.qualifies()) continue;

            int rebuildEnd = Math.min(candles.size() - 1, retest.index() + REBUILD_MAX_CANDLES);
            Rebuild rebuild = rebuild(candles, retest.index() + 1, rebuildEnd, retest, first.getOpenPrice());
            if (!rebuild.qualifies()) continue;

            Candle current = candles.get(candles.size() - 1);
            // The completed sequence must still be relevant to the current decision.
            if (rebuild.lastBullishIndex() < candles.size() - REBUILD_MAX_CANDLES - 1) continue;

            String explanation = "Pressure probe ready: bullish burst at " + first.getOpenTime()
                    + " (weighted buy=" + pct(burst.weightedBuy())
                    + "%, volume=" + fmt(burst.volumeMultiple()) + "x baseline, advance="
                    + pct(burst.priceAdvance()) + "%), rejected into a higher-low retest at "
                    + candles.get(retest.index()).getOpenTime()
                    + " (structural low=" + fmtPrice(retest.structuralLow())
                    + ", retest low=" + fmtPrice(retest.retestLow()) + "), then buyer pressure rebuilt across "
                    + rebuild.buyCandleCount() + " candles with " + rebuild.strongBuyCandleCount()
                    + " strong-buy candle(s). Current close=" + fmtPrice(current.getClosePrice()) + ".";

            return new Result(
                    State.PROBE_READY,
                    true,
                    first.getOpenTime(),
                    candles.get(retest.index()).getOpenTime(),
                    candles.get(rebuild.lastBullishIndex()).getOpenTime(),
                    burst.weightedBuy(),
                    burst.volumeMultiple(),
                    burst.priceAdvance(),
                    retest.structuralLow(),
                    retest.retestLow(),
                    burst.high(),
                    rebuild.buyCandleCount(),
                    rebuild.strongBuyCandleCount(),
                    explanation
            );
        }

        return Result.none("No complete burst -> rejection -> higher-low retest -> pressure-rebuild sequence exists as-of this signal.");
    }

    private Burst burst(Candle first, Candle second, List<Candle> prior) {
        BigDecimal combinedVolume = nz(first.getVolume()).add(nz(second.getVolume()));
        BigDecimal combinedBuy = nz(first.getTakerBuyBaseVolume()).add(nz(second.getTakerBuyBaseVolume()));
        double weightedBuy = ratio(combinedBuy, combinedVolume);
        double firstBuy = ratio(first.getTakerBuyBaseVolume(), first.getVolume());
        double secondBuy = ratio(second.getTakerBuyBaseVolume(), second.getVolume());
        double priceAdvance = ratio(second.getClosePrice(), first.getOpenPrice()) - 1d;
        BigDecimal averagePriorVolume = prior.stream().map(Candle::getVolume).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prior.size()), 12, RoundingMode.HALF_UP);
        double volumeMultiple = averagePriorVolume.signum() <= 0 ? 0d
                : combinedVolume.divide(averagePriorVolume.multiply(BigDecimal.valueOf(2)), 12, RoundingMode.HALF_UP).doubleValue();
        BigDecimal high = max(first.getHighPrice(), second.getHighPrice());

        boolean qualifies = weightedBuy >= BURST_WEIGHTED_BUY_MIN
                && Math.max(firstBuy, secondBuy) >= BURST_SINGLE_CANDLE_BUY_MIN
                && priceAdvance >= BURST_MIN_PRICE_ADVANCE
                && volumeMultiple >= BURST_MIN_VOLUME_MULTIPLE;
        return new Burst(qualifies, weightedBuy, volumeMultiple, priceAdvance, high);
    }

    private Retest retest(List<Candle> candles, int start, int end, Burst burst, List<Candle> prior) {
        if (start > end) return Retest.none();
        BigDecimal structuralLow = prior.stream().map(Candle::getLowPrice).filter(v -> v != null)
                .min(BigDecimal::compareTo).orElse(null);
        if (structuralLow == null || burst.high() == null) return Retest.none();

        int lowIndex = -1;
        BigDecimal low = null;
        boolean aggressiveSellingSeen = false;
        for (int i = start; i <= end; i++) {
            Candle c = candles.get(i);
            if (c.getLowPrice() != null && (low == null || c.getLowPrice().compareTo(low) < 0)) {
                low = c.getLowPrice();
                lowIndex = i;
            }
            if (ratio(c.getTakerBuyBaseVolume(), c.getVolume()) <= RETEST_SELL_DOMINANCE_MAX_BUY) {
                aggressiveSellingSeen = true;
            }
        }
        if (low == null || lowIndex < 0) return Retest.none();

        double pullback = 1d - ratio(low, burst.high());
        double higherLow = ratio(low, structuralLow) - 1d;
        boolean qualifies = aggressiveSellingSeen
                && pullback >= RETEST_MIN_PULLBACK_FROM_BURST_HIGH
                && higherLow >= RETEST_MIN_HIGHER_LOW;
        return new Retest(qualifies, lowIndex, structuralLow, low, pullback, higherLow);
    }

    private Rebuild rebuild(List<Candle> candles, int start, int end, Retest retest, BigDecimal burstOpen) {
        if (start > end || retest.retestLow() == null || burstOpen == null) return Rebuild.none();
        int buyCount = 0;
        int strongCount = 0;
        int lastBullish = -1;
        BigDecimal latestClose = null;
        for (int i = start; i <= end; i++) {
            Candle c = candles.get(i);
            double buy = ratio(c.getTakerBuyBaseVolume(), c.getVolume());
            if (buy >= REBUILD_BUY_MIN && c.getClosePrice() != null && c.getOpenPrice() != null
                    && c.getClosePrice().compareTo(c.getOpenPrice()) >= 0) {
                buyCount++;
                lastBullish = i;
                if (buy >= REBUILD_STRONG_BUY_MIN) strongCount++;
            }
            latestClose = c.getClosePrice();
        }
        if (latestClose == null) return Rebuild.none();
        double recovery = ratio(latestClose, retest.retestLow()) - 1d;
        boolean reclaim = latestClose.compareTo(burstOpen) >= 0;
        boolean qualifies = buyCount >= REBUILD_MIN_BUY_CANDLES
                && strongCount >= REBUILD_MIN_STRONG_BUY_CANDLES
                && recovery >= REBUILD_MIN_RECOVERY_FROM_RETEST
                && reclaim;
        return new Rebuild(qualifies, buyCount, strongCount, lastBullish, recovery);
    }

    private boolean usable(Candle c) {
        return c != null && c.getOpenTime() != null && c.getCloseTime() != null
                && c.getOpenPrice() != null && c.getHighPrice() != null && c.getLowPrice() != null
                && c.getClosePrice() != null && c.getVolume() != null && c.getVolume().signum() > 0;
    }

    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal max(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.max(b);
    }
    private double ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) return 0d;
        return numerator.divide(denominator, 12, RoundingMode.HALF_UP).doubleValue();
    }
    private String pct(double ratio) { return fmt(ratio * 100d); }
    private String fmt(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
    private String fmtPrice(BigDecimal value) { return value == null ? "n/a" : value.stripTrailingZeros().toPlainString(); }

    public enum State {
        NORMAL,
        PROBE_READY
    }

    public record Result(
            State state,
            boolean probeReady,
            Instant burstAt,
            Instant retestAt,
            Instant rebuildAt,
            double burstWeightedBuyRatio,
            double burstVolumeMultiple,
            double burstPriceAdvance,
            BigDecimal structuralLow,
            BigDecimal retestLow,
            BigDecimal burstHigh,
            int rebuildBuyCandles,
            int rebuildStrongBuyCandles,
            String explanation
    ) {
        static Result none(String explanation) {
            return new Result(State.NORMAL, false, null, null, null,
                    0d, 0d, 0d, null, null, null, 0, 0, explanation);
        }
    }

    private record Burst(boolean qualifies, double weightedBuy, double volumeMultiple,
                         double priceAdvance, BigDecimal high) {}
    private record Retest(boolean qualifies, int index, BigDecimal structuralLow, BigDecimal retestLow,
                          double pullback, double higherLow) {
        static Retest none() { return new Retest(false, -1, null, null, 0d, 0d); }
    }
    private record Rebuild(boolean qualifies, int buyCandleCount, int strongBuyCandleCount,
                           int lastBullishIndex, double recovery) {
        static Rebuild none() { return new Rebuild(false, 0, 0, -1, 0d); }
    }
}
