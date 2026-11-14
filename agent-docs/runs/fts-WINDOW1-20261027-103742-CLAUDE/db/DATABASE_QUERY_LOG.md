
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-27T10:42:06.4985813+03:00
- agent: CLAUDE
- purpose: Read the actual schema of the related trader tables before selecting their columns (AGENTS.md rule).
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 43
- approximate output lines: 281
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-27T10:42:06.8217852+03:00
- agent: CLAUDE
- purpose: Full lifecycle rows for the six frozen trades (prices, quantity, SL/TP, reasons, exit signal).
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-27T10:42:07.1210873+03:00
- agent: CLAUDE
- purpose: Actual close trigger per frozen trade (resolves F-03 and F-05).
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-27T10:42:07.4413139+03:00
- agent: CLAUDE
- purpose: Wallet fills (BUY, adds, partial harvests, SELL) inside each trade's lifecycle window, for P&L recomputation and fill-price/age checks.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-27T10:42:08.0250757+03:00
- agent: CLAUDE
- purpose: Measure the selection-scope gap (F-01/F-02): range of losing rows by status, and whether any positions opened after 2026-10-12.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 24
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-27T10:42:08.3303764+03:00
- agent: CLAUDE
- purpose: Neighbouring positions on the same symbols in the study span (EGLD re-entry context in F-04, interleaved STOPPED or winning trades).
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-27T10:50:18.8356851+03:00
- agent: CLAUDE
- purpose: Re-issue of failed I1-005 with the correct `interval_code` column. Covers entry signals, exit/audit source signals, and the 158 5m trigger (#165415).
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 57
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-27T10:50:19.2110908+03:00
- agent: CLAUDE
- purpose: Read the schema of the position-management event and other audit tables before querying them.
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 38
- approximate output lines: 161
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-27T10:50:19.5688065+03:00
- agent: CLAUDE
- purpose: Managed-position state for the six trades plus #142's position (171): highest price (max favourable excursion), profit-lock fields, final SL/TP, entry-quality score.
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-27T10:50:19.8838714+03:00
- agent: CLAUDE
- purpose: TP/SL revision history for the managed positions (resolves U-10). The column name `wallet_position_id` comes from the `PositionManagementEvent` builder; I2-002 will confirm it.
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-27T10:50:20.1772564+03:00
- agent: CLAUDE
- purpose: EGLD tick sequence around the #142 exit, the #143 entry and the #143 hard-floor exit (F-03, F-09, U-11).
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 69
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-27T10:50:20.6480865+03:00
- agent: CLAUDE
- purpose: 1m candles for each trade's lifecycle: excursion versus TP/SL, and candle data freshness/provenance.
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 183
- approximate output lines: 176
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-27T10:50:21.9642175+03:00
- agent: CLAUDE
- purpose: Admission and freshness of each entry signal's processing work (origin, attempts, completion latency). Resolves U-14.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 1047
- approximate output lines: 20
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-27T10:50:36.6258014+03:00
- agent: CLAUDE
- purpose: The 1m/5m/1h decision sequence from 10 minutes before entry until exit, to check the exit evidence and whether earlier SELLs were ignored (F-08).
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 14346
- approximate output lines: 244
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-10-27T10:50:36.9453932+03:00
- agent: CLAUDE
- purpose: Population test of F-07 and F-08: how often the 0.5% stop floor binds, how often entry R:R < 1, and outcomes by close reason, across all CLOSED and STOPPED positions.
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I2-010
- timestamp: 2026-10-27T10:50:37.3228168+03:00
- agent: CLAUDE
- purpose: Position-analysis rows referenced by the exit audits (exit-score components behind the HOLD/REDUCE recommendations).
- SQL file: DBQ-CLAUDE-I2-010-CLAUDE.sql
- exit code: 0
- duration_ms: 41
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-011
- timestamp: 2026-10-27T10:50:50.0628005+03:00
- agent: CLAUDE
- purpose: Measure U-13 and the F-01 gap: positions and signals per day from 2026-10-10 to the snapshot, to show whether trading or analysis stopped after 2026-10-15.
- SQL file: DBQ-CLAUDE-I2-011-CLAUDE.sql
- exit code: 0
- duration_ms: 12378
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-27T11:02:39.2625345+03:00
- agent: CLAUDE
- purpose: Find where the 5m decision was downgraded (F-14, U-22) and when the boundary-race 5m signals were committed (F-13).
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-27T11:02:40.8509559+03:00
- agent: CLAUDE
- purpose: The exact triggering price observation and the one before it for each exit (E-21, F-15), plus any non-APPLIED rows.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 1312
- approximate output lines: 47
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-27T11:02:42.2171110+03:00
- agent: CLAUDE
- purpose: Gaps between price observations during each holding period (F-12, U-20).
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 1035
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-27T11:02:43.4396907+03:00
- agent: CLAUDE
- purpose: Non-canonical, pending or unresolved price events during the frozen holding periods (data completeness).
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 921
- approximate output lines: 0
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-27T11:02:49.8636421+03:00
- agent: CLAUDE
- purpose: How often the 5m-boundary race happens in the study period (F-13, U-25): how often a 1m signal at a 5m boundary is generated before the 5m signal for the just-closed 5m candle.
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 6051
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-27T11:02:50.2104111+03:00
- agent: CLAUDE
- purpose: When the boundary 5m candles were persisted as closed, to prove the 5m data was available before the 1m decision (F-13).
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 7
- password: never logged

