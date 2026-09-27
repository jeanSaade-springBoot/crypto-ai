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
                SELECT observed_at, price, source
                FROM market_price_event
                WHERE delivery_status IN ('LEGACY','APPLIED') AND symbol = ? AND observed_at >= ? AND observed_at <= ?
                ORDER BY observed_at ASC, id ASC
                """, (rs, rowNum) -> new PriceEvent(
                        rs.getTimestamp("observed_at").toInstant(),
                        rs.getBigDecimal("price"), rs.getString("source")),
                symbol, Timestamp.from(startInclusive), Timestamp.from(endInclusive));
    }


    /** FIX-056: newest canonical live-price observation at or before the execution clock. */
    public java.util.Optional<PriceEvent> findLatestAtOrBefore(String rawSymbol, Instant reference) {
        String symbol = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        List<PriceEvent> rows = jdbcTemplate.query("""
                SELECT observed_at, price, source
                FROM market_price_event
                WHERE delivery_status IN ('LEGACY','APPLIED') AND symbol = ? AND observed_at <= ?
                ORDER BY observed_at DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> new PriceEvent(
                        rs.getTimestamp("observed_at").toInstant(),
                        rs.getBigDecimal("price"), rs.getString("source")),
                symbol, Timestamp.from(reference));
        return rows.stream().findFirst();
    }

    /** FIX-132: known skipped/unconfirmed collector delivery must not silently
     * become legacy candle-price fallback. Historical pre-collector windows retain
     * their established fallback. This reads immutable evidence, never live claims. */
    public void assertReplayEvidence(String symbol,Instant from,Instant to) {
        Long pending=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM market_price_event WHERE symbol=? AND observed_at BETWEEN ? AND ? AND source_event_id IS NOT NULL AND delivery_status='PENDING'",
            Long.class,symbol,Timestamp.from(from),Timestamp.from(to));
        if(pending!=null && pending>0)throw new IllegalStateException("FIX-132 Replay contains unconfirmed Production price outcomes; reconcile before claiming parity");
        Long cutovers=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_history WHERE symbol=? AND cutover_at<=?",Long.class,symbol,Timestamp.from(to));
        if(cutovers!=null && cutovers>0) {
            Long applied=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM market_price_event WHERE symbol=? AND observed_at BETWEEN ? AND ? AND delivery_status IN ('LEGACY','APPLIED')",
                Long.class,symbol,Timestamp.from(from),Timestamp.from(to));
            if(applied==null || applied==0)throw new IllegalStateException("FIX-132 no applied price evidence in collector-era Replay window; signal-price fallback is not Production reproduction");
        }
    }

    public record PriceEvent(Instant observedAt, BigDecimal price, String source) {
        public PriceEvent(Instant observedAt,BigDecimal price) { this(observedAt,price,"BINANCE_KLINE_LIVE_CLOSE"); }
    }
}
