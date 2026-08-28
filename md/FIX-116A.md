# FIX-116A — System Health slow-query recovery

## Evidence
Production `EXPLAIN ANALYZE` showed the seven-day `trade_signal` strategy/regime aggregation taking about 78.7 seconds while returning only 18 groups from 1,240 qualifying rows. `SHOW FULL PROCESSLIST` also showed a separate long-running Hibernate select materializing the wide `TradeSignal` entity.

## Root causes corrected
1. FIX-116 cache expiry was calculated from the timestamp captured **before** `computeDailyHealth()`. If the calculation exceeded the 45-second TTL, the completed snapshot was immediately stale and the next request recalculated it.
2. The strategy/regime aggregation used the `generated_at` index but still needed strategy/regime values from the base table. V76 adds a covering index for the exact read-only Health columns.
3. Score Diagnostics loaded full `TradeSignal` entities, including large JSON/TEXT context fields it never reads. It now uses a narrow Spring Data projection containing only the scalar fields required by the existing calculations.

## Safety boundary
This fix is observability/read performance only. It does not change Order Book collection/evaluation, signal generation, FinalDecision, execution, wallet behavior, Catching Market detection, Production trading logic, or Replay logic. Replay = Production remains the governing rule.
