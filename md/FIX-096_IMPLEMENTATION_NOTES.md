# FIX-096 — Blamed signal popup empty-black chart hardening

## Scope
Read-only Catching Market diagnostics only. No Production trading, Replay, FinalDecision, ExecutionIntelligence, or wallet behavior changes.

## Root cause
FIX-095 clipped blamed-signal candle history to the caught move's 8-hour block. `bestSignalId` can point near or outside that block boundary, which could make the resulting `from/to` range exclude the blamed signal or contain no candles. The browser could also center ApexCharts on the signal timestamp even when that timestamp was outside the candle timestamps returned by the backend, resulting in an all-black viewport.

## Changes
- `PriceMoveMonitorService.eventChart()` now anchors candle history directly to the persisted blamed signal timestamp.
- Uses a timeframe-aware read-only radius: 1m ~2h, 5m ~8h, 15m ~18h, 1h ~3d, 4h ~10d.
- Returns only closed persisted candles.
- If an older blamed signal's native interval has no stored candles, the chart may fall back to persisted 1m candles. The blamed signal itself is unchanged and its native interval is returned separately.
- `catching-market.js` clamps the initial X-axis to the actual candle range and labels any fallback interval.
- If there are still no candles, the popup shows an explicit diagnostic message instead of initializing an empty black chart.
- Static assets are bumped to `v=096`.
- FIX-095 (previously documented in implementation notes but absent from the JS Fix Registry array) and FIX-096 are both registered in `fix-registry.js`.

## Regression checks
Open multiple Catching Market blamed-signal charts across interval types and block boundaries. Confirm one blamed signal remains highlighted, candles are visible when persisted history exists, fallback is labeled, no-candle cases show a message, and no trading/replay/wallet paths change.
