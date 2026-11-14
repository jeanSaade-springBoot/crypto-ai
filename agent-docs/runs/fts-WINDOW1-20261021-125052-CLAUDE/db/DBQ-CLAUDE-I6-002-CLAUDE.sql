SELECT id, symbol, status, started_at, anchor_entry_price, best_entry_price, latest_signal_id, decision_code
FROM execution_opportunity
WHERE id IN (35535,36524,37621,37952,39062,40290)
ORDER BY id