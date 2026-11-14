package com.crypto.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "paper_position")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaperPosition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PositionSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PositionStatus status;

    @Column(nullable = false, precision = 30, scale = 12)
    private BigDecimal quantity;

    @Column(name = "entry_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss", nullable = false, precision = 30, scale = 12)
    private BigDecimal stopLoss;

    @Column(name = "take_profit", nullable = false, precision = 30, scale = 12)
    private BigDecimal takeProfit;

    @Column(name = "exit_price", precision = 30, scale = 12)
    private BigDecimal exitPrice;

    @Column(name = "realized_pnl", precision = 30, scale = 12)
    private BigDecimal realizedPnl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "signal_id")
    private TradeSignal signal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_signal_id")
    private TradeSignal exitSignal;

    @Column(name = "close_reason", length = 40)
    private String closeReason;

    // FIX-144: Full signal/execution explanations and progressive additions exceed 2,000 chars.
    // Preserve audit text without truncation; V100 widens the existing physical columns.
    @Column(name = "entry_reason", columnDefinition = "MEDIUMTEXT")
    private String entryReason;

    // FIX-144: Exit audit explanations use the same expanded storage contract.
    @Column(name = "exit_reason", columnDefinition = "MEDIUMTEXT")
    private String exitReason;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
