SELECT signal_id, symbol, interval_code, candle_open_time, origin, status, attempts, next_attempt_at, updated_at,
       completed_at, paper_position_id, failure_stage, LEFT(error_message, 600) AS error_message_head
FROM crypto_ai.signal_processing_work
WHERE signal_id IN (176692,171462,171484,167646,165218,165389,159281,156695)
ORDER BY symbol, signal_id;