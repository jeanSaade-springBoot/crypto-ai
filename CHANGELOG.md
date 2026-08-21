# Change Log

## FIX-035 — Independent BUY/SELL signal refresh and execution filters

- Added an independent BUY/SELL signal refresh selector: Off/on-demand, 10 seconds, 1 minute, or 5 minutes.
- Added a dedicated Load button flow; the normal dashboard refresh no longer silently reloads the signal evidence table.
- Replaced the broad historical periods with Today, Last 4 hours, Last 2 hours, Last 1 hour, and All time. Today is resolved using Asia/Riyadh while persisted timestamps remain UTC.
- Added execution-status filtering for all actionable BUY/SELL signals, wallet-executed BUY/SELL positions, and BUY positions blocked by the final entry gate.
- BUY rows blocked by `final_entry_allowed=false` now display `BUY POSITION BLOCKED` instead of the generic `NOT EXECUTED` state.
- Added batched wallet-execution lookup so filtering does not issue one database query per signal.
- Display/data-loading only: scoring, Production, Replay, BUY/SELL decisions, Execution Intelligence, TP/SL, Wallet and Binance behavior are unchanged.


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

## FIX-032 — Wallet/Binance separation and LIVE_MICRO safety controls
- Moved Paper Wallet and Binance user account out of Administration into first-class left-menu pages.
- Added user-scoped rolling/circuit-breaker configuration and Trade Inspector execution-venue tags/filter.
- No trading behavior changed.
## FIX-033 — Trade Path Base Technical scoring detail
- Added an expandable Base Technical scoring tree to every Trade Inspector View Path signal phase.
- Shows category totals and exact persisted criteria scores: Trend Direction/Structure/Strength/Price Location, Bollinger, Relative Volume, Volume SMA20, RSI and MACD.
- Sentiment and Fundamentals show availability/exclusion state.
- EMA cross / price-vs-EMA200 / EMA alignment / SMA20 are shown as diagnostics and explicitly marked not to be double-counted.
- Diagnostic/UI only; no Production, Replay, scoring or execution behavior changed.



## FIX-034 — Remove automatic executions from Wallet page
- Removed the `AUTOMATIC EXECUTIONS / Wallet trades` panel from Wallet.
- Removed its frontend renderer while preserving all wallet-trade persistence and Trade Inspector history.
- UI-only; no trading or execution behavior changed.
