# Change Log

## FIX-070 — Unified chart hover and crosshair presentation

- Added one shared display-only ApexCharts crosshair overlay for Proven Analysis, Trade Activity, Dashboard and Trade Inspector.
- Pointer X labels now use `dd/MM/yyyy HH:mm` in Asia/Riyadh and pointer Y labels use adaptive price/value precision.
- Added matching horizontal/vertical crosshair lines and dark theme-matched axis badges.
- Replaced page-specific/default axis-hover behavior with the shared overlay while preserving chart zoom, pan, selection and click behavior.
- Dashboard candle open/close labels remain KSA-local but no longer append a GMT+3 suffix.
- Added FIX-070 to Fix Registry.

Trading, Replay, wallet and persistence behavior are unchanged; this release is UI-only.

## FIX-069 — Replay signal parity and Proven Analysis Lab layout

- Added `trade_signal_test`, cloned from production `trade_signal`, plus replay metadata (`test_run_id`, source id, replay flag, generation error).
- Replay now persists every generated `TradeSignal` field into the production-shaped test table, while retaining the legacy compact diagnostic table for compatibility.
- Added backend-only `trade_signal_test_archive` so Clear Data can remain safety-first without exposing an Archive section in the UI.
- Replay signal API now reads from `trade_signal_test`, enabling field-by-field Production vs Replay comparison.
- Test names are generated automatically from symbol + From + To.
- Progress and result cards are placed directly under the run input; result cards are compact.
- Recent Test Runs remains below the current run summary and now supports a Proven checkbox that promotes/removes all closed trades from that run.
- Removed the visible Archive section, archive-leg controls, timezone conversion sentence, and Clear Data archive explanation.
- Moved `PERSISTENT MANUAL REVIEW · Proven trades` to the end of the page.

Trading behavior is unchanged. This release changes replay persistence/diagnostics and review UI only.
