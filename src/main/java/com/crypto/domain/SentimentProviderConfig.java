package com.crypto.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sentiment_provider", uniqueConstraints = @UniqueConstraint(name = "uk_sentiment_provider_code", columnNames = "provider_code"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_code", nullable = false, length = 60)
    private String providerCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal weight;

    @Column(name = "collection_interval_seconds", nullable = false)
    private long collectionIntervalSeconds;

    @Column(name = "last_collection_at")
    private Instant lastCollectionAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_status", nullable = false, length = 30)
    private String lastStatus;

    @Column(name = "last_message", length = 1000)
    private String lastMessage;

    @Column(name = "api_key_env_var", length = 100)
    private String apiKeyEnvVar;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public boolean isDue(Instant now) {
        return enabled && (lastCollectionAt == null
                || !lastCollectionAt.plusSeconds(Math.max(60, collectionIntervalSeconds)).isAfter(now));
    }
}
