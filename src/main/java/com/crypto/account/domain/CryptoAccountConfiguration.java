package com.crypto.account.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * FIX-031: one exchange-account configuration per logged-in application user.
 *
 * This entity is intentionally separate from market analysis and signal generation. Shared market
 * intelligence must not change because a different user logs in; only execution-account settings
 * and credentials belong to the user boundary.
 */
@Entity
@Table(name = "crypto_account_configuration",
        uniqueConstraints = @UniqueConstraint(name = "uk_crypto_account_user_exchange", columnNames = {"user_id", "exchange_code"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CryptoAccountConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "exchange_code", nullable = false, length = 20)
    private String exchangeCode;

    @Column(name = "account_label", nullable = false, length = 100)
    private String accountLabel;

    @Column(name = "execution_mode", nullable = false, length = 20)
    private String executionMode;

    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Column(name = "api_secret_encrypted", columnDefinition = "TEXT")
    private String apiSecretEncrypted;

    @Column(name = "api_key_hint", length = 32)
    private String apiKeyHint;

    @Column(name = "max_order_usdt", nullable = false, precision = 20, scale = 8)
    private BigDecimal maxOrderUsdt;

    @Column(name = "max_total_exposure_usdt", nullable = false, precision = 20, scale = 8)
    private BigDecimal maxTotalExposureUsdt;

    @Column(name = "max_open_positions", nullable = false)
    private int maxOpenPositions;

    @Column(name = "max_daily_loss_usdt", nullable = false, precision = 20, scale = 8)
    private BigDecimal maxDailyLossUsdt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
