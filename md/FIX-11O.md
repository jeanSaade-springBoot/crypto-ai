# FIX-11O — Replay historical-gap tolerance and directional signal visibility

## Scope
Replay/Test infrastructure and UI only. Production analysis, persistence, execution, wallet behavior, BUY/SELL authority, thresholds, and position management are unchanged.

## Evidence
PEPEUSDT Replay run #1 for 2026-08-30 13:00–20:30 UTC generated all requested fresh signals with zero generation errors, but the run was marked FAILED because historical event-resolution coverage was 445/446 for 1m. Production evidence showed an old signal-processing gap around 16:00 UTC; the fresh Replay itself still generated 446/446 1m signals.

## Change
- Preserve `replayable_*_events` as historical source/event-coverage diagnostics.
- Define `cadence_path_passed` from the fresh Replay output cadence: generated 1m/5m/1h signals must equal the requested candle counts.
- Keep zero generation errors as a separate mandatory PASS condition.
- Keep incomplete historical event coverage visible in the persisted result notes instead of silently ignoring it.
- Update the Replay result cards to headline fresh Replay cadence and show historical event coverage underneath.
- Add a Replay-only table showing fresh final BUY/STRONG_BUY/SELL/STRONG_SELL signals so directional output remains visible even when old Production source coverage has a gap.

## Safety
No Production path reads these UI changes or the revised Replay pass interpretation. No Production table is written. No signal is changed, added, removed, or re-scored by FIX-11O. Historical coverage defects remain visible; they simply no longer convert a complete, error-free Replay generation into FAILED.

## Validation
Repeat the same PEPEUSDT window. Confirm:
1. Replay fresh cadence is 446/446, 91/91, 8/8 (or the exact requested counts for that run).
2. Historical event coverage still reports the known 445/446 warning rather than being hidden.
3. Generation errors remain 0.
4. Decision authority remains PASS.
5. Fresh generated BUY and SELL rows are visible in the new directional-signals table.
6. Production behavior and tables remain unchanged.
