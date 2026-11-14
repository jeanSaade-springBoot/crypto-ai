SELECT wt.id AS wallet_trade_id, wt.symbol, wt.side, wt.executed_at, wt.created_at,
       (SELECT MIN(s.captured_at) FROM crypto_ai.wallet_snapshot s
         WHERE s.captured_at BETWEEN wt.executed_at AND wt.executed_at + INTERVAL 10 SECOND) AS first_snapshot_after,
       (SELECT COUNT(*) FROM crypto_ai.wallet_asset) AS wallet_asset_rows
FROM crypto_ai.wallet_trade wt
WHERE wt.id IN (451,454,426,429,403,407,392,401,358,359,344,346)
ORDER BY wt.id;