SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'crypto_ai'
  AND TABLE_NAME IN ('paper_position','trade_signal','wallet_trade','production_exit_audit','signal_processing_work','market_price_event','wallet_managed_position','execution_opportunity')
ORDER BY TABLE_NAME, ORDINAL_POSITION;