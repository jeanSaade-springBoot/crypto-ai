SELECT a.id, a.paper_position_id, a.wallet_position_id, a.symbol, a.close_trigger,
       a.source_signal_id, a.source_signal_decision, a.source_signal_original_decision,
       a.position_analysis_id, a.position_recommendation, a.entry_price_usdt, a.exit_price_usdt,
       a.stop_loss_usdt, a.take_profit_usdt, a.close_explanation, a.audited_at
FROM crypto_ai.production_exit_audit a
WHERE a.paper_position_id IN (182,172,162,158,143,137)
ORDER BY a.paper_position_id DESC;