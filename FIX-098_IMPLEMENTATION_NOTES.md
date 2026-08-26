# FIX-098 — Catching Market retrospective blame diagnosis enrichment

## Scope

Retrospective diagnosis only. The implementation is limited to `PriceMoveMonitorService.applyBlame()` and private helper methods plus constructor injection of the existing read-only `ExecutionOpportunityRepository`. No trading service is called, no live-trading table is written, and the existing one-`PriceMoveEvent`-row-per-direction-per-8-hour-block output shape remains unchanged.

## Changes

1. `SIGNALLED_NOT_TRADED` now reuses persisted `execution_opportunity` audit data. Exact `latestSignalId` linkage is preferred; symbol/direction/lifecycle overlap at the signal generation timestamp is the fallback for progressively accumulated opportunities. Persisted `decisionCode` and `decisionExplanation` are surfaced when found.
2. `MISSED_SIGNAL` now prefers FIX-091 `TradeSignal.primaryBlockingStage`. The explanation is taken from the matching immutable `decision_path` adjustment. The historical six-flag blocker hierarchy remains as a fallback for pre-FIX-091 signals.
3. Best-signal selection remains exactly one signal per row but now prioritizes: move-direction BUY/SELL -> non-ATR-deferred candidate -> historical score ranking. If all candidates are deferred, the original pool remains eligible.
4. Soft warnings are appended only when there is no canonical or legacy hard blocker. Current warnings are moderate BTC conflict, mixed higher-timeframe confluence, and thin/insufficient order-book sampling. They are explicitly described as non-blocking contributors.

## Safety boundary

- No `TradeSignal` writes.
- No `ExecutionOpportunity` writes.
- No `FinalDecisionService` invocation.
- No `ExecutionIntelligenceService` invocation.
- No Wallet behavior changes.
- No Replay behavior changes.
- No schema/Flyway change.

## Verification

Because Maven/Maven Wrapper may be unavailable in the artifact workspace, run the normal project `mvn test` in the development/build environment before deployment. Regression checks should cover exact opportunity linkage, overlap fallback, canonical and legacy blockers, directional/non-deferred selection, and conditional soft warnings.
