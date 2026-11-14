SELECT w.signal_id, w.symbol, w.interval_code, w.candle_open_time, w.origin, w.status,
       w.attempts, w.next_attempt_at, w.updated_at, w.completed_at,
       w.paper_position_id, w.failure_stage, LEFT(w.error_message, 500) AS error_500
FROM crypto_ai.signal_processing_work w
WHERE w.signal_id IN (SELECT signal_id FROM crypto_ai.paper_position WHERE id IN (172,162,158,143,137,131))
   OR w.paper_position_id IN (172,162,158,143,137,131)
ORDER BY w.signal_id;