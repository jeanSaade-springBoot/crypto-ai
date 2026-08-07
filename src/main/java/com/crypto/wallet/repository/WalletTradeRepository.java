package com.crypto.wallet.repository;

import com.crypto.wallet.domain.WalletTrade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface WalletTradeRepository extends JpaRepository<WalletTrade, Long> {
    List<WalletTrade> findTop100ByOrderByExecutedAtDesc();

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

    boolean existsByExecutionKey(String executionKey);
}
