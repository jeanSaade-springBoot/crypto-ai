package com.crypto.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_cash_flow")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletCashFlow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "flow_type", nullable = false, length = 20)
    private String flowType;
    @Column(name = "amount_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal amountUsdt;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(length = 500)
    private String notes;
}
