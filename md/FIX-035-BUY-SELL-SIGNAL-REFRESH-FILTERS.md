# FIX-035 — Independent BUY/SELL signal refresh and execution filters

## Scope
Dashboard `BUY and SELL signals` evidence panel only. No trading behavior changes.

## Requested behavior
- Separate refresh cadence: Off/on-demand, 10 seconds, 1 minute, 5 minutes.
- Explicit `Load` button.
- Time windows: Today, Last 4 hours, Last 2 hours, Last 1 hour, All time.
- Execution filters: all actionable BUY/SELL, BUY/SELL position executed, BUY position blocked.

## Implementation
`dashboard.js` owns a dedicated timer that is independent of the normal dashboard refresh. A symbol/timeframe selection loads the evidence once for the new context; subsequent dashboard refreshes do not refresh this board. Filter selection is applied when Load is clicked or when the dedicated timer fires.

`DashboardApiController /api/dashboard/signals` resolves KSA Today and rolling-hour windows against UTC persistence, batches linked `wallet_trade` executions, and filters blocked BUYs using immutable `trade_signal.final_entry_allowed`.

## Regression protection
- No scoring or decision fields are changed.
- No Production/Replay execution path is changed.
- No order, wallet, TP/SL, position-management, or Binance logic is changed.
- Existing actionable-signal rendering remains based on persisted `trade_signal` rows.
