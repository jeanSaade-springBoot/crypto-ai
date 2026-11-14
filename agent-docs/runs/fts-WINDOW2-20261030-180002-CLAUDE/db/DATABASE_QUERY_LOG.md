
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-30T18:05:04.8320739+03:00
- agent: CLAUDE
- purpose: Check the time zone, re-run the frozen selection with UTC and KSA times, and test I1-F01/F02 (newest close of any status, counts after 2026-10-12, and the size of the STOPPED losses).
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-30T18:05:05.1471637+03:00
- agent: CLAUDE
- purpose: Get the lifecycle row for each frozen trade with UTC/KSA times, holding time, and stop/target distances. Reason fields are cut to 800 characters.
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-30T18:05:05.4592391+03:00
- agent: CLAUDE
- purpose: Get the entry and exit signals for each trade: candle, decision chain, data-quality flag, plan, and the delay from candle open to signal.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-30T18:05:05.7588930+03:00
- agent: CLAUDE
- purpose: Find the recorded close trigger for each trade.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-30T18:05:06.0584795+03:00
- agent: CLAUDE
- purpose: Get the wallet fills within each position's open/close window (±120 s) to rebuild quantity, prices, gross P&L and fees, and to check fill price against decision price and price age.
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-30T18:05:06.3705998+03:00
- agent: CLAUDE
- purpose: Test I1-F01 by checking whether the trader kept running after 2026-10-12. Shows the newest paper position of any status and the newest saved trade signal, with UTC times.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 16
- password: never logged

