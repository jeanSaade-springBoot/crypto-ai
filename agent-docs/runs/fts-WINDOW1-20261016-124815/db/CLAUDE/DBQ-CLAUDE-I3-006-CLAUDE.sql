SELECT 'INGEST_LAG' metric, COUNT(*) n,
       MIN(TIMESTAMPDIFF(MICROSECOND, observed_at, created_at))/1000 min_ms,
       AVG(TIMESTAMPDIFF(MICROSECOND, observed_at, created_at))/1000 avg_ms,
       MAX(TIMESTAMPDIFF(MICROSECOND, observed_at, created_at))/1000 max_ms,
       SUM(created_at < observed_at) negatives
FROM crypto_ai.market_price_event
WHERE observed_at BETWEEN '2026-10-11 20:50:00' AND '2026-10-12 10:10:00'
UNION ALL
SELECT 'SIG_1M_CLOSE_TO_GEN', COUNT(*),
       MIN(TIMESTAMPDIFF(MICROSECOND, candle_open_time + INTERVAL 60 SECOND, generated_at))/1000,
       AVG(TIMESTAMPDIFF(MICROSECOND, candle_open_time + INTERVAL 60 SECOND, generated_at))/1000,
       MAX(TIMESTAMPDIFF(MICROSECOND, candle_open_time + INTERVAL 60 SECOND, generated_at))/1000,
       SUM(generated_at < candle_open_time + INTERVAL 60 SECOND)
FROM crypto_ai.trade_signal
WHERE interval_code='1m' AND generated_at BETWEEN '2026-10-11 20:50:00' AND '2026-10-12 10:10:00';