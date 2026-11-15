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

# Production paper trade #16 (XLMUSDT) â€” 2026-11-16 KSA

- Scope: read-only investigation of closed `paper_position.id=16`, XLMUSDT.
  No application, configuration, Production database, or wallet data was
  changed.
- Entry: wallet trade #34 bought 1,785.714285714285 XLM at 0.14000000 for
  250 USDT. The authoritative observed-price evidence was 21:09:02.377 UTC
  (00:09:02.377 KSA); the wallet execution committed at 00:09:05.589 KSA.
  It used accumulated fresh evidence (3 BUY, 10 WATCH, evidence score 19),
  with fresh 5m BUY and 1h NEUTRAL context. The entry-quality guard marked it
  `LATE_ENTRY` (50/100) because of a nearby 0.1402 rejection zone, so it
  limited initial allocation to 25% rather than blocking it.
- Exit: wallet trade #35 sold at 0.13940000 at 00:20:12.575 KSA, producing
  -1.071428571429 USDT (-0.428571%). It was not stop-loss or take-profit:
  the execution reason was `SELL_CONFIRMED`. Signal #35591 was 1m
  `STRONG_SELL` (80 confidence), and signal #35590 was 5m `SELL`; the exit
  message records a non-bullish 1h context. This is the normal-confirmed exit
  path in `PositionExitPolicy`.
- Outcome: immediately after the exit, XLM reached 0.1392 before recovering;
  it remained under entry at +15 minutes (0.1396), recovered to 0.1404 at
  +30 minutes, and later reached 0.1406 within the next hour. The exit avoided
  a deeper initial decline, but later forewent a recovery. Available evidence
  supports a valid losing trade rather than a confirmed logic or execution
  defect.
- Timestamp caveat: the MySQL session uses `W. Europe Standard Time` (UTC+01),
  so direct SQL renders TIMESTAMP values one hour later than UTC. The UTC
  observed-price evidence and the supplied UI time reconcile to the KSA
  timeline above; do not treat raw session-rendered SQL values as KSA.
- Replay: not run. A parity conclusion cannot be made without a bounded
  Replay window and its evidence-qualification results.

## Follow-up: why XLMUSDT did not enter around 22:00 KSA

- Scope: read-only production query around 15 November 2026, 22:00 KSA. No
  XLM wallet execution exists in the reviewed 21:45–22:16 KSA window.
- At 22:00 KSA, the 1m signal was `NEUTRAL` and the contemporaneous 1h signal
  was `SELL` (score 40, weak downtrend). There was therefore no eligible BUY
  authority at that exact time.
- A 1m BUY around 22:05 KSA was blocked by `RANGE_ENTRY_LOCATION`: raw score
  87 was reduced to effective confidence 49. Around 22:08 KSA, a further 1m
  BUY was blocked by `ATR_RISK`; the strategy requested a retracement and
  recommended 0% size. A later 5m BUY at about 22:15 KSA was blocked by
  `MULTI_TIMEFRAME`, consistent with the bearish 1h context.
- Conclusion: the lack of entry near 22:00 KSA was intentional gating, not a
  missed wallet execution or a technical failure.

## Production paper trade #15 (ETHUSDT) â€” 2026-11-15 KSA

- Scope: read-only investigation of closed `paper_position.id=15`, ETHUSDT.
  No application, configuration, Production database, or wallet data changed.
- Entry: wallet trade #32 bought 0.081703796503 ETH at 1,835.90 for 150 USDT.
  Its authoritative observed price was 13:53:00.045 UTC (16:53:00.045 KSA),
  and it committed at 16:53:02.961 KSA. Signal #24140 was 1m `STRONG_BUY`
  (85 score, 73 confidence), but context was cautious: 5m NEUTRAL, 1h WATCH
  in a strong downtrend, an ask wall near the target, and entry quality 50/100.
  `IMMEDIATE_VALIDATION` therefore approved only a 25% exploratory allocation,
  producing the 15%-of-budget buy.
- Exit: wallet trade #33 sold at 1,830.55 at 17:00:08.020 KSA for
  -0.437115311433 USDT (-0.291410%). The exit reason was `SELL_CONFIRMED`.
  At the decision point, the 1m signal was SELL (score 33, confidence 80),
  the 5m signal was SELL (score 41), and 1h was WATCH/non-bullish. This is the
  normal lower-timeframe-confirmed exit path in `PositionExitPolicy`, not a
  stop-loss or take-profit exit.
- Outcome: ETH continued down immediately after the exit: 1m closed 1,827.12
  in the next minute and 1,821.69 within five minutes, then reached 1,807.06
  within roughly 31 minutes. The exit avoided a materially larger loss.
- Confirmed concern for follow-up, not a concluded defect: the persisted plan
  reports stop 1,826.7205, target 1,841.0553 and risk/reward 0.561605, while
  the attached ATR explanation says “Stop uses 2.0 ATR and target uses 3.0
  ATR.” At ATR 1.7184, the stored stop is materially farther than 2 ATR and
  the reward/risk is below 1. This needs a separate code/config trace before
  calling it a bug.
- Timestamp caveat: direct MySQL queries render TIMESTAMP in the server’s
  UTC+01 session. KSA values above are reconciled from UTC evidence and are
  not raw SQL display values. Replay was not run.

## Production paper trade #14 (SUIUSDT) â€” 2026-11-15 KSA

- Scope: read-only investigation of closed `paper_position.id=14`, SUIUSDT.
  No application, configuration, Production database, or wallet data changed.
- Entry: wallet trade #30 bought 340.506673930809 SUI at 0.7342 for 250 USDT
  at 16:00:12.172 KSA. It was a `SETUP_CONFIRMATION_WAKEUP`: a fresh 5m
  `STRONG_BUY` (#22801) reactivated an earlier unexecuted opportunity, while
  the latest 1m signal (#22800) was WATCH at 0.7347. The entry was a 25%
  confirmation allocation under the balanced-strong higher-timeframe profile.
- Assessment at entry: this is not the XLM-style late-entry pattern. The 1m
  assessment said `STANDARD_ENTRY`, price was only 1.05 ATR from SMA20, and
  there was no `LATE_ENTRY`, rejection-zone, ATR-risk, or other entry block.
  The cautious 25% allocation reflected mixed higher timeframes (5m bullish,
  1h neutral/strong-downtrend), not a late-price chase.
- Exit: wallet trade #31 sold at 0.7358 at 16:22:02.547 KSA for
  +0.544810678289 USDT (+0.217924%). It was `PROFIT_LOCK_EXIT`: the position
  had achieved 82.24% of target distance, then price breached the protected
  lock while 1m/5m/1h were all neutral. The configured profile had 70%
  activation, 40% initial lock, and 10% trail.
- Outcome: after exit, the next minute traded as high as 0.7371 but did not
  reach the 0.737482887855 target; shortly afterward SUI reversed to 0.7335.
  Available evidence supports the profit-lock exit as a valid protection of a
  small gain, not a confirmed early-exit defect. Replay was not run.

## Production paper trade #12 (ENAUSDT) — 2026-11-15 KSA

- Scope: read-only investigation of closed `paper_position.id=12`, ENAUSDT.
  No application, configuration, Production database, or wallet data changed.
- Entry: wallet trade #25 bought 3,311.258278145695 ENA at 0.1208 for 400 USDT
  at 13:45:12.144 KSA. Signal #19367 was 1m BUY (score 80, confidence 76)
  with 5m WATCH and 1h WATCH. `IMMEDIATE_VALIDATION` treated this as an early,
  balanced-profile reduced entry.
- Late-entry assessment: not late. Entry Quality was 73/100, explicitly
  `GOOD_ENTRY`, and price was 0.50 ATR from SMA20. A nearby 0.1209 rejection
  zone capped the initial allocation at 40%, but was not a hard veto and did
  not change the entry classification to `LATE_ENTRY`.
- Exit: wallet trade #29 sold at 0.1204 at 14:05:11.627 KSA for
  -1.324503311258 USDT (-0.331126%). It followed signal #19893, a 1m
  `STRONG_SELL` (score 16, confidence 80), via the `SIGNAL_SELL` execution
  path. Immediately after exit ENA reached 0.1201 before a later recovery to
  0.1215 within the following hour; this supports a valid protective exit at
  the decision point but does not prove the later recovery was knowable.
- Conclusion: no late-entry or missed-blocker defect is confirmed for this
  trade. Replay was not run.

## ENAUSDT move review — 2026-11-15 23:20 to 2026-11-16 00:05 KSA

- Scope: read-only production review of ENA candles, signals, and wallet
  executions. No wallet execution occurred in the reviewed interval.
- Market path: ENA rose from approximately 0.1202 at 23:20 KSA to 0.1213 by
  23:34 KSA (about +0.9%), then retraced toward 0.1204 before a later move
  resumed near midnight.
- Admission behavior: the apparent breakout was not silently missed. At about
  23:21 KSA, a 1m STRONG_BUY (score 93) was blocked by
  `RANGE_ENTRY_LOCATION`. At about 23:24 and 23:26 KSA, BUY/STRONG_BUY signals
  were blocked by `ATR_RISK`, with a retracement requested near 0.12037–0.12040
  and zero recommended initial size. At about 23:35 KSA, another BUY was also
  blocked by `ATR_RISK` after price had extended more than four ATR from SMA20.
- Interpretation: the system intentionally refused to chase the first sharp
  rise. Near 00:04–00:05 KSA, fresh 1m/5m BUY signals became allowed as price
  structure reset; no wallet execution is present in this bounded window, and
  the available records do not establish why no later execution occurred.

## Supplied Trade Inspector #13 (PEPEUSDT) identity mismatch - 2026-11-16 KSA

- Scope: read-only attempt to investigate the supplied completed PEPEUSDT
  trade with open/close times 2026-11-15 13:46:05/13:50:46 KSA and P&L
  -1.85185185 USDT (-0.741%). No application, configuration, Production
  database, or wallet data was changed.
- Evidence mismatch: the current configured `crypto_ai` server reports
  `paper_position.id=13` as EDUUSDT on 2026-10-09, not the supplied PEPEUSDT
  trade. It has no PEPEUSDT wallet execution, paper position, or signal in
  the supplied 15 November window, and no wallet/position row matching the
  supplied P&L. Its newest PEPEUSDT persisted signal is 2026-11-14.
- Conclusion: a late-entry diagnosis would be invented from the currently
  available evidence. Resolve the application database/connection identity or
  provide the Trade Inspector record's entry and exit wallet-trade IDs before
  retrying this investigation.

## Production paper trade #13 (PEPEUSDT) - corrected Contabo investigation - 2026-11-15 KSA

- Scope: read-only investigation against Contabo production `vmi3479567:3307`,
  schema `crypto_ai`, using the Trade Inspector identifiers: paper position
  #13, wallet BUY #27, wallet SELL #28, and entry signal #19380. The earlier
  local JohnnyM result above is superseded for this trade; that connection was
  not the deployed application's database. No application, configuration,
  Production database, or wallet data was changed.
- Entry and timing: signal #19380 was generated at 13:46:03.693 KSA for the
  13:45 one-minute candle, and its work completed with paper position #13 at
  13:46:05.999 KSA. Wallet BUY #27 used the observed price at 13:46:02.265
  KSA and committed at 13:46:05.731 KSA: there is no execution backlog or
  delayed-processing evidence.
- Earlier attempts: this was not the first PEPE opportunity. Position #8
  bought at 13:01 KSA near 0.00000401 and took profit at 13:11 near
  0.00000408. Position #11 bought at 13:16 near 0.00000406 and stopped at
  13:28 near 0.00000402. Between that stop and #13's entry, persisted 1m
  signals were NEUTRAL/WATCH and 5m signals were NEUTRAL; no earlier eligible
  BUY is present in that post-stop interval.
- Entry quality: #19380 was a fresh BUY (score 77, confidence 64),
  `STANDARD_ENTRY`, entry quality 80/100, 0.84 ATR from SMA20, with no blocker.
  It was deliberately only a 25% exploratory allocation because 5m was
  NEUTRAL and 1h WATCH/strong-downtrend. The entry price 0.00000405 was below
  the prior 13:11 peak (0.00000408), but it was the high of the immediately
  preceding 16-minute 0.00000402--0.00000405 range. The current entry-quality
  logic did not classify that local-range location as `LATE_ENTRY`.
- Exit: wallet SELL #28 stopped at 13:50:46 KSA at 0.00000402, crossing the
  persisted 0.000004023744 stop. Realized P&L was -1.851851851852 USDT
  (-0.740741%).
- Conclusion: no late execution or blocked earlier post-stop BUY is confirmed.
  The valid concern is strategy quality: the system re-entered on a local-range
  high despite mixed higher timeframes; its current rules allowed the reduced
  25% trial rather than vetoing it. This is a candidate future entry-quality
  policy review, not a confirmed execution defect.

## PEPEUSDT move review - 2026-11-15 11:20 to 12:16 KSA

- Scope: read-only Contabo production review of PEPEUSDT one-minute candles,
  persisted BUY decisions, blockers, and wallet executions. No application,
  configuration, Production database, or wallet data was changed.
- Market path: PEPE was approximately 0.00000388 at 11:20 KSA, rose to a high
  of 0.00000405 at 12:11 KSA (about +4.4%), then traded near 0.00000403 at
  12:16 KSA.
- Admission behavior: the system did not execute a PEPE wallet BUY in this
  interval. At 12:05 KSA, 5m signal #16814 was an original BUY (score 79) but
  was blocked by `MULTI_TIMEFRAME`, reducing confidence to 49. At 12:09 KSA,
  1m signal #16903 was an original STRONG_BUY (score 85) but was blocked by
  `ATR_RISK`, reducing confidence to 49 and requesting a pullback near
  0.000003987754. The reviewed market low after that request was about
  0.00000402, so that retracement condition was not reached.
- Interpretation: this is a genuine blocked-opportunity candidate, not an
  execution-delay finding. It supports a future review of how the
  multi-timeframe and ATR-risk gates interact during a sustained breakout;
  subsequent price appreciation alone does not prove that the contemporaneous
  vetoes were invalid.

### Score and confidence interpretation

- `total_score` measures the technical setup; confidence is a separate
  context/trust measure. The Trade Inspector's existing `confidence` field is
  the effective confidence used by the entry authority, not necessarily the
  raw confidence.
- In the reviewed PEPE move, blocked 5m BUY #16814 had technical score 79 and
  raw confidence 66; blocked 1m STRONG_BUY #16903 had technical score 85 and
  raw confidence 64. Both show effective/displayed confidence 49 because the
  final entry was blocked (`MULTI_TIMEFRAME` and `ATR_RISK`, respectively).
- Code evidence: `FinalDecisionService` calculates
  `effectiveConfidence = entryAllowed ? rawConfidence : min(rawConfidence,49)`.
  An allowed example, #19380, retained raw/effective/displayed confidence 64.
- UI learning recommendation: show technical score, raw confidence, effective
  confidence labelled as capped when blocked, and the primary blocker as four
  separate facts. Do not treat 49 as a measured market-confidence value.
