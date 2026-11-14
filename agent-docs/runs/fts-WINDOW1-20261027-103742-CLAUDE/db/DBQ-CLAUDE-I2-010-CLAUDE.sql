SELECT id, wallet_position_id, trade_signal_id, symbol, interval_code, current_price_usdt, unrealized_pnl_percent, holding_minutes, trend_deterioration_score, momentum_exhaustion_score, profit_protection_score, risk_event_score, exit_score, recommendation, confidence, LEFT(explanation, 600) AS explanation, analyzed_at
FROM position_analysis
WHERE id IN (2632,2730,3308,3386,3591,3749)
ORDER BY id