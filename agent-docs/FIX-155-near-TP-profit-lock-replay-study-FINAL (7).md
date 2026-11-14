# FIX-155 — Near-TP Profit-Lock Replay Study

**Specification: FINAL for review** | Date: 2026-10-10 | Revision: 1.1 (adds F6 expiry/grace semantics, F7 protection continuity at handoff, and numeric freeze before the feasibility count)

## 0. Status and scope

| Item | Status |
|---|---|
| Research design | Approved by review |
| Replay-only experimentation | Approved, after the verification gates in Section 13 |
| Numerical pre-registration | Pending (Section 12) |
| Specification freeze | Pending (Section 14) |
| Replay implementation | Not started / not verified |
| Production implementation | NOT approved. Production behavior is unchanged. |

- This is a Replay-only research specification. It does not change Production.
- It is independent of FIX-153 and FIX-154 and must not alter or delay either release.
- No trading behavior changes because of SHIBUSDT #89469 or this document.
- No team work starts until the review phase closes and this document is accepted as the reference.

## 1. Objective

Determine whether one narrowly bounded continuation window after a profit-lock breach improves net, risk-adjusted outcomes compared with the current immediate-exit policy. The study does not relax the general 1h rule. It tests only a defined near-TP cohort with fresh, valid evidence.

## 2. Diagnostic example (not evidence)

SHIBUSDT paper position #89469. Database times are UTC; KSA = UTC+3.

| Item | Value |
|---|---|
| Entry | 0.000005410 (9 Oct 23:45:05 KSA / 20:45:05 UTC) |
| Original take profit | 0.000005433730 (+0.4386%) |
| Highest price before exit | 0.000005430 = 84.28% of the entry-to-TP distance |
| Lock activated | 9 Oct 23:58:09 KSA at 0.000005421865 = 50.0% of the TP distance |
| Exit | 9 Oct 23:58:43 KSA / 20:58:43 UTC at 0.000005420, PROFIT_LOCK_EXIT; +0.185% gross; progress about 42.1% |
| Context at exit | 1m WATCH, 5m WATCH, 1h NEUTRAL |

Findings from the source:
- The exit was correct under current policy. `PositionExitPolicy.evaluateProfitLockBreach` holds only for (1h bullish and 5m supportive) or (1h WATCH, 5m supportive, current frame not bearish). A 1h NEUTRAL falls through to PROFIT_LOCK_EXIT. This is a sharp threshold between 1h WATCH and 1h NEUTRAL.
- TP extension was never eligible, because price never reached the original TP.
- FIX-11T near-TP protection could not apply. It arms at 90% progress and runs after the profit-lock exit logic.
- Paper auto-trading records `fee_usdt = 0`, so +0.185% is gross paper P&L, not a net Binance-equivalent result.
- The trade does NOT qualify for the experiment (max progress 84.28% < 85%). The threshold must not be lowered to fit it. It is a regression/diagnostic fixture only, never validation evidence.

## 3. Production baseline (unchanged)

Exit immediately when the active profit lock is breached, unless existing higher-timeframe continuation rules already hold.

Lock geometry with default settings (activation 70, initial 40, step 10), from `ProfitLockPolicy`:

| Max TP progress reached | Lock level (% of TP distance) |
|---|---|
| 70% to under 80% | 40 |
| 80% to under 90% | 50 |
| 90% to under 100% | 60 |
| 100% | 70 |

The hard protected-profit floor is entry x 1.0005 (+0.05%), which is below realistic round-trip trading costs. That is a separate issue (Section 15).

Production evaluation order (live path, `LivePositionProtectionService`): take profit reached (continuation extend or TAKE_PROFIT); stop loss; dynamic profit-lock state update (`DynamicProfitLockService`); TP-extension-rebase hard floor; if the lock is triggered, the hard protected-profit floor and then `PositionExitPolicy.evaluateProfitLockBreach`; then `PositionExitPolicy.evaluateNormalExit`; then FIX-11T near-TP protection last.

## 4. Replay candidates

- **A — Existing policy.** Immediate profit-lock exit. Baseline.
- **B — Near-TP continuation.** PRIMARY experiment: one bounded continuation window under strict eligibility.
- **C — Volatility-adjusted trailing protection.** SECONDARY / exploratory only. Its exact formula is fixed before Replay. It is reported separately and never used to select a winner.

The primary statistical comparison is B versus A.

## 5. Cohort and unit of analysis

5.1 Progress measures (stored for every assessed breach, from immutable original entry and original TP):
- `max_tp_progress` = (highest price reached before the breach - entry) / (original TP - entry).
- `breach_tp_progress` = (executable breach price - entry) / (original TP - entry).

5.2 Eligibility uses `max_tp_progress`, not `breach_tp_progress`. A breach means price has fallen to the lock or below, and with the default profile the lock is at most 70% of the TP distance before TP is reached. A band of 85% or more on `breach_tp_progress` could therefore never match a trade. `breach_tp_progress` is still recorded and used for the floor and tolerance calculation and for interpreting profit surrendered.

5.3 Band (frozen), fixed decimal precision, deterministic precedence:
- `max_tp_progress` < 85.00%: the existing profit-lock exit is the authority.
- 85.00% <= `max_tp_progress` < 90.00%: Candidate B may evaluate.
- `max_tp_progress` >= 90.00%, or FIX-11T state is NEAR_TP_ARMED: existing FIX-11T / profit-lock behavior is the sole authority. FIX-11T latches once armed, so an armed position stays FIX-11T-owned even if price later falls back.
- Candidate B and FIX-11T must never own the same decision because of rounding, delayed events or threshold transitions.

5.4 Unit of analysis (pre-registered): the first lock breach at which baseline A issues an EXIT decision. Breaches that existing higher-timeframe rules resolve as a hold are not events. Because A exits at that breach, a position has at most one event. Breaches occurring only inside a B window are recorded for diagnostics and not counted as independent events. Confidence intervals are clustered by symbol and by time period.

5.5 Paired design: each eligible event receives both outcomes (A and B), reported as B minus A. Because existing rules already hold for a bullish or WATCH 1h with a supportive 5m, B differs from A only where A would exit, which is effectively the 1h NEUTRAL cohort.

## 6. Candidate B eligibility

All conditions are evaluated at the exact breach timestamp, using only information available as of that timestamp.

1. Original TP distance is positive and valid. Zero, negative, null or invalid distances are excluded and counted in the report.
2. `max_tp_progress` is inside the band in 5.3.
3. 1h context is fresh, data-quality-valid, and exactly NEUTRAL.
4. 5m context is fresh, data-quality-valid, and supportive under the existing policy.
5. The current 1m signal is non-bearish and passes the existing timestamp-safe trend, momentum and market-structure calculations.
6. The retracement below the lock is within the pre-defined numeric tolerance (Section 12).
7. The position has not already used its continuation window.
8. Mandatory vetoes: 1h SELL/STRONG_SELL, or 5m SELL/STRONG_SELL.
9. Missing, stale, invalid, NULL-quality or unknown 1h/5m evidence makes the event ineligible. It never passes as "not bearish".

B is not a new scoring engine. It reuses the application's existing timestamp-safe trend, momentum, market-structure, market-context and ATR/risk calculations. "Positive momentum", "supportive structure" and "small retracement" must be defined numerically in terms of those calculations before any results are viewed, and the structure/momentum inputs must not be derived solely from the same signal decision that already produced WATCH.

## 7. Continuation protection

### 7.1 Risk statement
B never removes protection. It does not increase the original position risk and must never intentionally permit a net loss under the specified cost model. It may, however, surrender a pre-defined, capped portion of already-protected profit, so the distance between the executable price and the exit floor can be wider than under the existing lock. That is the extra downside under test.

### 7.2 Emergency floor
```
continuation_emergency_floor =
    max( original_profit_lock - configured_tolerance,
         fee_adjusted_breakeven )
```
- Computed per trade, never globally.
- The maximum surrender (original lock minus floor) is a pre-registered cap.
- If the executable price already violates the floor when B is considered, exit immediately.

### 7.3 Window
- Exactly one window of N complete 1m candles (N pre-registered). No reset, no extension after a favorable candle, no second window.
- Timing: T is the exact breach timestamp. The partial 1m candle containing T does not count. The first counted candle opens at the next 1m boundary after T. The window expires at the close time of the Nth counted candle. Every event records T and the absolute expiry timestamp. The real duration is therefore more than N minutes and less than N+1 minutes (intentional).
- Confirmation is evaluated at each counted candle close using the signal for that candle.
- One confirmation grace G (pre-registered) applies to every counted candle: the signal for a candle closing at time t must be available by t + G.
- Expiry and the Nth confirmation (CONFIRMATION_PENDING state): at the close of the Nth counted candle (expiry E) the continuation window stops. From E until the Nth confirmation arrives or E + G passes, the position is in CONFIRMATION_PENDING: the emergency floor stays in force, bearish 1m/5m/1h evidence still causes an immediate exit, and no new or extended window can start. If the valid Nth confirmation arrives within the grace, the window resolves successfully at its arrival time (terminal state in 7.4). If it arrives invalid, or does not arrive by E + G, the position exits at that moment.
- Missing or delayed candle before the Nth: if a counted candle's signal is not available by its close + G, confirmation fails and the position exits at that moment. The floor is checked on every price event regardless of candle arrival.
- Same-timestamp ordering (deterministic, so Replay is reproducible): events are ordered by (timestamp, source sequence id), and events with an identical timestamp are processed in this class order: (1) price events and higher-priority authorities (take profit, stop loss, lock update, hard floors); (2) emergency-floor breach; (3) arriving signals that carry bearish evidence; (4) confirmation evaluation; (5) time actions (expiry, grace deadline). A floor breach or bearish evidence at the same timestamp as a confirmation therefore wins over the confirmation.
- TP extension stays unavailable until the original TP is actually reached.

### 7.4 Terminal states (exactly one outcome, no indefinite hold)
- Exit at once on: confirmation failure at any counted candle close, a floor breach, bearish 1m/5m/1h evidence, or a missing/delayed candle.
- Window resolves with valid Nth confirmation (within E + G): the window ends and the used-marker stays set, so B never applies again to this position. The original profit-lock policy (as in A, with the original lock) is evaluated at that moment with current evidence. If price is at or below the original lock, A's rules decide immediately (an exit unless an existing hold rule applies). If price has recovered above the lock, the position continues under the original profit lock and normal management, and a later breach exits as in A.
- No second window, no extension, and no other protection state exists.

### 7.5 Cost model (each cost counted exactly once)
- A long position exits by selling, so the floor and exit are evaluated against the executable BID.
- If the price source is bid, spread is already included and only modeled slippage is added. If the source is last-traded or mid, apply the modeled half-spread once, then slippage once.
- Entry costs are counted once, through the entry fill (add modeled entry spread and slippage if the recorded paper entry lacks them).
- `fee_adjusted_breakeven` = entry fill x (1 + entry fee) / (1 - exit fee). Exit-side spread and slippage are applied to the executable bid and are not folded into breakeven a second time.
- A stop price is not a guaranteed fill price; slippage is modeled on the floor and the exit. Report actual losses caused by slippage beyond the modeled floor.
- All inputs are named and pre-registered.

### 7.6 Required reporting for the protection design
- Maximum and average profit surrendered per event (original lock minus floor), in price and in net P&L.
- How often fee-adjusted breakeven is the binding constraint, and a pre-count of eligible events where breakeven is above (lock - tolerance). If that is most events, the floor formula is the real constraint.

## 8. Ownership order and FIX-11T handoff

If `max_tp_progress` reaches 90% while a B window is active, FIX-11T takes ownership immediately and B's window terminates permanently. There is no return to B after a later retracement.

Single decision sequence, evaluated for each price event in this order, with exactly one decision per event:
1. Higher-priority authorities keep their existing order (Section 3): take profit, stop loss, profit-lock state update, rebase hard floor, hard protected-profit floor.
2. At the handoff event, B's window is closed and its state marked terminated in the same atomic Replay transition. Protection continuity (no relaxation during ownership transfer): the active protection floor after the handoff must not be lower than the floor immediately before it. After the handoff the active floor is max(original profit lock as updated at that event, B's floor carried over). The Replay asserts this at every handoff and fails the run on a violation.
3. From the handoff event on, the existing Production order governs unchanged: profit-lock breach logic (original lock, exit rules as in A), then normal signal exits, then FIX-11T last. FIX-11T is armed by the transition but does not override a higher-priority exit in the same cycle.
4. An immediate breach of the original lock after the handoff therefore exits as in A. B and FIX-11T can never both decide, because B is terminated before step 3 runs.

Why continuity normally holds: reaching 90% means price climbed from below the lock to at least 90% of the TP distance, so it is above every floor at the handoff event. The lock update that runs before FIX-11T in the Production order steps the original lock to 60% of the TP distance at 90% progress (`ProfitLockPolicy`, Section 3), which is above B's floor (at most the 50% lock minus tolerance). The only case where B's floor can exceed the updated lock is when fee-adjusted breakeven is the binding constraint (very small TP distances). There the carried-over floor stays in force until the position closes. Report the number of handoffs where the carry-over bound, because those positions can exit where A would have held.

The Replay path (`ShadowProductionReplayService`, which calls `evaluateProfitLockBreach` from its own orchestration) is separate code from the live path. Its order must be verified independently against the live order before any Replay result is trusted (Section 13).

## 9. Replay validity and path data

A, B and C use identical historical data, evaluation period, execution assumptions and fee/spread/slippage model. Evidence is available only as of the breach moment.

- Candle high/low alone cannot show whether TP or the floor was touched first. If tick or durable price-event data exists, use it for order of contact. If only OHLC exists, mark the event ambiguous and apply one consistent conservative rule everywhere (adverse floor contact first). Report results with and without ambiguous events.
- Paper trading records zero fees, so Replay applies an explicit cost model: assumed taker fee per side, spread and slippage, shown at two or three cost levels. If the candidate wins only at zero cost, it fails.
- If historical spread is not stored, use a stated fixed assumption and run it as a sensitivity case.

Path-data audit (required before Replay). If coverage is incomplete, report the limitation and do not treat the replayed path as definitive:
- Are tick/price-event records complete, ordered and timestamp-consistent?
- Do they represent executable prices (bid/ask) or last-traded prices?
- Are the spread and slippage assumptions appropriate for SHIB and other low-priced assets?
- Would the same evidence have been available to the live application at that moment?
- Are ATR, tick size and spread available as of the breach timestamp?

## 10. Required tests

State integrity:
- The continuation-used marker is persistent Replay position state, written in the same Replay step that starts the window, so a crash cannot leave a window started but unmarked. It survives simulated restarts and event reprocessing.
- Tests: handoff continuity (active floor after handoff >= floor before, including a breakeven-bound case); expiry with the Nth confirmation arriving inside and outside the grace; same-timestamp events (floor breach or bearish evidence versus confirmation); repeated lock breaches; duplicate events; reprocessed events; simulated restart mid-window; handoff when B starts at 87% and the position reaches 91% during the window (including retracement afterwards); a position crossing the 90% boundary during or after the window.

Order parity:
- A Production-versus-Replay exit-order parity test on baseline A, including same-event cases where a hard exit and FIX-11T arming occur together.

Fee model (numerical examples):
- Bid-source data and last-traded-source data; entry fee, exit fee, spread and slippage each counted exactly once; a case where modeled slippage pushes the exit fill below net breakeven; an entry fill that already includes entry costs versus one that does not.

## 11. Outputs and evaluation stages

First, pre-count and report the number of eligible B events. If the cohort is below the pre-registered minimum, the conclusion is INSUFFICIENT EVIDENCE — keep the current policy and continue collecting history. This is an accepted outcome. Eligibility is never loosened after inspecting results.

For every eligible event calculate paired B minus A. Report:
- Net P&L after fees, spread and slippage; net expectancy improvement (incremental expectancy, not only TP-hit rate)
- Original-TP hit rate; retained profit
- Worst post-breach adverse movement; false-hold rate; maximum drawdown
- Cases where immediate exit avoided a later decline; cases where B improved the outcome
- Floor-relaxation statistics and how often breakeven was binding (Section 7.6)
- Both `max_tp_progress` and `breach_tp_progress` for every event, stratified by the distance between them (a position that peaked at 88% and breached near 50% is not equivalent to one still trading near 85%)
- Results by symbol, regime, volatility, and 1h/5m state
- Clustered confidence intervals (Section 5.4)
- Chronological out-of-sample results
- Results with and without ambiguous intrabar cases, and at each cost level

Stages:
- Stage 1 — trade-level paired counterfactual: what each eligible position would have done under B instead of A.
- Stage 2 — portfolio-level Replay, run only if Stage 1 meets the pre-registered bar. A longer hold keeps capital allocated and can block later opportunities, so model capital allocation and concurrent positions and count signals skipped or delayed because capital was held as lost opportunity. A positive Stage 1 result does not imply a positive Stage 2 result; if they disagree, the portfolio result decides.

## 12. Pre-registration

Sequencing: freeze ALL numerical thresholds and the statistical method (every value in the table below) and commit the file with its SHA-256 BEFORE running any count. Only then may the team run the blinded feasibility count (event counts and breakeven-binding counts only; no comparative outcomes; no cohort characteristics used to adjust anything). The count cannot influence the sample-size threshold or any success criterion. If anything changes after the count, that is a new logged specification version with a new hash, and the report labels it post-count.

The pre-registration file is committed before any Replay outcome is viewed. Its SHA-256 is recorded in the investigation log. No post-result tuning is allowed; sensitivity grids may be reported but never used to choose parameters.

| Item | Value |
|---|---|
| Eligibility band and precision | 85.00% <= max_tp_progress < 90.00% (frozen) |
| Unit of analysis | First baseline-A exit breach per position (frozen, Section 5.4) |
| Minimum eligible-event sample size | TO BE FROZEN |
| Required net-expectancy improvement, B versus A | TO BE FROZEN |
| Maximum drawdown deterioration | TO BE FROZEN |
| Maximum false-hold rate / adverse-move rate | TO BE FROZEN |
| Continuation window length N (1m candles) | TO BE FROZEN |
| Confirmation grace G (every counted candle, including the Nth) | TO BE FROZEN |
| ATR / tolerance formula (ATR, tick size, spread/slippage) | TO BE FROZEN |
| Numeric definitions of "positive momentum", "supportive structure", "small retracement" | TO BE FROZEN |
| Maximum giveback cap (original lock minus floor) | TO BE FROZEN |
| Emergency-floor formula | Section 7.2 (frozen); inputs TO BE FROZEN |
| Price source (bid, last, mid) and the non-double-counting rule | Rule in Section 7.5; values TO BE FROZEN |
| Fee, spread and slippage assumptions and sensitivity levels | TO BE FROZEN |
| Ambiguity rule | Adverse floor contact first (frozen, Section 9) |
| Candidate C trailing formula | TO BE FROZEN |
| Chronological train / out-of-sample split | TO BE FROZEN |
| Statistical confidence method (clustered) | TO BE FROZEN |
| Pre-registration file SHA-256 | RECORD AFTER COMMIT |

## 13. Gates

- **Specification freeze:** the Section 12 pre-registration is complete, committed, and its SHA-256 recorded. This can happen before any test is run.
- **Execution readiness (separate gate):** Replay results may be trusted only after all of these pass: Production-versus-Replay exit-order parity on baseline A (otherwise a measured difference could come from baseline differences and not from the continuation rule), the path-data audit and coverage report, the fee-model unit tests, and the state-transition tests.
- **Production decision:** Candidate B may be proposed for Production only if it meets the pre-registered net-expectancy, drawdown, false-hold, sample-size and out-of-sample criteria at Stage 1 and, where run, Stage 2. Otherwise the existing PROFIT_LOCK_EXIT policy is retained. Any Production proposal needs its own separate fix, design review and approval.

## 14. Work to complete before the specification is frozen

Three deliverables for the verification phase: (1) a Production-versus-Replay exit-order parity test, (2) the completed numerical pre-registration, (3) a historical price-data coverage report.

Order of work:
1. Complete the path-data audit (Section 9), including availability of ATR, tick size and spread as of the breach timestamp, so the numeric values can be set realistically.
2. Fill in every "TO BE FROZEN" value and the statistical method, commit the pre-registration file, and record its SHA-256 in the investigation log. This freezes the specification.
3. Run the blinded feasibility count of eligible events and breakeven-binding cases (counts only). It cannot change anything frozen in step 2.
4. Build and pass the tests in Section 10 and the parity test, then run the Replay.

## 15. Separate follow-up (not part of FIX-155)

The hard protected-profit floor (entry x 1.0005, +0.05% gross) and the early lock steps sit below realistic round-trip costs, so a "protected profit" exit can be a net loss on Binance. Track as its own investigation item.

## Appendix A — Review history (resolved)

| Finding | Resolution |
|---|---|
| Eligibility on `breach_tp_progress` produces an empty cohort | Eligibility and the FIX-11T boundary use `max_tp_progress` (5.2) |
| 85% vs 80% threshold; SHIB #89469 must not drive it | Band frozen at 85–90%; SHIB is a fixture only (Section 2) |
| Emergency floor can surrender protected profit | Qualified risk statement, capped giveback, reporting (7.1, 7.6) |
| FIX-11T handoff ambiguous | Permanent B termination and one decision sequence (Section 8) |
| Historical path reliability | Mandatory path-data audit and ambiguity rule (Section 9) |
| Pre-registration sequencing | All numeric thresholds and the method are frozen and hashed first; the blinded feasibility count comes after and cannot change them (Section 12) |
| Window expiry versus confirmation grace | CONFIRMATION_PENDING state, floor retained, no new window, deterministic same-timestamp ordering (7.3) |
| Handoff must not weaken protection | Active floor after handoff >= floor before, asserted in Replay; carry-over floor where breakeven binds (Section 8) |
| First-breach definition | First baseline-A exit breach (5.4) |
| Window timing and missing candle | Explicit timestamps, boundary rule, fail-safe (7.3) |
| Fee/spread double counting | Single-count cost model and unit tests (7.5, Section 10) |
| Terminal state after successful confirmation | Defined (7.4) |
| Replay may not match Production order | Parity test and execution-readiness gate (Sections 8, 10, 13) |
| Portfolio effects | Two-stage evaluation (Section 11) |
