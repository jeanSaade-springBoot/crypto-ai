# Change Log

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
