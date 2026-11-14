-- PROPOSAL ONLY: not executed during the 2026-10-03 latency audit.
-- LOCAL DATABASE ONLY. Remote 169.58.108.119/crypto_ai already has an equivalent
-- (symbol, interval_code, generated_at) index. Do not apply this file remotely.
-- See trade-signal-remote-proposal.sql and the separate remote audit instead.
-- Not a Flyway migration: placing it here prevents automatic deployment.
-- Check SHOW INDEX FROM trade_signal in the intended schema before applying.
-- If adopted, put the approved DDL in a NEW Flyway migration; do not edit an
-- already-applied migration or apply this manually and then duplicate it in Flyway.
-- Online index creation still needs metadata locks and consumes I/O. Benchmark
-- representative reads and insert throughput before/after in the target workload.

-- Priority 1: latest / previous signal within a symbol and timeframe.
-- Priority 2: latest signal and generated-time history across a symbol's timeframes.
-- These are different equality prefixes: interval_code between symbol and
-- generated_at prevents one index from fully serving both ORDER BY patterns.
-- InnoDB implicitly includes the primary key in secondary index records.
ALTER TABLE trade_signal
    ADD INDEX idx_ts_symbol_interval_generated (symbol, interval_code, generated_at),
    ADD INDEX idx_ts_symbol_generated (symbol, generated_at),
    ALGORITHM = INPLACE,
    LOCK = NONE;

-- Conditional priority 3: price-move diagnostics and regression history ordered
-- by candle time across all intervals. Existing (symbol, interval_code,
-- candle_open_time) supports per-interval reads but not this range/order pattern.
-- Apply separately only if these historical reads justify another write index.
-- ALTER TABLE trade_signal
--     ADD INDEX idx_ts_symbol_candle (symbol, candle_open_time),
--     ALGORITHM = INPLACE,
--     LOCK = NONE;

-- Repository companion change needed for blocked BUY/SELL symbol searches:
-- choose a symbol-specific query with `s.symbol = :symbol` when supplied, and a
-- separate all-symbol query when absent. Do not wrap the indexed symbol column
-- in UPPER(). The audited database uses utf8mb4_unicode_ci and all stored symbols
-- are uppercase; retain normalization at API/service boundaries.
-- Preserve original/final decision OR predicates and all audit semantics.

-- Keep the unique candle identity index, the primary key, and V76 health index.
-- Do not drop an index solely because recent Performance Schema usage is zero.
-- idx_trade_signal_symbol_interval is a left-prefix duplicate of the unique
-- candle index; consider removal separately after workload/FK validation.
-- Refresh optimizer statistics with ANALYZE TABLE trade_signal only as a
-- separately planned operation: estimated rows were 2,669 vs 4,940 actual.
