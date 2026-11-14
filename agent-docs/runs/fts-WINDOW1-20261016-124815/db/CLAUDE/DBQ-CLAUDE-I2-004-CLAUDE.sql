SELECT id, observed_at, source_received_at, created_at, price, source, source_sequence,
       delivery_status, ordering_slot, outcome_reason
FROM crypto_ai.market_price_event
WHERE symbol = 'EGLDUSDT'
  AND observed_at BETWEEN '2026-10-11 22:38:30' AND '2026-10-11 22:43:45'
ORDER BY observed_at, id
LIMIT 300;