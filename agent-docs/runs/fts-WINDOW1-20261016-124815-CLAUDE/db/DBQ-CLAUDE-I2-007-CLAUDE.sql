SELECT id, entry_signal_id, symbol, quantity, average_entry_price_usdt, total_cost_usdt, entry_decision,
       stop_loss_usdt, take_profit_usdt, status, opened_at, updated_at, highest_price_usdt,
       profit_lock_active, profit_lock_price_usdt, profit_lock_progress_percent, profit_lock_activated_at,
       profit_lock_state, profit_lock_rebase_started_at, entry_stage, allocated_position_percent,
       entry_quality_score, last_scale_in_at, near_tp_state, near_tp_best_price, near_tp_bearish_streak,
       near_tp_harvest_used
FROM crypto_ai.wallet_managed_position
WHERE id IN (211,201,191,187,172,171,166)
ORDER BY id DESC;