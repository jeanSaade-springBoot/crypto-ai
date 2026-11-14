SELECT id, signal_id, position_analysis_id, symbol, side, quantity, price_usdt, decision_price_usdt,
       execution_price_observed_at, UNIX_TIMESTAMP(execution_price_observed_at) AS obs_epoch,
       gross_amount_usdt, fee_usdt, net_amount_usdt, cost_basis_usdt,
       realized_pnl_usdt, realized_pnl_percent, execution_type, execution_reason, status,
       executed_at, UNIX_TIMESTAMP(executed_at) AS exec_epoch, LEFT(notes, 300) AS notes_head
FROM wallet_trade
WHERE (symbol='EGLDUSDT' AND executed_at BETWEEN '2026-10-11 20:30:00' AND '2026-10-11 22:50:00')
   OR (symbol='XRPUSDT'  AND executed_at BETWEEN '2026-10-12 02:30:00' AND '2026-10-12 03:35:00')
   OR (symbol='DOGEUSDT' AND executed_at BETWEEN '2026-10-12 04:00:00' AND '2026-10-12 04:30:00')
   OR (symbol='SHIBUSDT' AND executed_at BETWEEN '2026-10-12 06:30:00' AND '2026-10-12 07:20:00')
   OR (symbol='ACEUSDT'  AND executed_at BETWEEN '2026-10-12 09:50:00' AND '2026-10-12 10:10:00')
ORDER BY symbol, executed_at
LIMIT 100;