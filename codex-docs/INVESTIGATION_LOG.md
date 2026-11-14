# Local candle coverage check — 2026-10-09

# FIX-156 applied-migration verification — 2026-10-16

Read-only Flyway evidence (`DBQ-FIX156-V110-CHECK-CODEX`) showed V110
`fix 156 replay owned tables` already applied successfully. An in-progress
attempt to extend V110 was immediately reverted before validation/deployment;
V110 is retained in its original applied form. All additional Replay-owned
schema is placed in new forward-only V111. This is a migration-safety finding,
not a Production behavior change.

- Scope: read-only check of all 21 enabled symbols in `crypto_ai.candle`, over the two-hour window ending at the database UTC clock.
- Current state: all 63 enabled symbol/interval lanes (1m, 5m, 1h) have closed-candle records and current live ingestion. The newest 1m candle was 54 seconds old at the check.
- Continuity: 5m and 1h had no internal gaps in the inspected window. Twelve symbols had one missing 1m candle: the interval from `2026-10-08T22:18:00Z` to `2026-10-08T22:19:00Z` (KSA `01:18` to `01:19`). This coincides with the local application startup period; the check establishes the gap but does not prove its cause.
- Impact: current live signals are being generated, but the missing historical 1m candles should be reconciled before relying on uninterrupted historical indicator lineage for that startup window. No database data or code was changed by this check.

# Signal-staleness root cause — 2026-10-09

- System Health CRITICAL signal-staleness alerts were confirmed as accurate for 1m and 5m analysis, not a dashboard timezone issue. At the check, candles continued through `2026-10-09T06:43:00Z` (1m) and `06:35:00Z` (5m), while the newest live signals were `04:34:03Z` (1m) and `04:30:07Z` (5m).
- The most recent 210-candle data-quality window has a synchronized gap for every enabled symbol: missing 1m open `2026-10-09T04:34:00Z` (KSA 07:34) and missing 5m open `2026-10-09T04:30:00Z` (KSA 07:30).
- Cause confirmed from the WebSocket lifecycle log: generation 1 was revoked at `2026-10-09T04:35:00.133951100Z`; generation 2 was admitted at `04:35:01.369103400Z` (1,236 ms interruption). The affected 1m and 5m candles had both closed at the reconnect boundary. Binance did not replay their final closed-kline events, leaving the already-created local rows open rather than finalized.
- `CandleDataQualityService` is configured with `minimum-candles: 210` and `maximum-missing-candles: 0`. Therefore one missing candle makes the entire relevant analysis window invalid. Runtime logs confirm `DATA_QUALITY_BLOCKED` with `Missing 1 candle(s) in the analysis window` for current 1m and 5m worker attempts.
- 1h has no comparable gap and produced a live signal at `2026-10-09T06:00:03Z` (KSA 09:00).
- No repair or configuration change was made. Correct remediation must restore authoritative missing candles rather than relaxing the zero-missing data-quality safety rule.

## Bounded local repair completed

- Authorized local repair finalized the 42 interrupted `RUNTIME` rows using Binance REST candles for the exact identities above: 21 x 1m and 21 x 5m.
- Each response was validated for the expected open and close timestamp before one transaction finalized only a row where `closed=0`. Existing closed candles, provenance, and close-transition tokens were not changed; no close-dispatch, analysis, signal, or trade was initiated by the repair.
- Verification: all 1m/5m 210-candle quality windows had zero gaps after the repair. The next live close produced new LIVE signals at `2026-10-09T07:30:07Z` for both 1m and 5m.

# ETHUSDT paper-trade 59 investigation — 2026-10-09

- Scope: local `crypto_ai` paper trade `wallet_trade.id=59` for `ETHUSDT`; all database reads were bounded and read-only. Stored UTC timestamps are shown below with their KSA (`UTC+03:00`) interpretation.
- Entry: signal `trade_signal.id=69226` was generated at `2026-10-09T07:33:00.233691Z` (10:33:00.234 KSA) from the 1m candle opened at 10:32 KSA. It was a `BUY`, score 79, confidence 70, entry allowed, at 2502.77. The trade executed at `07:33:01.654548Z` (10:33:01.655 KSA), 1.42 seconds after the signal; this is not an execution-latency issue.
- Exit: `wallet_trade.id=60` closed the position at 2504.30 at `07:34:34.196100Z` (10:34:34.196 KSA) with `PROFIT_LOCK_EXIT`. The managed position recorded a high of 2505.80 and an activated profit-lock price of 2504.429502243252. The realized paper gain was about 0.02445 USDT before fees (0.015982291620 ETH x 1.53 USDT).
- Market path: ETH rose from 2485.88 at 07:54 KSA to 2502.77 at entry (+16.89, +0.68%). It reached 2505.56 at 10:25 KSA before the entry; entry was 2.79 (0.11%) below that high. The post-entry high was 2505.93 at 10:34 KSA, only 3.16 (0.13%) above entry, then the candle retraced. This supports the user's observation that entry occurred late in the observed move.
- Primary cause: the local candle-quality interruption stopped 1m ETH signals after 07:34 KSA. There were no ETH 1m signals from 07:34 to 10:26 KSA, the period containing most of the rise. The interrupted 1m and 5m rows were repaired at about 10:28 KSA, so analysis resumed only near the later consolidation/breakout attempt. This is a confirmed data-availability gap, not proof that the strategy would have bought near 07:54 KSA.
- Immediate signal sequence after recovery: at 10:30 KSA the 5m signal was a BUY but blocked by `RANGE_ENTRY_LOCATION`; at 10:31 KSA the 1m raw BUY was vetoed by a bearish BTC context; at 10:33 KSA BTC context became neutral and the 1m trend-following BUY was allowed. The entry signal itself explicitly recorded mixed higher-timeframe context (5m BUY, 1h NEUTRAL) and a weakening but ultra-close ask wall before its target.
- Assessment: the trade followed the implemented rules once local analysis resumed, and the trade was entered/exited promptly. It was nevertheless a late entry relative to the full 07:54–10:33 KSA advance because the system lacked signal coverage during the main move. The data gap is an operational defect already covered by the local-candle recovery deployment gate. Whether the unobserved earlier period would have produced an executable BUY is uncertain without a controlled Replay run; none was started because that would create replay state.
# SHIBUSDT signal #89469 / position #35 — 2026-10-10

- Scope: read-only local `crypto_ai` investigation of wallet trades `106`/`107`,
  paper position `35`, managed position `64`, and signal `89469`.
- Timestamp correction (2026-10-10): the preceding interpretation was wrong
  because the read-only investigation connection used MySQL's `SYSTEM` timezone
  (KSA), while the application connection explicitly sets its session to UTC.
  For position `35`, MySQL's KSA session renders `opened_at` as
  `2026-10-09 23:45:06.682175`; converting that instant to UTC yields
  `2026-10-09 20:45:06.682175`. The Inspector's main trade card parses backend
  timestamps as UTC and renders them in the browser-local timezone. Therefore,
  with a KSA browser, its displayed 23:45 entry time is correct. Do not add
  three hours to the value shown by this KSA-session SQL connection.
- Signal confirmation: `trade_signal.id=89469` was generated at
  `2026-10-09 23:45:03.498678` KSA (`20:45:03.498678` UTC), from the 1m
  candle opened at 23:44 KSA. It is `WATCH`, score 60, confidence 74,
  `WEAK_UPTREND`, `TREND_FOLLOWING`, and `final_entry_allowed=1`.
- Entry: `0.000005410`; stop `0.000005382950`; TP `0.000005433730`; the
  25%-size setup-wakeup route entered from a 5m BUY with 1h NEUTRAL context.
- Exit: highest recorded price before close was `0.000005430`, or 84.2815% of
  the original TP distance. The dynamic lock activated at `0.000005421865` and
  live price `0.000005420` breached it. At the breach the current 1m and 5m
  were WATCH and 1h was NEUTRAL. The shared exit policy therefore returned
  `PROFIT_LOCK_EXIT`; it requires bullish 1h plus supportive 5m, or WATCH 1h
  plus supportive 5m, to hold a breached lock.
- TP extension: not evaluated. Extension begins only once price is at or above
  the original TP. The recorded high remained below TP at the time of exit.
- Subsequent action: later local candles reached `0.000005500` at about 00:40
  KSA, above the original TP. This is hindsight, not evidence available at the
  exit. Local SHIB 1m/5m coverage ends at about 00:56 KSA, so any claimed
  continuation through 12:15 KSA is not verifiable from local candle evidence.
- Assessment: behavior followed the existing protection rule, without a
  confirmed execution or latency defect. A future exit-policy change could
  explicitly allow a near-TP WATCH/WATCH/NEUTRAL continuation hold under
  measurable momentum/structure conditions and a tighter safety floor; it
  requires a separate design and Replay validation.

# System Health critical after FIX-154 deployment â€” 2026-10-10

- Scope: read-only database and local-log diagnosis; no configuration, service,
  code, or database change was made.
- Finding: current candles are live for all enabled symbols and intervals. The
  critical state is signal staleness, not Binance ingestion or price protection.
  At the check, 15 symbols had last 1m signals around 00:15 KSA while their
  latest closed 1m candles were at 01:26 KSA.
- Direct evidence: `CandleClosedAnalysisWorker` logs at 01:28 KSA show live
  close events being processed but ending in `DATA_QUALITY_BLOCKED`, e.g.
  `warnings=[Missing 1 candle(s) in the analysis window]` for ONG/ENA/SOL and
  `Missing 3 candle(s)` for AERO. The worker correctly does not persist a new
  `trade_signal` when that zero-missing-candle gate fails.
- Assessment: this is an existing local-candle continuity/data-quality issue;
  FIX-154 higher-timeframe BUY assessment is not reached for these blocked
  1m analyses and is not the demonstrated cause of the critical health state.

# SHIBUSDT #89469 near-TP continuation investigation — 2026-10-10

- Status: investigation approved; implementation is explicitly not approved.
  This workstream must not alter or delay FIX-153 release work.
- Hypothesis to test: a breached profit lock close to target may sometimes
  continue when current market structure and momentum remain supportive.
- Important counter-hypothesis: `1h=NEUTRAL` is not positive continuation
  evidence merely because 1m/5m are `WATCH`. Relaxing that guard on distance
  to TP alone could create false holds in weak or sideways markets.
- Required evidence before a policy change: use historical Replay/production
  evidence to compare the current exit rule against a hypothetical bounded
  hold using only information available at the breach. Segment results by
  1h/5m context, regime, trend structure, momentum, retracement depth and
  target-progress band. Evaluate TP hit rate, retained profit, adverse move,
  drawdown, and false-hold frequency. No post-exit price may inform entry or
  exit eligibility.
- Rejected approach: do not authorize continuation from `WATCH/WATCH/NEUTRAL`
  or near-TP distance alone.
- Code-review refinement: `PositionExitPolicy` creates a sharp tier boundary:
  with supportive 5m/current evidence, 1h `WATCH` can hold a breached lock but
  1h `NEUTRAL` falls through to `PROFIT_LOCK_EXIT`. Existing FIX-11T near-TP
  protection arms at 90% target progress and is evaluated after the ordinary
  lock-breach exit, so it could not affect #89469 at 84.28% progress.
- Fee-model refinement: `WalletAutoExecutionService` writes `fee_usdt=0` for
  paper BUY, SELL, and near-TP partial-harvest executions. Any policy Replay
  comparison must add an explicit fee/slippage model before claiming protected
  gross profit is a net gain.
- Approved experiment protocol from independent review: Production policy is
  unchanged. Compare (A) existing immediate lock exit, (B) a one-window,
  near-TP continuation using existing time-safe structure/momentum/context
  calculations, and (C) a volatility-adjusted tighter trailing exit. Use a
  pre-registered 85% TP-progress primary threshold; #89469 at 84.28% does not
  qualify and must not drive threshold selection. Require non-bearish 5m/1h,
  a fee-adjusted protected floor with modeled adverse fills, explicit numeric
  momentum/retracement definitions, no timer resets or repeated extensions,
  and strictly as-of-breach evidence. Evaluate net expectancy, drawdown,
  false-hold/adverse-move rate, and include later-decline counterexamples;
  reserve an out-of-sample period before any implementation decision.
- Final protocol controls: calculate TP progress only from immutable original
  entry and original TP; exclude and report zero/negative/invalid target
  distance. Every A/B/C candidate must use identical historical data,
  evaluation periods, and fee/slippage assumptions. Candidate B must model an
  intrawindow price path sufficiently to determine which of its emergency
  floor or target was reached first; candle high/low alone is not evidence of
  that order. Pre-register the minimum sample size, required net-expectancy
  improvement, and maximum allowed drawdown deterioration before results are
  viewed, then report the qualification count for the B cohort.
- Final cohort and comparison definition: Candidate B is eligible only in the
  85% to strictly-less-than-90% original-TP-progress band, with fresh,
  quality-valid 1h `NEUTRAL`, supportive fresh/quality-valid 5m, and required
  current-frame structure/momentum evidence. Missing, stale, invalid, or
  unknown 1h/5m evidence makes the case ineligible rather than non-bearish.
  This avoids changing or competing with existing FIX-11T behaviour at 90%.
  Pre-count the eligible cohort; insufficient sample means keep collecting.
  B-versus-A is the sole primary comparison; C is secondary/exploratory and
  must have its trailing formula fixed before Replay. Verify ATR, spread, and
  tick-size availability as of breach; if historical spread is unavailable,
  use a declared fixed assumption and sensitivity analysis.
- Final breach semantics: eligibility uses `breach_tp_progress`, calculated
  from the executable price at the exact profit-lock breach, not the historic
  high-water mark. Persist/report both `max_tp_progress` and
  `breach_tp_progress`. The continuation emergency floor is distinct from the
  breached original lock; if actual executable price (including modeled
  fee/spread/slippage) already violates that floor, exit immediately. Freeze
  numerical precision, 90%-boundary precedence (FIX-11T owns 90%+), hold
  length, tolerance, floor, costs, sample threshold, and success criteria
  before inspecting results. Report confidence intervals and chronological
  out-of-sample results; a small qualifying cohort is insufficient evidence.
- Team-2 final validity controls: create and commit a numerical pre-registration
  before any outcome is viewed, recording its immutable content hash here.
  Verify available intrabar path data; where tick/price-event evidence cannot
  order TP versus emergency-floor contact, mark the case ambiguous, apply one
  conservative adverse-move-first rule consistently, and report analyses both
  with and without those cases. Compute the floor per trade as
  `max(original_lock - tolerance, fee_adjusted_breakeven)` and report when
  breakeven binds. Compare A and B as paired outcomes on the same eligible
  breach events, reporting per-event B-minus-A and the count of later declines
  avoided by A.

# Replay-versus-Production parity audit — 2026-10-10

- Scope: source-level comparison of the manual Regression/Replay pipeline with
  the current paper-trading Production path. No trading code, configuration,
  database data, or Replay result was changed.
- Confirmed shared authority: Replay regenerates technical indicators through
  `TechnicalIndicatorService`, signals through `AnalysisService`, BUY authority
  through `ExecutionIntelligenceService`, BUY/SELL validation through
  `TradeExecutionValidationService`, and normal exit, TP-continuation,
  profit-lock, and near-TP policies through the corresponding shared policy
  classes. When canonical `LEGACY`/`APPLIED` price events exist, the Replay
  consumes them in timestamp order before making the same close-candle signal
  visible, matching the documented live ordering.
- Confirmed boundary: Replay is a shadow simulation, not a transactionally
  identical copy of Production. It starts with fixed capital of 10,000 USDT,
  manages one symbol/position in isolation, uses replay-local daily budget and
  position state, and writes only `*_test` tables. It therefore cannot
  reproduce an actual wallet balance, simultaneous positions, global
  `maxOpenPositions`, daily loss limit, idempotency races, database locks, or
  real execution latency.
- Confirmed historical-evidence limitation: derivatives history is not
  persisted, so historical Replay always receives `UNAVAILABLE` derivatives
  context when that feature is enabled. Historical order-book evaluation uses
  stored snapshots only and becomes `UNAVAILABLE` when no snapshot exists,
  whereas live Production samples its in-memory/live Binance source. Such runs
  are useful simulations but are not full signal-parity proof unless the
  required historical evidence exists and is reported.
- Confirmed price-evidence limitation: a Replay run labels itself
  `EXACT_PRICE_REPLAY` whenever at least one accepted price event exists. It
  does not prove complete tick coverage for every time an open position needed
  protection. Windows with no accepted events fall back to signal prices and
  are labelled `SIGNAL_PRICE_FALLBACK`; neither fallback nor partial coverage
  should be described as tick-exact reproduction.
- Test evidence: Java 21 focused tests passed for profit-lock/TP-extension
  parity, position exit/continuation, near-TP policy, and shared sizing.
  Existing Replay-specific coverage is narrow: it does not execute a complete
  Production-versus-Replay scenario across entry, add, wallet controls,
  price-event ordering, partial harvest, and exit.
- Conclusion: Replay is suitable for controlled, manual strategy regression
  and the approved FIX-155 research work only when each run carries explicit
  evidence-quality labels. It is not yet valid to claim it is fully identical
  to Production in all phases. A future parity program must add deterministic
  scenario tests and a run-level eligibility contract before using Replay as a
  release-equivalence certificate.
