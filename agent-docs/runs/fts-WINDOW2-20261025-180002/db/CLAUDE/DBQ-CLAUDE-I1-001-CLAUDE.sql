SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'crypto_ai'
  AND TABLE_NAME IN ('paper_position','trade_signal','wallet_trade','wallet_managed_position','production_exit_audit','market_price_event','position_management_event','execution_opportunity','signal_processing_work','position_analysis')
ORDER BY TABLE_NAME, ORDINAL_POSITION
LIMIT 1500;