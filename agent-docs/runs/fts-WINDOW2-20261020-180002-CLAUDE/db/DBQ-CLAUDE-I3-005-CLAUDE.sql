DESCRIBE crypto_ai.fix124_protection_incident;
SELECT * FROM crypto_ai.fix124_protection_incident WHERE observed_at BETWEEN FROM_UNIXTIME(1791741000) AND FROM_UNIXTIME(1791789000) ORDER BY observed_at LIMIT 50;
SHOW TABLES FROM crypto_ai LIKE '%reconcil%';