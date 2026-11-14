SELECT p.id AS position_id, m.id AS event_id, m.wallet_position_id, m.event_type, m.old_value_usdt, m.new_value_usdt, m.market_price_usdt, LEFT(m.reason, 400) AS reason_head, m.occurred_at
FROM crypto_ai.paper_position p
JOIN crypto_ai.position_management_event m ON m.symbol = p.symbol AND m.occurred_at BETWEEN p.opened_at - INTERVAL 1 MINUTE AND p.closed_at + INTERVAL 1 MINUTE
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC, m.occurred_at
LIMIT 200;