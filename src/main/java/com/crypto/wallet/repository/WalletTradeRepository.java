package com.crypto.wallet.repository;

import com.crypto.wallet.domain.WalletTrade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface WalletTradeRepository extends JpaRepository<WalletTrade, Long> {
    List<WalletTrade> findTop100ByOrderByExecutedAtDesc();

    List<WalletTrade> findTop100BySymbolAndStatusOrderByExecutedAtDesc(String symbol, String status);

    java.util.Optional<WalletTrade> findTopBySymbolAndStatusOrderByExecutedAtDesc(String symbol, String status);

    java.util.Optional<WalletTrade> findTopBySignalIdAndSideAndStatusOrderByExecutedAtDesc(
            Long signalId, String side, String status);

    java.util.Optional<WalletTrade> findTopBySignalIdAndStatusOrderByExecutedAtDesc(
            Long signalId, String status);

    // FIX-035: Dashboard signal evidence resolves execution state in one batch so the
    // EXECUTED / BUY_BLOCKED filters do not create one wallet query per signal row.
    List<WalletTrade> findBySignal_IdInAndStatus(List<Long> signalIds, String status);

    @Query("select coalesce(sum(t.realizedPnlUsdt), 0) from WalletTrade t where t.status='EXECUTED'")
    BigDecimal totalRealizedPnl();

    @Query("""
            select t from WalletTrade t
            where t.status = 'EXECUTED'
              and t.side = 'SELL'
              and t.realizedPnlUsdt is not null
            order by t.executedAt desc
            """)
    List<WalletTrade> findRecentClosedTrades(Pageable pageable);

    // FIX-106: paginate completed Trade Inspector exits in SQL. Symbol filtering happens
    // before LIMIT/OFFSET so a symbol is never hidden merely because it is outside the
    // newest global batch of wallet trades. A null symbol means ALL.
    @Query("""
            select t from WalletTrade t
            where t.status = 'EXECUTED'
              and t.side = 'SELL'
              and t.realizedPnlUsdt is not null
              and (:symbol is null or upper(t.symbol) = upper(:symbol))
            order by t.executedAt desc
            """)
    Page<WalletTrade> findClosedTradesForInspector(@Param("symbol") String symbol, Pageable pageable);

    // FIX-11R: persisted operator review filter only.
    @Query(value = """
            SELECT wt.* FROM wallet_trade wt JOIN trade_inspector_review r ON r.wallet_sell_trade_id=wt.id AND r.marked_for_review=1
            WHERE wt.status='EXECUTED' AND wt.side='SELL' AND wt.realized_pnl_usdt IS NOT NULL AND (:symbol IS NULL OR UPPER(wt.symbol)=UPPER(:symbol)) ORDER BY wt.executed_at DESC
            """, countQuery = """
            SELECT COUNT(*) FROM wallet_trade wt JOIN trade_inspector_review r ON r.wallet_sell_trade_id=wt.id AND r.marked_for_review=1
            WHERE wt.status='EXECUTED' AND wt.side='SELL' AND wt.realized_pnl_usdt IS NOT NULL AND (:symbol IS NULL OR UPPER(wt.symbol)=UPPER(:symbol))
            """, nativeQuery=true)
    Page<WalletTrade> findMarkedClosedTradesForInspector(@Param("symbol") String symbol, Pageable pageable);

    // FIX-106: populate the symbol dropdown from the complete persisted closed-trade set,
    // not from whichever page happens to be loaded.
    @Query("""
            select distinct t.symbol from WalletTrade t
            where t.status = 'EXECUTED'
              and t.side = 'SELL'
              and t.realizedPnlUsdt is not null
              and t.symbol is not null
            order by t.symbol
            """)
    List<String> findDistinctClosedTradeSymbols();

    // FIX-106: historical pages must pair each SELL with a historical BUY without relying
    // on findTop100ByOrderByExecutedAtDesc(). We fetch prior BUY candidates for this symbol
    // and retain the existing quantity-match-first/fallback semantics in the service.
    @Query("""
            select t from WalletTrade t
            where t.status = 'EXECUTED'
              and t.side = 'BUY'
              and upper(t.symbol) = upper(:symbol)
              and t.executedAt < :before
            order by t.executedAt desc
            """)
    List<WalletTrade> findEntryCandidatesBefore(@Param("symbol") String symbol,
                                                 @Param("before") Instant before,
                                                 Pageable pageable);

    @Query("""
            select t from WalletTrade t
            where t.status = 'EXECUTED'
              and t.side = 'SELL'
              and t.realizedPnlUsdt is not null
              and t.executedAt >= :from
              and t.executedAt < :to
            order by t.executedAt desc
            """)
    List<WalletTrade> findClosedTradesBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(t),
                   coalesce(sum(case when t.realizedPnlUsdt > 0 then 1 else 0 end), 0),
                   coalesce(sum(case when t.realizedPnlUsdt < 0 then 1 else 0 end), 0),
                   coalesce(sum(case when t.realizedPnlUsdt = 0 then 1 else 0 end), 0),
                   coalesce(sum(t.realizedPnlUsdt), 0),
                   coalesce(sum(case when t.realizedPnlUsdt > 0 then t.realizedPnlUsdt else 0 end), 0),
                   coalesce(sum(case when t.realizedPnlUsdt < 0 then -t.realizedPnlUsdt else 0 end), 0)
            from WalletTrade t
            where t.status = 'EXECUTED'
              and t.side = 'SELL'
              and t.realizedPnlUsdt is not null
              and t.executedAt >= :from
              and t.executedAt < :to
            """)
    Object[] summarizeClosedTradesBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select t.symbol,
                   coalesce(sum(t.realizedPnlUsdt), 0),
                   count(t)
            from WalletTrade t
            where t.status = 'EXECUTED'
              and t.side = 'SELL'
              and t.realizedPnlUsdt is not null
              and t.executedAt >= :from
              and t.executedAt < :to
            group by t.symbol
            """)
    List<Object[]> summarizeClosedTradePnlBySymbolBetween(@Param("from") Instant from, @Param("to") Instant to);


    long countByStatusAndSideAndExecutedAtGreaterThanEqual(String status, String side, Instant executedAt);

    @Query("""
            select count(t) from WalletTrade t
            where t.status = 'EXECUTED'
              and t.side = 'SELL'
              and t.realizedPnlUsdt > 0
              and t.executedAt >= :from
            """)
    long countProfitableClosedTradesSince(@Param("from") Instant from);

    // FIX-025: Trade Inspector View Path needs the persisted wallet lifecycle between
    // the selected BUY and SELL so confirmation adds / scale-ins are visible before exit.
    List<WalletTrade> findBySymbolAndStatusAndExecutedAtBetweenOrderByExecutedAtAsc(
            String symbol, String status, Instant from, Instant to);

    boolean existsByExecutionKey(String executionKey);
}
