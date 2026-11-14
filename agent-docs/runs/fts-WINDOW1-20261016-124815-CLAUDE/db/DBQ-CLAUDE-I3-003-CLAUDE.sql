SELECT id, symbol, interval_code, stage, started_at, finished_at, elapsed_ms, outcome, thread_name, observed_at
FROM crypto_ai.fix129_stage_timing
WHERE started_at BETWEEN '2026-10-11 20:50:00' AND '2026-10-12 10:10:00'
ORDER BY started_at
LIMIT 300;