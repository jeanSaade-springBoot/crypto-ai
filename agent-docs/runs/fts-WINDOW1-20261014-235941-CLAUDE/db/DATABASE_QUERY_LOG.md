
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-15T00:05:46.5489415+03:00
- agent: CLAUDE
- purpose: Full lifecycle rows for the frozen positions (prices, SL/TP, qty, reasons, exit signal)
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 175
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-15T00:05:46.9249225+03:00
- agent: CLAUDE
- purpose: Prove actual schemas before choosing columns (AGENTS.md rule)
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 55
- approximate output lines: 239
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-15T00:05:47.2567056+03:00
- agent: CLAUDE
- purpose: Entry-signal decision evidence for the six positions (columns per entity mapping; validate against DBQ-CLAUDE-I1-002)
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-15T00:05:47.6273271+03:00
- agent: CLAUDE
- purpose: Exit-signal evidence (signal carried on close)
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 50
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-15T00:05:47.9721081+03:00
- agent: CLAUDE
- purpose: Immutable actual close trigger per position
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-15T00:05:48.3915010+03:00
- agent: CLAUDE
- purpose: Wallet executions inside each lifecycle (fill vs decision price, price age, USDT P&L, partial harvests)
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 71
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-15T00:05:48.7144400+03:00
- agent: CLAUDE
- purpose: Selection-scope check (F-2): newest closes by status and losing STOPPED rows after the newest frozen close
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 37
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-15T00:05:49.0677974+03:00
- agent: CLAUDE
- purpose: TP-extension / profit-lock management history for each lifecycle (priority: 143)
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 47
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-10-15T00:05:49.5344037+03:00
- agent: CLAUDE
- purpose: Latest 5m and 1h signals available at or before each entry (MTF context known at entry; no look-ahead)
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 116
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-15T00:14:17.1902772+03:00
- agent: CLAUDE
- purpose: Verify H2-6. Which EGLD 5m and 1m signals existed around 143's entry, and in what generation order.
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 75
- approximate output lines: 26
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-15T00:14:18.4499035+03:00
- agent: CLAUDE
- purpose: Exit source signals plus the 5m/1h context at each exit (F2-5).
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 167
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-15T00:14:19.6838961+03:00
- agent: CLAUDE
- purpose: Entry execution reasoning (route, percent, entry quality, 5m authority ID) for the six BUY fills (F2-6, F2-7).
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 147
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-15T00:14:20.2833592+03:00
- agent: CLAUDE
- purpose: Final managed-position state: profit-lock, highest price and Near-TP (U-5, U-6).
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 137
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-15T00:14:20.6878155+03:00
- agent: CLAUDE
- purpose: Price-event cadence and path for 143 (peak, lock breach, floor overshoot); bounded.
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 61
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-15T00:14:21.2926539+03:00
- agent: CLAUDE
- purpose: Outcome price path (U-12), kept separate from entry assessment. High/low during the hold and in the 60 min after exit, and whether TP or SL would have been hit. Run SHOW TABLES first; if `candle` is absent, use market_price_event.
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 258
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I2-006B
- timestamp: 2026-10-15T00:14:24.1513843+03:00
- agent: CLAUDE
- purpose: Fallback for I2-006 using price events, if no candle table exists.
- SQL file: DBQ-CLAUDE-I2-006B-CLAUDE.sql
- exit code: 0
- duration_ms: 2516
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-15T00:14:24.5282666+03:00
- agent: CLAUDE
- purpose: The EGLD lifecycle before 143 (F2-9) and all EGLD positions between 137 and 143.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-15T00:14:26.4861592+03:00
- agent: CLAUDE
- purpose: Activity check for the ~54 h gap (U-13): latest signal, price event, position open, and current open positions.
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 1619
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-10-15T00:14:26.9757334+03:00
- agent: CLAUDE
- purpose: Is F2-1 a pattern, not just these six? Outcome by entry-signal R:R bucket across all closed paper positions.
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 130
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-15T00:27:47.1335880+03:00
- agent: CLAUDE
- purpose: Confirm F3-1. Session/global time zone of the wrapper and epoch of a known instant (143 price observed at 19:40:01.028Z = 1791747601.028).
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 57
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-15T00:28:07.9464515+03:00
- agent: CLAUDE
- purpose: F3-3 scope. Across all symbols, how often the 5m signal is generated after the matching 1m signal (same close instant), and by how much.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 20282
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-15T00:28:26.8155903+03:00
- agent: CLAUDE
- purpose: Does analysis latency spike at 5m boundaries? 1m candle-close ? generated_at by boundary vs non-boundary.
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 18455
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-15T00:28:27.3325220+03:00
- agent: CLAUDE
- purpose: F3-3 effect. Every wallet BUY executed on a 1m signal at a 5m boundary, the same-instant 5m decision it could not see, and the outcome.
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 158
- approximate output lines: 85
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-15T00:28:37.2689350+03:00
- agent: CLAUDE
- purpose: How common price-update gaps are. Distribution of the gap between consecutive EGLD updates during 143's hold.
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 427
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I3-008
- timestamp: 2026-10-15T00:28:38.7787141+03:00
- agent: CLAUDE
- purpose: F3-7 outcome order (U-16). First 1m candle after exit reaching TP vs SL (240 min bound), plus the ACE high discrepancy.
- SQL file: DBQ-CLAUDE-I3-008-CLAUDE.sql
- exit code: 0
- duration_ms: 313
- approximate output lines: 25
- password: never logged

