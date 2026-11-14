SELECT id, observed_at, UNIX_TIMESTAMP(observed_at) AS obs_epoch, price, source, delivery_status,
       ordering_slot, outcome_reason, source_event_id, source_sequence,
       UNIX_TIMESTAMP(source_received_at) AS recv_epoch, UNIX_TIMESTAMP(created_at) AS created_epoch
FROM market_price_event
WHERE symbol = 'EGLDUSDT'
  AND observed_at BETWEEN '2026-10-11 22:42:50' AND '2026-10-11 22:43:40'
ORDER BY observed_at, id
LIMIT 200;