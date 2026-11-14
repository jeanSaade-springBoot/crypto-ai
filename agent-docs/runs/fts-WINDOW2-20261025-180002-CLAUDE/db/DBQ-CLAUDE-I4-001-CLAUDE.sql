SELECT id, interval_code, candle_open_time, generated_at, decision, original_decision, selected_strategy, confluence_status, confluence_higher_interval, confluence_higher_decision, confluence_higher_trend_score, confluence_higher_signal_generated_at, LEFT(confluence_explanation, 600) AS conf_expl
FROM crypto_ai.trade_signal
WHERE symbol = 'ACEUSDT' AND interval_code IN ('5m','15m') AND generated_at BETWEEN '2026-10-12 09:00:00' AND '2026-10-12 10:06:00'
ORDER BY generated_at
LIMIT 40;