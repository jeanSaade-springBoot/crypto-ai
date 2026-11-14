SELECT p.id AS position_id, p.symbol, p.signal_id, w.id AS wallet_trade_id, w.execution_reason,
       UNIX_TIMESTAMP(w.executed_at) AS exec_epoch, p.close_reason, p.realized_pnl
FROM paper_position p
JOIN wallet_trade w ON w.signal_id = p.signal_id AND w.side = 'BUY'
WHERE p.id IN (191,163,160,143,100,42,20,6)
ORDER BY p.id DESC
LIMIT 20;