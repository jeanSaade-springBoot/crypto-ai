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

    List<TradeSignal> findBySymbolAndIntervalAndDecisionInAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(
            String symbol, String interval, List<SignalDecision> decisions, Instant generatedAt, org.springframework.data.domain.Pageable pageable
    );

    List<TradeSignal> findBySymbolAndIntervalAndDecisionInOrderByGeneratedAtDesc(
            String symbol, String interval, List<SignalDecision> decisions, org.springframework.data.domain.Pageable pageable
    );
    List<TradeSignal> findByGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(Instant generatedAt);
    long countByGeneratedAtGreaterThanEqual(Instant generatedAt);

    @Query("""
            select count(distinct s.symbol) from TradeSignal s
            where s.generatedAt >= :from
            """)
    long countDistinctSymbolsSince(@Param("from") Instant from);

    @Query("select count(distinct s.symbol) from TradeSignal s")
    long countDistinctSymbols();

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

    // FIX-039: Blocked-signal diagnostics are filtered in the database by symbol and time
    // so a narrow symbol/date search cannot be lost behind a generic recent-row limit.
    @Query("""
            select s from TradeSignal s
            where s.finalEntryAllowed = false
              and (s.decision in (:buy, :strongBuy)
                   or s.originalDecision in (:buy, :strongBuy))
              and (:symbol is null or upper(s.symbol) = :symbol)
              and s.generatedAt between :from and :to
            order by s.generatedAt desc
            """)
    List<TradeSignal> findBlockedBuys(
            @Param("buy") SignalDecision buy,
            @Param("strongBuy") SignalDecision strongBuy,
            @Param("symbol") String symbol,
            @Param("from") Instant from,
            @Param("to") Instant to,
            org.springframework.data.domain.Pageable pageable);

    // A blocked SELL means the isolated/base SELL or STRONG_SELL candidate was persisted,
    // but the final decision pipeline changed it to a non-SELL state. This is audit-only.
    @Query("""
            select s from TradeSignal s
            where s.originalDecision in (:sell, :strongSell)
              and s.decision not in (:sell, :strongSell)
              and (:symbol is null or upper(s.symbol) = :symbol)
              and s.generatedAt between :from and :to
            order by s.generatedAt desc
            """)
    List<TradeSignal> findBlockedSells(
            @Param("sell") SignalDecision sell,
            @Param("strongSell") SignalDecision strongSell,
            @Param("symbol") String symbol,
            @Param("from") Instant from,
            @Param("to") Instant to,
            org.springframework.data.domain.Pageable pageable);

    List<TradeSignal> findBySymbolAndGeneratedAtBetweenOrderByGeneratedAtAsc(
            String symbol, Instant from, Instant to
    );
}
