-- FIX-116: Catching Market is read-only diagnostics. The summary page filters by end_time before
-- grouping by symbol/direction/window; the original indexes lead with symbol or review_status and
-- cannot efficiently serve the unfiltered 1h/4h/24h page. This index changes no trading data/rules.
CREATE INDEX idx_price_move_event_summary_time
    ON price_move_event (end_time, importance_level, symbol, direction, detection_window, start_time, id);
