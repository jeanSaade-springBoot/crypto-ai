# FIX-11L — Replay Fresh-Signal Deep Performance Profiler

## Scope

Replay observability only. No Production trading behavior, Replay decision semantics, thresholds, event ordering, persistence semantics, BUY/SELL authority, or shadow execution behavior is changed.

## Evidence

PEPEUSDT ReplayDataset run #1 (`2026-08-30 17:00` to `20:30` UTC) passed 100% parity but spent 654.813 seconds of 717.652 seconds total in `Generate fresh signals` (about 91.2%). The run generated 258 primary replay signals. FIX-11J identified the stage but not the dominant operation inside it.

## Implementation

`RegressionTestWorker.generateFreshSignals()` now measures existing Replay operations with `System.nanoTime()` and aggregates the values in memory. One summary log is emitted when the stage ends, including cancellation/error exits. There is no per-signal logging and no new database write.

Measured categories:

- technical snapshot calculation
- `AnalysisService.analyzeForRegression()`
- `analysis_test_signal` persistence
- exact source-signal lineage lookup
- `trade_signal_test` persistence
- Replay progress/heartbeat update
- residual loop overhead (`otherMs`)

Snapshot and analysis timing also report call count, average milliseconds per call, and slowest call.

## Log marker

`FIX11L_REPLAY_SIGNAL_PROFILE`

The summary explicitly records `productionMutation=false replayDecisionMutation=false`.

## Next decision

Repeat the same PEPE ReplayDataset window. Use the measured dominant category to choose the next Replay-only optimization. No optimization is included in FIX-11L.
