package com.crypto.debug.monitor.service;

import com.crypto.debug.monitor.domain.PriceMoveEvent;
import com.crypto.debug.monitor.domain.PriceMoveMonitorSettings;
import com.crypto.debug.monitor.dto.PriceMoveMonitorSettingsRequest;
import com.crypto.debug.monitor.repository.PriceMoveEventRepository;
import com.crypto.debug.monitor.repository.PriceMoveMonitorSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEBUG-ONLY market-move tracker.
 *
 * This service is deliberately a one-way observer of live prices. It never calls
 * AnalysisService, FinalDecisionService, ExecutionIntelligenceService, wallet,
 * position management, Profit Lock, stop-loss or take-profit logic.
 *
 * One row represents one completed rally/drop, not one websocket threshold tick.
 */
@Service
@RequiredArgsConstructor
public class PriceMoveMonitorService {

    private static final long SETTINGS_CACHE_MILLIS = 10_000L;

    private final PriceMoveMonitorSettingsRepository settingsRepository;
    private final PriceMoveEventRepository eventRepository;

    private final Map<String, SymbolTracker> trackers = new ConcurrentHashMap<>();
    private volatile PriceMoveMonitorSettings cachedSettings;
    private volatile long cachedSettingsAt;

    /**
     * Observe one live price. Recording is debug-only and has no path back into
     * trading decisions.
     */
    @Transactional
    public void onPrice(String rawSymbol, BigDecimal price, Instant observedAt) {
        if (rawSymbol == null || price == null || price.signum() <= 0 || observedAt == null) return;

        PriceMoveMonitorSettings settings = settings();
        if (!settings.isEnabled()) return;

        String symbol = rawSymbol.trim().toUpperCase(Locale.ROOT);
        SymbolTracker tracker = trackers.computeIfAbsent(symbol, ignored -> new SymbolTracker());

        synchronized (tracker) {
            if (tracker.cooldownUntil != null) {
                if (observedAt.isBefore(tracker.cooldownUntil)) return;
                tracker.resetWaiting(price, observedAt);
            }

            if (tracker.phase == Phase.WAITING) {
                observeWaiting(tracker, price, observedAt, settings);
                return;
            }

            if (tracker.phase == Phase.TRACKING_UP) {
                observeUp(tracker, symbol, price, observedAt, settings);
                return;
            }

            if (tracker.phase == Phase.TRACKING_DOWN) {
                observeDown(tracker, symbol, price, observedAt, settings);
            }
        }
    }

    private void observeWaiting(
            SymbolTracker tracker,
            BigDecimal price,
            Instant observedAt,
            PriceMoveMonitorSettings settings
    ) {
        if (tracker.lowPrice == null) {
            tracker.resetWaiting(price, observedAt);
            return;
        }

        if (price.compareTo(tracker.lowPrice) < 0) {
            tracker.lowPrice = price;
            tracker.lowTime = observedAt;
        }
        if (price.compareTo(tracker.highPrice) > 0) {
            tracker.highPrice = price;
            tracker.highTime = observedAt;
        }

        BigDecimal upMove = percentChange(tracker.lowPrice, price);
        BigDecimal downMove = percentChange(tracker.highPrice, price).abs();
        BigDecimal threshold = settings.getMinimumMovePercent();

        boolean upTriggered = upMove.compareTo(threshold) >= 0;
        boolean downTriggered = downMove.compareTo(threshold) >= 0;

        if (upTriggered && (!downTriggered || upMove.compareTo(downMove) >= 0)) {
            tracker.startUp(tracker.lowPrice, tracker.lowTime, price, observedAt);
        } else if (downTriggered) {
            tracker.startDown(tracker.highPrice, tracker.highTime, price, observedAt);
        }
    }

    private void observeUp(
            SymbolTracker tracker,
            String symbol,
            BigDecimal price,
            Instant observedAt,
            PriceMoveMonitorSettings settings
    ) {
        if (price.compareTo(tracker.extremePrice) > 0) {
            tracker.extremePrice = price;
            tracker.extremeTime = observedAt;
        }

        BigDecimal fullMove = tracker.extremePrice.subtract(tracker.startPrice);
        if (fullMove.signum() <= 0) return;

        BigDecimal retraced = tracker.extremePrice.subtract(price).max(BigDecimal.ZERO);
        BigDecimal retracementPercent = retraced
                .divide(fullMove, 12, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (canClose(tracker, observedAt, retracementPercent, settings)) {
            saveCompletedMove(symbol, "UP", tracker);
            tracker.startCooldown(price, observedAt, settings.getCooldownMinutes());
        }
    }

    private void observeDown(
            SymbolTracker tracker,
            String symbol,
            BigDecimal price,
            Instant observedAt,
            PriceMoveMonitorSettings settings
    ) {
        if (price.compareTo(tracker.extremePrice) < 0) {
            tracker.extremePrice = price;
            tracker.extremeTime = observedAt;
        }

        BigDecimal fullMove = tracker.startPrice.subtract(tracker.extremePrice);
        if (fullMove.signum() <= 0) return;

        BigDecimal retraced = price.subtract(tracker.extremePrice).max(BigDecimal.ZERO);
        BigDecimal retracementPercent = retraced
                .divide(fullMove, 12, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (canClose(tracker, observedAt, retracementPercent, settings)) {
            saveCompletedMove(symbol, "DOWN", tracker);
            tracker.startCooldown(price, observedAt, settings.getCooldownMinutes());
        }
    }

    private boolean canClose(
            SymbolTracker tracker,
            Instant observedAt,
            BigDecimal retracementPercent,
            PriceMoveMonitorSettings settings
    ) {
        long ageSeconds = Math.max(0L, Duration.between(tracker.startTime, observedAt).getSeconds());
        long minimumSeconds = settings.getMinimumDurationMinutes() * 60L;
        return ageSeconds >= minimumSeconds
                && retracementPercent.compareTo(settings.getRetracementClosePercent()) >= 0;
    }

    private void saveCompletedMove(String symbol, String direction, SymbolTracker tracker) {
        BigDecimal changePercent = percentChange(tracker.startPrice, tracker.extremePrice)
                .setScale(8, RoundingMode.HALF_UP);

        eventRepository.save(PriceMoveEvent.builder()
                .symbol(symbol)
                .direction(direction)
                .startTime(tracker.startTime)
                .endTime(tracker.extremeTime)
                .startPrice(tracker.startPrice)
                .endPrice(tracker.extremePrice)
                .changePercent(changePercent)
                .durationSeconds(Math.max(0L, Duration.between(tracker.startTime, tracker.extremeTime).getSeconds()))
                .reviewStatus("NEW")
                .build());
    }

    private BigDecimal percentChange(BigDecimal from, BigDecimal to) {
        if (from == null || to == null || from.signum() == 0) return BigDecimal.ZERO;
        return to.subtract(from)
                .divide(from, 12, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    @Transactional(readOnly = true)
    public List<PriceMoveEvent> recentEvents() {
        return eventRepository.findTop250ByOrderByEndTimeDesc();
    }

    @Transactional(readOnly = true)
    public PriceMoveMonitorSettings currentSettings() {
        return settingsRepository.findById(1L).orElseGet(this::defaults);
    }

    @Transactional
    public PriceMoveMonitorSettings updateSettings(PriceMoveMonitorSettingsRequest request) {
        if (request.minimumMovePercent() == null || request.minimumMovePercent().signum() <= 0) {
            throw new IllegalArgumentException("Minimum market move must be greater than 0%");
        }
        if (request.minimumDurationMinutes() < 0 || request.minimumDurationMinutes() > 1440) {
            throw new IllegalArgumentException("Minimum duration must be between 0 and 1440 minutes");
        }
        if (request.retracementClosePercent() == null
                || request.retracementClosePercent().compareTo(BigDecimal.ONE) < 0
                || request.retracementClosePercent().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Retracement to close must be between 1% and 100%");
        }
        if (request.cooldownMinutes() < 0 || request.cooldownMinutes() > 1440) {
            throw new IllegalArgumentException("Cooldown must be between 0 and 1440 minutes");
        }
        if (request.retentionDays() < 1 || request.retentionDays() > 365) {
            throw new IllegalArgumentException("History retention must be between 1 and 365 days");
        }

        PriceMoveMonitorSettings settings = settingsRepository.findById(1L).orElseGet(this::defaults);
        settings.setId(1L);
        settings.setEnabled(request.enabled());
        settings.setMinimumMovePercent(request.minimumMovePercent().setScale(6, RoundingMode.HALF_UP));
        settings.setMinimumDurationMinutes(request.minimumDurationMinutes());
        settings.setRetracementClosePercent(request.retracementClosePercent().setScale(6, RoundingMode.HALF_UP));
        settings.setCooldownMinutes(request.cooldownMinutes());
        settings.setRetentionDays(request.retentionDays());

        // V47 compatibility only. The tracker no longer uses this value.
        if (settings.getWindowMinutes() <= 0) settings.setWindowMinutes(30);

        PriceMoveMonitorSettings saved = settingsRepository.save(settings);
        cachedSettings = saved;
        cachedSettingsAt = System.currentTimeMillis();

        // Changing debug settings starts fresh tracking state. It does not touch AI/trading state.
        trackers.clear();
        eventRepository.deleteOlderThan(Instant.now().minus(Duration.ofDays(saved.getRetentionDays())));
        return saved;
    }

    @Transactional
    public PriceMoveEvent updateReviewStatus(Long id, String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!List.of("NEW", "REVIEWED", "IGNORED").contains(status)) {
            throw new IllegalArgumentException("Review status must be NEW, REVIEWED or IGNORED");
        }
        PriceMoveEvent event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Market move event was not found"));
        event.setReviewStatus(status);
        return eventRepository.save(event);
    }

    @Transactional
    public int cleanupExpired() {
        PriceMoveMonitorSettings settings = settings();
        return eventRepository.deleteOlderThan(Instant.now().minus(Duration.ofDays(settings.getRetentionDays())));
    }

    private PriceMoveMonitorSettings settings() {
        long now = System.currentTimeMillis();
        PriceMoveMonitorSettings local = cachedSettings;
        if (local == null || now - cachedSettingsAt > SETTINGS_CACHE_MILLIS) {
            local = settingsRepository.findById(1L).orElseGet(this::defaults);
            cachedSettings = local;
            cachedSettingsAt = now;
        }
        return local;
    }

    private PriceMoveMonitorSettings defaults() {
        return PriceMoveMonitorSettings.builder()
                .id(1L)
                .enabled(true)
                .minimumMovePercent(new BigDecimal("0.300000"))
                .windowMinutes(30) // legacy V47 column; intentionally unused
                .minimumDurationMinutes(1)
                .retracementClosePercent(new BigDecimal("30.000000"))
                .cooldownMinutes(10)
                .retentionDays(7)
                .build();
    }

    private enum Phase {
        WAITING,
        TRACKING_UP,
        TRACKING_DOWN
    }

    private static final class SymbolTracker {
        private Phase phase = Phase.WAITING;
        private BigDecimal lowPrice;
        private Instant lowTime;
        private BigDecimal highPrice;
        private Instant highTime;
        private BigDecimal startPrice;
        private Instant startTime;
        private BigDecimal extremePrice;
        private Instant extremeTime;
        private Instant cooldownUntil;

        private void resetWaiting(BigDecimal price, Instant time) {
            phase = Phase.WAITING;
            lowPrice = price;
            lowTime = time;
            highPrice = price;
            highTime = time;
            startPrice = null;
            startTime = null;
            extremePrice = null;
            extremeTime = null;
            cooldownUntil = null;
        }

        private void startUp(BigDecimal basePrice, Instant baseTime, BigDecimal price, Instant time) {
            phase = Phase.TRACKING_UP;
            startPrice = basePrice;
            startTime = baseTime;
            extremePrice = price;
            extremeTime = time;
        }

        private void startDown(BigDecimal basePrice, Instant baseTime, BigDecimal price, Instant time) {
            phase = Phase.TRACKING_DOWN;
            startPrice = basePrice;
            startTime = baseTime;
            extremePrice = price;
            extremeTime = time;
        }

        private void startCooldown(BigDecimal price, Instant time, int cooldownMinutes) {
            resetWaiting(price, time);
            cooldownUntil = time.plus(Duration.ofMinutes(Math.max(0, cooldownMinutes)));
        }
    }
}
