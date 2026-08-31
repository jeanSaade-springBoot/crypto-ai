# FIX-11J — Persisted Replay Stage Performance Diagnostics

## Scope

FIX-11J is Replay-only observational instrumentation. It does not optimize Replay and does not change Production trading behavior or Replay business behavior.

The existing Replay pipeline remains in the same order and continues to use the same decision logic, thresholds, data boundaries, source-signal lineage, fresh analysis, shadow execution, parity checks, exception behavior and wallet isolation.

## Persisted measurements

Each Replay run can persist actual monotonic elapsed durations measured with `System.nanoTime()` for:

- Load historical data
- Verify event resolution
- Build ReplayDataset
- Generate fresh signals
- Shadow execution
- Parity comparison
- TOTAL

Durations are stored as nullable `BIGINT` nanoseconds on `analysis_test_run`. Existing runs therefore remain valid and display timing as unavailable. OLD Database Replay has no ReplayDataset build stage, so that field remains `NULL` and the UI renders it as not applicable rather than fabricating a zero duration.

## Failure-safety boundary

Timing values are accumulated locally while the existing Replay code executes. A single diagnostics-only persistence update runs from `finally` after the Replay path completes or unwinds. That update is best-effort and cannot replace an existing PASSED/FAILED/ERROR result or swallow a Replay exception.

No timing code executes a Replay stage twice, reorders stages, changes transactions, changes trading queries, changes signal persistence, changes shadow trades, or changes Production.

## UI

`Proven / Analyze Trades` displays a compact Replay Performance section for the selected run. `Recent Test Runs` also displays the persisted TOTAL duration. Selecting an older run reloads its persisted breakdown after browser refresh or application restart.

## Migration compatibility

V77 adds nullable timing fields to `analysis_test_run`. It also aligns `analysis_test_run_archive` with live run metadata added after the archive table was originally created (`heartbeat_at`, `replay_price_mode`, `replay_logic_mode`) before adding the same timing fields. This preserves the existing positional archive statement `INSERT ... SELECT ?, r.*`; no Production trading table is modified.

## Deliberate stop point

FIX-11J does not optimize `verifyEventResolution()`, `AnalysisService`, ReplayDataset, shadow execution, parity queries, or any other Replay stage. The next decision must be based on actual timings from a subsequent Replay run.
