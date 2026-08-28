# FIX-093 — Catching Market cleanup and signal chart repair

## Scope
Read-only Catching Market UI/diagnostics only. Trading, Replay and wallet behavior are unchanged.

## Changes
- Removed Trade?, Signal, Blame and Review columns from the Catching Market grid.
- Retired the global Catching Market blame badge UI (legacy backend/schema fields remain untouched for compatibility).
- Added a Level dropdown with **HIGH selected by default**, plus EXTREME, NORMAL and ALL.
- Kept signal/trade context inside the Graph view only.
- Fixed missing blocked signal markers: Graph now considers `originalDecision` as well as final `decision`, so BUY->WATCH and SELL->WATCH vetoes are visible as `B!` / `S!`.
- Fixed signal timing alignment: Graph uses `candleOpenTime` first and only falls back to `generatedAt` for legacy rows.
- Backend chart query now reads by candle time and merges legacy generated-time rows by signal ID.
- Added FIX-093 explanatory comments and Fix Registry entry.

## Explicit non-changes
- No AnalysisService / FinalDecisionService / ExecutionIntelligenceService behavior change.
- No Replay behavior change.
- No wallet behavior change.
- No schema migration.
- Historical blame columns/services are not deleted; only their UI presentation is retired to avoid destructive migration scope.
