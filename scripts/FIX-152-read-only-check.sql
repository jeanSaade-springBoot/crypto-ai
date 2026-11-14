-- FIX-152 small indexed lookups; read-only checks after local candidate startup.
SET SESSION time_zone = '+00:00';
SELECT version,description,success FROM crypto_ai.flyway_schema_history WHERE version IN ('104','105');
SELECT s.symbol,s.active_delivery_state,s.active_delivery_event_id,
       d.phase,d.status,d.protection_started_at,d.protection_completed_at,
       d.protection_outcome,p.delivery_status
FROM crypto_ai.shared_market_consumer_state s
LEFT JOIN crypto_ai.shared_market_event_delivery d ON d.source_event_id=s.active_delivery_event_id
LEFT JOIN crypto_ai.market_price_event p ON p.source_event_id=d.source_event_id
WHERE s.symbol='BTCUSDT';
SELECT source_event_id,operation,old_owner_token,new_owner_token,evidence,
       created_at AS audited_utc,DATE_ADD(created_at,INTERVAL 3 HOUR) AS audited_ksa
FROM crypto_ai.shared_delivery_resolution_audit
WHERE reviewer='FIX152_SYSTEM' ORDER BY id DESC LIMIT 10;
SELECT symbol,candle_open_time,generated_at,btc_context_status,final_entry_allowed
FROM crypto_ai.trade_signal
WHERE symbol='BTCUSDT' AND interval_code='1m'
ORDER BY candle_open_time DESC LIMIT 3;
