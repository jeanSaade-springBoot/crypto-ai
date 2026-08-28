# FIX-112 Implementation Notes

## Scope

This release intentionally contains only FIX-112A and FIX-112C. The proposed 5m-NEUTRAL balanced-entry strategy change (112B) is deferred so this release changes one proven entry correctness defect and one Replay-parity infrastructure gap without mixing in a new MTF strategy rule.

## FIX-112A — BUY_CONTINUATION execution-consumption correction

- Added `EntryConsumptionState` and `EntryConsumptionPolicy`.
- `TradeExecutionValidationService.validateBuy()` now applies `BUY_CONTINUATION` only when the previous 1m signal is fresh/bullish **and** the entry is actually consumed.
- Production consumption authority is the existing authoritative OPEN `WalletManagedPosition`.
- Replay does not read Production wallet state. `ExecutionReplayScope` owns isolated consumed-symbol state; `ShadowProductionReplayService` marks it only after a shadow BUY really opens and releases it on a terminal close.
- No other entry gate is bypassed. A previous blocked BUY simply lets the current BUY continue through normal validation.

## FIX-112C — persisted Order Book evidence

- Added Flyway `V74__order_book_snapshot_persistence.sql` and `(symbol, observed_at)` index.
- Added `OrderBookSnapshotService` using the same JDBC pattern as persisted market-price events.
- Production snapshot persistence is now isolated behind a dedicated bounded `orderBookPersistenceExecutor`. The live collector only enqueues historical evidence; database latency/failure cannot execute on or block the collector thread. Queue saturation drops Replay evidence with an explicit warning rather than changing Production sampling cadence.
- Production persists the normalized metrics computed by the existing `OrderBookLiquidityService.metrics()` method. `depth_imbalance` retains the existing signed formula `(bidDepth - askDepth)/(bidDepth + askDepth)` in `[-1,+1]`.
- Production and Replay now converge on one normalized evaluator for observation minimums, persistent walls, wall lifecycle, status and veto decisions.
- Replay queries only `windowStart <= observed_at <= replayEvaluationTime`; future observations are excluded.
- If historical rows exist but are below the configured minimum, Replay reproduces Production `INSUFFICIENT_DATA_HOLD`.
- If no historical rows exist (for example pre-V74), Replay returns `UNAVAILABLE` and does not substitute current Binance data.
- FIX-112C does **not** change Order Book thresholds or loosen the existing veto behavior.
- Production-behavior preservation correction: the shared evaluator explicitly retains the pre-FIX-112 latest-snapshot validity gate. If the latest snapshot cannot form a valid midpoint, both Production and Replay return `UNAVAILABLE` immediately even when older snapshots in the window are valid. Invalid collected snapshots are persisted with nullable metrics so Replay can reproduce this exact historical condition. Filtering/tolerating an invalid latest snapshot is intentionally deferred as a separate trading-behavior proposal and is not part of FIX-112C.

## Production behavior safety

- FIX-112C persistence is best-effort Replay infrastructure. A persistence failure may make a later Replay window incomplete, but it must not delay or alter live Order Book sampling.
- The dedicated executor uses a bounded queue and `AbortPolicy`; importantly it does **not** use `CallerRunsPolicy`, because that would move a slow DB insert back onto the live collector thread.
- FIX-112A consumption semantics were not broadened in this follow-up: no additional entry behavior was changed beyond the already approved consumed-entry correction.

## Deployment

1. Deploy FIX-112A code and V74/code together as the normal application release; Flyway applies V74 before runtime collection begins.
2. Verify `order_book_snapshot` exists and the `idx_ob_symbol_observed` index is present.
3. Let Production collect fresh Order Book history before using a post-V74 window for exact Order Book Replay comparison.
4. Regression-check the SHIB blocked-BUY continuation case and compare Production vs Replay Order Book outputs at identical historical timestamps.

## Known boundary

Historical windows before V74 cannot be made exact for Order Book because the original Binance depth snapshots were never persisted. They remain explicitly `UNAVAILABLE`; the application must not fabricate or substitute live data.


## FIX-112A final signal-consumption correction

Final review confirmed that `wallet_trade.signal_id` is written from the exact `TradeSignal` passed to `WalletAutoExecutionService.executeBuy(...)` for both initial entries and progressive adds. `PaperTradingService` also already resolves progressive-add quantity using `findTopBySignalIdAndSideAndStatusOrderByExecutedAtDesc(signal.getId(), "BUY", "EXECUTED")`, confirming that each successful add is keyed to its own triggering signal.

`EntryConsumptionPolicy` therefore no longer treats any OPEN symbol position as proof that the immediately previous bullish signal was consumed. It resolves the exact previous signal ID against an EXECUTED BUY row. Replay mirrors this with a replay-local set of consumed signal IDs and marks both successful initial shadow entries and successful progressive shadow adds. A blocked first entry and blocked progressive add remain NOT_CONSUMED; an executed first entry and executed progressive add are CONSUMED. No Production entry thresholds or progressive sizing rules are changed.
