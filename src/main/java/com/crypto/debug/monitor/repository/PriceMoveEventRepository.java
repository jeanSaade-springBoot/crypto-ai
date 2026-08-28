package com.crypto.debug.monitor.repository;

import com.crypto.debug.monitor.domain.PriceMoveEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.crypto.debug.monitor.dto.CatchingMarketSummaryView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PriceMoveEventRepository extends JpaRepository<PriceMoveEvent, Long> {
    List<PriceMoveEvent> findTop250ByOrderByEndTimeDesc();

    List<PriceMoveEvent> findTop250BySymbolOrderByEndTimeDesc(String symbol);


    /**
     * FIX-113: aggregate persisted catches at the database boundary so the browser receives only
     * one 20-row page. start_event_id is the earliest persisted event in the aggregate and is used
     * only by the lightweight Start Time chart. No trading tables or decision services are touched.
     */
    @Query(value = """
            SELECT e.symbol AS symbol,
                   e.direction AS direction,
                   e.detection_window AS detectionWindow,
                   COUNT(*) AS directionCount,
                   AVG(e.change_percent) AS averageProgress,
                   MIN(e.start_time) AS startTime,
                   MAX(e.end_time) AS endTime,
                   CAST(SUBSTRING_INDEX(GROUP_CONCAT(e.id ORDER BY e.start_time ASC, e.id ASC), ',', 1) AS UNSIGNED) AS startEventId
              FROM price_move_event e
             WHERE e.end_time >= :fromTime
               AND (:symbols IS NULL OR FIND_IN_SET(e.symbol, :symbols) > 0)
               AND (:level = 'ALL' OR e.importance_level = :level)
             GROUP BY e.symbol, e.direction, e.detection_window
             ORDER BY MAX(e.end_time) DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM (
                SELECT 1
                  FROM price_move_event e
                 WHERE e.end_time >= :fromTime
                   AND (:symbols IS NULL OR FIND_IN_SET(e.symbol, :symbols) > 0)
                   AND (:level = 'ALL' OR e.importance_level = :level)
                 GROUP BY e.symbol, e.direction, e.detection_window
            ) grouped_rows
            """, nativeQuery = true)
    Page<CatchingMarketSummaryView> findSummaryPage(@Param("fromTime") Instant fromTime,
                                                     @Param("symbols") String symbols,
                                                     @Param("level") String level,
                                                     Pageable pageable);

    @Modifying
    @Query("delete from PriceMoveEvent e where e.endTime < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    long countByBlameRequiredTrueAndBlameReviewedFalse();
    Optional<PriceMoveEvent> findBySymbolAndBlockStartTimeAndDirection(String symbol, Instant blockStartTime, String direction);
}
