# FIX-11E — Parallel Order Book Collection

## Scope
Production-critical acquisition/timing correction. `collectConfiguredOrderBooks()` dispatches enabled symbols to a dedicated bounded collection executor instead of performing Binance requests sequentially on the Spring scheduler thread.

## Single-flight protection
The guard is inside `collectSafely(symbol)`, not only around scheduled dispatch. This is required because live `evaluate()` also calls `collectSafely()` synchronously when its in-memory history is empty. At most one Binance Order Book request for a symbol can therefore be in flight across both callers. A live evaluation does not wait when the same symbol is already collecting; it continues through the established insufficient-data behavior.

## Overload
The collection executor uses `AbortPolicy`. Saturated submissions are logged and skipped until a later scheduled cycle; Binance network work is never pushed back onto the scheduler thread. The collection pool is separate from FIX-112C `orderBookPersistenceExecutor`.

## Replay = Production protection
FIX-11E does not change Order Book normalization, observation thresholds, `INSUFFICIENT_DATA_HOLD`, imbalance/wall calculations, veto rules, persisted snapshot semantics, or historical Replay evaluation. Production continues to persist the exact evidence that Replay consumes through the existing FIX-112C shared evaluator. Only live acquisition concurrency/timing changes.

## Tests
`OrderBookLiquidityServiceConcurrencyTest` is part of the normal Maven test source set. It verifies scheduler dispatch does not synchronously call Binance and verifies the scheduler/live-evaluate callers cannot overlap a Binance request for the same symbol.
