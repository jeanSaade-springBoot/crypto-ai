SELECT wt.*
FROM wallet_trade wt
JOIN paper_position pp
  ON pp.symbol = wt.symbol
 AND wt.executed_at BETWEEN pp.opened_at - INTERVAL 60 SECOND AND pp.closed_at + INTERVAL 60 SECOND
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY wt.symbol, wt.executed_at
LIMIT 60