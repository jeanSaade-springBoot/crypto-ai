package com.crypto.wallet.repository;
import com.crypto.wallet.domain.WalletManagedPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface WalletManagedPositionRepository extends JpaRepository<WalletManagedPosition, Long> {
    Optional<WalletManagedPosition> findTopBySymbolAndStatusOrderByOpenedAtDesc(String symbol, String status);
}
