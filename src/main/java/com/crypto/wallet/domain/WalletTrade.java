package com.crypto.wallet.domain;

import com.crypto.domain.TradeSignal;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_trade")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletTrade {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signal_id")
    private TradeSignal signal;
    @Column(name = "execution_key", length = 100)
    private String executionKey;
    @Column(nullable = false, length = 30)
    private String symbol;
    @Column(nullable = false, length = 10)
    private String side;
    @Column(nullable = false, precision = 30, scale = 12)
    private BigDecimal quantity;
    @Column(name = "price_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal priceUsdt;
    @Column(name = "gross_amount_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal grossAmountUsdt;
    @Column(name = "fee_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal feeUsdt;
    @Column(name = "net_amount_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal netAmountUsdt;
    @Column(name = "cost_basis_usdt", precision = 30, scale = 12)
    private BigDecimal costBasisUsdt;
    @Column(name = "realized_pnl_usdt", precision = 30, scale = 12)
    private BigDecimal realizedPnlUsdt;
    @Column(name = "realized_pnl_percent", precision = 20, scale = 8)
    private BigDecimal realizedPnlPercent;
    @Column(name = "execution_type", nullable = false, length = 20)
    private String executionType;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;
    @Column(length = 500)
    private String notes;
}
