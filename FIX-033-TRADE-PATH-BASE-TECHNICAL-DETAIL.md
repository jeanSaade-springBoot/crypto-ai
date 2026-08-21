# FIX-033 — Trade Path Base Technical Detail

## Goal
Make Trade Inspector -> View Path show exactly how the persisted **Base technical** score was built, without changing any trading behavior.

## Scope
Diagnostic/UI only. Production, Replay, scoring, thresholds, BUY/SELL, ATR, wallet, Binance, TP/SL and position management remain unchanged.

## View Path detail
Each signal phase now exposes an expandable **Base technical detail** tree using the scores already persisted on `trade_signal`:

- Trend `/25`
  - Trend Direction `/8`
  - Trend Structure `/7`
  - Trend Strength `/6`
  - Price Location `/4`
- Volume `/20`
  - Bollinger Bands `/6`
  - Relative Volume `/8`
  - Volume vs SMA20 `/6`
- Momentum `/15`
  - RSI `/7`
  - MACD `/8`
- Optional categories
  - Sentiment `/15`, with availability/exclusion state
  - Fundamentals `/10`, with availability/exclusion state
- Additional persisted EMA/SMA diagnostics are displayed separately and explicitly marked **not extra points** so they are not visually double-counted against the Trend total.

The headline still shows `raw_score / maximum_available_score`, preserving the exact availability-aware Base Technical score persisted by Production.

## Safety
No score is recomputed in JavaScript. Trade Inspector only renders persisted fields returned by `TradeInspectorService.signalPathView()`.
