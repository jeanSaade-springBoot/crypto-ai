SELECT signal_id, symbol, interval_code, candle_open_time, origin, status, attempts, next_attempt_at, updated_at, completed_at, paper_position_id, failure_stage, LEFT(error_message, 500) AS error_message, source_event_id
FROM signal_processing_work
WHERE signal_id IN (176710,171589,167668,165412,165415,159408,156723)
   OR paper_position_id IN (182,172,162,158,143,137)
ORDER BY signal_id
LIMIT 20