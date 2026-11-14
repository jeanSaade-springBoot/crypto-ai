SELECT pp.id AS pp_id, s.signal_id, s.symbol, s.interval_code, s.candle_open_time, s.origin, s.status, s.attempts,
       s.next_attempt_at, s.updated_at, s.completed_at, s.paper_position_id, s.failure_stage,
       LEFT(s.error_message, 400) AS err
FROM paper_position pp JOIN signal_processing_work s ON s.signal_id = pp.signal_id
WHERE pp.id IN (182,172,162,158,143,137) ORDER BY pp.id DESC;