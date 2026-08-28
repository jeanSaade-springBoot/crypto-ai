# FIX-044 — Trade Activity

UI/read-only audit change only.

- Removed FIX-039 BUY/SELL blocker grids and production-exit activity from Trade Inspector.
- Added Trade Activity directly under Trade Inspector in the left navigation.
- No activity data is loaded on page open; only the small symbol list is metadata-loaded.
- Search filters: BUY, SELL, BLOCKED, EXECUTED; symbol; 1h, 2h, 4h, 24h.
- Grid: Time, Symbol, TF, Action, Status, Source, Reason.
- Reason is keyword-only. Long decision/execution explanations are intentionally not rendered.
- Execution source identifies WAKE_UP, INITIAL, SCOUT, ACCUMULATED, POSITION_EXIT or SIGNAL.
- Backend hard-limits the time range to 24h and the result to 500 rows.
- No AnalysisService, FinalDecisionService, ExecutionIntelligenceService, wake-up, wallet execution, position management or Replay behavior changed.
