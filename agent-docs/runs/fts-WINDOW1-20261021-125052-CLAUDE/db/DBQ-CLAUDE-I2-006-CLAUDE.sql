SELECT p.id, p.symbol, p.status, p.close_reason, p.realized_pnl, s.selected_strategy, s.atr_percent, s.risk_reward_ratio,
       ROUND((s.take_profit - s.latest_price) / s.latest_price * 100, 4) AS planned_tp_pct,
       ROUND((s.latest_price - s.stop_loss) / s.latest_price * 100, 4) AS planned_sl_pct,
       p.opened_at, p.closed_at
FROM paper_position p
JOIN trade_signal s ON s.id = p.signal_id
WHERE p.opened_at >= '2026-10-11 00:00:00'
ORDER BY p.id
LIMIT 200