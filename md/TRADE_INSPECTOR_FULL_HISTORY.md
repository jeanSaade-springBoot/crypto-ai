# Trade Inspector Full-History Navigation — FIX-007

## Scope
UI/API-only Trade Inspector enhancement. No trading decision, execution, wallet, position management, pressure-probe, or Replay/Proven Analysis behavior is changed.

## Changes
- Default inspected chart interval is 1 minute.
- `/api/trade-inspector/chart` accepts an omitted `from/to` pair and then returns the complete persisted CLOSED candle history for the selected symbol + interval.
- Existing bounded chart calls remain supported for backward compatibility.
- Chart initially focuses around the inspected BUY -> SELL lifecycle while keeping the full candle series loaded.
- Drag-to-pan left/right across all loaded history.
- Mouse-wheel zoom around cursor.
- Toolbar pan/zoom/reset retained.
- Added `Fit trade`, `Jump to entry`, and `Full range` buttons.
- BUY and SELL markers remain anchored to their actual timestamps/prices.
- BUY -> SELL lifecycle line remains visible when both endpoints are in the viewport.
- Added entry, exit, stop-loss, and take-profit horizontal price references.
- Hover tooltip now shows exact time, OHLC, volume, taker-buy %, and number of trades.
- Full-history point count and first/last loaded times are shown above the chart.
- Missing candles are never synthesized or interpolated; real database gaps remain visible.
- FIX-005 is marked as refined by FIX-007 in the Fix Registry.

## Files changed
- `src/main/java/com/crypto/repository/CandleRepository.java`
- `src/main/java/com/crypto/inspector/service/TradeInspectorService.java`
- `src/main/java/com/crypto/inspector/controller/TradeInspectorController.java`
- `src/main/resources/static/js/trade-inspector.js`
- `src/main/resources/static/trade-inspector.html`
- `src/main/resources/static/css/trade-inspector.css`
- `src/main/resources/static/js/fix-registry.js`

## Validation
- `node --check src/main/resources/static/js/trade-inspector.js` passes.
- `node --check src/main/resources/static/js/fix-registry.js` passes.
- Maven is not installed in this execution environment, so the complete Java/Spring suite could not be run here. Jenkins should run the full Maven build/test suite before deployment.
