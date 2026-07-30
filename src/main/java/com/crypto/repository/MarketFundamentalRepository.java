package com.crypto.repository;

import com.crypto.domain.MarketFundamental;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketFundamentalRepository extends JpaRepository<MarketFundamental, Long> {
    Optional<MarketFundamental> findTopBySymbolOrderByObservedAtDesc(String symbol);
}
