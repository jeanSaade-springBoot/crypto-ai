
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-12T18:07:31.9265498+03:00
- agent: CLAUDE
- purpose: Full paper_position rows for the frozen trades (SL/TP geometry, entry/exit reasons, exit signal)
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 62
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-12T18:07:32.2304866+03:00
- agent: CLAUDE
- purpose: Entry and exit trade_signal context for each frozen trade
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-12T18:07:32.5285881+03:00
- agent: CLAUDE
- purpose: Wallet executions (fills, qty, fees, execution_key) bounded to the study symbols and window
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 20
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-12T18:07:32.8523392+03:00
- agent: CLAUDE
- purpose: Managed positions (managed SL/TP, highest price, profit-lock and near-TP state)
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 52
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-12T18:07:33.1375234+03:00
- agent: CLAUDE
- purpose: Actual recorded close trigger and source signal for each trade
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-12T18:07:33.4485854+03:00
- agent: CLAUDE
- purpose: Position management events (TP extensions, profit-lock activity) during each lifetime
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 26
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-12T18:07:48.6185179+03:00
- agent: CLAUDE
- purpose: Signal timeline for each trade (5m/1h from 70 min before entry; 1m from 10 min before entry; through 2 min after close) to identify the newest 1m/5m/1h signals at entry and at exit
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 14893
- approximate output lines: 329
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-12T18:07:50.0732192+03:00
- agent: CLAUDE
- purpose: Price events in the 30 s before each close (exit trigger tick, delivery_status)
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 1114
- approximate output lines: 34
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-10-12T18:07:50.3853337+03:00
- agent: CLAUDE
- purpose: Full price path for trade 143 (profit-lock activation and floor breach within 3m31s)
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 62
- password: never logged


## DBQ-CLAUDE-I1-010
- timestamp: 2026-10-12T18:07:50.6644121+03:00
- agent: CLAUDE
- purpose: Applied Flyway migrations (resolve the V107–V109 FIX-154 contradiction, F8)
- SQL file: DBQ-CLAUDE-I1-010-CLAUDE.sql
- exit code: 0
- duration_ms: 26
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I1-011
- timestamp: 2026-10-12T18:08:13.4171875+03:00
- agent: CLAUDE
- purpose: Signal mode and data quality of entry and exit signals (separate query in case the columns are absent)
- SQL file: DBQ-CLAUDE-I1-011-CLAUDE.sql
- exit code: 0
- duration_ms: 22443
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-12T18:15:29.6109847+03:00
- agent: CLAUDE
- purpose: Confluence context of the masked 5m signals (original SELL ? published NEUTRAL) for trade 182, plus the reference entry-time signals for all trades
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 51
- approximate output lines: 22
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-12T18:15:29.9045455+03:00
- agent: CLAUDE
- purpose: FIX-154 BUY-assessment audit for each frozen entry (which 5m/1h signals were used, band, code)
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-12T18:15:30.2986886+03:00
- agent: CLAUDE
- purpose: Pattern check — entry risk/reward bucket against outcome across all closed paper positions
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 127
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-12T18:15:30.6422366+03:00
- agent: CLAUDE
- purpose: Check the 143 gap — EGLDUSDT 1m candle for 22:43 and the price events between 22:43:27 and 22:43:35
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-12T18:27:14.3027997+03:00
- agent: CLAUDE
- purpose: Commit-visibility timing for the exit, entry and boundary signals (F21, F22)
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 132
- approximate output lines: 23
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-12T18:27:14.8536425+03:00
- agent: CLAUDE
- purpose: Durable signal-processing timing (completion relative to generation) for the same signals
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 62
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-12T18:27:15.3556607+03:00
- agent: CLAUDE
- purpose: FIX-154 audit rows for the 143 and 158 entries, by trigger IDs and by time window (F22, F23)
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 76
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-12T18:27:35.1140177+03:00
- agent: CLAUDE
- purpose: Was each gap without ticks global or symbol-specific (U16)?
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 19081
- approximate output lines: 50
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-12T18:27:36.3921729+03:00
- agent: CLAUDE
- purpose: Every tick (all statuses) in the 172 exit-delay window and in each entry's window from price observation to execution (U15, F28)
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 276
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-12T18:27:39.4849416+03:00
- agent: CLAUDE
- purpose: Slow or failed kline stages (= 1 s or not RETURNED) around the study trades and the gap windows (U16, U17)
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 2582
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I3-007
- timestamp: 2026-10-12T18:27:39.9549373+03:00
- agent: CLAUDE
- purpose: Candle provenance and trade counts for the gap minutes, plus any reconnect or recovery audit (F29, U16)
- SQL file: DBQ-CLAUDE-I3-007-CLAUDE.sql
- exit code: 0
- duration_ms: 77
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I3-008
- timestamp: 2026-10-12T18:27:40.4050040+03:00
- agent: CLAUDE
- purpose: Reconciliation or reconnect evidence for the study symbols during the study window
- SQL file: DBQ-CLAUDE-I3-008-CLAUDE.sql
- exit code: 0
- duration_ms: 68
- approximate output lines: 0
- password: never logged


## DBQ-CLAUDE-I3-009
- timestamp: 2026-10-12T18:27:40.8754334+03:00
- agent: CLAUDE
- purpose: Query plan of the snapshot price lookup, and the wallet asset count (F25, U18)
- SQL file: DBQ-CLAUDE-I3-009-CLAUDE.sql
- exit code: 0
- duration_ms: 39
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-12T18:38:01.9283633+03:00
- agent: CLAUDE
- purpose: Decide the time zone the study's query results are displayed in (F31, U20)
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 147
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-12T18:38:03.0236232+03:00
- agent: CLAUDE
- purpose: Epoch values that don't depend on time zone, to compare against the app's `priceObservedAt=…Z` strings (F31)
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 107
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-12T18:38:04.1989455+03:00
- agent: CLAUDE
- purpose: Opportunity records behind position 171's and trade 143's SETUP_TIMEFRAME_ATR entries (F33, U21); schema first
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 161
- approximate output lines: 45
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-12T18:38:05.2459989+03:00
- agent: CLAUDE
- purpose: Route gate fields for the EGLD re-entry signals (F33)
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 79
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-12T18:38:06.2408717+03:00
- agent: CLAUDE
- purpose: Outcome by entry route across all closed paper positions (U22)
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 138
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I4-006
- timestamp: 2026-10-12T18:38:07.2395026+03:00
- agent: CLAUDE
- purpose: Re-entries within 120 s of a same-symbol close, and how they turned out (F33, U22)
- SQL file: DBQ-CLAUDE-I4-006-CLAUDE.sql
- exit code: 0
- duration_ms: 124
- approximate output lines: 22
- password: never logged


## DBQ-CLAUDE-I4-007
- timestamp: 2026-10-12T18:38:22.1992204+03:00
- agent: CLAUDE
- purpose: How often the same-boundary 5m signal is generated after the 1m (F35)
- SQL file: DBQ-CLAUDE-I4-007-CLAUDE.sql
- exit code: 0
- duration_ms: 13850
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-12T18:47:45.1736483+03:00
- agent: CLAUDE
- purpose: Rebuild the EQ reference, recent high and rejection count from the 1m signals in the 30 minutes before each entry signal (F40, U23)
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 18009
- approximate output lines: 182
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-12T18:47:46.2608494+03:00
- agent: CLAUDE
- purpose: Opportunity anchor and best price, and age, at each entry (inputs to the EQ reference)
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 102
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-12T18:47:47.4738783+03:00
- agent: CLAUDE
- purpose: Gross and fee-adjusted (10 bps per side) outcome by target-distance bucket across closed positions (F44, U26)
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 199
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-12T18:47:48.4886794+03:00
- agent: CLAUDE
- purpose: Outcome by stored entry EQ band (F39, F40, U26)
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 122
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I5-005
- timestamp: 2026-10-12T18:47:49.5445087+03:00
- agent: CLAUDE
- purpose: How often the sizing inversion occurs and how it turns out (ATR-recommended size 0 treated as 100), plus the six entry signals' ATR plans (F41, U24)
- SQL file: DBQ-CLAUDE-I5-005-CLAUDE.sql
- exit code: 0
- duration_ms: 100
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I5-006
- timestamp: 2026-10-12T18:47:50.7575870+03:00
- agent: CLAUDE
- purpose: For each SETUP_TIMEFRAME_ATR buy, the 5m signal chosen by the `generated_at` rule versus the latest closed 5m candle, and the outcome (F43, F46, U27)
- SQL file: DBQ-CLAUDE-I5-006-CLAUDE.sql
- exit code: 0
- duration_ms: 331
- approximate output lines: 23
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-12T18:54:47.4351870+03:00
- agent: CLAUDE
- purpose: Prevalence and outcome of BUYs whose audited 5m signal had a bearish original_decision masked to non-bearish (F51, U29)
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 217
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-12T18:54:48.5910804+03:00
- agent: CLAUDE
- purpose: Route and outcome of executions whose 1m ATR plan said NO_ENTRY or WAIT_FOR_RETRACEMENT (F53, U28)
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 308
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-12T18:54:49.5785152+03:00
- agent: CLAUDE
- purpose: Decisions of the 5m signal used versus the latest closed 5m in the six superseded SETUP_TIMEFRAME_ATR cases (F52, U30)
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 91
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-10-12T18:54:50.6345444+03:00
- agent: CLAUDE
- purpose: P&L by exit reason and holding time across all closed positions (U31)
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 91
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I6-005
- timestamp: 2026-10-12T18:54:51.6434049+03:00
- agent: CLAUDE
- purpose: How often an entry's opportunity age sat within 1 s of an EQ age threshold (20/40/60 min), measuring F50's timing sensitivity
- SQL file: DBQ-CLAUDE-I6-005-CLAUDE.sql
- exit code: 0
- duration_ms: 134
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-10-12T19:02:58.8128563+03:00
- agent: CLAUDE
- purpose: Measure the losers the frozen selection excluded (STOPPED) between the oldest frozen close and the snapshot (F57, U33)
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 105
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-10-12T19:02:59.8487503+03:00
- agent: CLAUDE
- purpose: Stored route code (SETUP_TIMEFRAME_WAKEUP vs REDUCED_POSITION_ALLOWED) for the 0%-plan and suspected replaced-5m entries (F58, F59, U34)
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 93
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-10-12T19:03:00.9085695+03:00
- agent: CLAUDE
- purpose: Confirm time column types for the remaining study tables (F62, U35)
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 95
- approximate output lines: 19
- password: never logged

