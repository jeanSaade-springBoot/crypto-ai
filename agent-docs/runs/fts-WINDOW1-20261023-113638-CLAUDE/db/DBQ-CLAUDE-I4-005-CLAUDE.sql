SELECT pp.id, pp.exit_price, ts.latest_price, ts.generated_at FROM paper_position pp JOIN trade_signal ts ON ts.id=168277 WHERE pp.id=162;
SELECT id, observed_at, price, created_at FROM market_price_event
WHERE symbol='DOGEUSDT' AND observed_at BETWEEN '2026-10-12 04:25:58' AND '2026-10-12 04:26:06' ORDER BY observed_at LIMIT 20;