SELECT pp.id AS pp_id, wt.id, wt.signal_id, wt.execution_key, wt.side, wt.quantity, wt.price_usdt,
       wt.gross_amount_usdt, wt.fee_usdt, wt.net_amount_usdt, wt.cost_basis_usdt, wt.realized_pnl_usdt,
       wt.realized_pnl_percent, wt.execution_type, wt.status, wt.executed_at, LEFT(wt.notes, 300) AS notes
FROM paper_position pp
JOIN wallet_trade wt ON wt.symbol = pp.symbol
 AND wt.executed_at BETWEEN pp.opened_at - INTERVAL 2 MINUTE AND pp.closed_at + INTERVAL 2 MINUTE
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id DESC, wt.executed_at LIMIT 200;