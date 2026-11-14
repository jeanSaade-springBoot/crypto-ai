SELECT wt.id, wt.symbol, wt.executed_at, wt.execution_price_observed_at, wt.price_usdt, e.id later_tick, e.observed_at, e.created_at, e.price
FROM wallet_trade wt JOIN market_price_event e ON e.symbol=wt.symbol AND e.delivery_status='APPLIED'
 AND e.observed_at > wt.execution_price_observed_at AND e.created_at < wt.executed_at
WHERE wt.side='BUY' AND wt.executed_at >= '2026-10-06 00:00:00' ORDER BY wt.id LIMIT 100;