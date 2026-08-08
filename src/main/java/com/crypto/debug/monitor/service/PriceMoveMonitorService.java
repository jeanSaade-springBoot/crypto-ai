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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PriceMoveMonitorService {

    private static final long SETTINGS_CACHE_MILLIS = 10_000L;

    private final PriceMoveMonitorSettingsRepository settingsRepository;
    private final PriceMoveEventRepository eventRepository;

    private final Map<String, Deque<PricePoint>> points = new ConcurrentHashMap<>();
    private volatile PriceMoveMonitorSettings cachedSettings;
    private volatile long cachedSettingsAt;

    /**
     * DEBUG-ONLY observer. This method only records price movement and never calls
     * signal, execution, wallet, position-management, or risk services.
     */
    @Transactional
    public void onPrice(String rawSymbol, BigDecimal price, Instant observedAt) {
        if (rawSymbol == null || price == null || price.signum() <= 0 || observedAt == null) return;
        PriceMoveMonitorSettings settings = settings();
        if (!settings.isEnabled()) return;

        String symbol = rawSymbol.trim().toUpperCase(Locale.ROOT);
        Deque<PricePoint> deque = points.computeIfAbsent(symbol, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            Instant cutoff = observedAt.minus(Duration.ofMinutes(settings.getWindowMinutes()));
            while (!deque.isEmpty() && deque.peekFirst().time().isBefore(cutoff)) {
                deque.removeFirst();
            }
            if (deque.isEmpty()) {
                deque.addLast(new PricePoint(observedAt, price));
                return;
            }

            PricePoint base = deque.peekFirst();
            BigDecimal changePercent = price.subtract(base.price())
                    .divide(base.price(), 12, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            if (changePercent.abs().compareTo(settings.getMinimumMovePercent()) >= 0) {
                eventRepository.save(PriceMoveEvent.builder()
                        .symbol(symbol)
                        .direction(changePercent.signum() >= 0 ? "UP" : "DOWN")
                        .startTime(base.time())
                        .endTime(observedAt)
                        .startPrice(base.price())
                        .endPrice(price)
                        .changePercent(changePercent.setScale(8, RoundingMode.HALF_UP))
                        .durationSeconds(Math.max(0L, Duration.between(base.time(), observedAt).getSeconds()))
                        .reviewStatus("NEW")
                        .build());

                // Rebase after a detected move. This prevents one rally/drop from creating
                // hundreds of duplicate rows on every websocket tick.
                deque.clear();
                deque.addLast(new PricePoint(observedAt, price));
            } else {
                deque.addLast(new PricePoint(observedAt, price));
            }
        }
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
            throw new IllegalArgumentException("Minimum price move must be greater than 0%");
        }
        if (request.windowMinutes() < 1 || request.windowMinutes() > 1440) {
            throw new IllegalArgumentException("Monitoring window must be between 1 and 1440 minutes");
        }
        if (request.retentionDays() < 1 || request.retentionDays() > 365) {
            throw new IllegalArgumentException("History retention must be between 1 and 365 days");
        }
        PriceMoveMonitorSettings settings = settingsRepository.findById(1L).orElseGet(this::defaults);
        settings.setId(1L);
        settings.setEnabled(request.enabled());
        settings.setMinimumMovePercent(request.minimumMovePercent().setScale(6, RoundingMode.HALF_UP));
        settings.setWindowMinutes(request.windowMinutes());
        settings.setRetentionDays(request.retentionDays());
        PriceMoveMonitorSettings saved = settingsRepository.save(settings);
        cachedSettings = saved;
        cachedSettingsAt = System.currentTimeMillis();
        points.clear();
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
                .orElseThrow(() -> new IllegalArgumentException("Price move event was not found"));
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
                .windowMinutes(30)
                .retentionDays(7)
                .build();
    }

    private record PricePoint(Instant time, BigDecimal price) {}
}
