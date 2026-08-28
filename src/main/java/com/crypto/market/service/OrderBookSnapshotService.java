package com.crypto.market.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * FIX-112C: durable normalized Order Book evidence for exact historical Replay.
 * The stored imbalance is the existing signed Production formula in [-1,+1].
 *
 * Persistence is deliberately asynchronous and best-effort. Historical Replay parity may
 * become unavailable if persistence fails, but Production sampling/decisions must remain
 * unaffected by database latency, pool exhaustion, or insert errors.
 */
@Service
public class OrderBookSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(OrderBookSnapshotService.class);

    private final JdbcTemplate jdbcTemplate;
    private final Executor persistenceExecutor;

    public OrderBookSnapshotService(
            JdbcTemplate jdbcTemplate,
            @Qualifier("orderBookPersistenceExecutor") Executor persistenceExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.persistenceExecutor = persistenceExecutor;
    }

    /**
     * FIX-112C parity-safety boundary: enqueue persistence and return immediately to the
     * live collector. Queue saturation is reported and the historical row is sacrificed;
     * it must never execute synchronously on the Production collection thread.
     */
    public void recordAsync(String rawSymbol, Instant observedAt,
                            BigDecimal bestBid, BigDecimal bestAsk, BigDecimal spreadPercent,
                            BigDecimal bidDepth, BigDecimal askDepth, BigDecimal depthImbalance,
                            BigDecimal bidWallPrice, BigDecimal bidWallQuantity,
                            BigDecimal askWallPrice, BigDecimal askWallQuantity,
                            int collectionLatencyMs) {
        try {
            persistenceExecutor.execute(() -> {
                try {
                    record(rawSymbol, observedAt, bestBid, bestAsk, spreadPercent,
                            bidDepth, askDepth, depthImbalance,
                            bidWallPrice, bidWallQuantity, askWallPrice, askWallQuantity,
                            collectionLatencyMs);
                } catch (Exception exception) {
                    log.warn("Unable to persist historical order-book snapshot for {} at {}: {}. Live Production sampling is unaffected.",
                            rawSymbol, observedAt, exception.getMessage());
                }
            });
        } catch (RejectedExecutionException exception) {
            log.warn("Historical order-book persistence queue is full for {} at {}; snapshot was not persisted. Live Production sampling is unaffected.",
                    rawSymbol, observedAt);
        }
    }

    private void record(String rawSymbol, Instant observedAt,
                        BigDecimal bestBid, BigDecimal bestAsk, BigDecimal spreadPercent,
                        BigDecimal bidDepth, BigDecimal askDepth, BigDecimal depthImbalance,
                        BigDecimal bidWallPrice, BigDecimal bidWallQuantity,
                        BigDecimal askWallPrice, BigDecimal askWallQuantity,
                        int collectionLatencyMs) {
        if (rawSymbol == null || rawSymbol.isBlank() || observedAt == null) return;
        String symbol = rawSymbol.trim().toUpperCase(Locale.ROOT);
        // Existing live code expresses spread as percent. Persist bps explicitly.
        BigDecimal spreadBps = spreadPercent == null ? null : spreadPercent.multiply(BigDecimal.valueOf(100));
        jdbcTemplate.update("""
                INSERT INTO order_book_snapshot(
                    symbol, observed_at, best_bid, best_ask, spread_bps,
                    bid_depth, ask_depth, depth_imbalance,
                    bid_wall_price, bid_wall_quantity, ask_wall_price, ask_wall_quantity,
                    collection_latency_ms, source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'BINANCE_LIVE')
                """, symbol, Timestamp.from(observedAt), bestBid, bestAsk, spreadBps,
                bidDepth, askDepth, depthImbalance,
                bidWallPrice, bidWallQuantity, askWallPrice, askWallQuantity,
                collectionLatencyMs);
    }

    public List<PersistedObservation> find(String rawSymbol, Instant startInclusive, Instant endInclusive) {
        String symbol = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        return jdbcTemplate.query("""
                SELECT observed_at, best_bid, best_ask, spread_bps,
                       bid_depth, ask_depth, depth_imbalance,
                       bid_wall_price, bid_wall_quantity, ask_wall_price, ask_wall_quantity
                FROM order_book_snapshot
                WHERE symbol = ? AND observed_at >= ? AND observed_at <= ?
                ORDER BY observed_at ASC, id ASC
                """, (rs, rowNum) -> new PersistedObservation(
                        rs.getTimestamp("observed_at").toInstant(),
                        rs.getBigDecimal("best_bid"), rs.getBigDecimal("best_ask"),
                        rs.getBigDecimal("spread_bps"),
                        rs.getBigDecimal("bid_depth"), rs.getBigDecimal("ask_depth"),
                        rs.getBigDecimal("depth_imbalance"),
                        rs.getBigDecimal("bid_wall_price"), rs.getBigDecimal("bid_wall_quantity"),
                        rs.getBigDecimal("ask_wall_price"), rs.getBigDecimal("ask_wall_quantity")),
                symbol, Timestamp.from(startInclusive), Timestamp.from(endInclusive));
    }

    public record PersistedObservation(
            Instant observedAt,
            BigDecimal bestBid,
            BigDecimal bestAsk,
            BigDecimal spreadBps,
            BigDecimal bidDepth,
            BigDecimal askDepth,
            BigDecimal depthImbalance,
            BigDecimal bidWallPrice,
            BigDecimal bidWallQuantity,
            BigDecimal askWallPrice,
            BigDecimal askWallQuantity) {}
}
