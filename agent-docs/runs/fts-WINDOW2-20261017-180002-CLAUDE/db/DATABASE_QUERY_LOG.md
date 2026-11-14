
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-17T18:05:52.4787250+03:00
- agent: CLAUDE
- purpose: Re-prove the session time-zone display (Finding 0) for this run and give UTC epochs for the frozen rows.
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 37
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-17T18:05:52.7940844+03:00
- agent: CLAUDE
- purpose: Re-capture the frozen paper_position rows, with a checksum of the reason texts to detect any change since the WINDOW1 snapshot.
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 39
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-17T18:05:53.1255564+03:00
- agent: CLAUDE
- purpose: Entry signals, exit signals and the 5m/1h context signals each decision used (explicit IDs).
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 46
- approximate output lines: 37
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-17T18:05:53.4741257+03:00
- agent: CLAUDE
- purpose: Wallet fills for the six positions, plus the prior EGLD profit-lock exit (#357) before trade 143.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-17T18:05:53.7753332+03:00
- agent: CLAUDE
- purpose: Managed-position protection state (without JSON columns) for the six, plus prior EGLD position 171.
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-17T18:05:54.1000152+03:00
- agent: CLAUDE
- purpose: The authoritative close trigger for each frozen position.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-17T18:05:54.4243264+03:00
- agent: CLAUDE
- purpose: Protection lifecycle events (TP extension, profit lock, SL moves) for each managed position.
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-17T18:05:56.9608691+03:00
- agent: CLAUDE
- purpose: Price events around each entry (20 s before to 3 s after) and each exit (60 s before to 3 s after), to tie fill price and exit trigger to a specific observation.
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 2252
- approximate output lines: 96
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-10-17T18:05:58.1767004+03:00
- agent: CLAUDE
- purpose: Price-event coverage during each position's life: count, min/max price and time span by delivery_status. Checks for gaps and for SL/TP proximity.
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 938
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-010
- timestamp: 2026-10-17T18:06:02.4382677+03:00
- agent: CLAUDE
- purpose: Operational activity up to the snapshot: latest fill, latest signal per interval and latest price event. Explains why the frozen set is unchanged since 10-12.
- SQL file: DBQ-CLAUDE-I1-010-CLAUDE.sql
- exit code: 0
- duration_ms: 3956
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-17T18:11:58.6548564+03:00
- agent: CLAUDE
- purpose: Resolve U2-1 by proving the data types of timestamp columns and the full candle schema.
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 112
- approximate output lines: 50
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-17T18:11:59.0182210+03:00
- agent: CLAUDE
- purpose: Check freshness and quality of the 1m candles for each entry (entry candle -5 to +1). Provenance and closed flag come back via SELECT *.
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 65
- approximate output lines: 42
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-17T18:11:59.3506566+03:00
- agent: CLAUDE
- purpose: Get the 5m and 1h candles that the context signals at entry were built from.
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 69
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-17T18:11:59.6928151+03:00
- agent: CLAUDE
- purpose: Get the complete ordered tick sequence for 143 from open to close, to locate the 4.022 low relative to profit-lock activation (22:40:23.057) and the TP extensions.
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 57
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-17T18:12:13.2502828+03:00
- agent: CLAUDE
- purpose: List all signals for each position's symbol during the position's life, to reconstruct exit-policy inputs and why no exit came earlier (notably 172 between 07:00 and 07:15).
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 13283
- approximate output lines: 165
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-17T18:12:13.6107196+03:00
- agent: CLAUDE
- purpose: Re-verify FIX-154 deployment (U-8/U2-5): which tables exist and which Flyway versions are applied.
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 80
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-17T18:12:19.5211011+03:00
- agent: CLAUDE
- purpose: Map the operational stoppage (F2-11) with per-day signal and price-event coverage since 10-13, and the latest candle per interval.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 5613
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-10-17T18:12:20.5110849+03:00
- agent: CLAUDE
- purpose: Get the Execution-Intelligence opportunity rows behind each entry, to time each decision against competing context commits (U2-8, F2-7).
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 416
- approximate output lines: 40
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-17T18:22:40.3437849+03:00
- agent: CLAUDE
- purpose: Signal timing chain, and which higher-timeframe signal each 1m confluence read (U3-2, F3-3, F3-6, F3-7).
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 25
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-17T18:22:40.6703956+03:00
- agent: CLAUDE
- purpose: Fill and position write timing (U3-3, F3-4).
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 26
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-17T18:22:41.1445149+03:00
- agent: CLAUDE
- purpose: Get the opportunity rows for entries 143, 158 and 172 that were cut off (U3-6).
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 154
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-17T18:22:41.4925919+03:00
- agent: CLAUDE
- purpose: Processing-work timing for the entry and exit signals, and the schema of the analysis-outcome tables.
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 80
- approximate output lines: 44
- password: never logged


## DBQ-CLAUDE-I3-008
- timestamp: 2026-10-17T18:22:43.9298143+03:00
- agent: CLAUDE
- purpose: Price-update ingest latency around each position: candle-close moments vs. mid-minute, and negative values (F3-2, F3-9).
- SQL file: DBQ-CLAUDE-I3-008-CLAUDE.sql
- exit code: 0
- duration_ms: 982
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-17T18:32:40.7455424+03:00
- agent: CLAUDE
- purpose: Runtime proof of which 5m and 1h signals FIX-154 validation actually read for each entry (F4-2, F4-4, U4-1).
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-17T18:32:59.6639481+03:00
- agent: CLAUDE
- purpose: Locate price-update ingest lags over 1 s around the 182 entry and across the trading window, to check for single-thread stalls during entry transactions (F4-5, U4-2).
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 18621
- approximate output lines: 180
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-17T18:33:01.0761155+03:00
- agent: CLAUDE
- purpose: System-wide exposure to label-only 5m authority, meaning entries approved on a blocked or neutralised 5m, and their results (F4-4, U4-4).
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 707
- approximate output lines: 36
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-17T18:42:12.9956505+03:00
- agent: CLAUDE
- purpose: Stored ATR and risk-plan fields for the six entry signals and their 5m authorities (U5-4, F5-1, F5-4, F5-6). The schema check runs first, so that missing columns are visible.
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 23
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-17T18:42:13.7771382+03:00
- agent: CLAUDE
- purpose: Rebuild the EQ reference and rejection-zone inputs: the 1m LIVE signal prices in the 30 min before each entry signal, plus the anchor and best prices of the active opportunities (U5-1, F5-3).
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 493
- approximate output lines: 222
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-17T18:42:14.0879566+03:00
- agent: CLAUDE
- purpose: The full text of the 143 and 162 entry explanations, to see which EQ value each message states (U5-2, F5-4).
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 23
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-17T18:42:14.5869593+03:00
- agent: CLAUDE
- purpose: System-wide test of F5-1/F5-2. Outcome by planned R:R bucket and by EQ bucket, with P&L after an assumed 0.10%-per-side fee, across all closed positions (CLOSED and STOPPED).
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 225
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-17T18:47:56.5704091+03:00
- agent: CLAUDE
- purpose: Split the system-wide gross loss by status, by stop floored or not, and by ATR% group. This sizes the STOPPED class the charter excluded (F6-1, F6-7, U6-2).
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 43
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-17T18:48:00.3491264+03:00
- agent: CLAUDE
- purpose: Test whether entries that follow a run of 1m SELL signals lose system-wide (F6-5, U6-4). The count uses 1m LIVE SELL/STRONG_SELL signals in the 15 minutes before the entry signal.
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 3485
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-10-17T18:56:37.7911150+03:00
- agent: CLAUDE
- purpose: Find which FinalDecisionService stage changed each authority signal's label, and get the original decisions. This settles whether 182 was caused by the F4-4 defect and whether the confirmed wake-up gap applied to 158 (F7-3, F7-4, U7-1).
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-10-17T18:56:38.1241648+03:00
- agent: CLAUDE
- purpose: Corrected I6-003. Count closed positions whose highest price reached the original signal TP, split by outcome, to size the TP-extension loss mechanism seen in 143 (U6-5, U7-2).
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 56
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-10-17T18:56:38.4715133+03:00
- agent: CLAUDE
- purpose: Gross outcome by entry route, to see whether the boundary-race-dependent routes (wake-ups, SETUP_TIMEFRAME_ATR) or the label-only direct route carry the system-wide loss (F7-1, U7-3).
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 50
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I7-004
- timestamp: 2026-10-17T18:56:38.8207969+03:00
- agent: CLAUDE
- purpose: Check what STOPPED means: how many STOPPED and CLOSED positions have a stop or entry price different from the original signal's (raised stops or progressive adds) (F7-5, U7-4).
- SQL file: DBQ-CLAUDE-I7-004-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 2
- password: never logged

