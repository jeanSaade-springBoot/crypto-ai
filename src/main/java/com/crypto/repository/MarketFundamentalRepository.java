package com.crypto.repository;

import com.crypto.domain.MarketFundamental;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface MarketFundamentalRepository extends JpaRepository<MarketFundamental, Long> {
    Optional<MarketFundamental> findTopBySymbolOrderByObservedAtDesc(String symbol);
    Optional<MarketFundamental> findTopBySymbolAndObservedAtLessThanEqualOrderByObservedAtDesc(
            String symbol, Instant observedAt);
}
