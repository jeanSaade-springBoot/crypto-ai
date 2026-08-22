# Replay / Production Parity Contract

## FIX-052 contract

- MySQL/Binance timestamps are UTC. Backend and Replay interpret them as UTC.
- Frontend/local presentation is responsible for timezone conversion (KSA = Asia/Riyadh for the current user).
- Fresh Replay signals are generated through the same TechnicalIndicatorService + AnalysisService decision path as Production.
- BUY routing, opportunity intelligence, sizing policy, continuation, exit validation and price-authority policies remain shared Production services.
- Production's canonical 1m live kline price observations are persisted in `market_price_event` before `LivePositionProtectionService` consumes them.
- Replay consumes those exact price observations before same-time candle-close signals, matching Production event ordering.
- Mechanical protection ordering is TP -> SL -> Profit Lock -> normal HTF exit.
- A signal-driven terminal SELL cannot reopen a BUY from the same signal invocation.
- A replay window without persisted `market_price_event` rows is degraded historical replay and must not be reported as exact Production parity.

## Intentional isolation

Replay never writes to the real wallet or Production position tables. Wallet/position persistence remains simulated by design so a test cannot move real money. This is side-effect isolation, not separate trading logic.

## Verification

After V64 is deployed, allow Production to collect live price events. Replay the identical UTC interval and compare BUY signal/decision, allocation, entry price, TP/SL/profit-lock events, SELL reason, exit price and exit time. UI reports should convert timestamps to the user's local timezone only at presentation time.
