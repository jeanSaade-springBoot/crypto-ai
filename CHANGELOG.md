# Change Log

## FIX-028 — Production exit audit truth

- Added immutable `production_exit_audit` persistence (Flyway V61).
- Corrected wallet exit audit metadata so TP/SL exits are no longer mislabeled `SIGNAL_SELL` merely because a current signal carried the execution price/context.
- Added source-signal decision and latest Position Analysis recommendation to the production exit audit.
- Updated Trade Inspector View Path to show the real close trigger first and classify the linked signal as either `SELL_TRIGGER` or `MARKET_CONTEXT_AT_EXIT`.
- Added legacy fallback through `paper_position`, so historical BTC #145 displays TAKE_PROFIT even though its old wallet row says SIGNAL_SELL.
- Added focused `ProductionExitAuditServiceTest` for BTC #145 / signal #105688 / Position Analysis #1234.
- Trading behavior, TP/SL thresholds, continuation logic, sizing, scoring, and Replay execution are unchanged.
