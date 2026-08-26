# FIX-103 — Trade Activity dedicated Signals & executions workspace

## Scope
UI/read-only diagnostics only. Built on top of FIX-102. No previously agreed trading, Replay, wallet, Catching Market, dashboard or Trade Inspector behavior was rolled back.

## Requested structure
- Keep Trade Inspector unchanged.
- Do not duplicate the new signal/execution analysis grid inside Trade Inspector.
- Trade Activity contains only the compact `Signals & executions` workflow.
- Filters: Symbol, Period (15m / 1h / 4h / 1d / 1w), Type (Blocked BUY / Blocked SELL / BUY+SELL done / BUY open).
- Keep the read-only Analyze evidence popup.
- Remove the old Trade Activity KPI strip, Forensic Trade Graph, volume chart, technical summaries, outcome card and chart-detail section.

## Technical changes
- `trade-activity.html`: replaced the mixed page with a single compact analysis panel and modal; removed ApexCharts/crosshair assets because the page no longer renders charts.
- `trade-activity.js`: removed all graph/KPI/couple/chart logic and DOM references. The remaining script only discovers symbols, queries persisted evidence, renders the grid and opens the Analyze modal.
- `trade-activity.css`: preserved existing grid/modal styles and added only a small analysis-only spacing rule.
- `trade-inspector.html/js`: intentionally not changed by FIX-103.
- `fix-registry.js`: registered FIX-103.

## Safety boundary
No calls were added to `AnalysisService`, `FinalDecisionService`, `ExecutionIntelligenceService`, Replay orchestration, wallet mutation or position mutation. The page remains read-only.
