SELECT
    c.table_name,
    c.ordinal_position,
    c.column_name,
    c.column_type,
    c.is_nullable,
    c.column_key
FROM information_schema.columns c
WHERE c.table_schema = 'crypto_ai'
  AND c.table_name IN (
      'paper_position',
      'production_exit_audit',
      'wallet_managed_position',
      'wallet_trade',
      'trade_signal',
      'market_price_event'
  )
ORDER BY c.table_name, c.ordinal_position;

SELECT
    k.table_name,
    k.constraint_name,
    k.column_name,
    k.referenced_table_name,
    k.referenced_column_name
FROM information_schema.key_column_usage k
WHERE k.table_schema = 'crypto_ai'
  AND (
      k.table_name IN (
          'paper_position',
          'production_exit_audit',
          'wallet_managed_position',
          'wallet_trade',
          'trade_signal',
          'market_price_event'
      )
      OR k.referenced_table_name = 'paper_position'
  )
ORDER BY k.table_name, k.constraint_name, k.ordinal_position;