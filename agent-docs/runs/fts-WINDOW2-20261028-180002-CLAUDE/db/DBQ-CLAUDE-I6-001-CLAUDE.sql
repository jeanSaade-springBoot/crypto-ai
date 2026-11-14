SELECT id, symbol, status, latest_signal_id, UNIX_TIMESTAMP(started_at) AS started_epoch,
       anchor_entry_price, best_entry_price, evidence_count, buy_count, watch_count, neutral_count,
       bearish_count, five_minute_decision, one_hour_decision, execution_source,
       recommended_position_percent, decision_code, UNIX_TIMESTAMP(executed_at) AS executed_epoch,
       LEFT(decision_explanation, 500) AS expl
FROM execution_opportunity
WHERE latest_signal_id IN (156723,159408,165412,171589)
   OR (symbol='EGLDUSDT' AND started_at BETWEEN FROM_UNIXTIME(1791740000) AND FROM_UNIXTIME(1791747700)
       AND status <> 'CANCELLED')
   OR (symbol='XRPUSDT' AND started_at BETWEEN FROM_UNIXTIME(1791754500) AND FROM_UNIXTIME(1791761710))
ORDER BY started_at DESC
LIMIT 80;