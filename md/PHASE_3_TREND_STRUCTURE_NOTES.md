# Phase 3 — Trend Structure

## Objective
Recognize improving bullish price structure before perfect EMA alignment, without lowering BUY thresholds or weakening existing risk checks.

## Trend group remains 25 points

- Direction: 8
- Structure: 7
- Strength: 6
- Price location: 4

## New 7-point structure score

- Higher-high / higher-low structure: 2
- Pullback quality: 2
- EMA20 respect: 1
- Breakout preparation / compression: 1
- Continuation support: 1

## Safety boundaries

- No BUY threshold was lowered.
- ATR, Multi-Timeframe, BTC Context, Derivatives, Order Book and wallet execution were not changed.
- The new score uses only closed candles up to the indicator candle time.
- Insufficient candle history returns zero structure points instead of guessing.

## Production validation
After deployment, inspect `trade_signal.analysis_breakdown` under:

`trendGroups.structure`

The object now includes the five component scores and the supporting evidence.
