SELECT
    table_name,
    ordinal_position,
    column_name,
    column_type,
    is_nullable,
    column_key,
    extra
FROM information_schema.columns
WHERE table_schema = 'crypto_ai'
  AND table_name IN (
      'paper_position',
      'wallet_trade',
      'wallet_managed_position',
      'trade_signal',
      'signal_processing_work',
      'market_price_event',
      'production_exit_audit',
      'position_management_event',
      'candle'
  )
ORDER BY table_name, ordinal_position;