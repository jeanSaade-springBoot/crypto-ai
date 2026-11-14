SELECT r.id AS ref_signal_id, p.id AS position_id, p.symbol, p.status, p.opened_at, p.closed_at
FROM trade_signal r
JOIN paper_position p ON p.opened_at <= r.generated_at + INTERVAL 10 SECOND
 AND (p.closed_at IS NULL OR p.closed_at >= r.generated_at)
WHERE r.id IN (167646,167668,176692,176710)
ORDER BY r.id, p.opened_at;
SELECT id, signal_id, symbol, side, status, executed_at FROM wallet_trade WHERE signal_id IN (167646,176692,167668,176710) ORDER BY id