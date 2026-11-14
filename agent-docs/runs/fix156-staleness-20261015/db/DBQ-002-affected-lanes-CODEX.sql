SELECT UTC_TIMESTAMP(6) AS db_utc_now, CONVERT_TZ(UTC_TIMESTAMP(6), '+00:00', '+03:00') AS db_ksa_now;
SELECT c.symbol, c.interval_code, MAX(c.close_time) AS last_closed_utc,
       CONVERT_TZ(MAX(c.close_time), '+00:00', '+03:00') AS last_closed_ksa,
       TIMESTAMPDIFF(MINUTE, MAX(c.close_time), UTC_TIMESTAMP()) AS candle_age_min,
       SUM(c.closed=0) AS open_rows
FROM candle c
WHERE c.symbol IN ('SHIBUSDT','SOLUSDT','SUIUSDT','TAOUSDT','UNIUSDT','XLMUSDT','XRPUSDT')
  AND c.interval_code IN ('1m','1h')
GROUP BY c.symbol,c.interval_code ORDER BY c.symbol,c.interval_code;
SELECT s.symbol, s.interval_code, MAX(s.generated_at) AS last_signal_utc,
       CONVERT_TZ(MAX(s.generated_at), '+00:00', '+03:00') AS last_signal_ksa,
       TIMESTAMPDIFF(MINUTE, MAX(s.generated_at), UTC_TIMESTAMP()) AS signal_age_min,
       COUNT(*) AS signals_4h
FROM trade_signal s
WHERE s.symbol IN ('SOLUSDT','SUIUSDT','TAOUSDT','UNIUSDT','XLMUSDT','XRPUSDT')
  AND s.interval_code='1h' AND s.generated_at >= UTC_TIMESTAMP() - INTERVAL 12 HOUR
GROUP BY s.symbol,s.interval_code ORDER BY s.symbol;
SELECT w.symbol,w.interval_code,w.status,w.origin,w.attempts,w.candle_open_time,w.updated_at,w.failure_stage,LEFT(w.error_message,160) AS error_message
FROM signal_processing_work w
WHERE w.symbol IN ('SOLUSDT','SUIUSDT','TAOUSDT','UNIUSDT','XLMUSDT','XRPUSDT') AND w.interval_code='1h'
ORDER BY w.updated_at DESC LIMIT 100;
SELECT o.symbol,o.interval_code,o.outcome,o.reason_code,o.created_at,
       CONVERT_TZ(o.created_at, '+00:00', '+03:00') AS created_ksa
FROM candle_analysis_outcome o
WHERE o.symbol IN ('SOLUSDT','SUIUSDT','TAOUSDT','UNIUSDT','XLMUSDT','XRPUSDT') AND o.interval_code='1h'
ORDER BY o.created_at DESC LIMIT 100;
SELECT symbol,direction,status,decision_code,last_evidence_at,created_at
FROM execution_opportunity
WHERE decision_code='MISSING_CONTEXT' AND created_at >= UTC_TIMESTAMP() - INTERVAL 1 DAY
ORDER BY created_at DESC LIMIT 20;