# Change Log

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
