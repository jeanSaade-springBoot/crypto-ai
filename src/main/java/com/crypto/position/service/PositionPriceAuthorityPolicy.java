package com.crypto.position.service;

import com.crypto.domain.TradeSignal;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Single authority for deciding whether a TradeSignal price is temporally valid for
 * price-based position protection (TP / SL / profit-lock / replay mark-to-market).
 *
 * IMPORTANT INCIDENT GUARD (ALLOUSDT 2026-08-17): a 5m signal generated at 18:23:28
 * carried the close of its 18:15-18:19 candle (0.2806). A position opened at 18:23:26
 * at 0.2822 was incorrectly stopped four seconds later because that historical candle
 * close was treated as a current market price. Signal prices remain valid as ANALYSIS
 * CONTEXT, but a candle whose market observation time predates the position may never
 * drive mechanical protection.
 */
@Service
public class PositionPriceAuthorityPolicy {

    public boolean canUseSignalPrice(TradeSignal signal, Instant positionOpenedAt) {
        if (signal == null || positionOpenedAt == null || signal.getLatestPrice() == null
                || signal.getLatestPrice().signum() <= 0) {
            return false;
        }
        Instant observedAt = marketObservationTime(signal);
        return observedAt != null && !observedAt.isBefore(positionOpenedAt);
    }

    /** Candle close/observation time, derived from candle open time + interval. */
    public Instant marketObservationTime(TradeSignal signal) {
        if (signal == null || signal.getCandleOpenTime() == null) return null;
        Duration interval = intervalDuration(signal.getInterval());
        return interval == null ? null : signal.getCandleOpenTime().plus(interval);
    }

    private Duration intervalDuration(String interval) {
        if (interval == null) return null;
        return switch (interval.trim().toLowerCase()) {
            case "1m" -> Duration.ofMinutes(1);
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "1h" -> Duration.ofHours(1);
            case "4h" -> Duration.ofHours(4);
            case "1d" -> Duration.ofDays(1);
            default -> null;
        };
    }
}
