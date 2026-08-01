# Final Decision Phase

Implemented after Market Context Phase 2:

- Added `FinalDecisionService` as the single owner of the final recommendation.
- Applies ordered checks for strategy score, data quality, ATR, multi-timeframe context, BTC context, and order-book liquidity.
- Added a separate confidence score (0-100) that measures reliability, not bullishness.
- Added an immutable JSON decision path to every new `trade_signal`.
- Added final entry permission and final-decision explanation.
- Added dashboard Final Decision Path section.
- Added Flyway migration `V22__add_final_decision_audit.sql`.

Still pending in later phases:

- Fully timeframe-aware order-book windows and persistence rules.
- Fully strategy-specific multi-timeframe confluence rules.
- Threshold calibration recommendations after stable operation.
