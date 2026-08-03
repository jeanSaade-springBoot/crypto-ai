package com.crypto.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_snapshot")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="portfolio_value_usdt", nullable=false, precision=30, scale=12) private BigDecimal portfolioValueUsdt;
    @Column(name="net_invested_usdt", nullable=false, precision=30, scale=12) private BigDecimal netInvestedUsdt;
    @Column(name="total_pnl_usdt", nullable=false, precision=30, scale=12) private BigDecimal totalPnlUsdt;
    @Column(name="total_return_percent", nullable=false, precision=20, scale=8) private BigDecimal totalReturnPercent;
    @Column(name="realized_pnl_usdt", nullable=false, precision=30, scale=12) private BigDecimal realizedPnlUsdt;
    @Column(name="unrealized_pnl_usdt", nullable=false, precision=30, scale=12) private BigDecimal unrealizedPnlUsdt;
    @Column(name="available_usdt", nullable=false, precision=30, scale=12) private BigDecimal availableUsdt;
    @Column(name="captured_at", nullable=false) private Instant capturedAt;
}
