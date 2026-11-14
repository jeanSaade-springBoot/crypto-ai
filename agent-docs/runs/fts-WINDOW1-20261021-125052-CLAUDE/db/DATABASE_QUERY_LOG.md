
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-22T13:33:47.9309172+03:00
- agent: CLAUDE
- purpose: Read the actual schema of the related trader tables before choosing columns (AGENTS.md rule)
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 390
- approximate output lines: 308
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-22T13:33:48.2371626+03:00
- agent: CLAUDE
- purpose: Full lifecycle row for the six frozen trades (prices, SL/TP, qty, exit signal, reasons)
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-22T13:33:48.5231794+03:00
- agent: CLAUDE
- purpose: Check what the selection skipped: positions closed after 2026-10-11 20:55 UTC (all statuses) and the latest open/close times overall
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 61
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-22T13:33:48.8247780+03:00
- agent: CLAUDE
- purpose: Recorded close trigger per frozen trade
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 39
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-22T13:33:49.1140864+03:00
- agent: CLAUDE
- purpose: Wallet fills for each trade window (entry, partial harvests, exit), to check paper vs wallet
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-22T13:33:49.4530480+03:00
- agent: CLAUDE
- purpose: Managed-position state (SL/TP, highest price, profit lock, entry thesis) for each trade
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 77
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-22T13:33:49.8285534+03:00
- agent: CLAUDE
- purpose: Entry and exit trade_signal rows (full row; any large JSON is trimmed in a later iteration)
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 112
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-22T13:34:01.0890322+03:00
- agent: CLAUDE
- purpose: 1m/5m/1h signal context available at entry and during the hold (checks F-05 and F-07)
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 10983
- approximate output lines: 279
- password: never logged


## DBQ-CLAUDE-I1-010
- timestamp: 2026-10-22T13:34:02.9230765+03:00
- agent: CLAUDE
- purpose: Every price observation for trade 143 (the hard-floor loss) and the 20 observations just before each other trade's exit
- SQL file: DBQ-CLAUDE-I1-010-CLAUDE.sql
- exit code: 0
- duration_ms: 1268
- approximate output lines: 102
- password: never logged


## DBQ-CLAUDE-I1-011
- timestamp: 2026-10-22T13:34:04.2936923+03:00
- agent: CLAUDE
- purpose: Position-management events (TP extension, profit lock, near-TP) and entry-signal work claim/latency
- SQL file: DBQ-CLAUDE-I1-011-CLAUDE.sql
- exit code: 0
- duration_ms: 1085
- approximate output lines: 175
- password: never logged


## DBQ-CLAUDE-I1-012
- timestamp: 2026-10-22T13:34:04.6241807+03:00
- agent: CLAUDE
- purpose: Migration level in effect around the trade dates (checks the FIX-153 OFF-mode level and whether V107/FIX-154 was applied before 2026-10-12)
- SQL file: DBQ-CLAUDE-I1-012-CLAUDE.sql
- exit code: 0
- duration_ms: 61
- approximate output lines: 25
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-22T13:41:18.3686983+03:00
- agent: CLAUDE
- purpose: Confirm which 5m/1h signals gave entry authority. F-13 (143 used 159285 instead of the newer 159407?) and the routes for 158 and 162.
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 51
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-22T13:41:18.6704758+03:00
- agent: CLAUDE
- purpose: Execution route, decision price, price-observation time and Entry Quality text for every entry and exit fill (U-10, F-17, F-21)
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 26
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-22T13:41:19.2767144+03:00
- agent: CLAUDE
- purpose: Execution-opportunity state (route, decision code, 5m/1h decision, health) at each entry
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 335
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-22T13:41:19.5693362+03:00
- agent: CLAUDE
- purpose: Read the candle table schema before requesting candle high/low for the gap through trade 143's floor (U-12) (AGENTS.md: read the schema first)
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 26
- approximate output lines: 19
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-22T13:41:21.0784308+03:00
- agent: CLAUDE
- purpose: Reissue of failed I1-009 with the correct columns: price-update coverage per hold, the largest gap between updates, and any non-APPLIED outcomes
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 1229
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-22T13:41:21.4300766+03:00
- agent: CLAUDE
- purpose: Is the low-reward/risk plan systemic (F-11)? Planned target/stop percentages and reward/risk against outcome for every position closed since 2026-10-11
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 76
- approximate output lines: 95
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-22T13:41:37.9478040+03:00
- agent: CLAUDE
- purpose: Resolve the FIX-154 contradiction (F-19): migration scripts, any FIX-154 tables, and when data_quality_valid started being populated
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 16200
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-22T13:42:12.1233280+03:00
- agent: CLAUDE
- purpose: How often 1m and 5m signals for the same candle close race each other at entry (F-13 generalisation), for all positions opened since 2026-10-11
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 33894
- approximate output lines: 32
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-10-22T13:42:14.1490410+03:00
- agent: CLAUDE
- purpose: Outcome only (U-15): price range in the 60 minutes after each exit, against the trade's take-profit and stop
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 1742
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-22T13:53:27.9833659+03:00
- agent: CLAUDE
- purpose: Prove the session time zone and the true stored epoch values (F-23). UNIX_TIMESTAMP on a TIMESTAMP column returns the stored epoch regardless of session. Expect 358 ? 1791747601.028 and the EGLD 1m candle ? 1791747540.
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 71
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-22T13:53:28.3472538+03:00
- agent: CLAUDE
- purpose: Raw price-feed rows around the trade 143 crash (U-12'), including NON_CANONICAL/duplicate rows, plus the stored 1m candles. Times are the session-shown values.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 38
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-22T13:53:29.6628730+03:00
- agent: CLAUDE
- purpose: Price-feed write lag by second within the minute during the six holds (U-19)
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 1033
- approximate output lines: 60
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-22T13:53:30.0233146+03:00
- agent: CLAUDE
- purpose: Read the schemas of the audit tables before querying them (U-13, U-18)
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 76
- approximate output lines: 27
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-22T13:53:30.7391767+03:00
- agent: CLAUDE
- purpose: Why 1m BUYs 167646 and 176692 were not executed (F-28): all opportunity rows for DOGE and ACE around those minutes
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 436
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-22T13:53:45.2018281+03:00
- agent: CLAUDE
- purpose: How often an entry's recorded 5m decision differs from the 5m signal for the same candle close (F-24 generalisation)
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 14176
- approximate output lines: 86
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-22T14:01:42.5109344+03:00
- agent: CLAUDE
- purpose: FIX-154 audit evidence for the six entry signals and the two skipped BUYs (U-18', U-21, F-32, F-33, F-35, F-36). Expect no row for 159408 (F-35).
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-22T14:01:44.0360494+03:00
- agent: CLAUDE
- purpose: Win/loss and P&L by entry route and decision code, for every position opened since 10-11 (are FIX-11G and the other routes over-represented among losers?)
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 1256
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-22T14:01:45.0672361+03:00
- agent: CLAUDE
- purpose: Planned reward/risk at entry versus the result, for all positions since 10-11 (F-31). Also, for each I3-006 case where the 5-minute input differed from the same-candle 5-minute signal, whether it won or lost (F-34).
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 751
- approximate output lines: 52
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-22T14:01:45.4582952+03:00
- agent: CLAUDE
- purpose: Every 1-minute and 5-minute signal around the two skipped BUYs (01:00–01:03 UTC DOGE, 06:54–06:58 UTC ACE), selected by ID range so the session time zone doesn't matter. This lets the Entry Quality inputs be rebuilt (U-20).
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 85
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-22T14:11:32.8713636+03:00
- agent: CLAUDE
- purpose: Rebuild the Entry Quality reference (recent low), recent high and rejection count for each evaluated signal (F-38, U-20', C-2). Includes the full 30-minute window; the code uses only the newest 20 signals (F-44). The filter uses a relative interval, so the session time zone does not matter.
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 294
- approximate output lines: 243
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-22T14:11:33.2309931+03:00
- agent: CLAUDE
- purpose: Test whether the limit of 3 open positions, which rejects without logging, stopped the skipped BUYs (U-18?). Also check whether a wallet trade was ever attempted for them.
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-22T14:11:34.5877432+03:00
- agent: CLAUDE
- purpose: Entry Quality score, reward/risk and regime against the result for every position opened since 10-11, **including STOPPED** (C-3, U-22). The score is read from the stored opportunity explanation.
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 1089
- approximate output lines: 92
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-22T14:22:00.6176906+03:00
- agent: CLAUDE
- purpose: Exact ATR, liquidity and regime inputs to close F-46 (U-25 ATR inputs). Filters by ID only, so the session time zone does not matter.
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 37
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-22T14:22:00.9382261+03:00
- agent: CLAUDE
- purpose: Opportunity anchor/best prices, to confirm which reference price the score used (U-23, F-46).
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-22T14:22:01.2819196+03:00
- agent: CLAUDE
- purpose: Position notionals, so P&L after fees can be computed (F-53, U-24). The first statement confirms the quantity column name before it is used.
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 43
- approximate output lines: 110
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-10-22T14:33:07.6735659+03:00
- agent: CLAUDE
- purpose: Settle U-26, U-27 and F-59. Pull bounded excerpts of `entry_reason` for the frozen six, and flag the rejection-zone and Entry Quality text plus the position percent. Read-only, filtered by ID.
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-10-22T14:33:08.0194842+03:00
- agent: CLAUDE
- purpose: Settle U-28 by confirming the route preconditions for 143 and 158. The first statement confirms the flag column names before they are used.
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 47
- approximate output lines: 14
- password: never logged

