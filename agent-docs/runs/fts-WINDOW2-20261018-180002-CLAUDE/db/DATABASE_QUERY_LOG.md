
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-18T18:05:52.0118891+03:00
- agent: CLAUDE
- purpose: Full lifecycle of the 6 frozen positions (prices, SL/TP, quantity, exit signal, reasons)
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-18T18:05:52.3679523+03:00
- agent: CLAUDE
- purpose: Wallet execution legs per position (fill vs decision price, observation age, partial exits, wallet P&L)
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-18T18:05:52.6956959+03:00
- agent: CLAUDE
- purpose: Immutable production exit audit (actual close trigger and source signal)
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-18T18:05:53.0366521+03:00
- agent: CLAUDE
- purpose: Managed-position state (highest price, profit-lock state, TP, near-TP) for each trade
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 43
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-18T18:05:53.3696540+03:00
- agent: CLAUDE
- purpose: Position management events (TP extensions, lock changes) during each trade
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-18T18:07:15.7722511+03:00
- agent: CLAUDE
- purpose: Entry and exit signal decision evidence (strategy, scores, gates, risk/reward, freshness inputs)
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 82060
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-18T18:07:35.6536061+03:00
- agent: CLAUDE
- purpose: 1m/5m/1h signal sequence during each trade (context the exit policy evaluated; 5m/1h from 70 min before entry)
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 19439
- approximate output lines: 318
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-18T18:07:37.4413284+03:00
- agent: CLAUDE
- purpose: Per-minute price path and price-evidence status coverage during each trade
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 1475
- approximate output lines: 163
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-10-18T18:07:39.2932069+03:00
- agent: CLAUDE
- purpose: Exact price events around entry (-20s..+2s) and exit (-45s..+5s) to identify the fill tick and the first exit-threshold tick
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 1524
- approximate output lines: 84
- password: never logged


## DBQ-CLAUDE-I1-010
- timestamp: 2026-10-18T18:07:39.6344298+03:00
- agent: CLAUDE
- purpose: Deployed migration level at trade time (resolve FIX-154/FIX-156 docs-vs-tree contradiction)
- SQL file: DBQ-CLAUDE-I1-010-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I1-011
- timestamp: 2026-10-18T18:07:39.9913825+03:00
- agent: CLAUDE
- purpose: Trading activity after the newest frozen close, plus latest STOPPED loss (scope context only, no substitution)
- SQL file: DBQ-CLAUDE-I1-011-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I1-012
- timestamp: 2026-10-18T18:07:43.4139841+03:00
- agent: CLAUDE
- purpose: Signal-processing latency/claim evidence for entry and exit signals
- SQL file: DBQ-CLAUDE-I1-012-CLAUDE.sql
- exit code: 0
- duration_ms: 3037
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-013
- timestamp: 2026-10-18T18:07:43.7985861+03:00
- agent: CLAUDE
- purpose: Verify actual schemas before Iteration 2 (columns assumed from entities/migrations)
- SQL file: DBQ-CLAUDE-I1-013-CLAUDE.sql
- exit code: 0
- duration_ms: 46
- approximate output lines: 56
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-18T18:15:24.2112064+03:00
- agent: CLAUDE
- purpose: Exact EGLD price events around the trade 143 gap-through (F3 / U2)
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 41
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-18T18:15:33.5425189+03:00
- agent: CLAUDE
- purpose: Feed-wide event density per second (all symbols) during the trade 143 gap: missing persistence or quiet market (U2)
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 8985
- approximate output lines: 44
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-18T18:15:33.9478870+03:00
- agent: CLAUDE
- purpose: EGLD 1m/5m candles around the 143 entry and exit (5m close vs fill, intra-minute low, candle finality/provenance)
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 51
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-18T18:15:34.3634658+03:00
- agent: CLAUDE
- purpose: Execution decision evidence for each BUY and for the prior EGLD profit-lock exit (F2, U1, U5)
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-18T18:15:34.7068428+03:00
- agent: CLAUDE
- purpose: Candidate 5m authority signals and the hidden-bearish 5m signals (F2, F5)
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-18T18:15:35.5703257+03:00
- agent: CLAUDE
- purpose: Work and claim rows for the wakeup, authority and exit signals, and any work linked to the frozen positions (F7)
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 580
- approximate output lines: 173
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-18T18:15:35.8939355+03:00
- agent: CLAUDE
- purpose: Population expectancy since 10-11 by status and close reason (F1 / U3)
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-10-18T18:15:36.5376158+03:00
- agent: CLAUDE
- purpose: EGLD churn context: all EGLD positions that evening, including the one closed 22 s before trade 143 (U5)
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 26
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I2-010
- timestamp: 2026-10-18T18:15:36.8965377+03:00
- agent: CLAUDE
- purpose: Prior EGLD managed position 171 profit-lock state (exit at 3.996 while price was rising)
- SQL file: DBQ-CLAUDE-I2-010-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I2-011
- timestamp: 2026-10-18T18:15:37.2511158+03:00
- agent: CLAUDE
- purpose: Discover Replay, test and audit tables, to check whether these trades can be reproduced in Replay (U7)
- SQL file: DBQ-CLAUDE-I2-011-CLAUDE.sql
- exit code: 0
- duration_ms: 71
- approximate output lines: 35
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-18T18:26:01.8653965+03:00
- agent: CLAUDE
- purpose: Settle T1: what time zone the wrapper session uses
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 25
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-18T18:26:02.1976218+03:00
- agent: CLAUDE
- purpose: Settle T1 with known anchors. If the display is KSA, event 2195977 (logged observedUtc 2026-10-17T00:16:18.036Z) shows 03:16:18.036 with epoch 1792196178.036, and the EGLD event logged at 19:40:01.028Z shows 22:40:01.028 with epoch 1791747601.028.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-18T18:26:02.5222221+03:00
- agent: CLAUDE
- purpose: List DATETIME columns, which display differently from TIMESTAMP through the wrapper, and check Flyway installed_on's type (affects F8/F9 timing)
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 47
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-18T18:26:02.8367711+03:00
- agent: CLAUDE
- purpose: Execution-price age and entry/exit wallet timing for all six positions, using stored fields rather than parsed messages (T5)
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-18T18:28:13.7178975+03:00
- agent: CLAUDE
- purpose: Push cadence per symbol in the 143 exit minute, to separate an EGLD-only gap from a feed-wide one (T3); 30 rows means a full 2 s cadence
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 130576
- approximate output lines: 21
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-18T18:28:14.1990785+03:00
- agent: CLAUDE
- purpose: Other open positions during the 143 exit, to check whether protection work on other symbols contributed to the T4 thread delay
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 104
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-18T18:40:18.3254945+03:00
- agent: CLAUDE
- purpose: Risk-plan fields for the 143 race signals and the prior EGLD entry signal (R1, R3, R4, R5)
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 37
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-18T18:40:18.6873858+03:00
- agent: CLAUDE
- purpose: Which 5m signal authorised the earlier EGLD SETUP_TIMEFRAME_ATR entry #356 (R5)
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-18T18:40:19.1708019+03:00
- agent: CLAUDE
- purpose: How often a BUY on a 5m boundary missed the same-close 5m signal, which was generated later, and with what result (R4)
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 206
- approximate output lines: 86
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-18T18:40:19.5429562+03:00
- agent: CLAUDE
- purpose: Schema of the FIX-154 audit and opportunity tables, before querying rows for 137 and 182 (R6) and the 143 opportunity anchor (R2)
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 44
- approximate output lines: 64
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-18T18:40:19.9164472+03:00
- agent: CLAUDE
- purpose: Profit and loss since 10-11 by entry route and FIX-11G sub-route (R5–R7, F1)
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-18T18:49:23.8705350+03:00
- agent: CLAUDE
- purpose: Opportunity reference and age behind each entry's score (Q3, Q4, Q8)
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 483
- approximate output lines: 127
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-18T18:49:24.4479925+03:00
- agent: CLAUDE
- purpose: The 30-minute 1m signal price history behind each score's reference low and rejected high (Q3, Q4)
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 207
- approximate output lines: 186
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-18T18:49:24.8304583+03:00
- agent: CLAUDE
- purpose: Population profit and loss since 10-11 by R:R band and execution-time score band (Q1, Q2)
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 58
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-18T18:49:25.2558434+03:00
- agent: CLAUDE
- purpose: Where each entry sat in its prior 30-minute 1m range (Q4, Q5, U-I5-4)
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 97
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-18T18:57:45.1706685+03:00
- agent: CLAUDE
- purpose: Actual exit price, percentage return and the risk plan at signal time for the six frozen trades (E2, U-I6-5)
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-18T18:57:45.5053710+03:00
- agent: CLAUDE
- purpose: Population since 10-11 by the R:R planned at entry and by ATR% band, as percentage returns gross and net of an assumed fee. Replaces I5-003 (E8, P3)
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 57
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-18T18:57:45.8422751+03:00
- agent: CLAUDE
- purpose: Distribution of the best favourable move (progress toward target), losers against winners, to test whether giving back gains is a population pattern (finding B)
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 24
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-10-18T18:57:46.2574862+03:00
- agent: CLAUDE
- purpose: 5m decision and original_decision in the 20 minutes before the FIX-11G entries 137 and 182 (E5 counterfactual)
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 123
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-10-18T19:04:33.3352203+03:00
- agent: CLAUDE
- purpose: Confirm the actual Production data types of the time columns used in this study (F7-4, T1)
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-10-18T19:04:33.8858737+03:00
- agent: CLAUDE
- purpose: Outcome only, not entry assessment. Within 180 minutes after the entry minute, which of the planned signal stop and target the 1m candles would have touched first, compared with the actual signal exit (F7-2, U-I7-2)
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 226
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-10-18T19:04:34.2298826+03:00
- agent: CLAUDE
- purpose: Why 182's 5m STRONG_SELL signals were demoted to NEUTRAL (F7-3, U-I7-3)
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 2
- password: never logged

