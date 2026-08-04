package com.crypto.position.domain;

import com.crypto.domain.TradeSignal;
import com.crypto.wallet.domain.WalletManagedPosition;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "position_analysis",
        indexes = {
                @Index(name = "idx_position_analysis_position_time", columnList = "wallet_position_id, analyzed_at"),
                @Index(name = "idx_position_analysis_symbol_time", columnList = "symbol, analyzed_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_position_analysis_position_signal",
                columnNames = {"wallet_position_id", "trade_signal_id"}
        ))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_position_id", nullable = false)
    private WalletManagedPosition walletPosition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_signal_id", nullable = false)
    private TradeSignal tradeSignal;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(name = "interval_code", nullable = false, length = 10)
    private String intervalCode;

    @Column(name = "entry_price_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal entryPriceUsdt;

    @Column(name = "current_price_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal currentPriceUsdt;

    @Column(name = "unrealized_pnl_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal unrealizedPnlUsdt;

    @Column(name = "unrealized_pnl_percent", nullable = false, precision = 20, scale = 8)
    private BigDecimal unrealizedPnlPercent;

    @Column(name = "holding_minutes", nullable = false)
    private long holdingMinutes;

    @Column(name = "trend_deterioration_score", nullable = false)
    private int trendDeteriorationScore;

    @Column(name = "momentum_exhaustion_score", nullable = false)
    private int momentumExhaustionScore;

    @Column(name = "profit_protection_score", nullable = false)
    private int profitProtectionScore;

    @Column(name = "risk_event_score", nullable = false)
    private int riskEventScore;

    @Column(name = "opportunity_cost_score", nullable = false)
    private int opportunityCostScore;

    @Column(name = "exit_score", nullable = false)
    private int exitScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PositionRecommendation recommendation;

    @Column(nullable = false)
    private int confidence;

    @Column(nullable = false, length = 2000)
    private String explanation;

    @Column(name = "details_json", columnDefinition = "json")
    private String detailsJson;

    @Column(name = "advisory_only", nullable = false)
    private boolean advisoryOnly;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;
}
