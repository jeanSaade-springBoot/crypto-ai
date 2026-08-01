ALTER TABLE market_fundamental
    ADD COLUMN max_supply DECIMAL(38, 8) NULL AFTER total_supply,
    ADD COLUMN tier1_exchange_count INT NULL AFTER max_supply,
    ADD COLUMN exchange_count INT NULL AFTER tier1_exchange_count;
