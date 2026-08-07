package com.crypto.repository;

import com.crypto.domain.PaperPosition;
import com.crypto.domain.PositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaperPositionRepository extends JpaRepository<PaperPosition, Long> {
    long countByStatus(PositionStatus status);
    boolean existsBySymbolAndStatus(String symbol, PositionStatus status);
    Optional<PaperPosition> findBySymbolAndStatus(String symbol, PositionStatus status);
    List<PaperPosition> findTop100ByOrderByOpenedAtDesc();
    List<PaperPosition> findTop20BySymbolOrderByOpenedAtDesc(String symbol);

    @Query("select p from PaperPosition p where p.signal.id = :entrySignalId and p.exitSignal.id = :exitSignalId order by p.openedAt desc")
    List<PaperPosition> findBySignalPair(@Param("entrySignalId") Long entrySignalId, @Param("exitSignalId") Long exitSignalId);

    @Query("select coalesce(sum(p.realizedPnl), 0) from PaperPosition p where p.closedAt >= :since")
    BigDecimal sumRealizedPnlSince(Instant since);
}
