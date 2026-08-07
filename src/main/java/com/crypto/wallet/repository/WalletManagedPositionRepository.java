package com.crypto.wallet.repository;

import com.crypto.wallet.domain.WalletManagedPosition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface WalletManagedPositionRepository extends JpaRepository<WalletManagedPosition, Long> {
    Optional<WalletManagedPosition> findTopBySymbolAndStatusOrderByOpenedAtDesc(String symbol, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletManagedPosition> findFirstBySymbolAndStatusOrderByOpenedAtDesc(String symbol, String status);
}
