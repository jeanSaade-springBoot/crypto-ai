package com.crypto.debug.monitor.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_move_event", schema = "crypto_ai")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PriceMoveEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(name = "block_start_time")
    private Instant blockStartTime;

    @Column(name = "block_end_time")
    private Instant blockEndTime;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "start_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal startPrice;

    @Column(name = "end_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal endPrice;

    @Column(name = "change_percent", nullable = false, precision = 20, scale = 8)
    private BigDecimal changePercent;

    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    @Column(name = "detection_window", length = 10)
    private String detectionWindow;

    @Column(name = "importance_level", nullable = false, length = 10)
    private String importanceLevel;

    @Column(name = "outcome_status", nullable = false, length = 40)
    private String outcomeStatus;

    @Column(name = "blame_required", nullable = false)
    private boolean blameRequired;

    @Column(name = "blame_reviewed", nullable = false)
    private boolean blameReviewed;

    @Column(name = "blame_code", length = 100)
    private String blameCode;

    @Column(name = "blame_explanation", length = 2000)
    private String blameExplanation;

    @Column(name = "best_signal_id")
    private Long bestSignalId;

    @Column(name = "best_signal_decision", length = 30)
    private String bestSignalDecision;

    @Column(name = "best_signal_score")
    private Integer bestSignalScore;

    @Column(name = "trade_id")
    private Long tradeId;

    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
