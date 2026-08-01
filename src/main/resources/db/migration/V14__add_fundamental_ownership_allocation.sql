ALTER TABLE market_fundamental
    ADD COLUMN team_supply DECIMAL(38, 8) NULL,
    ADD COLUMN treasury_supply DECIMAL(38, 8) NULL,
    ADD COLUMN private_investor_supply DECIMAL(38, 8) NULL,
    ADD COLUMN locked_supply DECIMAL(38, 8) NULL;
