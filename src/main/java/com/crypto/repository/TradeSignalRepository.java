package com.crypto.repository;

import com.crypto.domain.TradeSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TradeSignalRepository extends JpaRepository<TradeSignal, Long> {
    Optional<TradeSignal> findTopBySymbolOrderByGeneratedAtDesc(String symbol);
    Optional<TradeSignal> findTopBySymbolAndIntervalOrderByGeneratedAtDesc(String symbol, String interval);
    Optional<TradeSignal> findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
            String symbol, String interval, Instant generatedAt
    );
    List<TradeSignal> findTop100ByOrderByGeneratedAtDesc();
    List<TradeSignal> findTop20BySymbolOrderByGeneratedAtDesc(String symbol);
}
