SELECT pp.id AS paper_position_id, wt.id, wt.signal_id, wt.execution_key, wt.symbol, wt.side,
       wt.quantity, wt.price_usdt, wt.decision_price_usdt, wt.execution_price_observed_at,
       wt.gross_amount_usdt, wt.fee_usdt, wt.net_amount_usdt, wt.cost_basis_usdt,
       wt.realized_pnl_usdt, wt.realized_pnl_percent, wt.execution_type, wt.status,
       wt.executed_at, LEFT(wt.notes, 300) AS notes_300
FROM crypto_ai.paper_position pp
JOIN crypto_ai.wallet_trade wt
  ON wt.symbol = pp.symbol
 AND wt.executed_at BETWEEN pp.opened_at - INTERVAL 2 MINUTE AND pp.closed_at + INTERVAL 2 MINUTE
WHERE pp.id IN (172,162,158,143,137,131)
ORDER BY pp.id, wt.executed_at
LIMIT 200;