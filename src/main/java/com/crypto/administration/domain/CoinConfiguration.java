package com.crypto.administration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "coin_configuration", schema = "crypto_ai",
        uniqueConstraints = @UniqueConstraint(name = "uk_coin_configuration_symbol", columnNames = "symbol"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "system_default", nullable = false)
    private boolean systemDefault;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
