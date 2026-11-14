SELECT id, entry_signal_id, symbol, entry_decision, entry_stage, entry_quality_score, allocated_position_percent,
       average_entry_price_usdt, stop_loss_usdt, take_profit_usdt, UNIX_TIMESTAMP(opened_at) AS opened_epoch
FROM wallet_managed_position
WHERE id IN (166,172,187,191,201,211)
ORDER BY id;