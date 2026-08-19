# FIX-009 — Trade Inspector lazy-window performance

## Problem
The previous full-history implementation returned and rendered every closed candle for the selected symbol/interval. On 1m data this could create very large ApexCharts series and make the entire Trade Inspector page lag. A custom wheel listener also used `preventDefault()`, so normal page scrolling over the chart was blocked.

## Fix
- Keep full-history **navigation**, but render only a bounded moving window of candles.
- The chart API returns the active window plus global first/last timestamps and total candle count.
- When panning approaches a loaded edge, the browser automatically fetches/replaces the nearby window.
- Remove custom mouse-wheel interception; wheel/trackpad gestures scroll the page normally.
- Keep Apex toolbar zoom and explicit pan-hand navigation.
- Replace the expensive `Full range` action with `Earliest` and `Latest` jumps.
- Preserve real candle gaps; no synthetic candles are created.

## Trading safety
No AnalysisService, FinalDecisionService, Execution Intelligence, wallet, position management, pressure-probe, Replay, or Proven Analysis trading logic was changed.

## Validation performed
- `node --check` passed for `trade-inspector.js` and `fix-registry.js`.
- Static checks confirm the new chart path contains no wheel `preventDefault()` and no old full-range control.
- Diff against the previous controls-fix ZIP is limited to Trade Inspector chart API/repository/UI and Fix Registry files.
- Maven is not available in this environment; run the full Jenkins/Maven suite before deployment.
