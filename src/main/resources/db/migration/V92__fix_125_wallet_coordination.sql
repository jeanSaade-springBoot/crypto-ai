-- FIX-125: persistent rows survive flat positions; locks belong to the transaction.
CREATE TABLE wallet_symbol_coordination(symbol VARCHAR(30) NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT IGNORE INTO wallet_symbol_coordination SELECT symbol FROM coin_configuration;
INSERT IGNORE INTO wallet_symbol_coordination SELECT DISTINCT symbol FROM wallet_managed_position;
INSERT IGNORE INTO wallet_symbol_coordination SELECT CONCAT(symbol,'USDT') FROM wallet_asset WHERE symbol<>'USDT';
-- FIX-125/134: replaces body-only serialization for wallet mutations, not analysis.
-- Also protects absent daily-statistics/cash/asset initialization across writers.
CREATE TABLE wallet_mutation_coordination(id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO wallet_mutation_coordination VALUES(1);
