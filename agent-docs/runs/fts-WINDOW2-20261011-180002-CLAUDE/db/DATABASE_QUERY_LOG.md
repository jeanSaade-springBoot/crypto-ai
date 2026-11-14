
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-11T18:05:42.9191220+03:00
- agent: CLAUDE
- purpose: U1. Check whether the FIX-153/154 migrations were applied in Production and when.
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-11T18:05:43.2040457+03:00
- agent: CLAUDE
- purpose: Full lifecycle rows for the frozen positions (prices, SL/TP, qty, reasons).
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-11T18:05:43.5041920+03:00
- agent: CLAUDE
- purpose: U2/U6. Entry signal evidence for the six entry signals.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 51
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-11T18:05:46.6891034+03:00
- agent: CLAUDE
- purpose: U2/F2/F3. The 5m and 1h signal sequence per symbol, from 2h before entry to just after exit (bounded windows).
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 2951
- approximate output lines: 183
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-11T18:05:46.9557610+03:00
- agent: CLAUDE
- purpose: U3/U4. The recorded exit trigger and its source signal for each position.
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-11T18:05:47.2291849+03:00
- agent: CLAUDE
- purpose: U1/U5/F3. The FIX-154 BUY assessment audit for the entry signals (fails if the table does not exist, which also answers U1).
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-11T18:05:47.5115238+03:00
- agent: CLAUDE
- purpose: Read the schemas before the next queries (wallet_trade, wallet_managed_position, position_management_event, market_price_event).
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 44
- approximate output lines: 86
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-11T18:05:47.8259164+03:00
- agent: CLAUDE
- purpose: F6/U4. Wallet trades linked to the entry signals and their exits (columns to be checked against DB-007; uses only the documented signal link).
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 32
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-10-11T18:05:48.1736159+03:00
- agent: CLAUDE
- purpose: Check the table list so the price-event and analysis table names are verified rather than assumed.
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 74
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-11T18:12:29.9336683+03:00
- agent: CLAUDE
- purpose: U1/F8. FIX-154 audit for the wake-up 5m triggers and the 1m signals for trades 78/91/104.
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 45
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-11T18:12:30.2317597+03:00
- agent: CLAUDE
- purpose: U2/F6. Management events (target extension, profit lock, rebase) for the six wallet positions.
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 26
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-11T18:12:30.5717321+03:00
- agent: CLAUDE
- purpose: U2/F6. AERO price events during trade 101 (did 0.9586 print; how far apart were the events).
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 175
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-11T18:12:30.8611126+03:00
- agent: CLAUDE
- purpose: U2/F6. Final protection state of the six wallet positions.
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 43
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-11T18:12:31.1621945+03:00
- agent: CLAUDE
- purpose: Read the schemas before the candle, data-gap and opportunity queries.
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 67
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-11T18:13:02.3652202+03:00
- agent: CLAUDE
- purpose: U3/F7. 5m and 1h signal coverage at the 2026-10-10 18:00 UTC boundary across all symbols.
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 30975
- approximate output lines: 0
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-11T18:13:02.6740872+03:00
- agent: CLAUDE
- purpose: U6. Population context: exit reasons by period, before and after V107 (aggregate only; no substitution of frozen trades).
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-11T18:13:03.0302770+03:00
- agent: CLAUDE
- purpose: F4/F5. Risk-geometry fields on the entry signals (to trace where risk/reward and target are set).
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-11T18:23:40.8230182+03:00
- agent: CLAUDE
- purpose: T1. Prove the investigation session's time zone and the offset applied to timestamp columns. Log reference: signal 150942 has candleOpenTime=2026-10-11T14:07:00Z.
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 56
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-11T18:23:41.2098041+03:00
- agent: CLAUDE
- purpose: T6. Signal coverage per interval and candle around the DB-shown 18:00 boundary on 2026-10-10, across all symbols.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 108
- approximate output lines: 22
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-11T18:23:41.4937454+03:00
- agent: CLAUDE
- purpose: T6. Candle rows behind the missing signals (closed flag, ingestion source, write times).
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 27
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-11T18:23:41.8956209+03:00
- agent: CLAUDE
- purpose: T6. Analysis outcomes and reconciliation audit for the boundary identities (DESCRIBE first, then a bounded read).
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 133
- approximate output lines: 97
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-11T18:23:42.2445229+03:00
- agent: CLAUDE
- purpose: T5/U3. The price events that triggered the four HTF_SELL exits, including any non-APPLIED (rejected) ones.
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-11T18:23:42.6528872+03:00
- agent: CLAUDE
- purpose: U5 for iteration-2 F3. How far each frozen trade moved in its favour and against it while open, from 1m candles. Bounded to the six positions.
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 92
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-11T18:33:23.3047438+03:00
- agent: CLAUDE
- purpose: R7. Does recovery without analysis recur at the same time each day (Binance 24-hour disconnect)? Times are shown in KSA.
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 140
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-11T18:33:23.6074481+03:00
- agent: CLAUDE
- purpose: R5/R7. Reconciliation audit rows for the outage window (shown 17:58–18:06 KSA on 2026-10-10).
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 50
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-11T18:33:24.0201210+03:00
- agent: CLAUDE
- purpose: R2/U3. Population check: HTF_SELL_CONFIRMED exits where the newest 1h signal at close was created before the position opened (a stale 1h SELL acting as exit authority). Aggregate only.
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-11T18:33:24.4684844+03:00
- agent: CLAUDE
- purpose: R3/U4. Population check: outcome by entry-signal risk/reward bucket (below 1, 1 to 1.49, 1.5 and above). Joined through wallet_managed_position.entry_signal_id; aggregate only.
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 125
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-11T18:33:25.3825108+03:00
- agent: CLAUDE
- purpose: R5. Confirm that the 1m candles inserted as BACKFILL (shown 18:00 and 18:01 KSA) have no analysis outcome or signal.
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 620
- approximate output lines: 36
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-11T18:41:10.1093012+03:00
- agent: CLAUDE
- purpose: E1/E2/U1. Population check since 2026-10-03: results by entry stage and by the entry 1m signal's decision. Aggregate only. Times are as the DB displays them (KSA).
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 346
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-11T18:53:55.4744715+03:00
- agent: CLAUDE
- purpose: E3/U1. Population check: results by the raw decision of the newest 1h signal at entry (does entering against a 1h SELL/STRONG_SELL lose more?). Aggregate only.
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 765034
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-11T18:53:55.8675520+03:00
- agent: CLAUDE
- purpose: E7/U2. 1m candles around the BREAKOUT entries of 96 (PEPE) and 104 (ENA): where in the candle was the fill? Times are as displayed (KSA).
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 26
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-11T18:53:56.1972674+03:00
- agent: CLAUDE
- purpose: E5/U3. XLM 1m signals behind trade 82's rejection zone (0.1986) and accumulated evidence (2 BUY / 14 WATCH): which came before the outage, and when was the recent high set?
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 54
- approximate output lines: 26
- password: never logged


## DBQ-CLAUDE-I5-005
- timestamp: 2026-10-11T18:53:56.5476456+03:00
- agent: CLAUDE
- purpose: E0/U4. Candle rows, analysis outcomes and reconciliation audit for XLM 5m 17:55 and 1h 17:00 (displayed KSA), to explain the missing signals with no recovery row.
- SQL file: DBQ-CLAUDE-I5-005-CLAUDE.sql
- exit code: 0
- duration_ms: 41
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-11T19:03:47.5109940+03:00
- agent: CLAUDE
- purpose: G2/G3. Find every candle the startup import closed around the outage: closed RUNTIME rows with no token and no analysis outcome, updated 18:01–18:04 KSA on 2026-10-10. Shows how far the defect reached and the order symbols were processed in (bootstrap fingerprint).
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 1676
- approximate output lines: 32
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-11T19:03:49.0938940+03:00
- agent: CLAUDE
- purpose: G2/G3 across all trades. Count, by minute, the closed RUNTIME candles with no close token since 2026-10-04 (the fingerprint of the import closing a live row). Bursts mark restarts. Aggregate only; times shown in KSA.
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 1232
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-11T19:03:49.3975802+03:00
- agent: CLAUDE
- purpose: G3. Look for deploy markers: Flyway migrations applied since 2026-10-09, to compare with the restart and recovery times. Times shown in KSA.
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-10-11T19:03:49.9924116+03:00
- agent: CLAUDE
- purpose: G4/U2. What would have happened without each exit: within 240 min after closing, did price touch the take-profit or the stop first, and what were the high and low in the first 60 min? Limited to the six frozen positions. Caveat: the stored stop_loss may be the final protected stop.
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 329
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I6-005
- timestamp: 2026-10-11T19:03:50.3025272+03:00
- agent: CLAUDE
- purpose: G5/U3. Effect of fees across all trades: P&L before fees against estimated P&L after a 0.1% fee on each side, by close reason, since 2026-10-03. Aggregate only.
- SQL file: DBQ-CLAUDE-I6-005-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-10-11T19:11:01.1940121+03:00
- agent: CLAUDE
- purpose: C1/U3. Redo the entry-stage breakdown using the signal-ID link instead of the symbol + 5 s join, and report trades with no match. Aggregate only.
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 100
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-10-11T19:11:01.7988846+03:00
- agent: CLAUDE
- purpose: U7. List paper positions that were open during any of the five restart bursts (each window starts 5 min before the burst, KSA as displayed).
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I8-001
- timestamp: 2026-10-11T19:19:29.2653944+03:00
- agent: CLAUDE
- purpose: U7/O2. 1m low against the stored stop for UNIUSDT position 80 across the 18:00 KSA restart gap (displayed KSA). Did price breach the stop while protection was offline?
- SQL file: DBQ-CLAUDE-I8-001-CLAUDE.sql
- exit code: 0
- duration_ms: 43
- approximate output lines: 22
- password: never logged


## DBQ-CLAUDE-I8-002
- timestamp: 2026-10-11T19:19:29.7916516+03:00
- agent: CLAUDE
- purpose: U2. Re-issue of I7-002, which did not execute: target-first or stop-first within 240 min after every HTF_SELL_CONFIRMED exit since 2026-10-03.
- SQL file: DBQ-CLAUDE-I8-002-CLAUDE.sql
- exit code: 0
- duration_ms: 257
- approximate output lines: 7
- password: never logged

