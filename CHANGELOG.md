# Change Log

## FIX-071 — Daily production System Health diagnostics

- Refactored the left-menu System Health page around daily production invariants instead of aggregate AI-operation totals.
- Added read-only `/api/system-health/daily` diagnostics using Asia/Riyadh day boundaries converted to UTC Instants for production queries.
- Added daily closed-candle counts and trade-signal counts for 1m, 5m and 1h.
- Added BUY, SELL and open-position counts plus a conservative BUY/SELL imbalance diagnostic that does not assume every imbalance is a stuck process.
- Added enabled-symbol signal and candle staleness with explicit 1m/5m/1h WARNING/CRITICAL thresholds and missing-history detection.
- Added BUY/SELL 7-day baseline, BUY entry-route distribution, strategy/regime distribution and opportunity outcome distribution.
- Added a hard MISSING_CONTEXT operational alert: >0 WARNING, >5 CRITICAL.
- Baseline-dependent statuses remain LEARNING until a full seven days of production history exists.
- Kept runtime schedule/cadence visibility at the bottom of the page.
- Added FIX-071 to Fix Registry.

Trading, Replay, wallet, scoring, entry/exit and persistence behavior are unchanged. FIX-071 is observability-only.

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

### FIX-071A - System Health JdbcTemplate compile correction
- Replaced expression-style `JdbcTemplate.query(...)` row callbacks with block/void callbacks in `SystemHealthDailyService`.
- This removes Java overload ambiguity between `RowCallbackHandler` and `ResultSetExtractor<T>` (notably `ResultSetExtractor<Boolean>` from `List.add(...)`).
- No health thresholds, SQL semantics, trading behavior, replay behavior, or persistence behavior changed.
