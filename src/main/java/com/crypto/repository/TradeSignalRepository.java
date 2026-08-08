package com.crypto.repository;

import com.crypto.domain.TradeSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.crypto.domain.SignalDecision;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TradeSignalRepository extends JpaRepository<TradeSignal, Long> {
    boolean existsBySymbolAndIntervalAndCandleOpenTime(
            String symbol, String interval, Instant candleOpenTime
    );

    Optional<TradeSignal> findTopBySymbolOrderByGeneratedAtDesc(String symbol);
    Optional<TradeSignal> findTopBySymbolAndIntervalOrderByGeneratedAtDesc(String symbol, String interval);
    Optional<TradeSignal> findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
            String symbol, String interval, Instant generatedAt
    );
    Optional<TradeSignal> findTopBySymbolAndIntervalAndGeneratedAtLessThanOrderByGeneratedAtDesc(
            String symbol, String interval, Instant generatedAt
    );
    List<TradeSignal> findTop100ByOrderByGeneratedAtDesc();
    List<TradeSignal> findTop20BySymbolOrderByGeneratedAtDesc(String symbol);
    List<TradeSignal> findTop20BySymbolAndIntervalOrderByGeneratedAtDesc(String symbol, String interval);
    List<TradeSignal> findByGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(Instant generatedAt);
    long countByGeneratedAtGreaterThanEqual(Instant generatedAt);

    @Query("""
            select count(s) from TradeSignal s
            where s.generatedAt >= :from
              and (s.originalDecision in (:buy, :strongBuy)
                   or s.decision in (:buy, :strongBuy))
            """)
    long countBuyCandidatesSince(
            @Param("from") Instant from,
            @Param("buy") SignalDecision buy,
            @Param("strongBuy") SignalDecision strongBuy);
    List<TradeSignal> findBySymbolAndGeneratedAtBetweenOrderByGeneratedAtAsc(
            String symbol, Instant from, Instant to
    );
}
