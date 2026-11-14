SELECT
    p.id AS paper_position_id,
    p.symbol,
    p.opened_at AS paper_opened_at_utc,
    p.closed_at AS paper_closed_at_utc,
    p.stop_loss,
    p.take_profit,
    p.exit_price,
    p.close_reason,
    mpe.*
FROM crypto_ai.paper_position p
JOIN crypto_ai.market_price_event mpe
  ON mpe.symbol = p.symbol
 AND mpe.observed_at >= p.opened_at
 AND mpe.observed_at <= p.closed_at
WHERE p.id IN (182, 172, 162, 158, 143, 137)
ORDER BY p.id, mpe.observed_at, mpe.id
LIMIT 5000;