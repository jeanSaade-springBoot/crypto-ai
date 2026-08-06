package com.crypto.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_settings")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletSettings {
    @Id
    private Long id;
    @Column(name = "base_trade_amount_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal baseTradeAmountUsdt;
    @Column(name = "minimum_usdt_reserve", nullable = false, precision = 30, scale = 12)
    private BigDecimal minimumUsdtReserve;
    @Column(name = "maximum_daily_new_positions", nullable = false)
    private int maximumDailyNewPositions;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
