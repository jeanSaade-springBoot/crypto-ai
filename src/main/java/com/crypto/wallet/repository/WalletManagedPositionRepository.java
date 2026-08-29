package com.crypto.wallet.repository;

import com.crypto.wallet.domain.WalletManagedPosition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface WalletManagedPositionRepository extends JpaRepository<WalletManagedPosition, Long> {
    Optional<WalletManagedPosition> findTopBySymbolAndStatusOrderByOpenedAtDesc(String symbol, String status);
    Optional<WalletManagedPosition> findTopByEntrySignalIdOrderByOpenedAtDesc(Long entrySignalId);
    long countByStatus(String status);
    long countByOpenedAtGreaterThanEqual(Instant openedAt);
    List<WalletManagedPosition> findAllByStatusOrderByOpenedAtDesc(String status);

    // FIX-11I: Trade Inspector open-position browsing is read-only and database-paged.
    // A null symbol means ALL, matching the completed-trade inspector contract.
    @Query("""
            select p from WalletManagedPosition p
            where p.status = 'OPEN'
              and (:symbol is null or upper(p.symbol) = upper(:symbol))
            order by p.openedAt desc
            """)
    Page<WalletManagedPosition> findOpenPositionsForInspector(@Param("symbol") String symbol, Pageable pageable);

    @Query("""
            select distinct p.symbol from WalletManagedPosition p
            where p.status = 'OPEN'
              and p.symbol is not null
            order by p.symbol
            """)
    List<String> findDistinctOpenPositionSymbols();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletManagedPosition> findFirstBySymbolAndStatusOrderByOpenedAtDesc(String symbol, String status);
}
