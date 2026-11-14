SELECT p.id, p.symbol, p.realized_pnl, p.status, f.id AS five_id, f.decision, f.original_decision
FROM crypto_ai.paper_position p
JOIN crypto_ai.trade_signal s ON s.id = p.signal_id
JOIN crypto_ai.trade_signal f ON f.id = (
  SELECT f2.id FROM crypto_ai.trade_signal f2
  WHERE f2.symbol = p.symbol AND f2.interval_code = '5m' AND f2.generated_at <= s.generated_at
    AND f2.generated_at >= s.generated_at - INTERVAL 20 MINUTE
  ORDER BY f2.generated_at DESC LIMIT 1)
WHERE p.closed_at IS NOT NULL AND f.decision NOT IN ('SELL','STRONG_SELL') AND f.original_decision IN ('SELL','STRONG_SELL')
ORDER BY p.id DESC
LIMIT 100;