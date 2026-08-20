# FIX-024 — Trade Inspector View Path

## Scope
UI/diagnostic only. No trading, wallet, replay, scoring, entry, exit, ATR, HTF, liquidity or position-management behavior is changed.

## Trade Inspector behavior
A new **View path** button is displayed beside **View chart** for every completed inspected trade.

The overlay displays:
- exact BUY and SELL timestamps in KSA (UTC+3)
- exact holding time and elapsed time between lifecycle states
- opportunity start/age/evidence/health/momentum/execution source when available
- latest persisted 1m, 5m and 1h states available at wallet execution
- technical score and sub-score statistics (EMA, SMA, trend structure, RSI, MACD, Bollinger, RVOL, sentiment/fundamental availability)
- regime, strategy and confluence
- ATR, volatility, R/R, position recommendation, stop and take-profit
- BTC context, trend, correlation, beta and influence
- full order-book/liquidity snapshot including imbalance, depths, wall prices/sizes, persistence, observations, influence and target/stop flags
- derivatives/funding/open-interest context
- persisted ordered FinalDecision decision_path
- profit-lock timestamp/state when present

## Data integrity
The path endpoint is read-only and returns persisted production evidence. It does not recompute scores or decisions.
For progressive scout/add trades, the inspector first matches by latest_signal_id and then safely falls back to an execution opportunity whose persisted lifecycle overlapped the wallet BUY timestamp.

## Files
- `TradeInspectorService`
- `TradeInspectorController`
- `ExecutionOpportunityRepository` (read-only lookup only)
- `trade-inspector.html`
- `trade-inspector.js`
- `trade-inspector.css`
- `fix-registry.js`
