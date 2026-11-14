SELECT p.id AS position_id, w.id AS wallet_trade_id, w.signal_id, w.execution_key,
       w.side, w.quantity, w.price_usdt, w.decision_price_usdt,
       w.execution_price_observed_at, w.executed_at,
       TIMESTAMPDIFF(MICROSECOND, w.execution_price_observed_at, w.executed_at)/1e6 AS fill_age_s,
       w.gross_amount_usdt, w.fee_usdt, w.net_amount_usdt, w.cost_basis_usdt,
       w.realized_pnl_usdt, w.realized_pnl_percent, w.execution_type,
       w.execution_reason, w.status, LEFT(w.notes, 300) AS notes_head
FROM paper_position p
JOIN wallet_trade w
  ON w.symbol = p.symbol
 AND w.executed_at BETWEEN p.opened_at - INTERVAL 2 MINUTE AND p.closed_at + INTERVAL 2 MINUTE
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC, w.executed_at
LIMIT 200;