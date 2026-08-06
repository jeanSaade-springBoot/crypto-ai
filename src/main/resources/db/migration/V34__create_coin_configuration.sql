CREATE TABLE crypto_ai.coin_configuration (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    system_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_coin_configuration_symbol (symbol)
);

INSERT INTO crypto_ai.coin_configuration(symbol, enabled, system_default) VALUES
('BTCUSDT', 1, 1),
('ETHUSDT', 1, 1),
('BNBUSDT', 1, 1),
('XRPUSDT', 1, 1),
('SHIBUSDT', 1, 1),
('PEPEUSDT', 1, 1),
('XLMUSDT', 1, 1)
ON DUPLICATE KEY UPDATE system_default = VALUES(system_default);
