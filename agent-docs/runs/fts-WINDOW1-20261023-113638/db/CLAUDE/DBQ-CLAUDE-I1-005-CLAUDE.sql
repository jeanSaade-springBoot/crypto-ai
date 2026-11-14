SELECT pp.id AS pp_id, ts.id, LEFT(CAST(ts.decision_path AS CHAR), 4000) AS decision_path,
       LEFT(CAST(ts.market_context_snapshot AS CHAR), 3000) AS mcs
FROM paper_position pp JOIN trade_signal ts ON ts.id = pp.signal_id
WHERE pp.id IN (182,172,162,158,143,137) ORDER BY pp.id DESC;