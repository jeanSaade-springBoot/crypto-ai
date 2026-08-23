package com.crypto.position.repository;

import com.crypto.position.domain.PositionManagementEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PositionManagementEventRepository extends JpaRepository<PositionManagementEvent, Long> {
    List<PositionManagementEvent> findByWalletPositionIdAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
            Long walletPositionId, Instant from);

    // FIX-066: Trade Inspector needs the immutable management events that occurred
    // inside one exact BUY -> SELL lifecycle so TAKE_PROFIT_EXTENDED is visible in sequence.
    List<PositionManagementEvent> findByWalletPositionIdAndOccurredAtBetweenOrderByOccurredAtAsc(
            Long walletPositionId, Instant from, Instant to);
}
