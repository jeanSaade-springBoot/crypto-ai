package com.crypto.position.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * FIX-053: Immutable audit event for a live managed-position change.
 * Times are persisted as UTC Instants; timezone conversion is presentation-only.
 */
@Entity
@Table(name = "position_management_event",
        indexes = {
                @Index(name = "idx_position_management_event_position_time", columnList = "wallet_position_id, occurred_at"),
                @Index(name = "idx_position_management_event_symbol_time", columnList = "symbol, occurred_at")
        })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PositionManagementEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_position_id", nullable = false)
    private Long walletPositionId;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "old_value_usdt", precision = 30, scale = 12)
    private BigDecimal oldValueUsdt;

    @Column(name = "new_value_usdt", precision = 30, scale = 12)
    private BigDecimal newValueUsdt;

    @Column(name = "market_price_usdt", precision = 30, scale = 12)
    private BigDecimal marketPriceUsdt;

    @Column(length = 2000)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
