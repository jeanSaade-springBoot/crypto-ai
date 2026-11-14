SELECT pp.id pp_id, COUNT(*) ticks, MAX(g.gap_s) max_gap_s, SUM(g.gap_s>=4) gaps_ge_4s, SUM(g.gap_s>=10) gaps_ge_10s
FROM paper_position pp
JOIN (SELECT symbol, observed_at,
             TIMESTAMPDIFF(MICROSECOND, LAG(observed_at) OVER (PARTITION BY symbol ORDER BY observed_at), observed_at)/1000000 gap_s
      FROM market_price_event
      WHERE symbol IN ('ACEUSDT','SHIBUSDT','DOGEUSDT','XRPUSDT','EGLDUSDT')
        AND observed_at BETWEEN '2026-10-11 20:50:00' AND '2026-10-12 10:10:00') g
  ON g.symbol=pp.symbol AND g.observed_at BETWEEN pp.opened_at AND pp.closed_at
WHERE pp.id IN (182,172,162,158,143,137) GROUP BY pp.id ORDER BY pp.id DESC;