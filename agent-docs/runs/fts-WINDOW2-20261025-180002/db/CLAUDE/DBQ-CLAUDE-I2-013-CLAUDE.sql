SELECT signal_id, symbol, interval_code, candle_open_time, origin, status, attempts, next_attempt_at, updated_at, completed_at, paper_position_id, failure_stage, LEFT(error_message, 300) AS err_head, source_event_id
FROM crypto_ai.signal_processing_work
WHERE signal_id IN (176710,171589,167668,165412,159408,156723,176927,172587,168277,166819,159487,156850)
ORDER BY signal_id DESC
LIMIT 24;