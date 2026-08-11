package com.crypto.debug.monitor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_move_monitor_settings", schema = "crypto_ai")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PriceMoveMonitorSettings {
    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "minimum_move_percent", nullable = false, precision = 12, scale = 6)
    private BigDecimal minimumMovePercent;

    /**
     * Legacy column retained for Flyway/database compatibility with V47.
     * The Market Move Tracker no longer uses a rolling monitoring window.
     */
    @Column(name = "window_minutes", nullable = false)
    private int windowMinutes;

    @Column(name = "minimum_duration_minutes", nullable = false)
    private int minimumDurationMinutes;

    @Column(name = "retracement_close_percent", nullable = false, precision = 12, scale = 6)
    private BigDecimal retracementClosePercent;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    /** Comma-separated debug-only symbols selected in Administration. */
    @Column(name = "selected_symbols", nullable = false, length = 1000)
    private String selectedSymbols;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
