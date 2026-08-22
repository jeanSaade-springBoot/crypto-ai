-- FIX-055: preserve the real price anchor of long-lived BUY opportunities.
-- MySQL/Binance timestamps remain UTC; this migration adds price evidence only.
ALTER TABLE execution_opportunity
    ADD COLUMN anchor_entry_price DECIMAL(30,12) NULL AFTER last_evidence_at,
    ADD COLUMN best_entry_price DECIMAL(30,12) NULL AFTER anchor_entry_price;
