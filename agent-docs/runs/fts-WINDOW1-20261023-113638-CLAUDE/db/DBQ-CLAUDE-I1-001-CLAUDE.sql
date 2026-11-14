SELECT table_name, ordinal_position, column_name, column_type
FROM information_schema.columns
WHERE table_schema = 'crypto_ai'
  AND table_name IN ('paper_position','production_exit_audit','wallet_trade','wallet_managed_position',
                     'signal_processing_work','execution_opportunity','market_price_event','trade_signal',
                     'position_management_event','buy_htf_assessment_audit')
ORDER BY table_name, ordinal_position;
SELECT installed_rank, version, description, installed_on, success
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 15;