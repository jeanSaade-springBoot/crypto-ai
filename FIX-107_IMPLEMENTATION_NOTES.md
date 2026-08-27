# FIX-107 — Shared Dashboard and Trade Activity Signals & executions browser

## Scope
Read-only UI/retrieval consolidation only. No Production trading, Replay, FinalDecision, ExecutionIntelligence, position-management, or wallet-write behavior changes.

## Changes
- Added `signal-executions-browser.js` as the single implementation used by Dashboard and Trade Activity.
- Both pages now expose exactly the same filters: Symbol, Period (15m/1h/4h/1d/1w), Type (Blocked BUY, Blocked SELL, BUY / SELL done, BUY open), plus explicit Analyze.
- Removed filter-change/timer refresh authority for this browser. Initial display can load once; subsequent data reloads happen only when Analyze is pressed.
- Dashboard header symbol preselects the Signal Symbol dropdown on load and when the header symbol changes. The signal dropdown stays independent and never changes the Dashboard header.
- Added a common View action:
  - Blocked BUY / Blocked SELL / OPEN BUY: navigate to Dashboard market chart and highlight the exact persisted signal point.
  - BUY / SELL done: navigate to Dashboard market chart, focus the persisted lifecycle window, and highlight both BUY and SELL.
- `TradeInspectorService.completedTradeAnalysisView()` now read-only enriches DONE rows with the historical paired BUY and SELL ids/times/prices using the existing FIX-106 historical-safe pairing helper.
- Trade Activity and Dashboard use the same persisted-evidence endpoint and the same Analyze evidence modal renderer.

## Parity / safety
FIX-107 does not participate in Replay or Production decision processing. It only reads persisted evidence, so there is no Production/Replay behavior fork introduced by this change.

## Regression checklist
1. Same symbol/period/type on Dashboard and Trade Activity produces the same grid rows.
2. Changing a filter does not issue a reload until Analyze is pressed.
3. Dashboard header symbol selects the Dashboard Signal symbol; manually changing Signal symbol does not modify the header.
4. Changing Dashboard header symbol updates the Signal selector but does not auto-fetch.
5. Blocked BUY and Blocked SELL View links highlight the exact persisted signal candle/price.
6. OPEN BUY View highlights the persisted entry signal/price.
7. DONE View highlights both historical BUY and SELL and focuses the trade window.
8. Trade Inspector remains unchanged.
9. No trading, Replay, wallet-write, position-management, or execution-intelligence services are invoked by this UI workflow.
