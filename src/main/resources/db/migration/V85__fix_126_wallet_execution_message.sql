-- FIX-126: signal #634982 BUY rolled back because its audit explanation exceeded
-- VARCHAR(1000). Preserve complete BUY/SELL explanations; do not truncate lineage.
ALTER TABLE wallet_trade
    MODIFY COLUMN execution_message MEDIUMTEXT
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;
