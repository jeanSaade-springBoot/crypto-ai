ALTER TABLE crypto_ai.wallet_settings
    ADD COLUMN execution_profile VARCHAR(20) NOT NULL DEFAULT 'BALANCED' AFTER require_new_buy_transition;

UPDATE crypto_ai.wallet_settings
SET execution_profile = 'BALANCED'
WHERE execution_profile IS NULL OR TRIM(execution_profile) = '';
