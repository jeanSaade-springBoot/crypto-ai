SELECT id, wallet_position_id, trade_signal_id, interval_code, current_price_usdt, unrealized_pnl_percent, holding_minutes, trend_deterioration_score, momentum_exhaustion_score, profit_protection_score, risk_event_score, exit_score, recommendation, confidence, advisory_only, LEFT(explanation, 500) AS expl_head, analyzed_at
FROM crypto_ai.position_analysis
WHERE wallet_position_id IN (211,201,191,187,172,166)
ORDER BY wallet_position_id DESC, analyzed_at
LIMIT 300;