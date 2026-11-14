SELECT 'WORK' AS src, signal_id AS id, symbol, interval_code AS detail, status, failure_stage AS extra, updated_at AS ts, LEFT(error_message, 300) AS msg
FROM crypto_ai.signal_processing_work
WHERE status IN ('REVIEW_REQUIRED','RETRYABLE_FAILURE') AND updated_at >= '2026-10-05 00:00:00'
UNION ALL
SELECT 'TRADE', id, symbol, side, status, execution_reason, executed_at, CAST(signal_id AS CHAR)
FROM crypto_ai.wallet_trade
WHERE executed_at BETWEEN '2026-10-12 09:55:50' AND '2026-10-12 09:57:10'
ORDER BY ts
LIMIT 200;