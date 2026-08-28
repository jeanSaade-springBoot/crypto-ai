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

    Optional<TradeSignal> findBySymbolAndIntervalAndCandleOpenTime(
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

    // FIX-116A: Score Diagnostics needs scalar score/decision fields only. Do not load the wide
    // TradeSignal entity (JSON/TEXT context, explanations, snapshots) for this dashboard read.
    @Query("""
            select s.symbol as symbol, s.interval as interval, s.totalScore as totalScore,
                   s.rawScore as rawScore, s.maximumAvailableScore as maximumAvailableScore,
                   s.trendScore as trendScore, s.volumeScore as volumeScore,
                   s.momentumScore as momentumScore, s.sentimentScore as sentimentScore,
                   s.fundamentalScore as fundamentalScore, s.originalDecision as originalDecision,
                   s.decision as decision, s.selectedStrategy as selectedStrategy
            from TradeSignal s
            where s.generatedAt >= :from
            order by s.generatedAt desc
            """)
    List<TradeSignalDiagnosticsProjection> findScoreDiagnosticsSince(@Param("from") Instant from);
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

    // FIX-093: Catching Market graph markers must align to the analyzed candle, not the later persistence time.
    List<TradeSignal> findBySymbolAndCandleOpenTimeBetweenOrderByCandleOpenTimeAsc(
            String symbol, Instant from, Instant to
    );
    // FIX-100: Trade Inspector signal analysis reads the persisted signal ledger directly.
    // Filtering by symbol/time happens in SQL so the diagnostic grid can inspect a real
    // historical window instead of only the latest in-memory/recent rows.
    @Query("""
            select s from TradeSignal s
            where (:symbol is null or upper(s.symbol) = :symbol)
              and s.generatedAt between :from and :to
            order by s.generatedAt desc
            """)
    List<TradeSignal> findForInspectorAnalysis(
            @Param("symbol") String symbol,
            @Param("from") Instant from,
            @Param("to") Instant to,
            org.springframework.data.domain.Pageable pageable);

    // FIX-100: symbol choices for Trade Signal Analysis come from trade_signal itself,
    // not wallet history, so symbols that never executed are still analyzable.
    @Query("select distinct upper(s.symbol) from TradeSignal s order by upper(s.symbol)")
    List<String> findDistinctInspectorSymbols();

}
