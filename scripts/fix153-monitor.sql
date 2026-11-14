-- FIX-153 read-only monitor. Select the actual trader DB; all displayed clocks are UTC.
USE crypto_ai;
SET time_zone = '+00:00';
SELECT @@hostname AS host, @@port AS port, DATABASE() AS db, UTC_TIMESTAMP(6) AS observed_utc;
SELECT version, description, success FROM flyway_schema_history WHERE version IN ('105','106') ORDER BY installed_rank;

-- Exact per-symbol latest 1m LIVE signal. Historical as-of timestamps are excluded.
SELECT c.symbol, UTC_TIMESTAMP(6) AS observed_utc,
 (SELECT MAX(s.candle_open_time) FROM trade_signal s WHERE s.symbol=c.symbol AND s.interval_code='1m' AND s.signal_mode='LIVE') AS last_live_open_utc
FROM coin_configuration c WHERE c.enabled=1 ORDER BY c.symbol;

-- Last four hours: counts and observed close-to-generation wall delay, only LIVE signals.
SELECT symbol, COUNT(*) AS live_signals,
 ROUND(AVG(TIMESTAMPDIFF(MICROSECOND,candle_open_time+INTERVAL 1 MINUTE,generated_at))/1000000,3) AS avg_delay_seconds,
 ROUND(MAX(TIMESTAMPDIFF(MICROSECOND,candle_open_time+INTERVAL 1 MINUTE,generated_at))/1000000,3) AS max_delay_seconds,
 SUM(generated_at>candle_open_time+INTERVAL 1 MINUTE+INTERVAL 15 SECOND) AS over_15_seconds,
 MAX(candle_open_time) AS latest_open_utc, MAX(created_at) AS latest_insert_utc
FROM trade_signal WHERE interval_code='1m' AND signal_mode='LIVE'
 AND candle_open_time>=UTC_TIMESTAMP()-INTERVAL 4 HOUR GROUP BY symbol ORDER BY avg_delay_seconds DESC;

-- Unresolved NEW direct receipts only; never update these rows manually.
SELECT id,symbol,observed_at,created_at,delivery_status,outcome_reason
FROM market_price_event WHERE source='BINANCE_KLINE_DIRECT_FIX153' AND delivery_status='PENDING'
ORDER BY created_at,id LIMIT 100;
SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Max_used_connections');
