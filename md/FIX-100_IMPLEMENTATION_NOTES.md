# FIX-100 — Trade Inspector all-signal analysis grid restoration

## Scope
Read-only Trade Inspector diagnostics only. No trading, Replay, Execution Intelligence, FinalDecision, position-management or wallet behavior is changed.

## Root cause
FIX-045 removed the old blocked-signal diagnostics markup from `trade-inspector.html` but deliberately left the JavaScript helpers DOM-safe. As a result, the expected signal-analysis filters no longer appeared. Trade Inspector then exposed only completed wallet trades, which also meant signals that never executed could not be inspected there.

## Changes
- Restored a visible **Trade Signal Analysis** panel in `trade-inspector.html`.
- Added filters: Symbol, Interval, Decision, Entry state, From/To KSA and row limit.
- Added `GET /api/trade-inspector/signals` to read persisted `TradeSignal` rows only.
- Added `GET /api/trade-inspector/signals/symbols` so symbols come from `trade_signal`, including symbols with no wallet execution.
- Added `TradeSignalRepository.findForInspectorAnalysis(...)` and distinct signal-symbol discovery.
- Added an **Analyze** modal for exact persisted evidence: score, raw/effective confidence, primary blocker, score components, regime/strategy, ATR, MTF, BTC, liquidity, derivatives, SL/TP, explanations and `decision_path`.
- KSA datetime inputs are explicitly converted back to UTC for backend queries.
- Added explanatory FIX-100 comments and Fix Registry entry.

## Safety boundary
The new endpoints are `@Transactional(readOnly = true)` and do not call `AnalysisService`, `FinalDecisionService`, `ExecutionIntelligenceService`, Replay, wallet execution or position management.
