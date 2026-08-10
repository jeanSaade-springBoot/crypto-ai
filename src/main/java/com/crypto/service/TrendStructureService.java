package com.crypto.service;

import com.crypto.domain.Candle;
import com.crypto.dto.IndicatorSnapshot;
import com.crypto.dto.TrendStructureResult;
import com.crypto.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Phase 3 trend-structure analysis.
 *
 * The existing direction and strength groups remain unchanged. This service
 * fills the 7-point Trend Structure group with price-action evidence so the
 * engine can recognize an improving trend before perfect EMA alignment.
 */
@Service
@RequiredArgsConstructor
public class TrendStructureService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int REQUIRED_CANDLES = 10;
    private static final BigDecimal EMA_TOUCH_ATR_MULTIPLE = new BigDecimal("0.35");
    private static final BigDecimal COMPRESSION_RATIO = new BigDecimal("0.78");

    private final CandleRepository candleRepository;

    public TrendStructureResult evaluate(IndicatorSnapshot indicator) {
        if (indicator == null || indicator.candleOpenTime() == null) {
            return TrendStructureResult.unavailable("Indicator candle time is unavailable");
        }

        // Query the candle history AS OF the indicator timestamp. This is critical
        // for both live production and historical replay: live gets the latest
        // closed candles up to the current signal, while replay gets the exact
        // historical candles that were available at that moment. Querying the
        // latest candles first and filtering in memory can return no historical
        // context when the replay timestamp is older than the repository window.
        List<Candle> candles = candleRepository
                .findClosedCandlesAtOrBefore(
                        indicator.symbol(),
                        indicator.intervalCode(),
                        indicator.candleOpenTime(),
                        PageRequest.of(0, REQUIRED_CANDLES))
                .stream()
                .sorted(Comparator.comparing(Candle::getOpenTime))
                .toList();

        if (candles.size() < REQUIRED_CANDLES) {
            return TrendStructureResult.unavailable(
                    "At least " + REQUIRED_CANDLES + " closed candles are required for trend-structure scoring"
            );
        }

        List<Candle> window = candles.subList(candles.size() - REQUIRED_CANDLES, candles.size());
        List<String> evidence = new ArrayList<>();

        // Compare two consecutive 5-candle blocks. The previous implementation
        // skipped candles at the transition boundary (1..4 versus 6..9), which
        // could miss exactly the structure change we are trying to detect.
        Segment previous = segment(window.subList(0, 5));
        Segment recent = segment(window.subList(5, 10));

        boolean higherHigh = recent.high().compareTo(previous.high()) > 0;
        boolean higherLow = recent.low().compareTo(previous.low()) > 0;
        int marketStructure = (higherHigh ? 1 : 0) + (higherLow ? 1 : 0);
        if (higherHigh) evidence.add("Recent candles formed a higher high");
        if (higherLow) evidence.add("Recent candles preserved a higher low");

        Pullback pullback = pullbackQuality(window, indicator);
        if (pullback.qualityScore() > 0) evidence.add(pullback.reason());

        boolean ema20Respected = respectsEma20(window, indicator);
        int emaRespect = ema20Respected ? 1 : 0;
        if (ema20Respected) evidence.add("Price tested and closed back above EMA20");

        boolean compression = detectsCompression(window, indicator);
        boolean bullishExpansion = confirmsBullishExpansion(indicator, previous, recent);
        int breakoutPreparation = (compression || bullishExpansion) ? 1 : 0;
        if (compression) evidence.add("Recent candle ranges compressed while price held above EMA20");
        if (bullishExpansion && !compression) {
            evidence.add("Price broke prior structure with bullish momentum and volume expansion");
        }

        boolean continuation = supportsContinuation(indicator, previous, recent);
        int continuationScore = continuation ? 1 : 0;
        if (continuation) evidence.add("Momentum and price location support continuation");

        int total = Math.min(7,
                marketStructure
                        + pullback.qualityScore()
                        + emaRespect
                        + breakoutPreparation
                        + continuationScore
        );

        String explanation = total >= 6
                ? "Strong improving price structure with early continuation evidence"
                : total >= 4
                    ? "Developing bullish structure; confirmation is improving"
                    : total >= 2
                        ? "Partial transition evidence, but structure is not fully established"
                        : "No reliable bullish transition structure was detected";

        return new TrendStructureResult(
                total,
                marketStructure,
                pullback.qualityScore(),
                emaRespect,
                breakoutPreparation,
                continuationScore,
                higherHigh,
                higherLow,
                pullback.qualityScore() == 2,
                ema20Respected,
                compression,
                continuation,
                explanation,
                List.copyOf(evidence)
        );
    }

    private Pullback pullbackQuality(List<Candle> window, IndicatorSnapshot indicator) {
        List<Candle> impulse = window.subList(2, 7);
        List<Candle> recent = window.subList(7, 10);

        BigDecimal impulseHigh = maxHigh(impulse);
        BigDecimal impulseLow = minLow(impulse);
        BigDecimal recentLow = minLow(recent);
        BigDecimal impulseRange = impulseHigh.subtract(impulseLow).abs();
        BigDecimal pullbackDepth = impulseHigh.subtract(recentLow).max(BigDecimal.ZERO);

        boolean shallow = impulseRange.signum() > 0
                && pullbackDepth.divide(impulseRange, 8, RoundingMode.HALF_UP)
                    .compareTo(new BigDecimal("0.55")) <= 0;
        boolean reducedSellVolume = averageVolume(recent)
                .compareTo(averageVolume(impulse)) <= 0;
        boolean structureHeld = recentLow.compareTo(impulseLow) > 0;
        boolean closeRecovered = window.get(window.size() - 1).getClosePrice()
                .compareTo(indicator.ema20()) >= 0;

        int score = 0;
        if (structureHeld && shallow) score++;
        if (reducedSellVolume && closeRecovered) score++;

        String reason = score == 2
                ? "Pullback was shallow, held structure and recovered with controlled volume"
                : score == 1
                    ? "Pullback retained one healthy continuation characteristic"
                    : "Pullback quality did not support an early entry";
        return new Pullback(score, reason);
    }

    private boolean respectsEma20(List<Candle> window, IndicatorSnapshot indicator) {
        if (indicator.atr14() == null || indicator.atr14().signum() <= 0 || indicator.ema20() == null) {
            return false;
        }
        BigDecimal tolerance = indicator.atr14().multiply(EMA_TOUCH_ATR_MULTIPLE, MC);
        return window.subList(window.size() - 4, window.size()).stream().anyMatch(candle -> {
            BigDecimal distance = candle.getLowPrice().subtract(indicator.ema20()).abs();
            return distance.compareTo(tolerance) <= 0
                    && candle.getClosePrice().compareTo(indicator.ema20()) >= 0;
        });
    }

    private boolean detectsCompression(List<Candle> window, IndicatorSnapshot indicator) {
        BigDecimal earlierRange = averageRange(window.subList(1, 6));
        BigDecimal recentRange = averageRange(window.subList(7, 10));
        return earlierRange.signum() > 0
                && recentRange.compareTo(earlierRange.multiply(COMPRESSION_RATIO, MC)) <= 0
                && window.get(window.size() - 1).getClosePrice().compareTo(indicator.ema20()) >= 0;
    }

    private boolean supportsContinuation(IndicatorSnapshot indicator, Segment previous, Segment recent) {
        boolean rsiHealthy = indicator.rsi14() != null
                && indicator.rsi14().compareTo(new BigDecimal("45")) >= 0
                && indicator.rsi14().compareTo(new BigDecimal("72")) <= 0;
        boolean momentumHealthy = indicator.macdHistogram() != null
                && indicator.macdHistogram().signum() >= 0;
        boolean priceHealthy = indicator.latestPrice().compareTo(indicator.ema20()) >= 0
                && recent.close().compareTo(recent.open()) >= 0;

        // RSI is already scored and penalized separately by the momentum model.
        // Do not remove the structure-continuation point a second time when a
        // genuinely confirmed breakout is what pushed RSI above 72. Hot RSI alone
        // is never enough; structure break + MACD + RVOL + bullish recent segment
        // must all agree.
        boolean confirmedHotBreakout = indicator.rsi14() != null
                && indicator.rsi14().compareTo(new BigDecimal("72")) > 0
                && confirmsBullishExpansion(indicator, previous, recent);

        return momentumHealthy && priceHealthy && (rsiHealthy || confirmedHotBreakout);
    }

    private boolean confirmsBullishExpansion(
            IndicatorSnapshot indicator,
            Segment previous,
            Segment recent
    ) {
        if (indicator == null || previous == null || recent == null
                || indicator.latestPrice() == null || indicator.ema20() == null
                || indicator.macdHistogram() == null || indicator.relativeVolume() == null) {
            return false;
        }

        boolean brokePriorHigh = recent.high().compareTo(previous.high()) > 0
                && recent.close().compareTo(previous.high()) > 0;
        boolean bullishSegment = recent.close().compareTo(recent.open()) > 0;
        boolean aboveEma20 = indicator.latestPrice().compareTo(indicator.ema20()) >= 0;
        boolean positiveMomentum = indicator.macdHistogram().signum() > 0;
        boolean volumeExpansion = indicator.relativeVolume().compareTo(new BigDecimal("1.50")) >= 0;

        return brokePriorHigh && bullishSegment && aboveEma20 && positiveMomentum && volumeExpansion;
    }

    private Segment segment(List<Candle> candles) {
        return new Segment(
                maxHigh(candles),
                minLow(candles),
                candles.get(0).getOpenPrice(),
                candles.get(candles.size() - 1).getClosePrice()
        );
    }

    private BigDecimal maxHigh(List<Candle> candles) {
        return candles.stream().map(Candle::getHighPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private BigDecimal minLow(List<Candle> candles) {
        return candles.stream().map(Candle::getLowPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private BigDecimal averageRange(List<Candle> candles) {
        return candles.stream()
                .map(c -> c.getHighPrice().subtract(c.getLowPrice()).abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(candles.size()), MC);
    }

    private BigDecimal averageVolume(List<Candle> candles) {
        return candles.stream()
                .map(Candle::getVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(candles.size()), MC);
    }

    private record Segment(BigDecimal high, BigDecimal low, BigDecimal open, BigDecimal close) {}
    private record Pullback(int qualityScore, String reason) {}
}
