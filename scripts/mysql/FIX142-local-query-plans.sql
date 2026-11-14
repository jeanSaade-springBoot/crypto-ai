-- FIX-142: READ ONLY; use the actual production schema and compare with staging.
USE crypto_ai;
SET SESSION time_zone='+00:00';
SHOW INDEX FROM shared_market_event_delivery;
EXPLAIN SELECT source_event_id FROM shared_market_event_delivery
WHERE symbol='BTCUSDT' AND interval_code='1m' AND protection_completed_at IS NULL
AND observed_at BETWEEN UTC_TIMESTAMP(6)-INTERVAL 15 SECOND AND UTC_TIMESTAMP(6)
ORDER BY symbol_sequence LIMIT 8;
EXPLAIN SELECT source_event_id FROM shared_market_event_delivery
WHERE symbol='BTCUSDT' AND interval_code='1m' AND protection_completed_at IS NULL
AND protection_started_at IS NOT NULL AND symbol_sequence<999999999 LIMIT 1;
EXPLAIN SELECT source_event_id FROM shared_market_event_delivery
WHERE symbol='BTCUSDT' AND interval_code='1m' AND phase=4 AND analysis_status='PENDING'
AND closed=1 AND status='COMPLETED'
AND candle_close_time BETWEEN UTC_TIMESTAMP(6)-INTERVAL 90 SECOND AND UTC_TIMESTAMP(6)
ORDER BY candle_close_time DESC,symbol_sequence DESC LIMIT 1;
EXPLAIN SELECT source_event_id,symbol,interval_code FROM shared_market_event_delivery
WHERE analysis_status='REVIEW_REQUIRED' AND source_event_id>0 ORDER BY source_event_id LIMIT 64;
SHOW GLOBAL STATUS WHERE Variable_name IN (
'Innodb_buffer_pool_read_requests','Innodb_buffer_pool_reads','Innodb_buffer_pool_wait_free',
'Innodb_data_pending_reads','Innodb_data_pending_writes','Innodb_log_waits','Threads_running','Uptime');
-- Sample twice over a known interval; use deltas and reject counter resets.
SELECT THREAD_ID,EVENT_ID,TIMER_WAIT,LOCK_TIME,ROWS_EXAMINED,ROWS_SENT,DIGEST_TEXT
FROM performance_schema.events_statements_history_long
WHERE CURRENT_SCHEMA='crypto_ai' ORDER BY TIMER_START DESC LIMIT 50;
SELECT * FROM performance_schema.data_lock_waits LIMIT 50;
-- No EXPLAIN ANALYZE is issued: it executes the statement and can add workload.
