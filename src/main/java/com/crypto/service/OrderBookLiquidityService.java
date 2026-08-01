package com.crypto.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.crypto.client.binance.BinanceMarketDataClient;
import com.crypto.client.binance.dto.BinanceOrderBook;
import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.config.OrderBookProperties;
import com.crypto.config.OrderBookProperties.IntervalPolicy;
import com.crypto.domain.LiquidityContextStatus;
import com.crypto.domain.SignalDecision;
import com.crypto.dto.OrderBookLevel;
import com.crypto.dto.OrderBookLiquidityResult;
import com.crypto.dto.OrderBookSnapshot;

@Service
public class OrderBookLiquidityService {

    private static final Logger log = LoggerFactory.getLogger(OrderBookLiquidityService.class);
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final BinanceMarketDataClient marketDataClient;
    private final BinanceMarketDataProperties marketDataProperties;
    private final OrderBookProperties properties;
    private final Map<String, Deque<OrderBookSnapshot>> historyBySymbol = new ConcurrentHashMap<>();

    public OrderBookLiquidityService(
            BinanceMarketDataClient marketDataClient,
            BinanceMarketDataProperties marketDataProperties,
            OrderBookProperties properties
    ) {
        this.marketDataClient = marketDataClient;
        this.marketDataProperties = marketDataProperties;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${analysis.order-book.snapshot-interval-ms:5000}")
    public void collectConfiguredOrderBooks() {
        if (!properties.enabled() || !marketDataProperties.isEnabled()) {
            return;
        }
        marketDataProperties.getSymbols().stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .forEach(this::collectSafely);
    }

    /** Backward-compatible entry point used while building pre-strategy market context. */
    public OrderBookLiquidityResult evaluate(
            String symbol,
            SignalDecision currentDecision,
            boolean entryAllowed,
            BigDecimal currentPrice,
            BigDecimal stopLoss,
            BigDecimal takeProfit,
            Instant evaluatedAt
    ) {
        return evaluate(symbol, null, currentDecision, entryAllowed,
                currentPrice, stopLoss, takeProfit, evaluatedAt);
    }

    public OrderBookLiquidityResult evaluate(
            String symbol,
            String interval,
            SignalDecision currentDecision,
            boolean entryAllowed,
            BigDecimal currentPrice,
            BigDecimal stopLoss,
            BigDecimal takeProfit,
            Instant evaluatedAt
    ) {
        IntervalPolicy policy = properties.policyFor(interval == null ? "5m" : interval);
        Instant snapshotTime = evaluatedAt == null ? Instant.now() : evaluatedAt;
        if (!properties.enabled()) {
            return result(LiquidityContextStatus.DISABLED, currentDecision, currentDecision,
                    entryAllowed, null, null, null, null, null, null, null, null,
                    false, false, 0, "Order-book liquidity analysis is disabled.", snapshotTime,
                    policy, 0L);
        }

        String normalizedSymbol = normalize(symbol);
        Deque<OrderBookSnapshot> deque = historyBySymbol.get(normalizedSymbol);
        if (deque == null || deque.isEmpty()) {
            collectSafely(normalizedSymbol);
            deque = historyBySymbol.get(normalizedSymbol);
        }
        if (deque == null || deque.isEmpty()) {
            return result(LiquidityContextStatus.UNAVAILABLE, currentDecision, currentDecision,
                    entryAllowed, null, null, null, null, null, null, null, null,
                    false, false, 0, "No order-book snapshot is available yet.", snapshotTime,
                    policy, 0L);
        }

        List<OrderBookSnapshot> snapshots;
        synchronized (deque) {
            Instant windowStart = snapshotTime.minusSeconds(policy.windowSeconds());
            snapshots = deque.stream()
                    .filter(snapshot -> !snapshot.capturedAt().isAfter(snapshotTime))
                    .filter(snapshot -> !snapshot.capturedAt().isBefore(windowStart))
                    .toList();
        }
        if (snapshots.isEmpty()) {
            return result(LiquidityContextStatus.UNAVAILABLE, currentDecision, currentDecision,
                    entryAllowed, null, null, null, null, null, null, null, null,
                    false, false, 0,
                    "No order-book observation exists inside the " + policy.windowSeconds()
                            + " second window for interval " + interval + ".",
                    snapshotTime, policy, 0L);
        }

        OrderBookSnapshot latest = snapshots.get(snapshots.size() - 1);
        BigDecimal mid = midpoint(latest);
        if (mid == null || mid.signum() <= 0) {
            return result(LiquidityContextStatus.UNAVAILABLE, currentDecision, currentDecision,
                    entryAllowed, null, null, null, null, null, null, null, null,
                    false, false, snapshots.size(), "Order-book best bid/ask is unavailable.", snapshotTime,
                    policy, observedDurationSeconds(snapshots));
        }

        SnapshotMetrics latestMetrics = metrics(latest, mid);
        int observations = snapshots.size();
        long observedSeconds = observedDurationSeconds(snapshots);
        if (observations < policy.minimumObservations()) {
            return result(LiquidityContextStatus.LEARNING, currentDecision, currentDecision,
                    entryAllowed, latestMetrics.imbalance(), latestMetrics.bidDepth(), latestMetrics.askDepth(),
                    latestMetrics.spreadPercent(), latestMetrics.bidWallPrice(), latestMetrics.bidWallSize(),
                    latestMetrics.askWallPrice(), latestMetrics.askWallSize(), false, false, observations,
                    "Collecting " + interval + " liquidity observations (" + observations + "/"
                            + policy.minimumObservations() + ") inside a " + policy.windowSeconds()
                            + " second window. No liquidity veto was applied.", snapshotTime,
                    policy, observedSeconds);
        }

        PersistentWall bidWall = persistentWall(snapshots, true, policy);
        PersistentWall askWall = persistentWall(snapshots, false, policy);
        boolean targetBlocked = isBuy(currentDecision)
                && currentPrice != null
                && takeProfit != null
                && askWall != null
                && askWall.price().compareTo(currentPrice) > 0
                && askWall.price().compareTo(takeProfit) <= 0;
        boolean stopExposed = isBuy(currentDecision)
                && stopLoss != null
                && (bidWall == null || bidWall.price().compareTo(stopLoss) < 0);

        LiquidityContextStatus status = classify(latestMetrics.imbalance(), targetBlocked, stopExposed);
        boolean finalEntryAllowed = entryAllowed;
        SignalDecision finalDecision = currentDecision;
        String explanation = explanation(status, latestMetrics, bidWall, askWall,
                targetBlocked, stopExposed, interval, policy, observedSeconds);

        boolean strongBearishImbalance = latestMetrics.imbalance() != null
                && latestMetrics.imbalance().compareTo(properties.strongImbalance().negate()) <= 0;
        boolean strongConflict = targetBlocked || strongBearishImbalance;
        if (isBuy(currentDecision)
                && properties.vetoStrongConflict()
                && policy.allowVeto()
                && policy.influence().compareTo(new BigDecimal("0.50")) >= 0
                && strongConflict) {
            finalEntryAllowed = false;
            finalDecision = SignalDecision.WATCH;
            explanation += " The " + interval
                    + " policy permits a veto, so the long entry was downgraded to WATCH.";
        } else if (strongConflict && !policy.allowVeto()) {
            explanation += " This interval is informational only for liquidity; no entry veto was applied.";
        }

        return result(status, currentDecision, finalDecision, finalEntryAllowed,
                latestMetrics.imbalance(), latestMetrics.bidDepth(), latestMetrics.askDepth(),
                latestMetrics.spreadPercent(), bidWall == null ? null : bidWall.price(),
                bidWall == null ? null : bidWall.size(), askWall == null ? null : askWall.price(),
                askWall == null ? null : askWall.size(), targetBlocked, stopExposed,
                observations, explanation, snapshotTime, policy,
                Math.max(bidWall == null ? 0L : bidWall.persistenceSeconds(),
                        askWall == null ? 0L : askWall.persistenceSeconds()));
    }

    private void collectSafely(String symbol) {
        try {
            BinanceOrderBook raw = marketDataClient.getOrderBook(symbol, properties.depthLimit());
            OrderBookSnapshot snapshot = new OrderBookSnapshot(
                    symbol,
                    Instant.now(),
                    raw.lastUpdateId(),
                    mapLevels(raw.bids()),
                    mapLevels(raw.asks())
            );
            Deque<OrderBookSnapshot> deque = historyBySymbol.computeIfAbsent(symbol, key -> new ArrayDeque<>());
            synchronized (deque) {
                deque.addLast(snapshot);
                Instant oldestAllowed = snapshot.capturedAt()
                        .minusSeconds(properties.maximumWindowSeconds() + 60L);
                while (!deque.isEmpty() && (deque.peekFirst().capturedAt().isBefore(oldestAllowed)
                        || deque.size() > properties.historySize())) {
                    deque.removeFirst();
                }
            }
        } catch (Exception exception) {
            log.warn("Unable to collect order book for {}: {}", symbol, exception.getMessage());
        }
    }

    private List<OrderBookLevel> mapLevels(List<List<String>> rows) {
        if (rows == null) return List.of();
        List<OrderBookLevel> levels = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            if (row == null || row.size() < 2) continue;
            BigDecimal price = new BigDecimal(row.get(0));
            BigDecimal quantity = new BigDecimal(row.get(1));
            if (price.signum() > 0 && quantity.signum() > 0) {
                levels.add(new OrderBookLevel(price, quantity));
            }
        }
        return levels;
    }

    private SnapshotMetrics metrics(OrderBookSnapshot snapshot, BigDecimal mid) {
        BigDecimal range = properties.rangePercent().divide(ONE_HUNDRED, MC);
        BigDecimal lower = mid.multiply(BigDecimal.ONE.subtract(range), MC);
        BigDecimal upper = mid.multiply(BigDecimal.ONE.add(range), MC);
        List<OrderBookLevel> bids = snapshot.bids().stream()
                .filter(level -> level.price().compareTo(lower) >= 0).toList();
        List<OrderBookLevel> asks = snapshot.asks().stream()
                .filter(level -> level.price().compareTo(upper) <= 0).toList();
        BigDecimal bidDepth = sumNotional(bids);
        BigDecimal askDepth = sumNotional(asks);
        BigDecimal total = bidDepth.add(askDepth);
        BigDecimal imbalance = total.signum() == 0 ? BigDecimal.ZERO
                : bidDepth.subtract(askDepth).divide(total, 8, RoundingMode.HALF_UP);
        OrderBookLevel bidWall = wall(bids);
        OrderBookLevel askWall = wall(asks);
        return new SnapshotMetrics(bidDepth, askDepth, imbalance, spreadPercent(snapshot),
                bidWall == null ? null : bidWall.price(), bidWall == null ? null : bidWall.notional(),
                askWall == null ? null : askWall.price(), askWall == null ? null : askWall.notional());
    }

    private PersistentWall persistentWall(
            List<OrderBookSnapshot> snapshots,
            boolean bid,
            IntervalPolicy policy
    ) {
        List<WallObservation> candidates = snapshots.stream()
                .map(snapshot -> {
                    BigDecimal mid = midpoint(snapshot);
                    if (mid == null) return null;
                    SnapshotMetrics metrics = metrics(snapshot, mid);
                    BigDecimal price = bid ? metrics.bidWallPrice() : metrics.askWallPrice();
                    BigDecimal size = bid ? metrics.bidWallSize() : metrics.askWallSize();
                    return price == null ? null : new WallObservation(snapshot.capturedAt(), price, size);
                })
                .filter(observation -> observation != null)
                .toList();
        if (candidates.size() < policy.minimumObservations()) return null;

        WallObservation latest = candidates.get(candidates.size() - 1);
        List<WallObservation> matching = candidates.stream()
                .filter(observation -> percentDistance(observation.price(), latest.price())
                        .compareTo(properties.wallPriceTolerancePercent()) <= 0)
                .toList();
        if (matching.size() < policy.minimumObservations()) return null;
        long persistenceSeconds = Duration.between(
                matching.get(0).capturedAt(), matching.get(matching.size() - 1).capturedAt()).toSeconds();
        if (persistenceSeconds < policy.minimumWallPersistenceSeconds()) return null;
        BigDecimal avgSize = matching.stream().map(WallObservation::size)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(matching.size()), MC);
        return new PersistentWall(latest.price(), avgSize, matching.size(), persistenceSeconds);
    }

    private OrderBookLevel wall(List<OrderBookLevel> levels) {
        if (levels.isEmpty()) return null;
        BigDecimal average = sumNotional(levels).divide(BigDecimal.valueOf(levels.size()), MC);
        BigDecimal threshold = average.multiply(properties.wallSizeMultiplier(), MC);
        return levels.stream().filter(level -> level.notional().compareTo(threshold) >= 0)
                .max(Comparator.comparing(OrderBookLevel::notional)).orElse(null);
    }

    private LiquidityContextStatus classify(BigDecimal imbalance, boolean targetBlocked, boolean stopExposed) {
        if (targetBlocked) return LiquidityContextStatus.TARGET_BLOCKED;
        if (imbalance.compareTo(properties.strongImbalance().negate()) <= 0) {
            return LiquidityContextStatus.BEARISH_PRESSURE;
        }
        if (imbalance.compareTo(properties.moderateImbalance()) >= 0) {
            return LiquidityContextStatus.BULLISH_SUPPORT;
        }
        if (stopExposed) return LiquidityContextStatus.STOP_EXPOSED;
        return LiquidityContextStatus.BALANCED;
    }

    private String explanation(
            LiquidityContextStatus status,
            SnapshotMetrics metrics,
            PersistentWall bidWall,
            PersistentWall askWall,
            boolean targetBlocked,
            boolean stopExposed,
            String interval,
            IntervalPolicy policy,
            long observedSeconds
    ) {
        StringBuilder text = new StringBuilder("Order-book status ").append(status)
                .append(" for ").append(interval).append(" using a ")
                .append(policy.windowSeconds()).append(" second window, influence ")
                .append(policy.influence()).append(", veto ")
                .append(policy.allowVeto() ? "enabled" : "disabled")
                .append(". Observed ").append(observedSeconds).append(" seconds. Imbalance ")
                .append(metrics.imbalance().setScale(3, RoundingMode.HALF_UP)).append(".");
        if (askWall != null) {
            text.append(" Persistent ask wall at ").append(askWall.price())
                    .append(" persisted ").append(askWall.persistenceSeconds()).append(" seconds.");
        }
        if (bidWall != null) {
            text.append(" Persistent bid wall at ").append(bidWall.price())
                    .append(" persisted ").append(bidWall.persistenceSeconds()).append(" seconds.");
        }
        if (targetBlocked) text.append(" The ask wall is positioned before the proposed take-profit.");
        if (stopExposed) text.append(" No persistent bid support was detected above the stop-loss.");
        return text.toString();
    }

    private BigDecimal midpoint(OrderBookSnapshot snapshot) {
        if (snapshot.bids().isEmpty() || snapshot.asks().isEmpty()) return null;
        return snapshot.bids().get(0).price().add(snapshot.asks().get(0).price())
                .divide(BigDecimal.valueOf(2), MC);
    }

    private BigDecimal spreadPercent(OrderBookSnapshot snapshot) {
        if (snapshot.bids().isEmpty() || snapshot.asks().isEmpty()) return null;
        BigDecimal bid = snapshot.bids().get(0).price();
        BigDecimal ask = snapshot.asks().get(0).price();
        BigDecimal mid = bid.add(ask).divide(BigDecimal.valueOf(2), MC);
        return ask.subtract(bid).divide(mid, 10, RoundingMode.HALF_UP).multiply(ONE_HUNDRED);
    }

    private BigDecimal sumNotional(List<OrderBookLevel> levels) {
        return levels.stream().map(OrderBookLevel::notional).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal percentDistance(BigDecimal left, BigDecimal right) {
        if (right == null || right.signum() == 0) return ONE_HUNDRED;
        return left.subtract(right).abs().divide(right, 8, RoundingMode.HALF_UP).multiply(ONE_HUNDRED);
    }

    private boolean isBuy(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase();
    }

    private long observedDurationSeconds(List<OrderBookSnapshot> snapshots) {
        if (snapshots.size() < 2) return 0L;
        return Math.max(0L, Duration.between(
                snapshots.get(0).capturedAt(), snapshots.get(snapshots.size() - 1).capturedAt()).toSeconds());
    }

    private OrderBookLiquidityResult result(
            LiquidityContextStatus status,
            SignalDecision originalDecision,
            SignalDecision finalDecision,
            boolean entryAllowed,
            BigDecimal imbalance,
            BigDecimal bidDepth,
            BigDecimal askDepth,
            BigDecimal spreadPercent,
            BigDecimal bidWallPrice,
            BigDecimal bidWallSize,
            BigDecimal askWallPrice,
            BigDecimal askWallSize,
            boolean targetBlocked,
            boolean stopExposed,
            int observations,
            String explanation,
            Instant evaluatedAt,
            IntervalPolicy policy,
            long wallPersistenceSeconds
    ) {
        return new OrderBookLiquidityResult(status, originalDecision, finalDecision, entryAllowed,
                imbalance, bidDepth, askDepth, spreadPercent, bidWallPrice, bidWallSize,
                askWallPrice, askWallSize, targetBlocked, stopExposed, observations,
                explanation, evaluatedAt, policy.windowSeconds(), wallPersistenceSeconds,
                policy.influence(), policy.allowVeto());
    }

    private record SnapshotMetrics(
            BigDecimal bidDepth,
            BigDecimal askDepth,
            BigDecimal imbalance,
            BigDecimal spreadPercent,
            BigDecimal bidWallPrice,
            BigDecimal bidWallSize,
            BigDecimal askWallPrice,
            BigDecimal askWallSize
    ) {}

    private record WallObservation(Instant capturedAt, BigDecimal price, BigDecimal size) {}
    private record PersistentWall(BigDecimal price, BigDecimal size, int observations, long persistenceSeconds) {}
}
