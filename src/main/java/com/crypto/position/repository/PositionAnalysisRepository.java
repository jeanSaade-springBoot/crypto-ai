package com.crypto.position.repository;

import com.crypto.position.domain.PositionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface PositionAnalysisRepository extends JpaRepository<PositionAnalysis, Long> {
    boolean existsByWalletPositionIdAndTradeSignalId(Long walletPositionId, Long tradeSignalId);
    Optional<PositionAnalysis> findTopByWalletPositionIdOrderByAnalyzedAtDesc(Long walletPositionId);
    List<PositionAnalysis> findTop100ByOrderByAnalyzedAtDesc();
    List<PositionAnalysis> findBySymbolAndAnalyzedAtBetweenOrderByAnalyzedAtAsc(
            String symbol, Instant from, Instant to
    );
}
