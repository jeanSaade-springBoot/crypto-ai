package com.crypto.debug.monitor.repository;

import com.crypto.debug.monitor.domain.PriceMoveEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PriceMoveEventRepository extends JpaRepository<PriceMoveEvent, Long> {
    List<PriceMoveEvent> findTop250ByOrderByEndTimeDesc();

    @Modifying
    @Query("delete from PriceMoveEvent e where e.endTime < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
