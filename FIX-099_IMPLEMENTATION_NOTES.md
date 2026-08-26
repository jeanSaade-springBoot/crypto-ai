# FIX-099 — Catching Market blamed-signal black-chart rendering fix

## Scope
Display-only correction on top of FIX-098. No trading, Replay, FinalDecision, ExecutionIntelligence, signal persistence, position, or wallet behavior changes.

## Root cause
The blamed-signal popup still differed from the proven Trade Inspector Apex composition. It used a top-level `candlestick` chart, and sparse historical windows could also leave the initial X range with `min == max`. Apex can render that state as an all-black plotting area even when a valid candle row exists.

## Changes
- `catching-market.js` now uses the same stable Apex shape as Trade Inspector: `chart.type = line` plus a typed `candlestick` series.
- Initial X range is guaranteed to have non-zero width, including one-candle history.
- A safe Y min/max is derived from real returned OHLC values with a small pad, so sparse candles remain visible.
- Fit/Jump/Earliest/Latest ranges are clamped to loaded candle bounds and cannot navigate into a blank viewport.
- An explicit Apex `noData` message remains as a final visual fallback.
- Catching Market static assets are cache-busted to `v=099`.

## Boundary
Exactly one persisted/reconstructed blamed signal remains highlighted. This fix does not create or change signals and does not modify Replay or any live trading path.
