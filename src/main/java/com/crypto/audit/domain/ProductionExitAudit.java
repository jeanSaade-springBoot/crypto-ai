package com.crypto.audit.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * FIX-028 diagnostic/audit record for one completed production position.
 *
 * This entity deliberately separates the terminal close trigger (TAKE_PROFIT,
 * STOP_LOSS, genuine SELL signal, Profit Lock, etc.) from the latest market
 * signal used as context at that moment. It is read-only evidence for debugging;
 * it never participates in trading decisions or execution authority.
 */
@Entity
@Table(name = "production_exit_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionExitAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_position_id", unique = true)
    private Long paperPositionId;

    @Column(name = "wallet_position_id")
    private Long walletPositionId;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(name = "close_trigger", nullable = false, length = 40)
    private String closeTrigger;

    @Column(name = "source_signal_id")
    private Long sourceSignalId;

    @Column(name = "source_signal_decision", length = 30)
    private String sourceSignalDecision;

    @Column(name = "source_signal_original_decision", length = 30)
    private String sourceSignalOriginalDecision;

    @Column(name = "position_analysis_id")
    private Long positionAnalysisId;

    @Column(name = "position_recommendation", length = 20)
    private String positionRecommendation;

    @Column(name = "entry_price_usdt", precision = 30, scale = 12)
    private BigDecimal entryPriceUsdt;

    @Column(name = "exit_price_usdt", nullable = false, precision = 30, scale = 12)
    private BigDecimal exitPriceUsdt;

    @Column(name = "stop_loss_usdt", precision = 30, scale = 12)
    private BigDecimal stopLossUsdt;

    @Column(name = "take_profit_usdt", precision = 30, scale = 12)
    private BigDecimal takeProfitUsdt;

    @Column(name = "close_explanation", length = 2000)
    private String closeExplanation;

    @Column(name = "audited_at", nullable = false)
    private Instant auditedAt;
}
