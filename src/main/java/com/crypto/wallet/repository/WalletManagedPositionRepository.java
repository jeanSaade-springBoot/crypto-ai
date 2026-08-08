package com.crypto.wallet.repository;

import com.crypto.wallet.domain.WalletManagedPosition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface WalletManagedPositionRepository extends JpaRepository<WalletManagedPosition, Long> {
    Optional<WalletManagedPosition> findTopBySymbolAndStatusOrderByOpenedAtDesc(String symbol, String status);
    Optional<WalletManagedPosition> findTopByEntrySignalIdOrderByOpenedAtDesc(Long entrySignalId);
    long countByStatus(String status);
    long countByOpenedAtGreaterThanEqual(Instant openedAt);
    List<WalletManagedPosition> findAllByStatusOrderByOpenedAtDesc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletManagedPosition> findFirstBySymbolAndStatusOrderByOpenedAtDesc(String symbol, String status);
}
