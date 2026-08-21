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
 * FIX-026: detects a bearish -> absorption -> recovery transition from CLOSED 1m candles.
 *
 * <p>Regression scenario: ENAUSDT on 2026-08-20 around 12:55-13:04 KSA. After a
 * STRONG_SELL phase, price held near 0.0963 while taker-buy pressure printed 63%, 89%
 * and 95%. A short pullback followed, then the three completed candles immediately
 * before signal #101305 printed 85.26%, 87.47% and 73.91% taker-buy pressure while
 * closes rose 0.0967 -> 0.0969 -> 0.0971. The ordinary RANGE_MEAN_REVERSION strategy
 * correctly stayed WATCH, but the rate-of-change in evidence justified a small probe.</p>
 *
 * <p>This service never changes a TradeSignal or strategy regime. It only validates the
 * closed-candle microstructure portion of the recovery probe. Crucially, candles are
 * selected by close_time <= signal.generated_at. Production and Proven/Replay therefore
 * use identical as-of data and cannot see the still-open 13:04 KSA candle or the later
 * 13:06-13:07 expansion.</p>
 */
@Service
@RequiredArgsConstructor
public class RecoveryTransitionService {
    private static final String INTERVAL = "1m";
    private static final int LOOKBACK = 16;
    private static final double RECOVERY_BUY_MIN = 0.70d;
    private static final int RECOVERY_REQUIRED_CANDLES = 3;
    private static final double RECOVERY_MIN_PRICE_ADVANCE = 0.0030d; // 0.30%
    private static final double PRIOR_STRONG_BUY_MIN = 0.80d;
    private static final int PRIOR_STRONG_BUY_REQUIRED = 2;
    private static final double PULLBACK_BUY_MAX = 0.25d;

    private final CandleRepository candleRepository;

    @Transactional(readOnly = true)
    public Result evaluate(TradeSignal signal) {
        if (signal == null || signal.getSymbol() == null || signal.getGeneratedAt() == null) {
            return Result.none("Signal timestamp/symbol unavailable.");
        }
        List<Candle> descending = candleRepository.findClosedCandlesClosedAtOrBefore(
                signal.getSymbol(), INTERVAL, signal.getGeneratedAt(), PageRequest.of(0, LOOKBACK));
        if (descending == null || descending.size() < 8) {
            return Result.none("Insufficient closed candles for recovery-transition evaluation.");
        }
        List<Candle> candles = descending.stream().filter(this::usable)
                .sorted(Comparator.comparing(Candle::getOpenTime)).toList();
        if (candles.size() < 8) return Result.none("Insufficient usable closed candles.");

        int n = candles.size();
        List<Candle> recovery = candles.subList(n - RECOVERY_REQUIRED_CANDLES, n);
        boolean allStrong = recovery.stream().allMatch(c -> buyRatio(c) >= RECOVERY_BUY_MIN);
        boolean rising = recovery.get(1).getClosePrice().compareTo(recovery.get(0).getClosePrice()) >= 0
                && recovery.get(2).getClosePrice().compareTo(recovery.get(1).getClosePrice()) >= 0;
        double advance = ratio(recovery.get(2).getClosePrice(), recovery.get(0).getClosePrice()) - 1d;
        if (!allStrong || !rising || advance < RECOVERY_MIN_PRICE_ADVANCE) {
            return Result.none("Latest three closed candles do not prove persistent buy-pressure recovery.");
        }

        List<Candle> prior = candles.subList(Math.max(0, n - 10), n - RECOVERY_REQUIRED_CANDLES);
        long strongPrior = prior.stream().filter(c -> buyRatio(c) >= PRIOR_STRONG_BUY_MIN).count();
        boolean pullbackSeen = prior.stream().anyMatch(c -> buyRatio(c) <= PULLBACK_BUY_MAX);
        if (strongPrior < PRIOR_STRONG_BUY_REQUIRED || !pullbackSeen) {
            return Result.none("No prior absorption plus pullback/test sequence was proven.");
        }

        String explanation = "Closed-candle recovery confirmed: latest three taker-buy ratios="
                + pct(buyRatio(recovery.get(0))) + "%/" + pct(buyRatio(recovery.get(1))) + "%/"
                + pct(buyRatio(recovery.get(2))) + "% with closes "
                + price(recovery.get(0).getClosePrice()) + " -> " + price(recovery.get(1).getClosePrice())
                + " -> " + price(recovery.get(2).getClosePrice()) + " (advance=" + pct(advance)
                + "%). Prior window contained " + strongPrior + " >=80% taker-buy absorption candles"
                + " plus a <=25% taker-buy pullback/test.";
        return new Result(true, recovery.get(0).getOpenTime(), recovery.get(2).getCloseTime(), advance, explanation);
    }

    private boolean usable(Candle c) {
        return c != null && c.getClosePrice() != null && c.getClosePrice().signum() > 0
                && c.getVolume() != null && c.getVolume().signum() > 0
                && c.getTakerBuyBaseVolume() != null;
    }
    private double buyRatio(Candle c) { return ratio(c.getTakerBuyBaseVolume(), c.getVolume()); }
    private double ratio(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || b.signum() == 0) return 0d;
        return a.divide(b, 12, RoundingMode.HALF_UP).doubleValue();
    }
    private String pct(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v * 100d); }
    private String price(BigDecimal v) { return v == null ? "n/a" : v.stripTrailingZeros().toPlainString(); }

    public record Result(boolean probeReady, Instant recoveryStartedAt, Instant recoveryConfirmedAt,
                         double priceAdvance, String explanation) {
        static Result none(String explanation) { return new Result(false, null, null, 0d, explanation); }
    }
}
