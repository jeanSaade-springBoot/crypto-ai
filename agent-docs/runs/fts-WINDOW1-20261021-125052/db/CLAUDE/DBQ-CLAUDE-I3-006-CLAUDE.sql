SELECT p.id, p.symbol, o.execution_source, o.five_minute_decision AS used_5m, s5.id AS same_boundary_5m_id, s5.decision AS same_boundary_5m, TIMESTAMPDIFF(MICROSECOND, s1.generated_at, s5.generated_at) AS five_minus_one_us
FROM paper_position p
JOIN trade_signal s1 ON s1.id=p.signal_id
JOIN execution_opportunity o ON o.symbol=p.symbol AND o.executed_at=p.opened_at
LEFT JOIN trade_signal s5 ON s5.symbol=s1.symbol AND s5.interval_code='5m' AND s5.candle_open_time + INTERVAL 5 MINUTE = s1.candle_open_time + INTERVAL 1 MINUTE
WHERE p.opened_at >= '2026-10-11 00:00:00'
ORDER BY p.id