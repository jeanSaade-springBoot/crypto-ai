SELECT pp.id, pp.symbol, pp.opened_at, pp.realized_pnl, pp.close_reason, wmp.entry_stage, wmp.entry_quality_score,
       f.id AS five_id, f.decision AS five_decision, f.original_decision AS five_original,
       f.final_entry_allowed AS five_entry_allowed, f.confidence_score AS five_confidence
FROM crypto_ai.paper_position pp
JOIN crypto_ai.trade_signal s1 ON s1.id = pp.signal_id
LEFT JOIN crypto_ai.wallet_managed_position wmp ON wmp.entry_signal_id = pp.signal_id
LEFT JOIN crypto_ai.trade_signal f ON f.id = (
     SELECT f2.id FROM crypto_ai.trade_signal f2
     WHERE f2.symbol = s1.symbol AND f2.interval_code = '5m' AND f2.generated_at <= s1.generated_at
     ORDER BY f2.generated_at DESC, f2.id DESC LIMIT 1)
WHERE pp.status IN ('CLOSED','STOPPED') AND pp.opened_at >= '2026-10-05 00:00:00'
ORDER BY pp.id
LIMIT 300;