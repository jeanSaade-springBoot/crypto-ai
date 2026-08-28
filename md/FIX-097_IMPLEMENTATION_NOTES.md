# FIX-097 — Catching Market blamed-signal reconstruction for missing bestSignalId

## Root cause
Some `price_move_event` rows have `best_signal_id = NULL`. Two cases are valid: a caught move can still be `PENDING`, and older/finalized blame selection used `TradeSignal.generatedAt` even though the caught move window is defined by market/candle time. Signal persistence can lag its candle, so a valid signal candle could fall inside the move while `generatedAt` falls outside it.

FIX-095/096 then treated a missing `bestSignalId` as a hard chart error and returned HTTP 400.

## Changes
- `PriceMoveMonitorService.applyBlame()` now resolves move-window signal evidence by `candleOpenTime` first.
- Existing `generatedAt` lookup remains a legacy fallback for signals with missing candle time.
- `eventChart()` still honors a persisted `bestSignalId` first.
- If the ID is null/missing, the chart read path deterministically reconstructs the same best directional signal from immutable TradeSignal rows without mutating the event.
- If no signal exists at all, the endpoint returns a truthful no-signal payload (HTTP 200) and the popup explains that there is nothing to highlight.
- The popup continues to render exactly one blamed signal when one exists.
- Static assets are versioned `v=097` to avoid stale browser JavaScript.

## Scope safety
No signal generation, FinalDecision, ExecutionIntelligence, Replay, position or wallet behavior is changed. The only non-UI logic adjustment is retrospective blame-time alignment so future finalized caught moves persist the correct existing signal ID.
