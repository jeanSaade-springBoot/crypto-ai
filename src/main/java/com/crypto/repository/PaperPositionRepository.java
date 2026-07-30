package com.crypto.repository;

import com.crypto.domain.PaperPosition;
import com.crypto.domain.PositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PaperPositionRepository extends JpaRepository<PaperPosition, Long> {
    long countByStatus(PositionStatus status);
    boolean existsBySymbolAndStatus(String symbol, PositionStatus status);
    List<PaperPosition> findTop100ByOrderByOpenedAtDesc();
    List<PaperPosition> findTop20BySymbolOrderByOpenedAtDesc(String symbol);

    @Query("select coalesce(sum(p.realizedPnl), 0) from PaperPosition p where p.closedAt >= :since")
    BigDecimal sumRealizedPnlSince(Instant since);
}
