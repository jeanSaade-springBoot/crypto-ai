SELECT p.id, p.symbol, p.status, p.close_reason, ROUND(p.realized_pnl,6) AS pnl,
       o.execution_source, o.decision_code,
       COALESCE(REGEXP_SUBSTR(REGEXP_SUBSTR(o.decision_explanation,'Entry Quality [0-9]+/100'),'[0-9]+'),
                REGEXP_SUBSTR(REGEXP_SUBSTR(o.decision_explanation,'entryQuality=[0-9]+'),'[0-9]+')) AS entry_quality,
       s.decision, s.risk_reward_ratio, s.atr_percent, s.market_regime, s.atr_overextended
FROM paper_position p
JOIN trade_signal s ON s.id = p.signal_id
LEFT JOIN execution_opportunity o ON o.symbol = p.symbol AND o.executed_at = p.opened_at
WHERE p.status IN ('CLOSED','STOPPED') AND p.closed_at IS NOT NULL
  AND UNIX_TIMESTAMP(p.opened_at) >= UNIX_TIMESTAMP('2026-10-11 03:00:00')
ORDER BY p.id
LIMIT 200