# Change Log

## FIX-031 — Logged-in-user Crypto Account configuration

- Added `crypto_account_configuration` (Flyway V62), uniquely scoped by `app_user` + exchange.
- Added authenticated current-user API at `/api/crypto-account`; user ownership is derived from the Spring Security `Principal`, never from a client-supplied user id.
- Added PAPER / LIVE_MICRO account mode and conservative micro-live limits: maximum order, maximum total exposure, maximum open positions and maximum daily loss.
- Added AES-GCM encryption for Binance API key/secret using `CRYPTO_ACCOUNT_MASTER_KEY`; secrets are never returned to the browser and only a masked API-key hint is shown.
- Added Administration -> Crypto Account UI.
- LIVE_MICRO remains configuration-only in this release; no real Binance order adapter was enabled.
- Shared market data, scoring, BUY/SELL logic, TP/SL, wallet strategy, Production and Replay behavior remain unchanged.
- Added a focused service test proving authenticated-user ownership and encrypted/non-returned credentials.

## FIX-029 — Trade Path human-readable decision meaning

- Added a prominent **What this means** sentence to every Trade Inspector View Path phase.
- The sentence summarizes the persisted score, trend, momentum, volume/participation, HTF confirmation, ATR state and veto state into one trader-readable conclusion.
- PEPE #108246 WATCH 66 now explains that direction/momentum are supportive but participation/confirmation is insufficient.
- PEPE #108276 STRONG_BUY 86 now explains that strong trend, participation and momentum align for the stronger decision.
- Blocked and ATR-wait phases explicitly explain the blocker instead of sounding actionable.
- Display-only change: Production, Replay, scoring, BUY/SELL, TP/SL and position-management behavior are unchanged.

## FIX-028 — Production exit audit truth

- Added immutable `production_exit_audit` persistence (Flyway V61).
- Corrected wallet exit audit metadata so TP/SL exits are no longer mislabeled `SIGNAL_SELL` merely because a current signal carried the execution price/context.
- Added source-signal decision and latest Position Analysis recommendation to the production exit audit.
- Updated Trade Inspector View Path to show the real close trigger first and classify the linked signal as either `SELL_TRIGGER` or `MARKET_CONTEXT_AT_EXIT`.
- Added legacy fallback through `paper_position`, so historical BTC #145 displays TAKE_PROFIT even though its old wallet row says SIGNAL_SELL.
- Added focused `ProductionExitAuditServiceTest` for BTC #145 / signal #105688 / Position Analysis #1234.
- Trading behavior, TP/SL thresholds, continuation logic, sizing, scoring, and Replay execution are unchanged.

## FIX-030 — Trade Path Performance Context
- Added Holding Time, Holding Efficiency (realized positive return / MFE), and latest-20-trade Profit Factor to Trade Inspector View Path.
- Profit Factor is explicitly displayed as recent-system context rather than a per-trade metric.
- Diagnostic/UI only; no Production or Replay trading behavior changed.
