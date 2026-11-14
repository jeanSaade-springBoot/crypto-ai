
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-20T18:09:55.0392847+03:00
- agent: CLAUDE
- purpose: Re-prove session timezone; list temporal column types; read the production_exit_audit and position_analysis schemas.
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 50
- approximate output lines: 73
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-20T18:09:55.3358279+03:00
- agent: CLAUDE
- purpose: Frozen positions: lifecycle, SL/TP, return %, planned R:R, hold time, UTC conversion.
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-20T18:09:55.6353554+03:00
- agent: CLAUDE
- purpose: Wallet executions for entries and exits, including messages (fill price, observation age, route).
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-20T18:09:55.9804671+03:00
- agent: CLAUDE
- purpose: Managed-position protection state (highest price, profit lock, near-TP) for the six positions.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-20T18:09:56.2946043+03:00
- agent: CLAUDE
- purpose: Entry signals and setup authorities: mode, data quality, decision, ATR plan, R:R, context.
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 37
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-20T18:09:56.6231427+03:00
- agent: CLAUDE
- purpose: Exit-trigger signals, plus the DOGE 5m/1h context in force at the 162 signal-SELL (validateSell inputs).
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 47
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-20T18:09:56.9395672+03:00
- agent: CLAUDE
- purpose: Signal-processing work claim/completion and position linkage (entry signals plus 165415).
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-20T18:09:58.3352089+03:00
- agent: CLAUDE
- purpose: Price-event coverage per position: count, min/max, accepted status, SL/TP touches, events above entry.
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 1085
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-10-20T18:09:58.9886933+03:00
- agent: CLAUDE
- purpose: Data-quality proof at entry: closed 1m candles in the 210-minute window, zero-trade candles, non-RUNTIME provenance.
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 332
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-010
- timestamp: 2026-10-20T18:09:59.3300968+03:00
- agent: CLAUDE
- purpose: Same-symbol position immediately before each frozen trade (re-entry spacing, prior outcome).
- SQL file: DBQ-CLAUDE-I1-010-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-20T18:17:52.1635882+03:00
- agent: CLAUDE
- purpose: Tick sequences: the full hold for 143, and the final ~60 s before exit for 137, 158, 172 and 182 (trigger tick, gaps).
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 77
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-20T18:17:52.4717653+03:00
- agent: CLAUDE
- purpose: Position management events (TP revisions, profit lock, near-TP) for the six managed positions, to explain 143's TP of 4.104317.
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-20T18:17:52.7892708+03:00
- agent: CLAUDE
- purpose: Production exit audit for the frozen positions.
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-20T18:17:53.2204747+03:00
- agent: CLAUDE
- purpose: Signals that drove each SELL_CONFIRMED and HTF exit (1m/5m/1h near each exit), plus SHIB 5m/1h from 04:00 to 04:16 UTC.
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 143
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-20T18:17:53.5305685+03:00
- agent: CLAUDE
- purpose: Execution-opportunity records for the entry signals (recommended %, source, code), to explain the 25% ? 15% reduction for 162 and 182.
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 44
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-20T18:17:53.9173743+03:00
- agent: CLAUDE
- purpose: 1m candles around 143's hold and exit (was the 4.100 ? 4.027 move inside a traded candle?), and around 182's entry and exit.
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 19
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-20T18:26:55.9065375+03:00
- agent: CLAUDE
- purpose: How often SETUP_TIMEFRAME_ATR entries had a newer same-boundary 5m signal excluded by generation order (scale of F1). Bounded to the last 14 days.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 541522
- approximate output lines: 22
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-20T18:36:00.4199930+03:00
- agent: CLAUDE
- purpose: Trade 143: every EGLD price event (any delivery status), with Binance-E-to-DB ingestion lag, source and outcome, 19:39:58–19:44:00 UTC. Covers the entry gap, the exit gap, and when the 3.994 low printed.
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 41
- approximate output lines: 71
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-20T18:36:08.3262002+03:00
- agent: CLAUDE
- purpose: Per-second event coverage across all symbols during 143's hold, to tell a global ingestion gap from an EGLD-only (Binance-side) gap.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 7522
- approximate output lines: 236
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-20T18:36:08.7002400+03:00
- agent: CLAUDE
- purpose: Lineage of the exit-trigger and entry-price events: source (direct FIX-153 or collector) and DB-clock `created_at` vs Binance E (resolves F11 and the OFF-mode question).
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 43
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-20T18:36:09.0349262+03:00
- agent: CLAUDE
- purpose: Slow (=1000 ms) or failed ingestion-stage timings (FIX-129) around every entry and exit, and across 143's hold.
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 41
- approximate output lines: 19
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-20T18:36:09.3662462+03:00
- agent: CLAUDE
- purpose: Protection failures, retries and reconnect or reconciliation evidence during the study period (2026-10-11 17:50 to 2026-10-12 07:10 UTC).
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-20T18:36:09.6700458+03:00
- agent: CLAUDE
- purpose: Measure F13: wallet-asset fan-out, snapshot timing relative to each wallet SELL/BUY, and concurrent wallet mutations on any symbol inside 143's gaps.
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-20T18:46:52.0394505+03:00
- agent: CLAUDE
- purpose: Settle F24, whether FIX-154 and later migrations are applied in production and when, and whether FIX-154 audit rows exist for the frozen entry authorities.
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 38
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-20T18:46:52.3598296+03:00
- agent: CLAUDE
- purpose: Settle U17, the HTF context signals at entry: raw vs final decision, quality and confluence/BTC status, and the tail of the final-decision explanation (why STRONG_SELL became NEUTRAL).
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 39
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-20T18:46:52.8169161+03:00
- agent: CLAUDE
- purpose: Settle U20, closed-position outcome by entry route/code and by planned R:R bucket over the last 14 days (epoch-bounded, timezone-safe). Planned R:R uses `wallet_managed_position` original geometry where available.
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 156
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-20T18:46:53.1467280+03:00
- agent: CLAUDE
- purpose: Settle U19, the EGLD 1m/5m/1h signals visible during 143's hold (19:39:55–19:44:00 UTC), to identify the continuation inputs at each TP extension and the newest-signal view seen by live management.
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 59
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-20T18:46:53.4620079+03:00
- agent: CLAUDE
- purpose: Read-only F22 parity check. Find whether any existing Replay/shadow run already covers the 143/158 windows (schema first; no Replay execution).
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 29
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-20T18:55:56.2231230+03:00
- agent: CLAUDE
- purpose: Rebuild the entry-quality inputs (30-minute rolling close-low/high, rejection count, accumulated BUY observations) from 1m signals in the 30 minutes up to each frozen entry signal (U21, U22).
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 103
- approximate output lines: 186
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-20T18:55:56.5563702+03:00
- agent: CLAUDE
- purpose: Opportunity anchor, best price and age inputs to the chase reference and age penalty for the six executed opportunities (U21).
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-20T18:55:56.9802183+03:00
- agent: CLAUDE
- purpose: Real 30-minute 1m-candle range before each entry, to place each fill in its range (U23, F32).
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 89
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-20T18:55:57.3266162+03:00
- agent: CLAUDE
- purpose: Does recorded entry quality predict outcome? 14-day closed positions bucketed by `entry_quality_score`, overall and for FIX-11G entries (U24, F28, F29).
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 34
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-20T19:05:23.2247937+03:00
- agent: CLAUDE
- purpose: Test RC2 (F37) across the population. Outcome of 14-day closed positions, bucketed by the number of 1m SELL/STRONG_SELL signals in the 15 min before the entry signal.
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 2330
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-20T19:05:23.6146611+03:00
- agent: CLAUDE
- purpose: Fee-adjusted P&L (F39, U26), at 0.10% per side on entry and exit notional, by entry-quality bucket and by planned R:R bucket over 14 days.
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-20T19:05:23.9886501+03:00
- agent: CLAUDE
- purpose: Exit-reason distribution (F38, U27). Do signal exits dominate, and how large are the losses by reason over 14 days?
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-10-20T19:05:24.3643010+03:00
- agent: CLAUDE
- purpose: Whether RC4 is systemic (U28). For each closed position with a TP extension in 14 days, compare its first (original) TP gain with the realized return.
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 34
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-10-20T19:11:55.3565199+03:00
- agent: CLAUDE
- purpose: F42/U29. Size and timing of `STOPPED` against `CLOSED`, by close reason, all time and last 14 days.
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-10-20T19:11:55.6769224+03:00
- agent: CLAUDE
- purpose: F42. `STOPPED` losers closed at or after 137's close (about 18:00:12 UTC on 2026-10-11, epoch 1791741612). These are the trades the charter selection could not see.
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 21
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-10-20T19:11:56.1340371+03:00
- agent: CLAUDE
- purpose: U30. Re-run the R:R and EQ buckets with `STOPPED` included, net at both fee tiers, last 14 days.
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 108
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I7-004
- timestamp: 2026-10-20T19:12:00.0342871+03:00
- agent: CLAUDE
- purpose: U31/F45. Per-trade bearish-signal count for every position with =5, plus 137, 143 and 182, with `STOPPED` included.
- SQL file: DBQ-CLAUDE-I7-004-CLAUDE.sql
- exit code: 0
- duration_ms: 3541
- approximate output lines: 23
- password: never logged


## DBQ-CLAUDE-I7-005
- timestamp: 2026-10-20T19:12:00.4116666+03:00
- agent: CLAUDE
- purpose: U32/F44. Gross-positive positions that are net-negative after fees, by close reason, last 14 days.
- SQL file: DBQ-CLAUDE-I7-005-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I7-006
- timestamp: 2026-10-20T19:12:00.7784418+03:00
- agent: CLAUDE
- purpose: U33/F46. RC4 counterfactual using the actual market price at the first `TAKE_PROFIT_EXTENDED` event. This assumes the lowest event id per position is the earliest event.
- SQL file: DBQ-CLAUDE-I7-006-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 34
- password: never logged

