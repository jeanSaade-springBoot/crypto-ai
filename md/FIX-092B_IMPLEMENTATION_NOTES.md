# FIX-092B — Stable chart overlays / preserve signal focus

## Problem
FIX-092A used ApexCharts labelled Y-axis annotations for Fibonacci retracement. Signal View Chart highlighting also uses Apex point/x-axis annotations. Repeated chart refreshes/toggles could leave annotation DOM behind, causing repeated grey Fibonacci labels and visually obscuring/removing the selected signal marker.

## Changes
- `dashboard.js`: replaced Fibonacci Y-axis annotations with bounded line series drawn only from the selected swing start to swing end.
- Added a display-only minimum swing-size filter so tiny/noisy swings do not generate five nearly-overlapping Fibonacci lines.
- Explicitly disabled price-chart data labels and line markers.
- Before every update, call `clearAnnotations()` when available, then rebuild immutable position/debug/View Chart annotations from current state.
- Trend lines remain ordinary line series.
- Bollinger and ATR behavior is unchanged.

## Safety
UI/chart only. No database writes. No AnalysisService, FinalDecisionService, ExecutionIntelligenceService, Replay or wallet behavior changes.

## Validation
- `node --check src/main/resources/static/js/dashboard.js` passes.
- Static check confirms obsolete `chartRetracementAnnotations`/`retracementYAnnotations` path is removed.
