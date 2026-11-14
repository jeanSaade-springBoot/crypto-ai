SELECT id, symbol, observed_at, source_received_at, created_at, price, source, delivery_status, ordering_slot, outcome_reason
FROM crypto_ai.market_price_event
WHERE (symbol = 'EGLDUSDT' AND observed_at BETWEEN '2026-10-11 22:39:00' AND '2026-10-11 22:43:40')
   OR (symbol = 'EGLDUSDT' AND observed_at BETWEEN '2026-10-11 20:54:50' AND '2026-10-11 21:00:15')
   OR (symbol = 'ACEUSDT' AND observed_at BETWEEN '2026-10-12 09:56:50' AND '2026-10-12 10:05:25')
ORDER BY symbol, observed_at
LIMIT 300;