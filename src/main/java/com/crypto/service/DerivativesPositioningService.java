package com.crypto.service;

import com.crypto.client.binance.BinanceFuturesMarketDataClient;
import com.crypto.client.binance.dto.BinanceFundingRate;
import com.crypto.client.binance.dto.BinanceOpenInterestPoint;
import com.crypto.config.DerivativesPositioningProperties;
import com.crypto.domain.Candle;
import com.crypto.domain.DerivativesPositioningStatus;
import com.crypto.domain.SignalDecision;
import com.crypto.dto.DerivativesPositioningResult;
import com.crypto.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DerivativesPositioningService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final BinanceFuturesMarketDataClient client;
    private final DerivativesPositioningProperties properties;
    private final CandleRepository candleRepository;

    /**
     * Historical replay must never call the live Binance futures endpoints.
     * Derivatives history is not persisted by this application, so the only
     * truthful replay result is UNAVAILABLE for that historical timestamp.
     */
    public DerivativesPositioningResult evaluateHistorical(String symbol, String interval, SignalDecision decision,
                                                            boolean entryAllowed, Instant evaluatedAt) {
        Instant time = evaluatedAt == null ? Instant.now() : evaluatedAt;
        if (!properties.enabled()) {
            return result(DerivativesPositioningStatus.NOT_APPLICABLE, decision, entryAllowed,
                    null, null, null, null, null, null, 0, period(interval), 0,
                    "Derivatives positioning is disabled.", time);
        }
        return result(DerivativesPositioningStatus.UNAVAILABLE, decision, entryAllowed,
                null, null, null, null, null, null, 0, period(interval), 0,
                "Historical derivatives positioning was not persisted; live Binance data is intentionally not used during replay.",
                time);
    }

    public DerivativesPositioningResult evaluate(String symbol, String interval, SignalDecision decision,
                                                  boolean entryAllowed, Instant evaluatedAt) {
        Instant now = evaluatedAt == null ? Instant.now() : evaluatedAt;
        if (!properties.enabled()) return result(DerivativesPositioningStatus.NOT_APPLICABLE, decision, entryAllowed,
                null, null, null, null, null, null, 0, period(interval), 0,
                "Derivatives positioning is disabled.", now);
        try {
            List<BinanceFundingRate> funding = client.fundingHistory(symbol, properties.fundingHistoryLimit());
            List<BinanceOpenInterestPoint> oi = client.openInterestHistory(symbol, period(interval), properties.openInterestHistoryLimit());
            if (funding.isEmpty() || oi.size() < 2) {
                return result(DerivativesPositioningStatus.LEARNING, decision, entryAllowed,
                        latestFunding(funding), percentile(funding), latestOi(oi), latestOiValue(oi), null,
                        priceChange(symbol, interval), funding.size(), period(interval), -5,
                        "Funding or open-interest history is still insufficient; no veto was applied.", now);
            }
            BigDecimal fundingRate = latestFunding(funding);
            BigDecimal fundingPercentile = percentile(funding);
            BigDecimal oiChange = percentChange(oi.get(oi.size() - 2).sumOpenInterest(), oi.get(oi.size() - 1).sumOpenInterest());
            BigDecimal priceChange = priceChange(symbol, interval);
            DerivativesPositioningStatus status = classify(fundingRate, oiChange, priceChange);
            SignalDecision finalDecision = decision;
            boolean allowed = entryAllowed;
            int confidence = confidence(status);
            if (isBullish(decision) && status == DerivativesPositioningStatus.LONGS_CROWDED
                    && properties.vetoExtremeCrowding()
                    && oiChange != null && oiChange.abs().compareTo(properties.strongOpenInterestChangeThreshold()) >= 0) {
                finalDecision = SignalDecision.WATCH;
                allowed = false;
            }
            if (isBearish(decision) && status == DerivativesPositioningStatus.SHORTS_CROWDED
                    && properties.vetoExtremeCrowding()
                    && oiChange != null && oiChange.abs().compareTo(properties.strongOpenInterestChangeThreshold()) >= 0) {
                finalDecision = SignalDecision.NEUTRAL;
                allowed = false;
            }
            String explanation = explanation(status, fundingRate, fundingPercentile, oiChange, priceChange, allowed);
            BinanceOpenInterestPoint latest = oi.get(oi.size() - 1);
            return result(status, finalDecision, allowed, fundingRate, fundingPercentile,
                    latest.sumOpenInterest(), latest.sumOpenInterestValue(), oiChange, priceChange,
                    funding.size(), period(interval), confidence, explanation, now);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 400) {
                return result(DerivativesPositioningStatus.NOT_APPLICABLE, decision, entryAllowed,
                        null, null, null, null, null, null, 0, period(interval), 0,
                        "No matching Binance USD-M perpetual contract is available for " + symbol + ".", now);
            }
            return result(DerivativesPositioningStatus.UNAVAILABLE, decision, entryAllowed,
                    null, null, null, null, null, null, 0, period(interval), -10,
                    "Binance futures positioning data is unavailable: " + ex.getMessage(), now);
        } catch (RuntimeException ex) {
            return result(DerivativesPositioningStatus.UNAVAILABLE, decision, entryAllowed,
                    null, null, null, null, null, null, 0, period(interval), -10,
                    "Binance futures positioning data is unavailable: " + ex.getMessage(), now);
        }
    }

    private DerivativesPositioningStatus classify(BigDecimal funding, BigDecimal oiChange, BigDecimal priceChange) {
        if (funding != null && funding.compareTo(properties.extremeFundingRate()) >= 0) return DerivativesPositioningStatus.LONGS_CROWDED;
        if (funding != null && funding.compareTo(properties.extremeFundingRate().negate()) <= 0) return DerivativesPositioningStatus.SHORTS_CROWDED;
        if (oiChange == null || priceChange == null) return DerivativesPositioningStatus.BALANCED;
        boolean oiRising = oiChange.compareTo(properties.openInterestChangeThreshold()) >= 0;
        boolean oiFalling = oiChange.compareTo(properties.openInterestChangeThreshold().negate()) <= 0;
        if (priceChange.signum() > 0 && oiRising) return DerivativesPositioningStatus.FRESH_LONG_BUILDUP;
        if (priceChange.signum() < 0 && oiRising) return DerivativesPositioningStatus.FRESH_SHORT_BUILDUP;
        if (priceChange.signum() > 0 && oiFalling) return DerivativesPositioningStatus.SHORT_COVERING;
        if (priceChange.signum() < 0 && oiFalling) return DerivativesPositioningStatus.LONG_LIQUIDATION;
        if (funding != null && funding.compareTo(properties.moderateFundingRate()) >= 0) return DerivativesPositioningStatus.HEALTHY_BULLISH;
        if (funding != null && funding.compareTo(properties.moderateFundingRate().negate()) <= 0) return DerivativesPositioningStatus.HEALTHY_BEARISH;
        return oiChange.abs().compareTo(properties.openInterestChangeThreshold()) < 0
                ? DerivativesPositioningStatus.LOW_CONVICTION : DerivativesPositioningStatus.BALANCED;
    }

    private int confidence(DerivativesPositioningStatus status) {
        return switch (status) {
            case FRESH_LONG_BUILDUP, FRESH_SHORT_BUILDUP -> 8;
            case HEALTHY_BULLISH, HEALTHY_BEARISH, BALANCED -> 3;
            case SHORT_COVERING, LONG_LIQUIDATION, LOW_CONVICTION -> -5;
            case LONGS_CROWDED, SHORTS_CROWDED -> -15;
            case LEARNING -> -5;
            case UNAVAILABLE -> -10;
            case NOT_APPLICABLE -> 0;
        };
    }

    private String explanation(DerivativesPositioningStatus status, BigDecimal funding, BigDecimal percentile,
                               BigDecimal oiChange, BigDecimal priceChange, boolean allowed) {
        return "Positioning " + status + "; funding=" + value(funding) + ", percentile=" + value(percentile)
                + "%, OI change=" + value(oiChange) + "%, price change=" + value(priceChange)
                + "%. Entry " + (allowed ? "remains allowed" : "was blocked") + ".";
    }

    private BigDecimal priceChange(String symbol, String interval) {
        List<Candle> candles = new ArrayList<>(candleRepository.findClosedCandles(symbol, interval, PageRequest.of(0, 2)));
        if (candles.size() < 2) return null;
        candles.sort(Comparator.comparing(Candle::getOpenTime));
        return percentChange(candles.get(0).getClosePrice(), candles.get(1).getClosePrice());
    }

    private BigDecimal latestFunding(List<BinanceFundingRate> rates) {
        return rates.isEmpty() ? null : rates.get(rates.size() - 1).fundingRate();
    }
    private BigDecimal latestOi(List<BinanceOpenInterestPoint> points) { return points.isEmpty() ? null : points.get(points.size()-1).sumOpenInterest(); }
    private BigDecimal latestOiValue(List<BinanceOpenInterestPoint> points) { return points.isEmpty() ? null : points.get(points.size()-1).sumOpenInterestValue(); }

    private BigDecimal percentile(List<BinanceFundingRate> rates) {
        if (rates.size() < properties.minimumFundingSamples()) return null;
        BigDecimal latest = latestFunding(rates);
        long lessOrEqual = rates.stream().map(BinanceFundingRate::fundingRate).filter(v -> v.compareTo(latest) <= 0).count();
        return BigDecimal.valueOf(lessOrEqual).multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(rates.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentChange(BigDecimal before, BigDecimal after) {
        if (before == null || after == null || before.signum() == 0) return null;
        return after.subtract(before).divide(before.abs(), 8, RoundingMode.HALF_UP).multiply(ONE_HUNDRED);
    }

    private String period(String interval) {
        return switch (interval) {
            case "1m", "5m" -> "5m";
            case "15m" -> "15m";
            case "30m" -> "30m";
            case "1h" -> "1h";
            case "2h" -> "2h";
            case "4h" -> "4h";
            case "6h" -> "6h";
            case "12h" -> "12h";
            case "1d" -> "1d";
            default -> "5m";
        };
    }

    private boolean isBullish(SignalDecision d) { return d == SignalDecision.BUY || d == SignalDecision.STRONG_BUY; }
    private boolean isBearish(SignalDecision d) { return d == SignalDecision.SELL || d == SignalDecision.STRONG_SELL; }
    private String value(BigDecimal v) { return v == null ? "n/a" : v.stripTrailingZeros().toPlainString(); }

    private DerivativesPositioningResult result(DerivativesPositioningStatus status, SignalDecision decision,
            boolean allowed, BigDecimal funding, BigDecimal percentile, BigDecimal oi, BigDecimal oiValue,
            BigDecimal oiChange, BigDecimal priceChange, int samples, String period, int confidence,
            String explanation, Instant time) {
        return new DerivativesPositioningResult(status, decision, allowed, funding, percentile, oi, oiValue,
                oiChange, priceChange, samples, period, confidence, explanation, time);
    }
}
