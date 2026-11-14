
## DBQ-CLAUDE-I1-001
- timestamp: 2026-11-01T11:24:49.7974766+03:00
- agent: CLAUDE
- purpose: Prove the live schema of the related tables before relying on their columns.
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 149
- approximate output lines: 249
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-11-01T11:24:50.7711758+03:00
- agent: CLAUDE
- purpose: Full lifecycle of the six frozen positions (prices, SL/TP, quantity, reasons, exit signal).
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 92
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-11-01T11:24:51.8927522+03:00
- agent: CLAUDE
- purpose: Entry and exit trade_signal evidence for each frozen position.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 188
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-11-01T11:24:52.8528157+03:00
- agent: CLAUDE
- purpose: Wallet fills for each position window (quantity, price, fees, P&L, execution reason), to recompute P&L and fees.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 92
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-11-01T11:24:53.8823305+03:00
- agent: CLAUDE
- purpose: Recorded close trigger (production_exit_audit) for each frozen position.
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 127
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-11-01T11:24:54.9852373+03:00
- agent: CLAUDE
- purpose: Managed-position state (SL/TP, highest price, profit lock, entry stage) matched to each paper position.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 114
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-11-01T11:24:55.9258503+03:00
- agent: CLAUDE
- purpose: Management events (TP extensions, profit-lock changes, near-TP actions) during each position's life; key for trade 143.
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 119
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-11-01T11:24:59.4232069+03:00
- agent: CLAUDE
- purpose: Price evidence over each position's life (count, minimum, maximum, first and last by delivery_status), to compare with SL/TP and check for gaps.
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 1612
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-010
- timestamp: 2026-11-01T11:25:00.4283540+03:00
- agent: CLAUDE
- purpose: Entry and exit signal processing (origin, attempts, completion latency, failures).
- SQL file: DBQ-CLAUDE-I1-010-CLAUDE.sql
- exit code: 0
- duration_ms: 138
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-011
- timestamp: 2026-11-01T11:25:01.4036591+03:00
- agent: CLAUDE
- purpose: Scope check for F1/F2: closes by status after the oldest frozen close, plus the latest open and close times of any position.
- SQL file: DBQ-CLAUDE-I1-011-CLAUDE.sql
- exit code: 0
- duration_ms: 90
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-11-01T11:30:01.1910586+03:00
- agent: CLAUDE
- purpose: Corrects the failed I1-008. For each position, the newest 1m, 5m and 1h signal at or before opening and at or before closing, with its age in seconds. Tests F3 and the entry-time higher-timeframe context.
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 1169
- approximate output lines: 36
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-11-01T11:30:06.4995108+03:00
- agent: CLAUDE
- purpose: 5m and 1h signal series from 15 minutes before entry to 1 minute after exit. Shows how the higher-timeframe decision changed during each trade (172's 1h SELL, 5m flips).
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 4335
- approximate output lines: 50
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-11-01T11:30:10.4486329+03:00
- agent: CLAUDE
- purpose: Tick-level evidence for F10. Full tick sequence for 143 (lock to exit) with the gap to the previous tick. For the other five, only the last 30 s before the wallet SELL. Tests the gap and sampling-density explanations.
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 3151
- approximate output lines: 86
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-11-01T11:30:12.3128940+03:00
- agent: CLAUDE
- purpose: Outcome after exit, kept separate from the entry assessment. Minimum and maximum price within 60 minutes of each close, the first time price reached the stored target, and the first time it reached the stored stop.
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 1010
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-11-01T11:30:13.3420234+03:00
- agent: CLAUDE
- purpose: Tests F8 across the population. Production positions opened 10-11 to 10-15, grouped by entry-signal R:R, with win/loss counts and average gross P&L percent.
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 139
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-11-01T11:30:14.4739802+03:00
- agent: CLAUDE
- purpose: Data quality of the entry candles (U12). The 1m candle each entry signal used, plus the 5 preceding 1m candles: closed flag, created_at lag and gaps. Also lists the candle table columns to find a provenance column.
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 304
- approximate output lines: 56
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-11-01T11:30:16.0394463+03:00
- agent: CLAUDE
- purpose: Full Execution Intelligence approval text and opportunity state for each entry (U13, F9). Also returns the wallet execution and observed-price timestamps for the exits.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 722
- approximate output lines: 19
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-11-01T11:37:32.7638738+03:00
- agent: CLAUDE
- purpose: For U15/F15, the full decision path for the 5m signals used at entry, plus 182's 5m series.
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 92
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-11-01T11:37:33.7317871+03:00
- agent: CLAUDE
- purpose: For U17/F13, position-management events (TP extensions, profit lock) and the immutable exit audit for the six positions.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 97
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-11-01T11:37:34.7699532+03:00
- agent: CLAUDE
- purpose: For U14, the timestamp columns of the timing-relevant tables, to find a commit or created timestamp for signals.
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 127
- approximate output lines: 27
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-11-01T11:37:35.7591144+03:00
- agent: CLAUDE
- purpose: For U16, the complete tick gaps for 172 and 158 at the exit, and the EGLD 1m candles at 143's gap, to tell a real move from sparse sampling.
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 107
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-11-01T11:37:37.9549336+03:00
- agent: CLAUDE
- purpose: For F15, how widespread the overwrite is. Among production BUY entries since 10-11, count how many approved routes relied on a 5m signal stored as NEUTRAL/WATCH whose original decision was SELL/STRONG_SELL, and their outcomes.
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 1329
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-11-01T11:44:37.5256157+03:00
- agent: CLAUDE
- purpose: For U14, find out whether the signals were committed and visible when 158's 06:30:02.192 tick arrived. Compares `generated_at` with `created_at` for the exit signals and their work items, with 182's entry signals as a control.
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 78
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-11-01T11:44:52.7751555+03:00
- agent: CLAUDE
- purpose: For U18 and F15, the size of the neutralization. Counts 5m and 1h signals since 10-11 whose bearish original decision was stored as NEUTRAL, grouped by the higher-timeframe context decision. This separates cases neutralized only by trend score (context WATCH or NEUTRAL) from cases with a real bullish decision.
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 14399
- approximate output lines: 41
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-11-01T11:44:53.7511566+03:00
- agent: CLAUDE
- purpose: For U7, bound the deployed build at trade time by the schema migrations installed before and around 10-11/12.
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 104
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-11-01T11:44:54.6457273+03:00
- agent: CLAUDE
- purpose: For 143 (F13/E20), the final managed-position lock state (state, rebase timestamps) to confirm the lock was ACTIVE, not TP_EXTENSION_REBASE, at exit.
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 82
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-11-01T11:52:43.5169771+03:00
- agent: CLAUDE
- purpose: For U23/F18/E26, find which 5m signal was visible when each entry was decided. Lists 5m signals generated within 6 minutes before (up to 10 s after) each entry's 1m signal, with `created_at` (visibility), decision and ATR, alongside the 1m signal's own `generated_at` and `created_at`.
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 113
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-11-01T11:52:44.4522057+03:00
- agent: CLAUDE
- purpose: For U21/U24, the full opportunity rows behind each entry, including anchor and best entry prices, `created_at`/`updated_at`, health and the last bearish time.
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 82
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-11-01T11:52:45.5719527+03:00
- agent: CLAUDE
- purpose: For U24/E24, rebuild Entry Quality: the 1m signal prices in the 30 minutes before each entry signal (gives the rolling low/high, the recent rejected high, and expansion/ATR extension), plus each signal's ATR.
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 256
- approximate output lines: 182
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-11-01T11:52:46.5987081+03:00
- agent: CLAUDE
- purpose: For F19/F20/F21, outcomes across the population: production entries since 10-11 grouped by entry regime and decision, and by route and stored Entry Quality bucket.
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 174
- approximate output lines: 35
- password: never logged


## DBQ-CLAUDE-I5-005
- timestamp: 2026-11-01T11:52:47.5702017+03:00
- agent: CLAUDE
- purpose: For U25, the full decision path and ATR/authority fields of the six 1m entry signals (the RANGE_ENTRY_LOCATION result for 137, the ATR_RISK text for every entry).
- SQL file: DBQ-CLAUDE-I5-005-CLAUDE.sql
- exit code: 0
- duration_ms: 91
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-11-01T12:02:33.4442487+03:00
- agent: CLAUDE
- purpose: Test F23/F24. Population outcomes grouped by entry reward/risk bucket and ATR% bucket, with a net figure assuming 0.20% round-trip fees.
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 85
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-11-01T12:02:34.4041503+03:00
- agent: CLAUDE
- purpose: For U27/E32, check the predicted scores. Stored Entry Quality, entry price and stage, plus each entry signal's liquidity and stop-exposure flags and its stored reward/risk and ATR%.
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 85
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-11-01T12:03:16.5012236+03:00
- agent: CLAUDE
- purpose: For U28/F22, measure the 5-minute-boundary authority gap across the population. For each entry, find the 5m signal generated within -3 s to +5 s of the entry's 1m signal but not yet committed at 1m generation, and the latest 5m committed before it. Compares their decisions and immediate-entry flags with the outcome.
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 41241
- approximate output lines: 32
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-11-01T12:03:17.5242161+03:00
- agent: CLAUDE
- purpose: For U29, find the Replay test tables and their columns, so a bounded parity query can be written for 10-11 to 10-12 without guessing the schema.
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 183
- approximate output lines: 300
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-11-01T12:08:24.0877254+03:00
- agent: CLAUDE
- purpose: F28 / U30. Count entries whose stop was exposed in the order book, by the liquidity status they were given, with outcomes and stored Entry Quality.
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 177
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-11-01T12:08:25.1030550+03:00
- agent: CLAUDE
- purpose: F27 / U31. Route, 1m decision and regime mix of the high-volatility (ATR% = 0.25) group compared with the rest.
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 103
- approximate output lines: 28
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-11-01T12:08:26.1072943+03:00
- agent: CLAUDE
- purpose: Test whether F25 is confounded. Cross-tab stored Entry Quality bucket against the ATR% bucket.
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 112
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I7-004
- timestamp: 2026-11-01T12:08:27.0591339+03:00
- agent: CLAUDE
- purpose: F22 / U28, case 123 (with 143 as a control). Which 5m signal the 1m entry signal's confluence step actually read, compared with the 5m signals committed around the boundary.
- SQL file: DBQ-CLAUDE-I7-004-CLAUDE.sql
- exit code: 0
- duration_ms: 84
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I7-005
- timestamp: 2026-11-01T12:08:28.0768218+03:00
- agent: CLAUDE
- purpose: U29. Find any Replay run whose replayed signals cover the frozen trades' window (UTC 2026-10-11 17:00 to 2026-10-12 12:00), so 182 (F15/F28) and 143 (F22) can be checked for parity.
- SQL file: DBQ-CLAUDE-I7-005-CLAUDE.sql
- exit code: 0
- duration_ms: 131
- approximate output lines: 0
- password: never logged


## DBQ-CLAUDE-I8-001
- timestamp: 2026-11-01T12:13:56.8428295+03:00
- agent: CLAUDE
- purpose: Post-freeze, U29. Re-run the Replay coverage check, because the I7-005 result was empty. A minimal count, so zero rows can be told apart from a failed run.
- SQL file: DBQ-CLAUDE-I8-001-CLAUDE.sql
- exit code: 0
- duration_ms: 84
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I8-002
- timestamp: 2026-11-01T12:13:57.7873512+03:00
- agent: CLAUDE
- purpose: Post-freeze, U30. Stored Entry Quality and route for the entries whose exposed stop was hidden under another status, to size the F28 counterfactual.
- SQL file: DBQ-CLAUDE-I8-002-CLAUDE.sql
- exit code: 0
- duration_ms: 95
- approximate output lines: 9
- password: never logged

