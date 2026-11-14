-- Remote crypto_ai proposal, audited 2026-10-03. Nothing here was deployed.
-- Read-only SELECTs below can be tested separately from the commented DDL.
-- Choose the crypto_ai schema, set UTC, and bound SELECT execution time.
-- Parameter @from is illustrative; the repository must bind its Instant input.
SET SESSION time_zone = '+00:00';
SET SESSION max_execution_time = 5000;
SET @from = UTC_TIMESTAMP() - INTERVAL 24 HOUR;

-- Highest-value code change: replace countDistinctSymbolsSince's timestamp
-- range/base-row reads with MAX(generated_at) per indexed symbol/timeframe lane.
-- A symbol has a row >= cutoff exactly when at least one of its lane maxima
-- is >= cutoff. Preserve DISTINCT: a symbol can have several qualifying lanes.
-- Remote tested: both returned 21; existing query 4,132 ms client elapsed,
-- rewrite 210 ms client elapsed; rewrite EXPLAIN ANALYZE root 5.14 ms.
-- Reuse idx_trade_signal_symbol_interval_generated; add NO duplicate lane index.
SELECT COUNT(DISTINCT recent.symbol)
FROM (
    SELECT symbol, interval_code, MAX(generated_at) AS latest_generated_at
    FROM trade_signal
    GROUP BY symbol, interval_code
) recent
WHERE recent.latest_generated_at >= @from;

-- Repository implementation candidate (native query, no source edits made):
-- @Query(value = """
--     SELECT COUNT(DISTINCT recent.symbol)
--     FROM (SELECT symbol, interval_code, MAX(generated_at) latest_generated_at
--           FROM trade_signal GROUP BY symbol, interval_code) recent
--     WHERE recent.latest_generated_at >= :from
--     """, nativeQuery = true)
-- long countDistinctSymbolsSince(@Param("from") Instant from);

-- SystemHealthDailyService.signalStaleness candidate. MAX can be calculated
-- before applying the lower bound without changing each lane's returned value.
-- Current query's lower bound is inclusive; retain >= and the same intervals.
-- Do not add a new upper bound or replace generated_at with candle time.
-- Remote server plan: approximately 3.94 ms, covering grouping skip scan.
SELECT interval_code, symbol, last_signal
FROM (
    SELECT symbol, interval_code, MAX(generated_at) AS last_signal
    FROM trade_signal
    WHERE interval_code IN ('1m', '5m', '1h')
    GROUP BY symbol, interval_code
) recent
WHERE last_signal >= UTC_TIMESTAMP() - INTERVAL 4 HOUR;

-- Blocked BUY/SELL code companion change: split symbol-specific and all-symbol
-- query branches and use `s.symbol = :symbol` rather than `upper(s.symbol)`.
-- Preserve the original/final decision OR logic, time bounds, order, and limit.
-- The remote default three-hour blocked-BUY sample scanned 3,221 rows and took
-- 4,935 ms for one result. The following index complements direct symbol equality.
-- Candidate DDL, NOT executed. Integrate approved definitions into a new Flyway
-- migration after schema checks and a planned maintenance window. Index creation
-- reads a large table, consumes I/O, and still requires metadata locks.
-- ALTER TABLE trade_signal
--     ADD INDEX idx_ts_symbol_generated (symbol, generated_at),
--     ALGORITHM = INPLACE, LOCK = NONE;

-- Interval counts read only generated_at + interval_code but currently fetch
-- base rows from the generated-time index. A covering index is justified for
-- evaluation: the measured one-day aggregate took 1,474 ms on 8,935 rows.
-- No before/after index speedup has been measured: this index is not installed.
-- ALTER TABLE trade_signal
--     ADD INDEX idx_ts_generated_interval (generated_at, interval_code),
--     ALGORITHM = INPLACE, LOCK = NONE;

-- Do not recreate idx_trade_signal_symbol_interval_generated, the candle identity
-- unique index, or idx_trade_signal_health_strategy_regime: all exist remotely.
-- Do not replace the health strategy/regime covering index with the interval
-- index: they cover different projections.
-- No server buffer-pool setting is changed here. Its audited size was 256 MiB;
-- determine host RAM, co-resident services, and working set before sizing it.
