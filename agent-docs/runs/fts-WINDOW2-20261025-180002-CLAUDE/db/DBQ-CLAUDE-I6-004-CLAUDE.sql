SELECT test_run_id, symbol, interval_code, candle_open_time, generated_at, original_decision, final_decision, entry_authority, entry_authority_max_position_percent
FROM crypto_ai.analysis_test_signal
WHERE (symbol = 'EGLDUSDT' AND candle_open_time BETWEEN '2026-10-11 22:34:00' AND '2026-10-11 22:40:00')
   OR (symbol = 'ACEUSDT'  AND candle_open_time BETWEEN '2026-10-12 09:50:00' AND '2026-10-12 09:57:00')
ORDER BY test_run_id DESC, symbol, candle_open_time, interval_code
LIMIT 80;