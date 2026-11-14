SELECT id, symbol, interval_code, candle_open_time, generated_at, decision, original_decision, total_score, latest_price
FROM trade_signal WHERE id IN (176927,172587,166819,159487,156850,168277) ORDER BY id;
SELECT pea.paper_position_id AS pp_id, ts.interval_code, ts.id, ts.candle_open_time, ts.generated_at, ts.decision
FROM production_exit_audit pea
JOIN trade_signal ts ON ts.symbol = pea.symbol AND ts.interval_code IN ('5m','1h')
 AND ts.generated_at BETWEEN pea.audited_at - INTERVAL 2 HOUR AND pea.audited_at
WHERE pea.paper_position_id IN (182,172,162,158,143,137)
ORDER BY pp_id DESC, ts.interval_code, ts.generated_at DESC LIMIT 120;