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

    // FIX-038: Trade Inspector diagnostic feed for BUY/STRONG_BUY signals that were
    // persisted but blocked by final decision authority. Read-only; no execution logic changes.
    @Query("""
            select s from TradeSignal s
            where s.finalEntryAllowed = false
              and (s.decision in (:buy, :strongBuy)
                   or s.originalDecision in (:buy, :strongBuy))
            order by s.generatedAt desc
            """)
    List<TradeSignal> findRecentBlockedBuys(
            @Param("buy") SignalDecision buy,
            @Param("strongBuy") SignalDecision strongBuy,
            org.springframework.data.domain.Pageable pageable);

    List<TradeSignal> findBySymbolAndGeneratedAtBetweenOrderByGeneratedAtAsc(
            String symbol, Instant from, Instant to
    );
}
