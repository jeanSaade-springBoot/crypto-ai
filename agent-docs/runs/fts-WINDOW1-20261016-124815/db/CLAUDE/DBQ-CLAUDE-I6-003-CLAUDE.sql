SELECT CASE WHEN x.bear10 >= 6 THEN 'a_6+' WHEN x.bear10 >= 3 THEN 'b_3-5' ELSE 'c_0-2' END AS bear_bucket,
       COUNT(*) AS trades, SUM(x.realized_pnl < 0) AS losses, ROUND(SUM(x.realized_pnl), 6) AS pnl_usdt,
       GROUP_CONCAT(CASE WHEN x.id IN (182,172,162,158,143,137) THEN x.id END) AS study_ids
FROM (SELECT pp.id, pp.realized_pnl,
             (SELECT COUNT(*) FROM crypto_ai.trade_signal t
               WHERE t.symbol = s1.symbol AND t.interval_code = '1m'
                 AND t.generated_at >= s1.generated_at - INTERVAL 10 MINUTE AND t.generated_at < s1.generated_at
                 AND (t.decision IN ('SELL','STRONG_SELL') OR t.original_decision IN ('SELL','STRONG_SELL'))) AS bear10
      FROM crypto_ai.paper_position pp
      JOIN crypto_ai.trade_signal s1 ON s1.id = pp.signal_id
      WHERE pp.status IN ('CLOSED','STOPPED') AND pp.opened_at >= '2026-10-05 00:00:00') x
GROUP BY bear_bucket ORDER BY bear_bucket;