SELECT id, wallet_position_id, symbol, event_type, old_value_usdt, new_value_usdt, market_price_usdt,
       occurred_at, UNIX_TIMESTAMP(occurred_at) AS occurred_epoch, LEFT(reason, 600) AS reason_head
FROM position_management_event
WHERE wallet_position_id IN (211,201,191,187,172,166)
ORDER BY wallet_position_id, occurred_at
LIMIT 200;
SELECT id, paper_position_id, wallet_position_id, symbol, close_trigger, source_signal_id,
       source_signal_decision, source_signal_original_decision, position_analysis_id,
       position_recommendation, entry_price_usdt, exit_price_usdt, stop_loss_usdt, take_profit_usdt,
       UNIX_TIMESTAMP(audited_at) AS audited_epoch, LEFT(close_explanation, 800) AS expl_head
FROM production_exit_audit
WHERE paper_position_id IN (182,172,162,158,143,137) OR wallet_position_id IN (211,201,191,187,172,166);