# FIX-092C — Remove trend-line and retracement overlays

## Scope
Dashboard visualization only. No Production trading logic, Replay decision logic, Execution Intelligence, or wallet behavior was changed.

## Changes
- Removed Trend lines and Retracement checkboxes from `dashboard.html`.
- Removed `trendLines` / `retracement` overlay state and localStorage handling from `dashboard.js`.
- Removed swing-point detection, projected trend support/resistance logic, and Fibonacci retracement calculation/rendering.
- Price chart series are now strictly: candlesticks + optional persisted Bollinger bands.
- Preserved exact View Chart BUY/SELL/blocked-BUY annotations.
- Preserved runtime annotation clear/rebuild on refresh so signal focus cannot be lost.
- Preserved Volume and separate ATR14 chart.
- Added FIX-092C to Fix Registry.

## Regression checks
1. Open a blocked BUY via View chart and verify marker remains visible.
2. Open BUY and SELL signal View chart links and verify B/S markers remain visible.
3. Toggle Bollinger and ATR repeatedly; signal marker must remain.
4. Pan/load historical candles; signal marker must remain.
5. Confirm no Trend lines / Retracement controls or series exist.
6. Confirm Replay/trading/wallet code is unchanged.
