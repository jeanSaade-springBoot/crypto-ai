
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-17T10:05:59.7843272+03:00
- agent: CLAUDE
- purpose: Full paper_position rows for the frozen trades (prices, qty, SL/TP, exit signal, reasons) — U-1.
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 44
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-17T10:06:00.1232872+03:00
- agent: CLAUDE
- purpose: Read actual schemas before using them (U-6).
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 56
- approximate output lines: 239
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-17T10:06:00.4614828+03:00
- agent: CLAUDE
- purpose: Entry and exit signal rows (decision chain, freshness, R/R, quality) — U-2. Columns are taken from the TradeSignal entity; if the wrapper rejects an unknown column, rerun after I1-002.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-17T10:06:01.8367910+03:00
- agent: CLAUDE
- purpose: Latest 1m/5m/1h signal at or before entry and at or before exit. This reproduces what the live path loaded (LivePositionProtectionService.java:95-97) — U-3, F-2, F-4.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 1083
- approximate output lines: 36
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-17T10:06:19.1567687+03:00
- agent: CLAUDE
- purpose: Bounded signal timeline per trade, from 15 min before entry to 2 min after exit, for 1m/5m/1h (decision flips during the hold).
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 16895
- approximate output lines: 287
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-17T10:06:19.4819518+03:00
- agent: CLAUDE
- purpose: Wallet executions for each trade's lifecycle: fills, quantities, partial harvests and paper-vs-wallet agreement (U-4). SELECT * is used because the columns are unverified until I1-002.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-17T10:06:19.8143253+03:00
- agent: CLAUDE
- purpose: Managed position (lock state, highest price, TP) and management events (TP extension, lock) per trade, which decide trade 143 (U-5, F-3).
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 59
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-17T10:06:20.1525667+03:00
- agent: CLAUDE
- purpose: Recency and selection scope (F-5, F-8). Checks activity after the newest frozen loser and STOPPED losers inside the frozen window.
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 54
- approximate output lines: 28
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-17T10:14:20.3362520+03:00
- agent: CLAUDE
- purpose: Prove the session time zone, and compare a TIMESTAMP column with a DATETIME column (DATETIME is not converted) in the same row (Finding 0).
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-17T10:14:20.6780439+03:00
- agent: CLAUDE
- purpose: EGLDUSDT price events from trade 143's entry to its exit: gap-through versus protected hold (F-12, U-10).
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 61
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-17T10:14:23.2814982+03:00
- agent: CLAUDE
- purpose: Price-event density and range per hold, plus the ticks in the final 90 s before each exit (U-11).
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 2305
- approximate output lines: 105
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-17T10:14:23.5990720+03:00
- agent: CLAUDE
- purpose: The authoritative exit audit row for each frozen trade (U-12).
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 44
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-17T10:14:33.6716211+03:00
- agent: CLAUDE
- purpose: The same-boundary 1m/5m ordering race: exact persistence times and flags (F-11), plus how often it happens system-wide over 2 days (U-13).
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 9758
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-17T10:14:34.0254100+03:00
- agent: CLAUDE
- purpose: EGLDUSDT lifecycles between trades 137 and 143, including wallet position 171, to check re-entry after a profit-lock exit (U-14).
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 37
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-17T10:14:34.5637794+03:00
- agent: CLAUDE
- purpose: Outcome after exit (kept separate from the entry assessment): 1m closes for 60 min after each exit, plus the candle schema for iteration 3 (U-15).
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 245
- approximate output lines: 26
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-17T10:14:35.0494851+03:00
- agent: CLAUDE
- purpose: Do F-9 (R/R below 1 from the stop floor) and F-12 (target extension, then a losing exit) hold across all closed trades? (U-16)
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 201
- approximate output lines: 42
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-17T10:23:34.3882424+03:00
- agent: CLAUDE
- purpose: Candle rows behind each entry and exit, including EGLD's 22:43 1m and the candle high and low, to check ingestion latency and provenance (F-17, F-19).
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 81
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-17T10:23:34.7740458+03:00
- agent: CLAUDE
- purpose: Exact timing of the signals that drove each exit, compared with the wallet SELL time (F-19, U-20).
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 74
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-17T10:23:35.1885281+03:00
- agent: CLAUDE
- purpose: How often an executed BUY at a 5m boundary relied on a superseded 5m signal, and the newer 5m disagreed and was already inserted before the fill (F-18).
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 137
- approximate output lines: 52
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-17T10:23:35.5829198+03:00
- agent: CLAUDE
- purpose: List of TIMESTAMP and DATETIME columns, to correct the time-label rule (Finding 0, U-22).
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 105
- approximate output lines: 36
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-17T10:23:42.2769549+03:00
- agent: CLAUDE
- purpose: Whether the 8-second EGLD silence before trade 143's exit was specific to that symbol or a stream-wide gap (U-19).
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 6373
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-17T10:23:50.4214280+03:00
- agent: CLAUDE
- purpose: The distribution of analysis latency for 1m signals over the two trade days, to rule latency in or out as a general cause.
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 7828
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-17T10:35:08.7908928+03:00
- agent: CLAUDE
- purpose: For the 17 boundary BUYs whose newer 5m was not bullish, show the 5m actually used (the superseded one), the newer 5m, and the realized outcome (F-27, U-24).
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 77
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-17T10:35:11.5834572+03:00
- agent: CLAUDE
- purpose: The blocking stage and original decision of each entry's 5m context signal, and how often system-wide a BUY used a 5m that was bullish but blocked, or bearish but downgraded (F-25, F-26, U-25, U-26).
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 2488
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-17T10:35:11.9563337+03:00
- agent: CLAUDE
- purpose: Clock-offset proof. Compare the DB insert time with the Binance event time for each exit-triggering tick, and with the wallet SELL time (F-23, U-23).
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 38
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-17T10:35:12.2892351+03:00
- agent: CLAUDE
- purpose: The FIX-154 schema and runtime state at trade time (F-28, U-27). Two separate statements, so that if the audit table is missing only the second one fails.
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 20
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-17T10:35:15.8058050+03:00
- agent: CLAUDE
- purpose: Trading and analysis activity after 10-12, and the hourly 1m latency on 10-12, to find the 74 s window (F-29, U-28).
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 3238
- approximate output lines: 27
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-17T10:44:18.9457260+03:00
- agent: CLAUDE
- purpose: Entry Quality inputs for each entry and its 5m authority: ATR, liquidity status (STOP_EXPOSED), regime, entry-authority cap (F-30, F-34, U-29).
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-17T10:44:19.5220644+03:00
- agent: CLAUDE
- purpose: The 30-minute 1m evidence window before each entry, to rebuild the reference low, the rolling high, the rejection count and the single BUY behind 162 (F-30, F-33, F-36).
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 277
- approximate output lines: 189
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-17T10:44:20.4136712+03:00
- agent: CLAUDE
- purpose: The opportunity rows around each entry: anchor and best prices (FIX-055/105 chase reference), age, evidence counts (F-30, F-36, U-29).
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 601
- approximate output lines: 60
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-17T10:44:20.8025862+03:00
- agent: CLAUDE
- purpose: Whether the Entry Quality band and a target below the fee predict outcome across all closed trades (F-30, F-31, U-31).
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 111
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I5-005
- timestamp: 2026-10-17T10:44:21.1302959+03:00
- agent: CLAUDE
- purpose: Why 176655 was downgraded, and route-level outcomes for FIX-11G and for LATE_ENTRY accumulated entries (F-26, F-32, F-33, U-30, U-31).
- SQL file: DBQ-CLAUDE-I5-005-CLAUDE.sql
- exit code: 0
- duration_ms: 53
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-17T10:52:46.1183247+03:00
- agent: CLAUDE
- purpose: U-32. Find any XRP or SHIB opportunity active at entry time, which my I5-003 filter excluded, plus the BUY message for 158 that holds the decision-time EQ.
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 142
- approximate output lines: 77
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-17T10:52:50.1018229+03:00
- agent: CLAUDE
- purpose: U-33 / F-37. Outcome by the number of 1m STRONG_SELL signals (effective or original) in the 15 minutes before each entry signal, across all closed paper positions.
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 3698
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-17T10:52:50.4363188+03:00
- agent: CLAUDE
- purpose: U-34 / F-42. Confirm the schema, then compare floor-stop and ATR-stop classes by status and close reason, with gross P&L and an estimated 0.2% round-trip fee.
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 35
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-10-17T10:59:29.9299420+03:00
- agent: CLAUDE
- purpose: U-36. Check system-wide fee recording in `wallet_trade`.
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-10-17T10:59:30.2447018+03:00
- agent: CLAUDE
- purpose: U-37 / F-47. Daily outcome breakdown (KSA days) to expose code-version or regime effects.
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-10-17T10:59:30.6175817+03:00
- agent: CLAUDE
- purpose: U-38 / F-46. List each BUY whose 5m was a downgraded bearish signal, with outcome and size.
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 74
- approximate output lines: 8
- password: never logged

