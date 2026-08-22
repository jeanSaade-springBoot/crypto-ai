package com.crypto.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * FIX-052 exact Replay/Production parity support.
 *
 * Production protects open positions from every canonical 1m Binance live-price
 * update, not only from candle-close analysis signals. Persisting that exact feed
 * lets Replay evaluate TP/SL/profit-lock against the same observations and order.
 * Timestamps are always stored/interpreted as UTC Instants; timezone conversion is
 * a presentation concern only.
 */
@Service
@RequiredArgsConstructor
public class MarketPriceEventService {
    private final JdbcTemplate jdbcTemplate;

    public void record(String rawSymbol, BigDecimal price, Instant observedAt) {
        if (rawSymbol == null || rawSymbol.isBlank() || price == null || price.signum() <= 0 || observedAt == null) {
            return;
        }
        String symbol = rawSymbol.trim().toUpperCase(Locale.ROOT);
        jdbcTemplate.update("""
                INSERT INTO market_price_event(symbol, observed_at, price, source)
                VALUES (?, ?, ?, 'BINANCE_KLINE_LIVE_CLOSE')
                """, symbol, Timestamp.from(observedAt), price);
    }

    public List<PriceEvent> find(String rawSymbol, Instant startInclusive, Instant endInclusive) {
        String symbol = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        return jdbcTemplate.query("""
                SELECT observed_at, price
                FROM market_price_event
                WHERE symbol = ? AND observed_at >= ? AND observed_at <= ?
                ORDER BY observed_at ASC, id ASC
                """, (rs, rowNum) -> new PriceEvent(
                        rs.getTimestamp("observed_at").toInstant(),
                        rs.getBigDecimal("price")),
                symbol, Timestamp.from(startInclusive), Timestamp.from(endInclusive));
    }

    public record PriceEvent(Instant observedAt, BigDecimal price) {}
}
