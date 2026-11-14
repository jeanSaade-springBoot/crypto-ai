SELECT p.id AS position_id, m.id AS wallet_position_id, m.entry_signal_id,
       m.average_entry_price_usdt, m.stop_loss_usdt, m.take_profit_usdt,
       m.highest_price_usdt, m.profit_lock_active, m.profit_lock_price_usdt,
       m.profit_lock_progress_percent, m.profit_lock_activated_at, m.profit_lock_state,
       m.entry_stage, m.allocated_position_percent, m.entry_quality_score,
       m.near_tp_state, m.near_tp_best_price, m.near_tp_harvest_used,
       m.near_tp_harvested_quantity, m.opened_at, m.updated_at
FROM paper_position p
JOIN wallet_managed_position m ON m.entry_signal_id = p.signal_id
WHERE p.id IN (182,172,162,158,143,137);

SELECT p.id AS position_id, e.id, e.wallet_position_id, e.event_type,
       e.old_value_usdt, e.new_value_usdt, e.market_price_usdt,
       LEFT(e.reason, 400) AS reason_head, e.occurred_at
FROM paper_position p
JOIN position_management_event e
  ON e.symbol = p.symbol
 AND e.occurred_at BETWEEN p.opened_at AND p.closed_at + INTERVAL 1 MINUTE
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC, e.occurred_at
LIMIT 300;