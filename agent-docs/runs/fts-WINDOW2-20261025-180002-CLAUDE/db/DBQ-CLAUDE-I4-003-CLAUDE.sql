SELECT w.id AS wallet_id, w.symbol, w.execution_reason, s.id AS sig_1m, s.generated_at AS gen_1m, s.confluence_higher_decision, s.confluence_higher_signal_generated_at,
 f.id AS five_same_boundary, f.generated_at AS gen_5m, f.decision AS five_decision, f.original_decision AS five_original
FROM crypto_ai.wallet_trade w
JOIN crypto_ai.trade_signal s ON s.id = w.signal_id
LEFT JOIN crypto_ai.trade_signal f ON f.symbol = s.symbol AND f.interval_code = '5m' AND f.candle_open_time = s.candle_open_time - INTERVAL 4 MINUTE
WHERE w.side = 'BUY' AND w.status = 'EXECUTED' AND s.interval_code = '1m' AND MINUTE(s.candle_open_time) % 5 = 4
ORDER BY w.executed_at DESC
LIMIT 100;