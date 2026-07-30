package com.crypto.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trade_signal")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TradeSignal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(name = "interval_code", nullable = false, length = 10)
    private String interval;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SignalDecision decision;

    @Column(name = "total_score", nullable = false)
    private int totalScore;
    @Column(name = "trend_score", nullable = false)
    private int trendScore;
    @Column(name = "volume_score", nullable = false)
    private int volumeScore;
    @Column(name = "momentum_score", nullable = false)
    private int momentumScore;
    @Column(name = "sentiment_score", nullable = false)
    private int sentimentScore;
    @Column(name = "fundamental_score", nullable = false)
    private int fundamentalScore;

    @Column(name = "latest_price", nullable = false, precision = 30, scale = 12)
    private BigDecimal latestPrice;
    @Column(name = "stop_loss", precision = 30, scale = 12)
    private BigDecimal stopLoss;
    @Column(name = "take_profit", precision = 30, scale = 12)
    private BigDecimal takeProfit;

    @Column(length = 2000)
    private String explanation;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
