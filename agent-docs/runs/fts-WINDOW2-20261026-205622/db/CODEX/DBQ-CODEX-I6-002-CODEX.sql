SELECT
    p.*,
    a.id AS exit_audit_id,
    a.wallet_position_id,
    a.close_trigger,
    a.source_signal_id AS audit_source_signal_id,
    a.source_signal_decision,
    a.source_signal_original_decision,
    a.position_analysis_id,
    a.position_recommendation,
    a.entry_price_usdt AS audit_entry_price,
    a.exit_price_usdt AS audit_exit_price,
    a.stop_loss_usdt AS audit_stop_loss,
    a.take_profit_usdt AS audit_take_profit,
    a.close_explanation,
    a.audited_at,
    entry_signal.generated_at AS entry_signal_generated_at_utc,
    exit_signal.generated_at AS exit_signal_generated_at_utc,
    audit_signal.generated_at AS audit_source_signal_generated_at_utc
FROM crypto_ai.paper_position p
LEFT JOIN crypto_ai.production_exit_audit a
  ON a.paper_position_id = p.id
LEFT JOIN crypto_ai.trade_signal entry_signal
  ON entry_signal.id = p.signal_id
LEFT JOIN crypto_ai.trade_signal exit_signal
  ON exit_signal.id = p.exit_signal_id
LEFT JOIN crypto_ai.trade_signal audit_signal
  ON audit_signal.id = a.source_signal_id
WHERE p.id IN (182, 172, 162, 158, 143, 137)
ORDER BY FIELD(p.id, 182, 172, 162, 158, 143, 137);