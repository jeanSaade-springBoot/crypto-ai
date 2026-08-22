package com.crypto.position.repository;

import com.crypto.position.domain.PositionManagementEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PositionManagementEventRepository extends JpaRepository<PositionManagementEvent, Long> {
    List<PositionManagementEvent> findByWalletPositionIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
            Long walletPositionId, Instant from);
}
