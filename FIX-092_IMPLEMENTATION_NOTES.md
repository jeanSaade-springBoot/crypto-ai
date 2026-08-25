# FIX-092 — Dashboard Signals and chart context

## Scope
Read-only dashboard/UI enhancement only. No Production decision logic, Replay logic, wallet logic, sizing, entry, or exit behavior is changed.

## Changes
- Renamed the active-position analysis dashboard section to **Signals**.
- Added **Signal view** selector with **Open positions** selected by default, preserving the existing first-load behavior.
- Added **Blocked BUY**, **SELL signals**, and **BUY / SELL signals** filters using `/api/dashboard/signals`.
- Added signal `View chart` deep-links anchored to the persisted signal candle timestamp and signal price.
- Historical focus navigation now honors the selected persisted interval (1m/5m/1h), rather than the old 5m-only debug focus behavior.
- Signal chart markers visibly show **B** or **S**.
- Added Bollinger upper/middle/lower as price-chart overlays from persisted `technical_indicator` rows.
- Added a synchronized ATR14 chart below volume; ATR is intentionally not plotted on the price Y-axis.
- Added read-only `TechnicalIndicatorRepository` range retrieval for the visible candle window and chart history pages.
- Added FIX-092 to the Fix Registry.

## Replay / Production safety
- Replay path is unchanged and receives no new behavior.
- No historical order-book or Replay reconstruction work is introduced.
- No wallet classes are touched.
- Indicator data is display-only and is never fed back to analysis/execution.
- 4h/1d derived display candles do not fabricate Bollinger/ATR values.
