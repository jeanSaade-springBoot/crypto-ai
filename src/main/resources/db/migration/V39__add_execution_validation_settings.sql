ALTER TABLE crypto_ai.wallet_settings
    ADD COLUMN require_new_buy_transition TINYINT(1) NOT NULL DEFAULT 1 AFTER dashboard_intervals;
