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

    @Column(name = "max_supply", precision = 38, scale = 8)
    private BigDecimal maxSupply;

    @Column(name = "tier1_exchange_count")
    private Integer tier1ExchangeCount;

    @Column(name = "exchange_count")
    private Integer exchangeCount;

    @Column(name = "team_supply", precision = 38, scale = 8)
    private BigDecimal teamSupply;

    @Column(name = "treasury_supply", precision = 38, scale = 8)
    private BigDecimal treasurySupply;

    @Column(name = "private_investor_supply", precision = 38, scale = 8)
    private BigDecimal privateInvestorSupply;

    @Column(name = "locked_supply", precision = 38, scale = 8)
    private BigDecimal lockedSupply;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;
}
