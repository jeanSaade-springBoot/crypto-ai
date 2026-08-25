# FIX-092A — Display-only trend lines and retracement overlays

## Scope
FIX-092A changes only Dashboard chart rendering. It does not change Production analysis, Replay, FinalDecision, ExecutionIntelligence, BUY/SELL generation, position management, or wallet behavior.

## Changed files
- `src/main/resources/static/dashboard.html`
- `src/main/resources/static/js/dashboard.js`
- `src/main/resources/static/css/dashboard.css`
- `src/main/resources/static/js/fix-registry.js`

## Display behavior
- Adds four chart overlay controls: Bollinger, Trend lines, Retracement, ATR.
- All controls default to enabled and visibility preference is stored only in browser `localStorage`.
- Trend support uses the latest two local swing lows; trend resistance uses the latest two local swing highs. Swing detection uses a two-candle radius on the candles already loaded for display.
- Trend lines are projected to the newest loaded candle timestamp.
- Fibonacci retracement uses the latest completed swing between opposite pivot types and renders 23.6%, 38.2%, 50%, 61.8%, and 78.6% display levels.
- Historical chart panning/deep links recalculate these overlays from the currently loaded candle history.

## Safety boundary
No trend-line or retracement value is sent to the backend or persisted to the database. No Java trading class was changed. Replay and Production therefore continue to use exactly the same pre-FIX-092A trading inputs and decisions.

## Validation performed
- `node --check src/main/resources/static/js/dashboard.js` passed.
- Confirmed no Java source, Flyway migration, Replay class, execution class, or wallet class was changed by FIX-092A.
