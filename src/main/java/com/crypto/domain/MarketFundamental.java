package com.crypto.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_fundamental")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketFundamental {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(name = "market_cap", precision = 38, scale = 2)
    private BigDecimal marketCap;

    @Column(name = "fully_diluted_valuation", precision = 38, scale = 2)
    private BigDecimal fullyDilutedValuation;

    @Column(name = "volume_24h", precision = 38, scale = 2)
    private BigDecimal volume24h;

    @Column(name = "circulating_supply", precision = 38, scale = 8)
    private BigDecimal circulatingSupply;

    @Column(name = "total_supply", precision = 38, scale = 8)
    private BigDecimal totalSupply;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;
}
