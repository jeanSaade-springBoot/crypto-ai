package com.crypto.wallet.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "wallet_daily_statistics",
        uniqueConstraints = @UniqueConstraint(name = "uk_wallet_daily_statistics_date", columnNames = "trade_date"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDailyStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "maximum_new_positions", nullable = false)
    private int maximumNewPositions;

    @Column(name = "daily_trade_budget_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal dailyTradeBudgetUsdt;

    @Column(name = "executed_buys", nullable = false)
    private int executedBuys;

    @Column(name = "starting_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal startingUsdt;

    @Column(name = "ending_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal endingUsdt;

    @Column(name = "starting_portfolio_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal startingPortfolioUsdt;

    @Column(name = "ending_portfolio_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal endingPortfolioUsdt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
