-- FIX-116A: System Health groups the last seven days by strategy/regime and generated KSA day.
-- EXPLAIN ANALYZE on Production showed the existing generated_at index still required expensive
-- base-table lookups. Cover the only columns required by this read-only diagnostic aggregation.
-- This index does not change signal generation, execution, Replay, or any trading decision.
CREATE INDEX idx_trade_signal_health_strategy_regime
    ON trade_signal (generated_at, selected_strategy, market_regime);
