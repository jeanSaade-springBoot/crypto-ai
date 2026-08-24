## FIX-073 — Replay restart recovery and Shadow Trades restoration
- Restored Shadow Trades visibility from Recent Test Runs, including partial active/interrupted runs.
- Added automatic recovery of PENDING/RUNNING replay jobs after application restart. Recovery keeps the same run ID/window and deterministically rebuilds isolated replay state from the beginning because mid-stream in-memory opportunity/wallet/position state cannot be resumed safely.
- Added a replay heartbeat and a manual Resume action only for stale PENDING/RUNNING runs; a live worker cannot be duplicated accidentally.
- Production trading tables and behavior are unchanged.

# Change Log

## FIX-072 — Production signal persistence safety

- Changed `trade_signal.explanation` from `VARCHAR(2000)` to `TEXT` after live diagnostics reached the 2,000-character ceiling and longer values caused Production signal INSERT failures.
- Applied the same `TEXT` capacity to `trade_signal_test.explanation` and `trade_signal_test_archive.explanation` so Replay keeps production-shaped persistence parity.
- Kept `uk_trade_signal_symbol_interval_candle`; startup bootstrap now skips analysis/execution when that exact symbol/interval/candle signal already exists instead of attempting a duplicate INSERT.
- Corrected System Health opportunity-outcome SQL for MySQL `ONLY_FULL_GROUP_BY` by grouping on the exact composite expression used in the SELECT.
- Removed the misplaced `System Health / Daily production diagnostics` footer from the System Health sidebar.
- Added code comments around persistence and duplicate-prevention behavior.

Trading scores, entry/exit rules, wallet sizing and strategy behavior are unchanged.

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

## FIX-071B — Global System Health alert visibility
- Added a read-only System Health badge to the left navigation on application screens: OK (green), WARNING (amber), CRITICAL (red).
- Added a compact global red banner only for CRITICAL health, linking directly to System Health and showing the highest-priority active issue.
- Global status refreshes every 60 seconds without changing trading, replay, wallet, opportunity, or persistence behavior.
- Seven-day baseline checks remain LEARNING until enough historical coverage exists; LEARNING does not become CRITICAL merely because the application is new.
- Candle/signal staleness and other day-one invariant checks remain active immediately and can therefore raise WARNING/CRITICAL before seven days if a real pipeline problem exists.

## FIX-071C - System Health endpoint resilience
- Prevent `/api/system-health/daily` from returning HTTP 500 when one diagnostic query fails.
- Isolate every health section and surface the failing component as a CRITICAL alert while keeping the rest of the page available.
- Log the full server-side exception for diagnosis; browser output is bounded and readable.
- Observability only: no trading, replay, wallet, execution, or persistence behavior changed.


## FIX-072A - System Health strict GROUP BY compatibility
- Reworked Opportunity Outcome aggregation to group raw `status` and `decision_code` columns inside a subquery, then build the display key in the outer query.
- This avoids MySQL `ONLY_FULL_GROUP_BY` ambiguity entirely, including on servers that reject equivalent expressions.
- No health thresholds or trading/replay behavior changed.
