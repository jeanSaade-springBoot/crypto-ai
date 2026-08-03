package com.crypto.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_managed_position")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletManagedPosition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String symbol;
    @Column(nullable = false, precision = 30, scale = 12)
    private BigDecimal quantity;
    @Column(name = "average_entry_price_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal averageEntryPriceUsdt;
    @Column(name = "total_cost_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal totalCostUsdt;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
