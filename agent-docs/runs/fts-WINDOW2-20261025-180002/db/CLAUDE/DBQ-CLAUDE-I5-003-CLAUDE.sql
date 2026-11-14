SELECT execution_reason, eq_class, COUNT(*) AS n, SUM(realized_pnl > 0) AS wins, SUM(realized_pnl < 0) AS losses, ROUND(SUM(realized_pnl), 6) AS pnl_sum
FROM (
  SELECT p.id, p.realized_pnl, w.execution_reason,
   CASE
     WHEN w.execution_message LIKE '%BALANCED_NEUTRAL_5M_WATCH_1H%' THEN CONCAT('FIX11G_EQ', COALESCE(REGEXP_SUBSTR(REGEXP_SUBSTR(w.execution_message, 'entryQuality=[0-9]+'), '[0-9]+'), '?'))
     WHEN w.execution_message REGEXP 'Entry Quality [0-9]+/100' THEN REGEXP_SUBSTR(w.execution_message, '(EXCELLENT|GOOD|ACCEPTABLE|LATE)_ENTRY')
     ELSE 'NOT_PRINTED'
   END AS eq_class
  FROM crypto_ai.paper_position p
  JOIN crypto_ai.wallet_trade w ON w.signal_id = p.signal_id AND w.side = 'BUY' AND w.status = 'EXECUTED'
  WHERE p.status IN ('CLOSED','STOPPED') AND p.closed_at IS NOT NULL
) t
GROUP BY execution_reason, eq_class
ORDER BY execution_reason, eq_class
LIMIT 80;