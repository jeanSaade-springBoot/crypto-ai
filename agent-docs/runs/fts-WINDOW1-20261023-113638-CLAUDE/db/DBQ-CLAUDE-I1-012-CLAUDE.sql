SELECT pp.id AS pp_id, c.interval_code, c.open_time, c.close_time, c.open_price, c.high_price, c.low_price,
       c.close_price, c.volume, c.closed, c.ingestion_source, c.created_at, c.updated_at
FROM paper_position pp
JOIN candle c ON c.symbol = pp.symbol
 AND ((c.interval_code = '1m' AND c.open_time BETWEEN pp.opened_at - INTERVAL 8 MINUTE AND pp.opened_at)
   OR (c.interval_code = '5m' AND c.open_time BETWEEN pp.opened_at - INTERVAL 30 MINUTE AND pp.opened_at))
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id DESC, c.interval_code, c.open_time LIMIT 200;
SELECT a.* FROM buy_htf_assessment_audit a
JOIN paper_position pp ON a.reference_signal_id = pp.signal_id
WHERE pp.id IN (182,172,162,158,143,137) ORDER BY a.reference_signal_id, a.evaluated_at LIMIT 60;