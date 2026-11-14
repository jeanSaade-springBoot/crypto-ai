SELECT pp.id AS pp_id, w.id, w.entry_signal_id, w.symbol, w.quantity, w.average_entry_price_usdt, w.total_cost_usdt,
       w.status, w.opened_at, w.updated_at, w.entry_confidence, w.entry_total_score, w.entry_decision,
       w.highest_price_usdt, w.profit_lock_active, w.profit_lock_price_usdt, w.profit_lock_progress_percent,
       w.profit_lock_activated_at
FROM paper_position pp JOIN wallet_managed_position w ON w.entry_signal_id = pp.signal_id
WHERE pp.id IN (182,172,162,158,143,137) ORDER BY pp.id DESC;