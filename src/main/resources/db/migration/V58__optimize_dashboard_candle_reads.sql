-- Dashboard symbol/timeframe switches repeatedly request the latest closed candles.
-- Include `closed` in the composite index so MySQL can satisfy the filter and
-- descending open_time traversal from one index instead of combining indexes.
-- Read-performance only; no trading data or decision logic is changed.
CREATE INDEX idx_candle_dashboard_closed_time
    ON candle (symbol, interval_code, closed, open_time);
