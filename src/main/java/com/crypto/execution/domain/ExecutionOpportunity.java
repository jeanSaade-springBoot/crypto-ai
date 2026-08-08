package com.crypto.execution.domain;

import com.crypto.domain.TradeSignal;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "execution_opportunity", indexes = {
        @Index(name = "idx_execution_opportunity_symbol_status", columnList = "symbol,status,last_evidence_at"),
        @Index(name = "idx_execution_opportunity_latest_signal", columnList = "latest_signal_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionOpportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_evidence_at", nullable = false)
    private Instant lastEvidenceAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_signal_id")
    private TradeSignal latestSignal;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Column(name = "buy_count", nullable = false)
    private int buyCount;

    @Column(name = "watch_count", nullable = false)
    private int watchCount;

    @Column(name = "neutral_count", nullable = false)
    private int neutralCount;

    @Column(name = "evidence_score", nullable = false)
    private int evidenceScore;

    @Column(name = "average_signal_score", nullable = false)
    private int averageSignalScore;

    @Column(name = "average_confidence", nullable = false)
    private int averageConfidence;

    @Column(name = "five_minute_decision", length = 30)
    private String fiveMinuteDecision;

    @Column(name = "one_hour_decision", length = 30)
    private String oneHourDecision;

    @Column(name = "execution_source", length = 40)
    private String executionSource;

    @Column(name = "recommended_position_percent", nullable = false)
    private int recommendedPositionPercent;

    @Column(name = "decision_code", length = 80)
    private String decisionCode;

    @Column(name = "decision_explanation", length = 2000)
    private String decisionExplanation;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
