SELECT table_name, ordinal_position, column_name, column_type
FROM information_schema.columns
WHERE table_schema = 'crypto_ai'
  AND table_name IN ('paper_position','production_exit_audit','wallet_trade','signal_processing_work','market_price_event','wallet_managed_position','buy_htf_assessment_audit')
ORDER BY table_name, ordinal_position;