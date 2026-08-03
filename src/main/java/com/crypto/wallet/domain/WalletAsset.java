package com.crypto.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_asset")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 20)
    private String symbol;
    @Column(nullable = false, precision = 30, scale = 12)
    private BigDecimal quantity;
    @Column(name = "average_buy_price_usdt", precision = 30, scale = 12)
    private BigDecimal averageBuyPriceUsdt;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
