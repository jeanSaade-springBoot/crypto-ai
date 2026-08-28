# Crypto AI Trader — Development Handover

## Source of truth

This project snapshot is the current source of truth. Inspect this code before proposing or applying further trading-logic changes.

**Current priority:** fix and validate the existing production behavior. Do **not** add more indicators, scoring layers, or tune thresholds until the demonstrated execution/replay issues are understood.

## Project flow

The intended production flow is:

`Binance market data → candles → TechnicalIndicatorService → AnalysisService → FinalDecisionService → Execution Intelligence → opportunity/evidence tracking → wallet execution → position management → profit lock / exit`

Stack: Java 21, Spring Boot, MySQL, Flyway, Thymeleaf, Bootstrap/ApexCharts. Root package: `com.crypto`.

## Main incident under investigation

The key regression case is **BNBUSDT on 2026-08-09 from 07:00 to 16:00** (database/UTC timestamps used by the replay).

During this window BNB had an observed 1-minute candle range of approximately:

- Low: `601.00`
- High: `611.55`
- Range: `+1.7554%`
- 1m candles: `540`

Production did not capture the important rally as expected.

## Earlier production symptom

Many BNB BUY opportunities were repeatedly cancelled with reasons such as:

- `BEARISH_REVERSAL`
- `OPPORTUNITY_HEALTH_EXHAUSTED`

A decision-authority defect was identified around `originalDecision` versus the corrected/final decision. The correction is intended to prevent obsolete original decisions from incorrectly overriding the effective final decision and killing opportunities.

Do not revert this fix without evidence.

## Historical regression / shadow-production framework

Administration contains a historical regression runner.

The requested inputs are deliberately only:

- **Symbol**
- **From**
- **To**

Do not reintroduce a generic Period selector. The purpose is to answer:

> If production had run correctly between these exact timestamps, what would it have done?

### Isolation is mandatory

Replay must never mutate production state. It must not persist replay activity into live tables such as production `trade_signal`, production `execution_opportunity`, real wallet state, real positions, or real execution attempts.

Current isolated/shadow tables include:

- `analysis_test_run`
- `analysis_test_result`
- `analysis_test_signal`
- `execution_opportunity_test`
- `wallet_execution_test`
- `wallet_position_test`

Use the same production services/rules wherever possible, but route persistence/execution into shadow/test state.

## Latest BNB replay result

Replay window:

`BNBUSDT — 2026-08-09 07:00 → 2026-08-09 16:00`

Initial shadow capital: `10,000 USDT`.

The run completed `PASSED / 100%` with **660 fresh generated signals** and **0 generation errors**.

### Fresh replay decisions by timeframe

| Timeframe | Generated | BUY | WATCH | NEUTRAL | SELL | STRONG_SELL |
|---|---:|---:|---:|---:|---:|---:|
| 1h | 10 | 0 | 4 | 6 | 0 | 0 |
| 5m | 109 | 4 | 31 | 67 | 2 | 5 |
| 1m | 541 | 0 | 44 | 460 | 12 | 25 |

### Four important fresh 5m BUY signals

The corrected replay generated these fresh BUY decisions:

1. `2026-08-09 07:15` — price `603.19`, score `75`, confidence `73`
2. `2026-08-09 10:55` — price `604.11`, score `75`, confidence `73`
3. `2026-08-09 12:55` — price `604.35`, score `78`, confidence `74`
4. `2026-08-09 13:55` — price `606.66`, score `78`, confidence `73`, trend `19`, volume `20`, momentum `14`

The `13:55` BUY is especially important because BNB subsequently traded above `611`.

**Conclusion:** the corrected replay proves that `AnalysisService` can identify BUY conditions during this rally. Do not assume the main remaining defect is simply that AnalysisService cannot generate a BUY.

## Critical unresolved result

Despite those four fresh BUY signals:

- `wallet_execution_test` was **empty**
- `wallet_position_test` was **empty**

Therefore the current highest-priority question is:

`Fresh Analysis → BUY exists → Execution Intelligence / opportunity processing → ??? → no shadow BUY`

The next task is to identify the exact stage and reason that prevented the fresh BUY from becoming a simulated execution.

## Hypothesis that must be verified in code

There is a strong hypothesis that some Execution Intelligence / Opportunity Builder logic may still query live/latest production repository state rather than consuming the replay-generated historical state.

This is **not yet a fact**. Verify it in the source.

Inspect Execution Intelligence, Opportunity Builder/Memory, confirmation logic, and repository calls for patterns such as latest/top signal queries. During replay, every contextual lookup must resolve **as of the replay timestamp**, never current/future/live state.

There must be **no look-ahead bias**.

## Visual replay pipeline

The latest UI work adds a visual pipeline to make each important replay candidate explainable without large SQL dumps.

For a BUY candidate, the desired lifecycle is:

`Fresh Analysis → 1m Trigger → 5m Context → 1h Context → Evidence → Health → Execution → Shadow Wallet → Exit`

Each stage should make its state obvious (`PASS`, `WAIT`, `BLOCKED`, `EXECUTED`) and show the exact reason.

For example, the `13:55` BNB BUY should reveal exactly where it stopped rather than merely showing that no wallet row was created.

The visual pipeline must genuinely represent the same execution path as production. Do not create a simplified parallel set of trading rules just for visualization.

## Full shadow-production target

The regression framework should ultimately replay the complete production lifecycle chronologically:

`Historical candle → TechnicalIndicatorService → AnalysisService → FinalDecisionService → strategy/regime → multi-timeframe context → available historical BTC/derivatives/order-book context → Execution Intelligence → Opportunity Memory → evidence/health → confirmation/block/cancel → simulated wallet BUY → simulated position → production-style position management → SL/TP/profit lock/SELL → simulated close → P/L`

Historical context that was never persisted must be explicitly marked unavailable. Never substitute today's live context for historical data silently.

## Exact BUY / SELL identification

A successful replay must make simulated trades explicit, including:

- exact BUY timestamp and price
- entry signal/reason
- position percentage and quantity
- exact SELL timestamp and price
- exit reason (`PROFIT_LOCK`, `STOP_LOSS`, `TAKE_PROFIT`, `SELL_SIGNAL`, etc.)
- realized P/L in USDT and percent

If no BUY occurs, do not report only `PASS`. Show the closest candidate and the exact blocking stage/reason.

## Market Move Tracker / Debugger

The Market Move Tracker is a **standalone diagnostic tool**. It must not influence AI trading decisions.

Current settings around the investigated period were:

- enabled: `1`
- minimum move: `0.300000%`
- minimum duration: `6 minutes`
- retracement close: `30%`
- cooldown: `10 minutes`
- retention: `7 days`

The useful grid should emphasize **MEDIUM/HIGH** events and avoid LOW noise.

### BNB event caught by the debugger

The debugger did detect the important BNB acceleration:

- Event ID: `846`
- Symbol: `BNBUSDT`
- Direction: `UP`
- Start: `2026-08-09 13:41:19.647056`
- End: `2026-08-09 14:07:52.234447`
- Start price: `606.31`
- End/peak: `611.46`
- Change: `+0.84940047%`
- Duration: `1592 seconds` (~26m32s)
- Importance: `MEDIUM`

The tracker is therefore not simply failing to observe BNB.

### Latest debugger UI requirement

The Market Move Tracker must allow **multiple configured symbols** to be selected simultaneously, with `Select All` and `Clear`. The grid and live tracker state should be filtered to the selected symbols.

Again: this is diagnostic only and must remain isolated from execution logic.

## Regression reset and concurrency safety

Administration includes/needs a **Reset Test Data** action.

It must refuse while any regression is `PENDING` or `RUNNING` and otherwise clear only shadow/test tables. It must never delete production data.

The runner must also prevent double starts at both UI and backend levels. A previous double-click launched overlapping tests, so backend protection is mandatory.

## UI requirement already requested

Remove the dashboard/admin block:

- `SINCE START`
- `Portfolio vs invested capital`

Associated JavaScript must be removed or guarded so the missing DOM section causes no client-side error.

Preserve the previously condensed global AI-performance/header work unless there is a demonstrated reason to change it.

## Model-validation philosophy

An independent review correctly identified that many thresholds/category weights are hand-picked and that model complexity has outpaced empirical validation. Existing scoring includes approximately Trend 25, Volume 20, Momentum 15, Sentiment 15, Fundamentals 10, followed by additional strategy/regime/context/execution layers.

We agree this needs proper historical/out-of-sample validation, but **do not tune these weights yet**.

Order of work:

1. Make production behavior correct.
2. Make historical replay trustworthy and free of look-ahead/live-state leakage.
3. Make shadow execution production-equivalent.
4. Gather evidence.
5. Then perform backtesting/walk-forward validation and tune thresholds/weights based on outcomes.

Do not arbitrarily add another scoring layer.

## Immediate next investigation

Start by inspecting this ZIP/source. Before changing trading behavior, answer these questions from the code:

1. Is the visual pipeline genuinely tracing the same execution path as production?
2. Why did the four fresh BNB replay BUY signals create zero `wallet_execution_test` rows?
3. Does any replay/execution component accidentally read current/live/latest `trade_signal` or other production state instead of historical as-of replay state?
4. Is there any look-ahead bias?
5. Does the shadow execution path reuse production execution/position logic, or has simplified duplicate logic been introduced?

Focus especially on the `2026-08-09 13:55` BNB 5m BUY at `606.66`, score `78`, confidence `73`.

**Do not add more trading logic before diagnosing this path.** After the demonstrated defect is fixed, rerun the exact same BNB `07:00 → 16:00` regression and compare results.
