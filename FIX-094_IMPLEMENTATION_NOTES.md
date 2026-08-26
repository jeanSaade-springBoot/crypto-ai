# FIX-094 — Catching Market Trade-Inspector-style chart popup

## Scope
Read-only Catching Market UI/chart change only. No trading, Replay, Execution Intelligence, FinalDecision, or wallet behavior changes.

## Changes
- Replaced the inline Catching Market chart panel with the same fixed modal shell/classes used by Trade Inspector.
- Reused `trade-inspector.css` and the shared `chart-crosshair.js` presentation.
- Matched Trade Inspector chart height, dark candlestick theme, right-side price scale, zoom/pan toolbar, KSA X/Y crosshair, interval selector and focused navigation controls.
- Added 1m / 5m / 1h / 4h display interval support to the read-only catch chart endpoint.
- Signal annotations are filtered to the selected interval and retain FIX-093 `candleOpenTime` anchoring and `originalDecision` handling for blocked BUY/SELL markers.
- Executed trade markers remain display-only.
- Popup closes through Close, backdrop, or Escape and destroys its Apex instance/crosshair cleanly.

## Files
- `src/main/resources/static/catching-market.html`
- `src/main/resources/static/js/catching-market.js`
- `src/main/java/com/crypto/debug/monitor/controller/PriceMoveMonitorController.java`
- `src/main/java/com/crypto/debug/monitor/service/PriceMoveMonitorService.java`
- `src/main/resources/static/js/fix-registry.js`

## Validation
- `node --check src/main/resources/static/js/catching-market.js` passed.
- Maven/full Spring compilation was not available in this container; run the normal project build before deployment.
