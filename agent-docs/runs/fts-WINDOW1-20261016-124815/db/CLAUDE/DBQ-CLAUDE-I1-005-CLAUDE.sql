SELECT pp.id AS position_id, w.signal_id, w.symbol, w.interval_code, w.candle_open_time, w.origin, w.status,
       w.attempts, w.next_attempt_at, w.updated_at, w.completed_at, w.paper_position_id, w.failure_stage,
       LEFT(w.error_message, 500) AS error_message_head
FROM crypto_ai.paper_position pp
JOIN crypto_ai.signal_processing_work w ON w.signal_id = pp.signal_id
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id DESC;