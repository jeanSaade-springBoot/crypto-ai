-- READ ONLY. Trader must be stopped; Collector stays running.
SET SESSION time_zone = '+00:00';
SELECT UTC_TIMESTAMP(6) AS checked_at_utc;
SELECT d.status,d.analysis_status,COUNT(*) AS rows_checked,
 SUM(NOT(d.candle_open_time <=> s.candle_open_time)) AS open_mismatches,
 SUM(NOT(d.candle_close_time <=> s.candle_close_time)) AS close_mismatches,
 SUM(NOT(d.observed_at <=> s.observed_at)) AS observation_mismatches,
 SUM(NOT(d.received_at <=> s.received_at)) AS receipt_mismatches,
 SUM(NOT(d.source_created_at <=> s.created_at)) AS creation_mismatches
FROM crypto_ai.shared_market_event_delivery d
JOIN crypto_ai_v2.market_data_stream_event s ON s.id=d.source_event_id
GROUP BY d.status,d.analysis_status;
SELECT COUNT(*) AS deliveries_without_retained_source
FROM crypto_ai.shared_market_event_delivery d
LEFT JOIN crypto_ai_v2.market_data_stream_event s ON s.id=d.source_event_id
WHERE s.id IS NULL;
SELECT p.delivery_status,COUNT(*) AS price_evidence_rows,
 MIN(TIMESTAMPDIFF(SECOND,s.observed_at,p.observed_at)) AS min_shift_seconds,
 MAX(TIMESTAMPDIFF(SECOND,s.observed_at,p.observed_at)) AS max_shift_seconds
FROM crypto_ai.market_price_event p
JOIN crypto_ai_v2.market_data_stream_event s ON s.id=p.source_event_id
GROUP BY p.delivery_status;
SELECT d.analysis_status,w.status AS work_status,COUNT(*) AS linked_work_rows
FROM crypto_ai.shared_market_event_delivery d
JOIN crypto_ai.signal_processing_work w ON w.source_event_id=d.source_event_id
GROUP BY d.analysis_status,w.status;
-- Retain approvals for comparison to the original approved UTC evidence; do not update them.
SELECT * FROM crypto_ai.shared_market_consumer_state ORDER BY symbol;
SELECT * FROM crypto_ai.shared_market_cutover_approval ORDER BY symbol;
