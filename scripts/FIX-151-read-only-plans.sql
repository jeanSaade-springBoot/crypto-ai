-- FIX-151 read-only post-migration diagnostics. No EXPLAIN ANALYZE/load execution.
SET SESSION time_zone = '+00:00';
SELECT @@hostname,@@port,DATABASE(),UTC_TIMESTAMP(6) AS checked_utc;
SELECT version,description,success FROM crypto_ai.flyway_schema_history WHERE version='105';
SHOW INDEX FROM crypto_ai.shared_market_event_delivery;
EXPLAIN FORMAT=JSON SELECT source_event_id FROM crypto_ai.shared_market_event_delivery
 WHERE symbol='ACEUSDT' AND phase_walk_pending=1 ORDER BY symbol_sequence LIMIT 1;
SET @symbol='BTCUSDT';
SET @interval='1m';
SET @now_utc=UTC_TIMESTAMP(6);
SET @close_from=DATE_SUB(@now_utc,INTERVAL 90 SECOND);
SET @price_from=DATE_SUB(@now_utc,INTERVAL 15 SECOND);
EXPLAIN FORMAT=JSON

        SELECT COUNT(*) FROM (
          SELECT source_event_id FROM crypto_ai.shared_market_event_delivery
          WHERE symbol=@symbol AND interval_code=@interval AND phase=4
            AND analysis_status IN ('RUNNING','EXECUTION_RETRY','REVIEW_REQUIRED')
          UNION
          SELECT source_event_id FROM crypto_ai.shared_market_event_delivery
          WHERE symbol=@symbol AND interval_code=@interval AND phase=4
            AND analysis_status IN ('PENDING','HISTORICAL_DEFERRED') AND closed=1
            AND candle_close_time BETWEEN @close_from AND @now_utc
          UNION
          SELECT d.source_event_id FROM crypto_ai.shared_market_consumer_state s
          JOIN crypto_ai.shared_market_event_delivery d ON d.source_event_id=s.active_delivery_event_id
          WHERE s.symbol=@symbol AND d.symbol=@symbol AND d.interval_code=@interval AND d.phase<4
            AND s.active_delivery_state IN ('ACTIVE','REVIEW_REQUIRED')
          UNION
          SELECT source_event_id FROM crypto_ai.shared_market_event_delivery
          WHERE symbol=@symbol AND interval_code=@interval AND phase<4
            AND protection_started_at IS NOT NULL AND protection_completed_at IS NULL
          UNION
          SELECT source_event_id FROM crypto_ai.shared_market_event_delivery
          WHERE symbol=@symbol AND interval_code=@interval AND interval_code='1m' AND phase<4
            AND observed_at BETWEEN @price_from AND @now_utc
          UNION
          SELECT source_event_id FROM crypto_ai.shared_market_event_delivery
          WHERE symbol=@symbol AND interval_code=@interval AND phase<4 AND closed=1
            AND candle_close_time BETWEEN @close_from AND @now_utc
        ) exposure_ids
        ;
