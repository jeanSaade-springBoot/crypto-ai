
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-12T10:08:20.0222097+03:00
- agent: CLAUDE
- purpose: Find which migrations are applied in crypto_ai (are FIX-153 V106/V108 and FIX-154 V107/V109 present, and when were they installed).
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-12T10:08:20.3787030+03:00
- agent: CLAUDE
- purpose: Get the actual column list of the tables used in this study before relying on them.
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 54
- approximate output lines: 149
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-12T10:08:20.6779839+03:00
- agent: CLAUDE
- purpose: Get the paper_position rows for the frozen trades (identity, lifecycle, prices, P&L, reasons). Reason text is truncated to keep the result bounded.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 25
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-12T10:08:20.9892371+03:00
- agent: CLAUDE
- purpose: Get the recorded close trigger for each frozen trade.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-12T10:08:21.4874945+03:00
- agent: CLAUDE
- purpose: Get the entry and exit signals for each frozen trade (decision chain, mode, timing, context).
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 108
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-12T10:08:22.9844826+03:00
- agent: CLAUDE
- purpose: Get the signal-processing claim and attempt record for each entry signal (latency from signal to execution).
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 1237
- approximate output lines: 194
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-12T10:08:23.2910818+03:00
- agent: CLAUDE
- purpose: Get the wallet fills linked to each trade (entry and exit price, decision price versus fill price, price-observation age, quantity, P&L). Match by signal or by symbol inside the position lifetime ±2 min.
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 38
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-12T10:08:24.5382507+03:00
- agent: CLAUDE
- purpose: Summarise price-event coverage while each position was open, grouped by delivery status (to find protection gaps).
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 959
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-10-12T10:08:25.6783461+03:00
- agent: CLAUDE
- purpose: Find the first accepted (LEGACY/APPLIED) price observation at or below the stop-loss for each trade, to compare against the actual close time and price.
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 835
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-010
- timestamp: 2026-10-12T10:08:25.9739270+03:00
- agent: CLAUDE
- purpose: Get the candle each entry signal was built from (closed flag, provenance, close token) to check data freshness and integrity.
- SQL file: DBQ-CLAUDE-I1-010-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-12T10:15:50.0603514+03:00
- agent: CLAUDE
- purpose: Resolve F-I2-01. Get the database clock and time zones and compare them with the host time logged for this query.
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 6240
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-12T10:15:50.4413418+03:00
- agent: CLAUDE
- purpose: Measure the selection bias in F-I2-02: losses by status and close reason, and recent losses of any status.
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 37
- approximate output lines: 38
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-12T10:15:50.7591288+03:00
- agent: CLAUDE
- purpose: Get the managed-position state for each trade: profit-lock fields, entry stage, scale-ins and decision path. Wallet position IDs come from production_exit_audit.
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-12T10:15:52.4459472+03:00
- agent: CLAUDE
- purpose: Get the last 20 price events before each exit, to test the profit-lock gap (F-I2-06) and exit-price authority.
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 1389
- approximate output lines: 98
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-12T10:15:52.8550066+03:00
- agent: CLAUDE
- purpose: Get the 1m candles for each position's lifetime (entry quality, peak, gap evidence), plus the candle before entry.
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 158
- approximate output lines: 172
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-12T10:15:53.2095072+03:00
- agent: CLAUDE
- purpose: Get the scale-in, exit-trigger and higher-timeframe context signals, including `data_quality_valid`.
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 72
- approximate output lines: 22
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-12T10:15:53.5168229+03:00
- agent: CLAUDE
- purpose: Get the FIX-154 higher-timeframe BUY assessment audit for every entry and scale-in signal.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 37
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-12T10:15:53.8288292+03:00
- agent: CLAUDE
- purpose: Get the execution route and reason for every wallet fill, plus the EGLD position before 143 (re-entry and cooldown).
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 36
- password: never logged

