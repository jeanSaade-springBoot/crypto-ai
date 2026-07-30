package com.crypto.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sentiment_signal")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SentimentSignal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal score;

    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal confidence;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(length = 1000)
    private String summary;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;
}
