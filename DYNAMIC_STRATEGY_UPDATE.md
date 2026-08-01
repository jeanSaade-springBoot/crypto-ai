# Dynamic Market Strategy — Phase 1

This version preserves all previous functionality through Flyway V19 and adds V20.

## New flow

1. Calculate the existing indicator component scores.
2. Detect the current market regime and confidence.
3. Select a versioned strategy profile.
4. Rescale category contributions according to the active profile.
5. Normalize against the active available maximum.
6. Apply ATR, multi-timeframe, BTC-context, and order-book safety layers.
7. Store the complete strategy snapshot with the trade signal.

## Regimes

- STRONG_UPTREND
- WEAK_UPTREND
- RANGE
- BREAKOUT
- WEAK_DOWNTREND
- STRONG_DOWNTREND
- HIGH_VOLATILITY
- LOW_LIQUIDITY
- UNKNOWN

## Strategies

- TREND_FOLLOWING
- RANGE_MEAN_REVERSION
- BREAKOUT
- DEFENSIVE
- NO_TRADE

## Safety

- Existing component calculations remain unchanged.
- Dynamic profile selection is explicit and YAML-configurable.
- Scores are always normalized against the active maximum.
- Strategy, version, regime, confidence, weights, thresholds, and evidence are persisted.
- Historical signals are immutable snapshots.
- No automatic threshold learning is enabled in this phase.
