ALTER TABLE wallet_settings
    ADD COLUMN maximum_daily_new_positions INT NOT NULL DEFAULT 6 AFTER minimum_usdt_reserve;

CREATE TABLE wallet_daily_statistics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    maximum_new_positions INT NOT NULL,
    daily_trade_budget_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    executed_buys INT NOT NULL DEFAULT 0,
    starting_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    ending_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    starting_portfolio_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    ending_portfolio_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_daily_statistics_date (trade_date)
);

UPDATE wallet_settings
SET maximum_daily_new_positions = 6
WHERE id = 1;
