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

    @Column(name = "window_minutes", nullable = false)
    private int windowMinutes;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
