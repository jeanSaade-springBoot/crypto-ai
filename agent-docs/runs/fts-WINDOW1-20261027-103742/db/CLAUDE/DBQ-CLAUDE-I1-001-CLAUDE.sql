SELECT table_name, ordinal_position, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'crypto_ai'
  AND table_name IN ('wallet_trade','trade_signal','production_exit_audit','wallet_managed_position','market_price_event','candle','signal_processing_work','position_analysis')
ORDER BY table_name, ordinal_position
LIMIT 600