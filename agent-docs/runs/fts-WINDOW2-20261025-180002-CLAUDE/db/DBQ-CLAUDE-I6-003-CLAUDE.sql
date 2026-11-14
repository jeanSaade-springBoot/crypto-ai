SELECT p.id, p.entry_price, p.stop_loss, p.take_profit, s.id AS sig, s.latest_price, s.atr_at_signal, s.atr_percent, s.risk_reward_ratio
FROM crypto_ai.paper_position p JOIN crypto_ai.trade_signal s ON s.id = p.signal_id
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC
LIMIT 6;