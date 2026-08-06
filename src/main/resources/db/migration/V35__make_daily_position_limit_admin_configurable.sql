UPDATE crypto_ai.wallet_settings
SET maximum_daily_new_positions = 0
WHERE id = 1
  AND maximum_daily_new_positions = 6;

UPDATE crypto_ai.wallet_daily_statistics
SET maximum_new_positions = 0
WHERE trade_date = CURRENT_DATE
  AND maximum_new_positions = 6;
