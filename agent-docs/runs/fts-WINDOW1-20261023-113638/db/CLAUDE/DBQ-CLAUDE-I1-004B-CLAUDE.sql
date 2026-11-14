SELECT pp.id AS pp_id, ts.id, ts.signal_mode, ts.data_quality_valid
FROM paper_position pp JOIN trade_signal ts ON ts.id = pp.signal_id
WHERE pp.id IN (182,172,162,158,143,137) ORDER BY pp.id DESC;