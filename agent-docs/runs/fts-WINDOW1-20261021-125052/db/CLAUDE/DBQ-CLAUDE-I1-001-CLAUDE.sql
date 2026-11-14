SELECT table_name, ordinal_position, column_name, column_type
FROM information_schema.columns
WHERE table_schema = 'crypto_ai'
  AND table_name IN ('trade_signal','wallet_trade','wallet_managed_position','production_exit_audit','market_price_event','signal_processing_work','position_management_event','execution_opportunity','position_analysis')
ORDER BY table_name, ordinal_position