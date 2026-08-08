package com.crypto.wallet.repository;
import com.crypto.wallet.domain.WalletSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.Instant;
import java.util.Optional;
public interface WalletSnapshotRepository extends JpaRepository<WalletSnapshot, Long> {
    List<WalletSnapshot> findTop200ByOrderByCapturedAtDesc();
    Optional<WalletSnapshot> findFirstByCapturedAtGreaterThanEqualOrderByCapturedAtAsc(Instant capturedAt);
}
