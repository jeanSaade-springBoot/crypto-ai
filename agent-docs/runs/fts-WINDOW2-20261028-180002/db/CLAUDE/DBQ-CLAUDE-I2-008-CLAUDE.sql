SELECT p.id AS pid, s.id, s.interval_code, s.decision, s.original_decision,
       UNIX_TIMESTAMP(s.generated_at) AS gen_epoch, s.latest_price, s.total_score
FROM paper_position p
JOIN trade_signal s
  ON s.symbol = p.symbol AND s.generated_at BETWEEN p.opened_at - INTERVAL 5 MINUTE AND p.closed_at
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC, s.generated_at
LIMIT 400;
SELECT symbol, interval_code, UNIX_TIMESTAMP(open_time) AS open_epoch, open_price, high_price, low_price,
       close_price, closed, ingestion_source, UNIX_TIMESTAMP(updated_at) AS updated_epoch
FROM candle
WHERE symbol = 'EGLDUSDT' AND interval_code = '1m'
  AND ((open_time BETWEEN '2026-10-11 20:53:00' AND '2026-10-11 21:01:00')
    OR (open_time BETWEEN '2026-10-11 22:35:00' AND '2026-10-11 22:45:00'))
ORDER BY open_time
LIMIT 40;