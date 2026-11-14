
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-16T16:57:49.0957406+03:00
- agent: CLAUDE
- purpose: Read the live schema of the tables needed for reconstruction before choosing columns.
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 187
- approximate output lines: 294
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-16T16:57:50.3949684+03:00
- agent: CLAUDE
- purpose: Get the full lifecycle row for each frozen position, with holding time.
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 133
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-16T16:57:51.6515222+03:00
- agent: CLAUDE
- purpose: Get the authoritative close trigger for each position.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 148
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-16T16:57:53.0118151+03:00
- agent: CLAUDE
- purpose: Get the entry and exit signal decision chains (columns confirmed in the baseline migrations; large JSON excluded).
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 244
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-16T16:57:54.2864374+03:00
- agent: CLAUDE
- purpose: Get the signal-processing claim and completion record for each entry signal, to measure signal-to-execution latency.
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 140
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-16T16:57:55.5456291+03:00
- agent: CLAUDE
- purpose: Get the wallet executions (BUY, partial harvests, SELL) inside each position's lifetime, for paper-vs-wallet P&L and fill-price checks.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 102
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-16T16:57:58.3201792+03:00
- agent: CLAUDE
- purpose: Measure price-event coverage per position lifetime by delivery status. This finds protection gaps (PENDING / NON_CANONICAL / PROTECTION_NOT_APPLIED) and the extreme prices.
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 1604
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-16T17:06:19.3167312+03:00
- agent: CLAUDE
- purpose: List crypto_ai tables to locate management-event, candle, and position-analysis entities.
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 219
- approximate output lines: 78
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-16T17:06:20.5205066+03:00
- agent: CLAUDE
- purpose: Read the live schema of candle / management / analysis tables before querying them.
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 107
- approximate output lines: 50
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-16T17:06:24.1323830+03:00
- agent: CLAUDE
- purpose: Post-entry-only price excursion, distinct-price granularity, non-APPLIED events, and the time of the first high.
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 2476
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-16T17:06:25.2105903+03:00
- agent: CLAUDE
- purpose: The full EGLDUSDT tick sequence around the prior profit-lock exit and position 143 (lock gap vs hold).
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 102
- approximate output lines: 89
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-16T17:06:56.9634224+03:00
- agent: CLAUDE
- purpose: The signal sequence during each position's life (all intervals), to trace decision flips that drove exits or holds.
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 28859
- approximate output lines: 238
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-16T17:06:58.4812363+03:00
- agent: CLAUDE
- purpose: The wallet-managed position state for the six positions and the prior EGLD position 171 (profit lock, highest price, stage).
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 139
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-16T17:06:59.5757250+03:00
- agent: CLAUDE
- purpose: The persisted management events (TP extension, lock, hold) for these wallet positions.
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 108
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-10-16T17:07:00.7374566+03:00
- agent: CLAUDE
- purpose: Same-symbol paper positions around the study trades: re-entry churn, the prior EGLD position, and win/loss context.
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 107
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I2-010
- timestamp: 2026-10-16T17:07:02.5533777+03:00
- agent: CLAUDE
- purpose: The execution opportunities behind each entry route, plus the 5m wakeup trigger 165415 lineage for position 158.
- SQL file: DBQ-CLAUDE-I2-010-CLAUDE.sql
- exit code: 0
- duration_ms: 799
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-16T17:22:06.3649368+03:00
- agent: CLAUDE
- purpose: Prove the session time zone and the true epoch of known ticks (expected 1791747715.024 and 1791747815.021 if display = KSA).
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 99
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-16T17:22:07.5243131+03:00
- agent: CLAUDE
- purpose: Schemas for the timing and snapshot tables before querying their data.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 110
- approximate output lines: 48
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-16T17:22:08.7893631+03:00
- agent: CLAUDE
- purpose: Durable slow-stage timings (=1000 ms) in the study period, to confirm or refute F-I3-06 without logs.
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 224
- approximate output lines: 70
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-16T17:22:43.9982330+03:00
- agent: CLAUDE
- purpose: Cross-symbol ingestion lag during each exit's wallet?paper window (does a protection transaction stall other symbols?).
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 34233
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-16T17:22:45.1312903+03:00
- agent: CLAUDE
- purpose: Exit-trigger tick sequences from bearish confirmation to exit (F-I3-04), plus the 1h signal before 156853.
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 120
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-16T17:23:03.2337521+03:00
- agent: CLAUDE
- purpose: Clock-skew and boundary-latency distribution: ingest lag, and 1m signals generated before candle close.
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 17110
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I3-007
- timestamp: 2026-10-16T17:23:04.4213471+03:00
- agent: CLAUDE
- purpose: Wallet snapshot timing vs wallet trade and paper times (where the 1.2–3.2 s goes), and the wallet_asset row count.
- SQL file: DBQ-CLAUDE-I3-007-CLAUDE.sql
- exit code: 0
- duration_ms: 172
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-16T17:35:54.2933634+03:00
- agent: CLAUDE
- purpose: Decision path and entry authority of every signal that authorised or blocked the six entries (F-I4-01/03/04/05).
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 129
- approximate output lines: 22
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-16T17:35:55.6676439+03:00
- agent: CLAUDE
- purpose: The 15m and 1h context available (as of the moment) to each entry and 5m authority signal: inputs to the confluence neutralisation.
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 181
- approximate output lines: 24
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-16T17:35:56.8592198+03:00
- agent: CLAUDE
- purpose: Migrations applied around the trade dates, to establish whether FIX-154 and other code was deployed (F-I4-10).
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 108
- approximate output lines: 110
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-16T17:35:58.1705447+03:00
- agent: CLAUDE
- purpose: FIX-154 BUY assessment audit for the executed and preceding BUY signals. If the table is absent, the error is itself evidence.
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 159
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-16T17:35:59.4359030+03:00
- agent: CLAUDE
- purpose: Outcome of the earlier 1m BUYs that did not execute (F-I4-03 hypothesis for 162; U-I4-03).
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 111
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I4-006
- timestamp: 2026-10-16T17:36:00.7300855+03:00
- agent: CLAUDE
- purpose: Route-level outcomes since 2026-10-05 KSA, to test whether the study routes lose systematically (U-I4-05).
- SQL file: DBQ-CLAUDE-I4-006-CLAUDE.sql
- exit code: 0
- duration_ms: 147
- approximate output lines: 39
- password: never logged


## DBQ-CLAUDE-I4-007
- timestamp: 2026-10-16T17:36:04.8719344+03:00
- agent: CLAUDE
- purpose: For every closed position since 2026-10-05 KSA: the entry 5m effective vs raw decision, its entry permission, and entry quality. Measures how often F-I4-01/F-I4-04 entries occur and how they end.
- SQL file: DBQ-CLAUDE-I4-007-CLAUDE.sql
- exit code: 0
- duration_ms: 3062
- approximate output lines: 190
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-16T17:51:01.3181118+03:00
- agent: CLAUDE
- purpose: Outcome by entry-signal R:R bucket and entry-quality bucket since 2026-10-05 KSA (tests F-I5-01).
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 105
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-16T17:51:02.3692675+03:00
- agent: CLAUDE
- purpose: How often processing failures occur and what ran alongside the 176692 rollback (F-I5-05).
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 103
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-16T17:51:06.2140925+03:00
- agent: CLAUDE
- purpose: The 30-minute 1m look-back before each entry, for the entry-quality inputs (expansion, ATR extension, rejected highs) and the accumulated-evidence count (F-I5-06/07/08/09).
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 271
- approximate output lines: 192
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-16T17:51:12.7207238+03:00
- agent: CLAUDE
- purpose: Tick-level move into the entry (pre-entry chase) and the first five minutes after it (MFE/MAE, outcome only) for the six positions.
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 5047
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-005
- timestamp: 2026-10-16T17:51:14.4921576+03:00
- agent: CLAUDE
- purpose: The current execution-opportunity rows for the study symbols. They show only the latest state, so this checks whether any 167646-era evidence survives (U-I5-01).
- SQL file: DBQ-CLAUDE-I5-005-CLAUDE.sql
- exit code: 0
- duration_ms: 567
- approximate output lines: 50
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-16T18:00:03.0951787+03:00
- agent: CLAUDE
- purpose: Gross vs estimated fee-adjusted outcome by 1m ATR% bucket since 2026-10-05 KSA (F-I6-01/03).
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 129
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-16T18:00:04.2802486+03:00
- agent: CLAUDE
- purpose: Exit-reason mix and average P&L% since 2026-10-05 KSA (do confirmation exits dominate over SL/TP?).
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 109
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-16T18:00:09.3059181+03:00
- agent: CLAUDE
- purpose: Outcome by count of bearish 1m signals (effective or raw) in the 10 minutes before the entry signal (F-I6-01 streak pattern).
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 4003
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-10-16T18:00:10.8784946+03:00
- agent: CLAUDE
- purpose: Opportunity rows in each entry's pre-entry window, bounded per symbol (fixes the I5-005 LIMIT issue; U-I6-01).
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 650
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I6-006
- timestamp: 2026-10-16T18:00:13.0977439+03:00
- agent: CLAUDE
- purpose: Exact Replay row counts (U-I6-03), and entry quality for the six entry signals (U-I6-04).
- SQL file: DBQ-CLAUDE-I6-006-CLAUDE.sql
- exit code: 0
- duration_ms: 271
- approximate output lines: 10
- password: never logged

