# Proven Analysis / Production Parity Audit

## Rule
Proven/Regression must reuse production trading rules and services. Only persistence and historical/as-of data access may differ.

## Shared production logic
- Technical indicator formulas: `TechnicalIndicatorService` regression snapshot uses the production indicator calculations against historical candles.
- Signal analysis/scoring: live `AnalysisService.analyze(...)` and replay `analyzeForRegression(...)` both call `AnalysisService.buildSignal(...)`.
- Regime/strategy/ATR/MTF/BTC/final decision: invoked from the shared `buildSignal(...)` path.
- Entry intelligence: both live and replay call `ExecutionIntelligenceService.evaluateBuy(...)`, including `PressureReadinessService`.
- BUY/SELL MTF execution validation: shared `TradeExecutionValidationService`.
- Position continuation and exit authority: shared `PositionContinuationPolicy` and `PositionExitPolicy`.
- Dynamic Profit Lock progression: shared `ProfitLockPolicy` used by production and replay.
- Wallet BUY reserve/budget/allocation sizing: shared `WalletExecutionSizingPolicy` used by production and replay.
- Progressive add semantics: decision percentage is an ADD percentage; current allocation caps the remaining size.

## Intentional replay-only differences
- Persistence goes to `analysis_test_*`, `execution_opportunity_test`, `wallet_execution_test`, `wallet_position_test`, and `position_management_test`; production tables/wallet are not mutated.
- Order-book and derivatives context use historical/as-of readers during replay. This is the time-correct equivalent of live observations and prevents future-data leakage.
- Replay starts with the configured paper account baseline (currently 10,000 USDT in application.yml) rather than reading/mutating the live wallet balance.

## Data-granularity limitation
Production `LivePositionProtectionService` sees intra-candle live price updates. Historical regression currently has closed candles/signals, not the original ordered tick stream. Therefore TP/SL/profit-lock formulas and exit policies are shared, but exact tick-by-tick trigger order cannot be reconstructed when multiple levels are crossed inside one candle without historical tick data.

## Parity fixes from audit
1. Removed shadow-only Dynamic Profit Lock formula and centralized it in `ProfitLockPolicy`.
2. Removed shadow-only `INITIAL_CAPITAL * position%` spend formula and centralized production reserve/budget/allocation sizing in `WalletExecutionSizingPolicy`.
3. Added the shared production `TradeExecutionValidationService.validateSell(...)` path to replay in addition to the shared HTF `PositionExitPolicy`.
4. Added unit coverage for the new shared policies and updated Dynamic Profit Lock tests.


## FIX-004 — Temporal price authority for position protection

Production and Proven now share `PositionPriceAuthorityPolicy`. A delayed signal can carry a valid historical candle close while being generated after a new position opens. That signal remains valid as analysis/MTF context, but its price cannot drive TP, SL, profit-lock or replay mark-to-market when the candle observation time (`candleOpenTime + interval`) predates the position open time.

This specifically protects the ALLOUSDT 2026-08-17 incident where a 5m signal generated at 18:23:28 carried the 18:15-18:19 candle close 0.2806 and falsely stopped a position opened at 18:23:26 @ 0.2822. Production live mechanical protection continues to use `LivePositionProtectionService.onPrice(...)`. Replay uses the identical temporal authority policy before historical signal prices can trigger mechanical exits.
