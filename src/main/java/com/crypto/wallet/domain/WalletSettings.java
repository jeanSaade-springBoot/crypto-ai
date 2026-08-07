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
    @Column(name = "performance_window_type", nullable = false, length = 20)
    private String performanceWindowType;
    @Column(name = "performance_trade_count", nullable = false)
    private int performanceTradeCount;
    @Column(name = "performance_period_days", nullable = false)
    private int performancePeriodDays;
    @Column(name = "performance_start_date")
    private java.time.LocalDate performanceStartDate;
    @Column(name = "performance_end_date")
    private java.time.LocalDate performanceEndDate;
    @Column(name = "dashboard_intervals", nullable = false, length = 100)
    private String dashboardIntervals;
    @Column(name = "require_new_buy_transition", nullable = false)
    private boolean requireNewBuyTransition;
    @Column(name = "dynamic_profit_lock_enabled", nullable = false)
    private boolean dynamicProfitLockEnabled;
    @Column(name = "profit_lock_activation_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal profitLockActivationPercent;
    @Column(name = "profit_lock_initial_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal profitLockInitialPercent;
    @Column(name = "profit_lock_trail_step_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal profitLockTrailStepPercent;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
