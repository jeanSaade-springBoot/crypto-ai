SELECT CASE WHEN prev.status='STOPPED' THEN 'AFTER_STOP' ELSE 'AFTER_NON_STOP' END prev_kind,
       COUNT(*) n, SUM(nx.realized_pnl>0) wins, ROUND(SUM(nx.realized_pnl),6) pnl
FROM paper_position nx
JOIN paper_position prev ON prev.symbol=nx.symbol AND prev.closed_at IS NOT NULL
 AND prev.closed_at <= nx.opened_at AND prev.closed_at >= nx.opened_at - INTERVAL 5 MINUTE AND prev.id<>nx.id
WHERE nx.closed_at IS NOT NULL GROUP BY prev_kind LIMIT 10;