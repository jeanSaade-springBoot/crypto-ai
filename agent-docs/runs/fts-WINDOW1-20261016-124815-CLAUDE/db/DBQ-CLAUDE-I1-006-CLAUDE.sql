SELECT pp.id AS position_id, wt.id AS wallet_trade_id, wt.signal_id, wt.execution_key, wt.position_analysis_id,
       wt.side, wt.quantity, wt.price_usdt, wt.decision_price_usdt, wt.execution_price_observed_at,
       wt.gross_amount_usdt, wt.fee_usdt, wt.net_amount_usdt, wt.cost_basis_usdt, wt.realized_pnl_usdt,
       wt.realized_pnl_percent, wt.execution_type, wt.execution_reason, wt.status, wt.executed_at,
       LEFT(wt.execution_message, 300) AS execution_message_head
FROM crypto_ai.paper_position pp
JOIN crypto_ai.wallet_trade wt
  ON wt.symbol = pp.symbol
 AND wt.executed_at BETWEEN pp.opened_at - INTERVAL 60 SECOND AND pp.closed_at + INTERVAL 60 SECOND
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id DESC, wt.executed_at
LIMIT 200;