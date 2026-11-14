-- FIX-142: READ ONLY. Run against the collector schema before enabling fresh admission.
-- The trader's SELECT-only source pool never executes DDL. Do not infer installed
-- indexes from JPA annotations. LIMIT does not bound rows examined or lock waits.
USE crypto_ai_v2;
SET SESSION time_zone='+00:00';
SHOW INDEX FROM market_data_stream_event;
EXPLAIN SELECT id,symbol,symbol_sequence,interval_code,candle_open_time,candle_close_time,closed,observed_at,received_at,created_at,price,source,classification
FROM market_data_stream_event WHERE symbol='BTCUSDT' AND interval_code='1m'
AND source='LIVE_WEBSOCKET' AND classification='LIVE'
AND observed_at BETWEEN UTC_TIMESTAMP(6)-INTERVAL 15 SECOND AND UTC_TIMESTAMP(6)
ORDER BY symbol_sequence LIMIT 32;
EXPLAIN SELECT id,symbol,symbol_sequence,interval_code,candle_open_time,candle_close_time,closed,observed_at,received_at,created_at,price,source,classification
FROM market_data_stream_event WHERE symbol='BTCUSDT' AND interval_code='1m'
AND closed=1 AND source='LIVE_WEBSOCKET' AND classification='LIVE'
AND candle_close_time BETWEEN UTC_TIMESTAMP(6)-INTERVAL 90 SECOND AND UTC_TIMESTAMP(6)
ORDER BY candle_close_time DESC,symbol_sequence LIMIT 2;
-- Candidate prefixes if no equivalent leading columns exist:
-- (symbol,interval_code,source,classification,observed_at,symbol_sequence)
-- (symbol,interval_code,closed,source,classification,candle_close_time,symbol_sequence)
-- Collector-owned DDL needs its own observed SHOW INDEX/EXPLAIN and metadata-lock
-- rehearsal in THIS release; no automatic duplicate indexes or index drops are issued.
