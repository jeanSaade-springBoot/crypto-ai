# FIX-11H — ReplayDataset preload + OLD/NEW replay controls

Classification: Replay infrastructure/performance only — no Production behavior change.

- Adds immutable run-local `ReplayDataset` with exact closed/as-of/order/limit semantics.
- Historical prefix uses the existing proven `findClosedCandlesAtOrBefore` query; forward load explicitly requires `closed=true`.
- Adds a dataset-backed regression overload that calls the exact same shared `calculateSnapshot()` math.
- Adds explicit Replay-only `DATABASE` (OLD) and `DATASET` (NEW) modes.
- Replay Lab exposes fixed OLD and NEW buttons using the same symbol/window inputs.
- FIX-112D lineage is selected and cached by exact `candle_open_time` identity (`symbol+interval+candleOpenTime`), never by `generated_at`; recovery/backfilled signals may be persisted hours later. Duplicate identity fails loudly.
- `verifyEventResolution()` remains unchanged until parity is proven.
- Production behavior/path changes: zero. Shared repository/indicator classes receive additive methods only.

Parity rule: run OLD and NEW on the same stable historical window as separate test runs and compare execution-relevant signal fields plus the shadow BUY/SELL lifecycle before making DATASET the sole/default mechanism.
