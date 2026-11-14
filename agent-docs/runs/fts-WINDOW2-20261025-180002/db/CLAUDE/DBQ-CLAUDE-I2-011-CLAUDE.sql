SELECT p.id, p.symbol, s.take_profit AS signal_tp, p.stop_loss,
 (SELECT MIN(e.observed_at) FROM crypto_ai.market_price_event e WHERE e.symbol = p.symbol AND e.observed_at > p.opened_at AND e.observed_at <= p.opened_at + INTERVAL 4 HOUR AND e.price >= s.take_profit) AS first_tp_touch,
 (SELECT MIN(e.observed_at) FROM crypto_ai.market_price_event e WHERE e.symbol = p.symbol AND e.observed_at > p.opened_at AND e.observed_at <= p.opened_at + INTERVAL 4 HOUR AND e.price <= p.stop_loss) AS first_sl_touch,
 (SELECT MIN(e.price) FROM crypto_ai.market_price_event e WHERE e.symbol = p.symbol AND e.observed_at BETWEEN p.closed_at AND p.closed_at + INTERVAL 60 MINUTE) AS post60_min,
 (SELECT MAX(e.price) FROM crypto_ai.market_price_event e WHERE e.symbol = p.symbol AND e.observed_at BETWEEN p.closed_at AND p.closed_at + INTERVAL 60 MINUTE) AS post60_max
FROM crypto_ai.paper_position p JOIN crypto_ai.trade_signal s ON s.id = p.signal_id
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC
LIMIT 6;