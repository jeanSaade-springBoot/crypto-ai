SHOW COLUMNS FROM paper_position;
SELECT p.id, p.status, p.close_reason, ROUND(p.realized_pnl,6) AS pnl, p.entry_price, p.exit_price, p.quantity,
       ROUND(p.entry_price*p.quantity,4) AS entry_notional, s.risk_reward_ratio
FROM paper_position p JOIN trade_signal s ON s.id = p.signal_id
WHERE p.id BETWEEN 102 AND 193 AND p.status IN ('CLOSED','STOPPED') AND p.closed_at IS NOT NULL
ORDER BY p.id
LIMIT 120