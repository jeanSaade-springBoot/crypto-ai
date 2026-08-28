# FIX-102 — Trade Analysis moved into Trade Activity

## Scope
UI/read-only diagnostics only. Builds directly on FIX-101 and retains every prior FIX-091..101 behavior outside the relocated analysis panel.

## Changes
- Removed the FIX-101 Symbol/Period/Type Trade Analysis grid and evidence modal from `trade-inspector.html` and its event/rendering block from `trade-inspector.js`.
- Replaced Trade Activity's legacy Direction/State/COUPLE checkbox filters and old activity results grid with always-enabled dropdowns: Symbol, Period (15m/1h/4h/1d/1w), and Type (Blocked BUY, Blocked SELL, BUY/SELL done, BUY open).
- The Trade Activity grid reuses the existing FIX-101 read-only persisted-evidence endpoint. It never invokes analysis, FinalDecision, ExecutionIntelligence, Replay or wallet mutation logic.
- Filter changes auto-refresh; the Analyze button remains a manual refresh.
- Added a read-only evidence modal directly to Trade Activity. The existing forensic graph remains driven by the selected Symbol/Period filters and is kept separate from row analysis.
- The existing forensic chart, KSA rendering, indicators, completed-trade summaries and wallet/couple graph evidence remain unchanged.
- For graph performance/backward compatibility, 15m uses the existing 1h forensic chart window and 1w uses the existing 24h forensic chart cap; the analysis grid itself respects the exact requested 15m/1w period through the FIX-101 endpoint.

## Explicitly unchanged
- Production signal generation and scoring
- FinalDecisionService
- ExecutionIntelligenceService
- Replay
- Wallet execution/position behavior
- Catching Market / blame fixes
