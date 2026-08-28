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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.crypto.administration.service.CoinConfigurationService;
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
import com.crypto.market.service.OrderBookSnapshotService;

@Service
public class OrderBookLiquidityService {

    private static final Logger log = LoggerFactory.getLogger(OrderBookLiquidityService.class);
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final BinanceMarketDataClient marketDataClient;
    private final BinanceMarketDataProperties marketDataProperties;
    private final OrderBookProperties properties;
    private final CoinConfigurationService coinConfigurationService;
    private final OrderBookSnapshotService orderBookSnapshotService;
    private final Executor collectionExecutor;
    private final Map<String, Deque<OrderBookSnapshot>> historyBySymbol = new ConcurrentHashMap<>();

    // FIX-11E: shared single-flight guard protects scheduled and live fallback collection.
    // Golden rule: Replay = Production. Acquisition concurrency changes here; the shared
    // normalized Order Book evaluation consumed by Production and Replay does not.
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public OrderBookLiquidityService(
            BinanceMarketDataClient marketDataClient,
            BinanceMarketDataProperties marketDataProperties,
            OrderBookProperties properties,
            CoinConfigurationService coinConfigurationService,
            OrderBookSnapshotService orderBookSnapshotService,
            @Qualifier("orderBookCollectionExecutor") Executor collectionExecutor
    ) {
        this.marketDataClient = marketDataClient;
        this.marketDataProperties = marketDataProperties;
        this.properties = properties;
        this.coinConfigurationService = coinConfigurationService;
        this.orderBookSnapshotService = orderBookSnapshotService;
        this.collectionExecutor = collectionExecutor;
    }

    @Scheduled(fixedDelayString = "${analysis.order-book.snapshot-interval-ms:5000}")
    public void collectConfiguredOrderBooks() {
        if (!properties.enabled() || !marketDataProperties.isEnabled()) {
            return;
        }
        coinConfigurationService.enabledSymbols().stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(String::toUpperCase)
                .distinct()
                // FIX-11E: dispatch symbols independently; one slow Binance request must not serialize the sweep.
                .forEach(symbol -> {
                    try {
                        collectionExecutor.execute(() -> collectSafely(symbol));
                    } catch (RejectedExecutionException exception) {
                        // Do not run Binance network work on the scheduler thread under saturation.
                        log.warn("FIX-11E Order Book collection rejected because executor is saturated: symbol={}", symbol);
                    }
                });
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

    /**
     * FIX-112C historical replay path. Replay reads persisted Production Order Book
     * observations strictly as-of the historical evaluation timestamp and routes them
     * through the same normalized evaluator used by live Production. Pre-V74 windows
     * with no persisted evidence remain explicitly UNAVAILABLE.
     */
    public OrderBookLiquidityResult evaluateHistorical(
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

        // FIX-112C: historical Replay reads only persisted Production observations
        // whose observed_at is inside the exact as-of window. No live Binance book
        // and no future observation can enter a historical decision.
        Instant windowStart = snapshotTime.minusSeconds(policy.windowSeconds());
        List<NormalizedObservation> observations = orderBookSnapshotService
                .find(symbol, windowStart, snapshotTime).stream()
                .map(row -> new NormalizedObservation(row.observedAt(),
                        // FIX-112C parity preservation: an invalid persisted snapshot must remain
                        // in the observation sequence. Pre-FIX-112 Production counted every snapshot
                        // in the active window and, critically, returned UNAVAILABLE when the latest
                        // snapshot itself had no valid midpoint. Dropping invalid rows here would
                        // silently change that established Production behavior during Replay.
                        row.bestBid() == null || row.bestAsk() == null
                                || row.bestBid().add(row.bestAsk()).signum() <= 0
                                ? null
                                : new SnapshotMetrics(
                                        row.bidDepth(), row.askDepth(), row.depthImbalance(),
                                        row.spreadBps() == null ? null : row.spreadBps().divide(ONE_HUNDRED, MC),
                                        row.bidWallPrice(), row.bidWallQuantity(),
                                        row.askWallPrice(), row.askWallQuantity())))
                .toList();
        if (observations.isEmpty()) {
            return result(LiquidityContextStatus.UNAVAILABLE, currentDecision, currentDecision,
                    entryAllowed, null, null, null, null, null, null, null, null,
                    false, false, 0,
                    "No persisted Production order-book observation exists for this historical replay window; live data was not substituted.",
                    snapshotTime, policy, 0L);
        }
        return evaluateNormalized(symbol, interval, currentDecision, entryAllowed,
                currentPrice, stopLoss, takeProfit, snapshotTime, policy, observations);
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
            // FIX-091 / Fix 2: in LIVE analysis, zero observations are not proof that liquidity is safe.
            // Hold a fresh entry until the configured sample minimum is reached. Historical replay uses
            // evaluateHistorical(...) and deliberately keeps its separate UNAVAILABLE limitation.
            return result(LiquidityContextStatus.INSUFFICIENT_DATA_HOLD, currentDecision, currentDecision,
                    false, null, null, null, null, null, null, null, null,
                    false, false, 0, "Live order-book sampling is not ready (0/" + policy.minimumObservations()
                            + "). Fresh entry is held until enough observations are collected.", snapshotTime,
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
            // FIX-091 / Fix 2: an enabled LIVE order-book service with no observation in the active
            // window is an insufficient-sampling condition, not a neutral PASS.
            return result(LiquidityContextStatus.INSUFFICIENT_DATA_HOLD, currentDecision, currentDecision,
                    false, null, null, null, null, null, null, null, null,
                    false, false, 0,
                    "No live order-book observation exists inside the " + policy.windowSeconds()
                            + " second window for interval " + interval + "; fresh entry is held until "
                            + policy.minimumObservations() + " observations are available.",
                    snapshotTime, policy, 0L);
        }

        // FIX-112C parity preservation: keep every live snapshot in sequence and let the
        // shared evaluator enforce the exact pre-refactor latest-snapshot validity gate.
        // Do not filter individual invalid snapshots here; doing so changed live Production
        // semantics by allowing an older valid snapshot to replace an invalid latest reading.
        List<NormalizedObservation> observations = snapshots.stream()
                .map(snapshot -> {
                    BigDecimal mid = midpoint(snapshot);
                    return new NormalizedObservation(snapshot.capturedAt(),
                            mid == null || mid.signum() <= 0 ? null : metrics(snapshot, mid));
                })
                .toList();
        return evaluateNormalized(symbol, interval, currentDecision, entryAllowed,
                currentPrice, stopLoss, takeProfit, snapshotTime, policy, observations);
    }

    /**
     * FIX-112C shared decision path. Production normalizes live snapshots into this
     * representation; Replay loads the same normalized observations from MySQL.
     * All status/veto/wall-persistence logic below is therefore identical.
     */
    private OrderBookLiquidityResult evaluateNormalized(
            String symbol, String interval, SignalDecision currentDecision, boolean entryAllowed,
            BigDecimal currentPrice, BigDecimal stopLoss, BigDecimal takeProfit,
            Instant snapshotTime, IntervalPolicy policy,
            List<NormalizedObservation> observations) {
        int observationCount = observations.size();
        long observedSeconds = observedDurationSecondsNormalized(observations);

        // FIX-112C PRODUCTION-BEHAVIOR PRESERVATION:
        // Before the shared-evaluator refactor, live Production inspected ONLY the latest
        // snapshot's midpoint first. If that latest reading was invalid, the entire Order Book
        // evaluation returned UNAVAILABLE even when older snapshots in the window were valid.
        // Keep that exact gate explicitly in the shared path so Replay conforms to Production;
        // tolerating/filtering an invalid latest snapshot is a separate trading-behavior change
        // and must never ride along inside this parity/infrastructure fix.
        SnapshotMetrics latestMetrics = observations.get(observationCount - 1).metrics();
        if (latestMetrics == null) {
            return result(LiquidityContextStatus.UNAVAILABLE, currentDecision, currentDecision,
                    entryAllowed, null, null, null, null, null, null, null, null,
                    false, false, observationCount, "Order-book best bid/ask is unavailable.",
                    snapshotTime, policy, observedSeconds);
        }

        if (observationCount < policy.minimumObservations()) {
            // FIX-112C exact parity: once historical observations exist, partial sampling
            // must reproduce Production's INSUFFICIENT_DATA_HOLD rather than becoming a
            // Replay-only UNAVAILABLE pass. Only a completely absent historical window is
            // reported UNAVAILABLE by evaluateHistorical().
            return result(LiquidityContextStatus.INSUFFICIENT_DATA_HOLD,
                    currentDecision, currentDecision, false,
                    latestMetrics.imbalance(), latestMetrics.bidDepth(), latestMetrics.askDepth(),
                    latestMetrics.spreadPercent(), latestMetrics.bidWallPrice(), latestMetrics.bidWallSize(),
                    latestMetrics.askWallPrice(), latestMetrics.askWallSize(), false, false,
                    observationCount,
                    "Collecting " + interval + " liquidity observations (" + observationCount + "/"
                            + policy.minimumObservations() + ") inside a " + policy.windowSeconds()
                            + " second window. Fresh entry is held until the minimum sample is ready.",
                    snapshotTime, policy, observedSeconds);
        }

        PersistentWall bidWall = persistentWallNormalized(observations, true, policy);
        PersistentWall askWall = persistentWallNormalized(observations, false, policy);
        boolean targetBlocked = isBuy(currentDecision)
                && currentPrice != null && takeProfit != null && askWall != null
                && askWall.price().compareTo(currentPrice) > 0
                && askWall.price().compareTo(takeProfit) <= 0;
        boolean stopExposed = isBuy(currentDecision)
                && stopLoss != null && (bidWall == null || bidWall.price().compareTo(stopLoss) < 0);

        WallLifecycle askWallLifecycle = wallLifecycle(askWall, currentPrice, latestMetrics.imbalance(), policy);
        LiquidityContextStatus status = classify(latestMetrics.imbalance(), targetBlocked, stopExposed, askWallLifecycle);
        boolean finalEntryAllowed = entryAllowed;
        SignalDecision finalDecision = currentDecision;
        String explanation = explanation(status, latestMetrics, bidWall, askWall,
                targetBlocked, stopExposed, interval, policy, observedSeconds, askWallLifecycle);

        boolean strongBearishImbalance = latestMetrics.imbalance() != null
                && latestMetrics.imbalance().compareTo(properties.strongImbalance().negate()) <= 0;
        boolean ultraCloseWallBeingConsumed = targetBlocked
                && askWallLifecycle != null
                && askWallLifecycle.distancePercent().compareTo(new BigDecimal("0.10")) <= 0
                && askWallLifecycle.sizeChangePercent().compareTo(new BigDecimal("-10.0")) <= 0;
        boolean hardTargetBlock = targetBlocked
                && askWallLifecycle != null
                && askWallLifecycle.strengthScore() >= 70
                && askWallLifecycle.trend() != WallTrend.WEAKENING
                && !ultraCloseWallBeingConsumed;
        boolean strongConflict = hardTargetBlock || strongBearishImbalance;
        if (isBuy(currentDecision)
                && properties.vetoStrongConflict()
                && policy.allowVeto()
                && policy.influence().compareTo(new BigDecimal("0.50")) >= 0
                && strongConflict) {
            finalEntryAllowed = false;
            finalDecision = SignalDecision.WATCH;
            explanation += " The " + interval
                    + " policy permits a hard veto because liquidity pressure is strong enough to matter now; the long entry was downgraded to WATCH.";
        } else if (targetBlocked && !hardTargetBlock) {
            if (ultraCloseWallBeingConsumed) {
                explanation += " The target-side wall is ultra-close but is already shrinking materially, so it is treated as potentially consumable breakout liquidity rather than an automatic hard veto. It remains negative execution evidence and fresh confirmation is still required.";
            } else {
                explanation += " The wall is relevant to the target but is not strong/stable enough for a hard veto. It remains execution evidence and may reduce confidence while fresh signals continue to be evaluated.";
            }
        } else if (strongConflict && !policy.allowVeto()) {
            explanation += " This interval is informational only for liquidity; no entry veto was applied.";
        }

        return result(status, currentDecision, finalDecision, finalEntryAllowed,
                latestMetrics.imbalance(), latestMetrics.bidDepth(), latestMetrics.askDepth(),
                latestMetrics.spreadPercent(), bidWall == null ? null : bidWall.price(),
                bidWall == null ? null : bidWall.size(), askWall == null ? null : askWall.price(),
                askWall == null ? null : askWall.size(), targetBlocked, stopExposed,
                observationCount, explanation, snapshotTime, policy,
                Math.max(bidWall == null ? 0L : bidWall.persistenceSeconds(),
                        askWall == null ? 0L : askWall.persistenceSeconds()));
    }

    private PersistentWall persistentWallNormalized(List<NormalizedObservation> observations, boolean bid, IntervalPolicy policy) {
        List<WallObservation> candidates = observations.stream()
                .map(observation -> {
                    SnapshotMetrics metrics = observation.metrics();
                    // Pre-FIX-112 persistentWall(...) skipped an individual older snapshot when
                    // its midpoint was invalid. Preserve that behavior while the latest-snapshot
                    // validity gate above remains authoritative for the overall evaluation.
                    if (metrics == null) return null;
                    BigDecimal price = bid ? metrics.bidWallPrice() : metrics.askWallPrice();
                    BigDecimal size = bid ? metrics.bidWallSize() : metrics.askWallSize();
                    return price == null ? null : new WallObservation(observation.capturedAt(), price, size);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        if (candidates.size() < policy.minimumObservations()) return null;
        WallObservation latest = candidates.get(candidates.size() - 1);
        List<WallObservation> matching = candidates.stream()
                .filter(observation -> percentDistance(observation.price(), latest.price())
                        .compareTo(properties.wallPriceTolerancePercent()) <= 0)
                .toList();
        if (matching.size() < policy.minimumObservations()) return null;
        long persistenceSeconds = Duration.between(matching.get(0).capturedAt(), matching.get(matching.size() - 1).capturedAt()).toSeconds();
        if (persistenceSeconds < policy.minimumWallPersistenceSeconds()) return null;
        BigDecimal avgSize = matching.stream().map(WallObservation::size).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(matching.size()), MC);
        BigDecimal firstSize = matching.get(0).size();
        BigDecimal latestSize = matching.get(matching.size() - 1).size();
        BigDecimal sizeChangePercent = firstSize == null || firstSize.signum() == 0 ? BigDecimal.ZERO
                : latestSize.subtract(firstSize).divide(firstSize, 8, RoundingMode.HALF_UP).multiply(ONE_HUNDRED);
        return new PersistentWall(latest.price(), avgSize, latestSize, firstSize, sizeChangePercent,
                matching.size(), persistenceSeconds);
    }

    private long observedDurationSecondsNormalized(List<NormalizedObservation> observations) {
        if (observations == null || observations.size() < 2) return 0L;
        return Math.max(0L, Duration.between(observations.get(0).capturedAt(),
                observations.get(observations.size() - 1).capturedAt()).toSeconds());
    }

    private void collectSafely(String symbol) {
        // FIX-11E: guard the actual collection boundary because evaluate() also calls this method directly.
        if (!inFlight.add(symbol)) {
            log.debug("FIX-11E Order Book collection already in-flight: symbol={}", symbol);
            return;
        }
        long collectionStartedNanos = System.nanoTime();
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

            // FIX-112C: persist EVERY collected snapshot, including a snapshot whose latest
            // bid/ask is invalid. That invalid observation is trading-relevant evidence because
            // established Production behavior returns UNAVAILABLE when the latest midpoint cannot
            // be formed. Replay cannot reproduce that behavior if invalid snapshots disappear
            // during persistence.
            BigDecimal mid = midpoint(snapshot);
            SnapshotMetrics m = mid == null || mid.signum() <= 0 ? null : metrics(snapshot, mid);
            int latencyMs = (int) Math.min(Integer.MAX_VALUE,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - collectionStartedNanos));
            orderBookSnapshotService.recordAsync(symbol, snapshot.capturedAt(),
                    snapshot.bids().isEmpty() ? null : snapshot.bids().get(0).price(),
                    snapshot.asks().isEmpty() ? null : snapshot.asks().get(0).price(),
                    m == null ? null : m.spreadPercent(),
                    m == null ? null : m.bidDepth(),
                    m == null ? null : m.askDepth(),
                    m == null ? null : m.imbalance(),
                    m == null ? null : m.bidWallPrice(),
                    m == null ? null : m.bidWallSize(),
                    m == null ? null : m.askWallPrice(),
                    m == null ? null : m.askWallSize(), latencyMs);
        } catch (Exception exception) {
            log.warn("Unable to collect order book for {}: {}", symbol, exception.getMessage());
        } finally {
            // Always release on success/failure so a symbol cannot become permanently stuck in-flight.
            inFlight.remove(symbol);
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
        BigDecimal firstSize = matching.get(0).size();
        BigDecimal latestSize = matching.get(matching.size() - 1).size();
        BigDecimal sizeChangePercent = firstSize == null || firstSize.signum() == 0
                ? BigDecimal.ZERO
                : latestSize.subtract(firstSize)
                        .divide(firstSize, 8, RoundingMode.HALF_UP)
                        .multiply(ONE_HUNDRED);
        return new PersistentWall(latest.price(), avgSize, latestSize, firstSize, sizeChangePercent,
                matching.size(), persistenceSeconds);
    }


    private WallLifecycle wallLifecycle(
            PersistentWall wall,
            BigDecimal currentPrice,
            BigDecimal imbalance,
            IntervalPolicy policy
    ) {
        if (wall == null || currentPrice == null || currentPrice.signum() <= 0) return null;

        BigDecimal change = wall.sizeChangePercent() == null ? BigDecimal.ZERO : wall.sizeChangePercent();
        WallTrend trend = change.compareTo(new BigDecimal("-20")) <= 0
                ? WallTrend.WEAKENING
                : change.compareTo(new BigDecimal("20")) >= 0 ? WallTrend.GROWING : WallTrend.STABLE;

        BigDecimal distance = percentDistance(wall.price(), currentPrice);
        int persistenceScore;
        long minimumPersistence = Math.max(1L, policy.minimumWallPersistenceSeconds());
        double persistenceRatio = Math.min(2.0, wall.persistenceSeconds() / (double) minimumPersistence);
        persistenceScore = (int) Math.round(35.0 * (persistenceRatio / 2.0));

        int trendScore = switch (trend) {
            case GROWING -> 30;
            case STABLE -> 20;
            case WEAKENING -> 5;
        };

        int proximityScore = distance.compareTo(new BigDecimal("0.10")) <= 0 ? 25
                : distance.compareTo(new BigDecimal("0.25")) <= 0 ? 20
                : distance.compareTo(new BigDecimal("0.50")) <= 0 ? 10 : 5;
        int imbalanceScore = imbalance != null && imbalance.compareTo(BigDecimal.ZERO) < 0 ? 10 : 0;
        int strength = Math.max(0, Math.min(100, persistenceScore + trendScore + proximityScore + imbalanceScore));

        return new WallLifecycle(trend, strength, change, distance);
    }

    private OrderBookLevel wall(List<OrderBookLevel> levels) {
        if (levels.isEmpty()) return null;
        BigDecimal average = sumNotional(levels).divide(BigDecimal.valueOf(levels.size()), MC);
        BigDecimal threshold = average.multiply(properties.wallSizeMultiplier(), MC);
        return levels.stream().filter(level -> level.notional().compareTo(threshold) >= 0)
                .max(Comparator.comparing(OrderBookLevel::notional)).orElse(null);
    }

    private LiquidityContextStatus classify(
            BigDecimal imbalance,
            boolean targetBlocked,
            boolean stopExposed,
            WallLifecycle askWallLifecycle
    ) {
        if (targetBlocked) {
            if (askWallLifecycle != null && askWallLifecycle.trend() == WallTrend.WEAKENING) {
                return LiquidityContextStatus.WALL_WEAKENING;
            }
            return LiquidityContextStatus.TARGET_BLOCKED;
        }
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
            long observedSeconds,
            WallLifecycle askWallLifecycle
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
            if (askWallLifecycle != null) {
                text.append(" Wall lifecycle=").append(askWallLifecycle.trend())
                        .append(", strength=").append(askWallLifecycle.strengthScore()).append("/100")
                        .append(", size change=").append(askWallLifecycle.sizeChangePercent().setScale(1, RoundingMode.HALF_UP)).append("%")
                        .append(", distance=").append(askWallLifecycle.distancePercent().setScale(3, RoundingMode.HALF_UP)).append("%.");
            }
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

    private record NormalizedObservation(Instant capturedAt, SnapshotMetrics metrics) {}

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
    private enum WallTrend { GROWING, STABLE, WEAKENING }
    private record WallLifecycle(WallTrend trend, int strengthScore, BigDecimal sizeChangePercent, BigDecimal distancePercent) {}
    private record PersistentWall(
            BigDecimal price,
            BigDecimal size,
            BigDecimal latestSize,
            BigDecimal firstSize,
            BigDecimal sizeChangePercent,
            int observations,
            long persistenceSeconds
    ) {}
}
