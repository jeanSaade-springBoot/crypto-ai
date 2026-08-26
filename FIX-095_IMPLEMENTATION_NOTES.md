# FIX-095 — Blamed signal focused popup

- Removed mixed chart context from the Catching Market blame graph.
- `PriceMoveMonitorService.eventChart()` now resolves `PriceMoveEvent.bestSignalId` and returns only that persisted `TradeSignal` plus surrounding candles.
- The blamed signal's own interval is authoritative; the popup cannot change timeframe and accidentally hide the highlight.
- Removed unrelated signal annotations, wallet execution markers and the caught-move start/end line from this diagnostic chart.
- Popup reuses the Trade Inspector modal shell/controls and highlights exactly one `BLAMED BUY` or `BLAMED SELL` marker at `candleOpenTime` and `latestPrice`.
- No changes to Replay, FinalDecision, ExecutionIntelligence, trade generation, wallet execution or position management.
