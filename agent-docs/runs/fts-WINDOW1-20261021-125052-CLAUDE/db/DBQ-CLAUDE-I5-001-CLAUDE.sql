SELECT r.id AS ref_signal_id, s.id, s.symbol, s.generated_at, s.latest_price, s.decision,
       TIMESTAMPDIFF(SECOND, s.generated_at, r.generated_at) AS secs_before_ref
FROM trade_signal r
JOIN trade_signal s ON s.symbol = r.symbol AND s.interval_code = '1m'
 AND s.generated_at <= r.generated_at AND s.generated_at >= r.generated_at - INTERVAL 30 MINUTE
WHERE r.id IN (156723,159408,165412,167646,167668,171589,176692,176710)
ORDER BY r.id, s.generated_at DESC
LIMIT 300