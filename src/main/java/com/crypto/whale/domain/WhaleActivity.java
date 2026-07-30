package com.crypto.whale.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "whale_activity", uniqueConstraints = @UniqueConstraint(
        name = "uk_whale_activity_tx_wallet_horizon",
        columnNames = {"blockchain", "transaction_hash", "wallet_address", "evaluation_horizon"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WhaleActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30) private String blockchain;
    @Column(name = "transaction_hash", nullable = false, length = 180) private String transactionHash;
    @Column(name = "wallet_address", nullable = false, length = 220) private String walletAddress;
    @Column(name = "counterparty_address", length = 220) private String counterpartyAddress;
    @Column(name = "wallet_label", length = 180) private String walletLabel;
    @Column(name = "counterparty_label", length = 180) private String counterpartyLabel;
    @Column(nullable = false, length = 30) private String symbol;
    @Column(nullable = false, length = 20) private String asset;
    @Enumerated(EnumType.STRING) @Column(name = "movement_type", nullable = false, length = 40) private WhaleMovementType movementType;
    @Column(precision = 38, scale = 12) private BigDecimal amount;
    @Column(name = "usd_value", nullable = false, precision = 24, scale = 2) private BigDecimal usdValue;
    @Column(name = "transaction_score", nullable = false, precision = 10, scale = 8) private BigDecimal transactionScore;
    @Column(name = "transaction_confidence", nullable = false, precision = 10, scale = 8) private BigDecimal transactionConfidence;
    @Column(name = "price_at_signal", precision = 30, scale = 12) private BigDecimal priceAtSignal;
    @Enumerated(EnumType.STRING) @Column(name = "evaluation_horizon", nullable = false, length = 30) private WhaleEvaluationHorizon evaluationHorizon;
    @Column(name = "evaluation_due_at", nullable = false) private Instant evaluationDueAt;
    @Column(name = "price_at_evaluation", precision = 30, scale = 12) private BigDecimal priceAtEvaluation;
    @Column(name = "market_return", precision = 14, scale = 10) private BigDecimal marketReturn;
    @Enumerated(EnumType.STRING) @Column(name = "evaluation_result", nullable = false, length = 30) private WhaleEvaluationResult evaluationResult;
    @Column(name = "prediction_quality", precision = 10, scale = 8) private BigDecimal predictionQuality;
    @Column(name = "evaluated_at") private Instant evaluatedAt;
    @Column(name = "whale_total_signals", nullable = false) private long whaleTotalSignals;
    @Column(name = "whale_correct_signals", nullable = false) private long whaleCorrectSignals;
    @Column(name = "whale_incorrect_signals", nullable = false) private long whaleIncorrectSignals;
    @Column(name = "whale_inconclusive_signals", nullable = false) private long whaleInconclusiveSignals;
    @Column(name = "whale_accuracy", nullable = false, precision = 10, scale = 8) private BigDecimal whaleAccuracy;
    @Column(name = "whale_average_quality", nullable = false, precision = 10, scale = 8) private BigDecimal whaleAverageQuality;
    @Column(name = "whale_learned_weight", nullable = false, precision = 10, scale = 8) private BigDecimal whaleLearnedWeight;
    @Column(name = "observed_at", nullable = false) private Instant observedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
