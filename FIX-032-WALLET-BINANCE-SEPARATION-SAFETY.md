# FIX-032 — Wallet / Binance separation + LIVE_MICRO safety configuration

## Scope
Diagnostic/configuration only. No BUY/SELL, TP/SL, position-management, Replay or execution behavior is changed.

## Changes
- Removed Paper Wallet and per-user Binance account panels from Administration.
- Added first-class left-menu pages: Wallet and Binance.
- Wallet preserves the existing internal/shadow wallet configuration, holdings and trade history.
- Binance owns the authenticated user's exchange credentials, LIVE_MICRO limits and rolling circuit-breaker configuration.
- Added V63 user-scoped safety controls: 3 losses -> 120m pause, 4 losses -> manual resume, rolling 240m max loss 10 USDT, daily max loss 20 USDT, same-symbol 2 losses -> 240m quarantine, max slippage 0.30%, 2 Binance failures -> pause.
- Trade Inspector adds ALL/WALLET/BINANCE filter and execution-venue badge. Existing rows are truthfully WALLET; BINANCE stays empty until a real execution bridge exists.

## Safety invariant
Future safety governor may block only new real exposure. Risk-reducing SELL/STOP_LOSS/TAKE_PROFIT/emergency exit must remain allowed. Shadow Wallet continues even while Binance is paused.

## Regression
- Existing strategy/replay tests must remain unchanged.
- Verify /administration no longer shows wallet or Binance account forms.
- Verify /wallet loads current Paper Wallet data/forms.
- Verify /binance loads only logged-in user's configuration and persists V63 values.
- Verify Trade Inspector WALLET filter returns current trades, BINANCE returns none before live bridge.
- node --check wallet.js, binance-account.js, trade-inspector.js.
