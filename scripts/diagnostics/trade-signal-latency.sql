-- Read-only audit for MySQL 8.0.18+. Run against the intended Trader schema.
-- EXPLAIN ANALYZE executes SELECTs; it does not measure HTTP, JDBC mapping,
-- connection-pool wait, or the complete trading evaluation pipeline.
-- An 8-second per-SELECT limit bounds diagnostic load. Settings are session-only.
SET SESSION time_zone = '+00:00';
SET SESSION max_execution_time = 8000;

SELECT DATABASE() AS audited_schema, VERSION() AS mysql_version,
       @@innodb_buffer_pool_size AS buffer_pool_bytes;
SHOW INDEX FROM trade_signal;
SELECT COUNT(*) AS actual_rows, COUNT(DISTINCT symbol) AS symbols,
       CAST(MIN(generated_at) AS CHAR) AS earliest_utc,
       CAST(MAX(generated_at) AS CHAR) AS latest_utc
FROM trade_signal;
SELECT table_rows AS estimated_rows, data_length, index_length
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'trade_signal';

-- Anchor to recorded data rather than today's date: an idle local database
-- otherwise gives deceptively fast empty-window plans.
SET @audit_symbol = 'BTCUSDT';
SET @audit_interval = '1m';
SET @audit_to = (SELECT MAX(generated_at) FROM trade_signal);
SET @audit_from = @audit_to - INTERVAL 24 HOUR;
SET @audit_before = @audit_to - INTERVAL 6 MINUTE;

EXPLAIN ANALYZE
SELECT * FROM trade_signal
WHERE symbol = @audit_symbol AND interval_code = @audit_interval
ORDER BY generated_at DESC LIMIT 1;

-- Repeat this full-row query and the scalar query to distinguish first-read
-- effects from warmed-cache behavior. Compare examined rows as well as time.
EXPLAIN ANALYZE
SELECT * FROM trade_signal
WHERE symbol = @audit_symbol AND interval_code = @audit_interval
  AND generated_at <= @audit_before
ORDER BY generated_at DESC LIMIT 20;

EXPLAIN ANALYZE
SELECT id, symbol, interval_code, generated_at, decision, total_score
FROM trade_signal
WHERE symbol = @audit_symbol AND interval_code = @audit_interval
  AND generated_at <= @audit_before
ORDER BY generated_at DESC LIMIT 20;

EXPLAIN ANALYZE
SELECT * FROM trade_signal
WHERE symbol = @audit_symbol AND generated_at BETWEEN @audit_from AND @audit_to
ORDER BY generated_at ASC;

EXPLAIN ANALYZE
SELECT * FROM trade_signal
WHERE symbol = @audit_symbol AND candle_open_time BETWEEN @audit_from AND @audit_to
ORDER BY candle_open_time ASC;

-- Exact blocked-BUY predicates used by TradeSignalRepository.
EXPLAIN ANALYZE
SELECT * FROM trade_signal
WHERE final_entry_allowed = 0
  AND (decision IN ('BUY', 'STRONG_BUY') OR original_decision IN ('BUY', 'STRONG_BUY'))
  AND (@audit_symbol IS NULL OR UPPER(symbol) = @audit_symbol)
  AND generated_at BETWEEN @audit_from AND @audit_to
ORDER BY generated_at DESC LIMIT 100;

EXPLAIN ANALYZE
SELECT * FROM trade_signal
WHERE symbol = @audit_symbol AND interval_code = @audit_interval
  AND decision IN ('BUY', 'STRONG_BUY', 'SELL', 'STRONG_SELL')
ORDER BY generated_at DESC LIMIT 50;

-- Already supported by the existing V76 covering index.
EXPLAIN ANALYZE
SELECT selected_strategy, market_regime, COUNT(*)
FROM trade_signal
WHERE generated_at >= @audit_from AND generated_at < @audit_to
GROUP BY selected_strategy, market_regime;

-- Recorded statement history is limited to the current Performance Schema
-- retention period. These results include this audit and must be distinguished
-- from application statements; no global counters are reset here.
SELECT digest_text, count_star,
       ROUND(avg_timer_wait / 1000000000, 3) AS average_ms,
       ROUND(max_timer_wait / 1000000000, 3) AS maximum_ms,
       sum_rows_examined, sum_rows_sent, sum_no_index_used, sum_sort_rows
FROM performance_schema.events_statements_summary_by_digest
WHERE schema_name = DATABASE() AND digest_text LIKE '%trade_signal%'
  AND digest_text NOT LIKE '%trade_signal_test%'
ORDER BY sum_timer_wait DESC LIMIT 20;

-- Recorded stage timings are selective (always-record stages, failures, or
-- slow stages), so their average is not a full-pipeline latency distribution.
SELECT stage, COUNT(*) AS recorded_samples, AVG(elapsed_ms) AS average_ms,
       MAX(elapsed_ms) AS maximum_ms
FROM fix129_stage_timing GROUP BY stage;
