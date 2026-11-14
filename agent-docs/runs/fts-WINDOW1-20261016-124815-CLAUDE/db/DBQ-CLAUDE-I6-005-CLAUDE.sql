SELECT 'TIMING' AS src, symbol, interval_code AS iv, stage AS detail, started_at AS ts, finished_at, elapsed_ms, outcome, thread_name
FROM crypto_ai.fix129_stage_timing
WHERE started_at BETWEEN '2026-10-12 09:56:00' AND '2026-10-12 09:56:10'
UNION ALL
SELECT 'WORK', symbol, interval_code, status, created_at, completed_at, attempts, failure_stage, CAST(signal_id AS CHAR)
FROM crypto_ai.signal_processing_work
WHERE created_at BETWEEN '2026-10-12 09:56:00' AND '2026-10-12 09:56:10'
ORDER BY ts LIMIT 200;