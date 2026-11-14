
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-19T18:03:59.2917411+03:00
- agent: CLAUDE
- purpose: Full paper_position rows for the frozen trades (entry/exit reasons, SL/TP, quantity, exit signal).
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-19T18:03:59.6111979+03:00
- agent: CLAUDE
- purpose: Entry and exit signal evidence (decision chain, mode, quality, freshness, MTF/BTC context, R:R).
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-19T18:03:59.9094331+03:00
- agent: CLAUDE
- purpose: Wallet executions (fills, fill authority time, decision price, P&L, reason) for each trade lifecycle.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 26
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-19T18:04:02.0923630+03:00
- agent: CLAUDE
- purpose: 5m/1h signals visible to PositionExitPolicy around each hold (age, mode, decision) — tests F-4.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 1894
- approximate output lines: 187
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-19T18:04:02.4925995+03:00
- agent: CLAUDE
- purpose: 1m signal sequence from 10 min before entry to close (entry consolidation and exit 1m decision).
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 113
- approximate output lines: 200
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-19T18:04:02.9036976+03:00
- agent: CLAUDE
- purpose: Actual close trigger audit for each position.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-19T18:04:03.3981536+03:00
- agent: CLAUDE
- purpose: Schemas needed before managed-position / price-event / candle / work queries (U8).
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 98
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-19T18:04:03.7066627+03:00
- agent: CLAUDE
- purpose: Management events (TP extension, profit lock, near-TP) during each hold — resolves U4 for trade 143.
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-10-19T18:04:05.0345082+03:00
- agent: CLAUDE
- purpose: Price-path summary per hold (MFE/MAE inputs, event count) and price events in the 90 s before each close.
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 1054
- approximate output lines: 105
- password: never logged


## DBQ-CLAUDE-I1-010
- timestamp: 2026-10-19T18:04:05.3231306+03:00
- agent: CLAUDE
- purpose: Explain the 7-day gap before the snapshot (F-5 / U7).
- SQL file: DBQ-CLAUDE-I1-010-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-19T18:10:53.6452159+03:00
- agent: CLAUDE
- purpose: Prove the session time zone in this run (F-0) using values that do not depend on the session zone.
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-19T18:10:53.9697817+03:00
- agent: CLAUDE
- purpose: Final state of the managed positions for the six trades, plus the prior EGLD position 171: lock and near-TP state, highest price, entry stage (U5).
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-19T18:10:54.4578032+03:00
- agent: CLAUDE
- purpose: Closed 1m candles over each hold (OHLC-based maximum favourable/adverse excursion) and EGLD 22:36–22:45 KSA, to check trade 143's 8 s gap and 171's exit (U3).
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 204
- approximate output lines: 170
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-19T18:10:59.0335917+03:00
- agent: CLAUDE
- purpose: Was the price stream active for all symbols during trade 143's gap (22:43:27–22:43:35 KSA), and which EGLD events were delivered or rejected? (U3)
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 4306
- approximate output lines: 32
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-19T18:10:59.3433207+03:00
- agent: CLAUDE
- purpose: Why the 5m SELL signals were downgraded to NEUTRAL or WATCH (trade 182's masked 5m; trade 162's 167746; trade 143's 5m authority signals) (U2, F-3, F-4).
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-19T18:10:59.7069110+03:00
- agent: CLAUDE
- purpose: Full, untruncated execution messages for the override-path entries (U4).
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-19T18:11:00.0033004+03:00
- agent: CLAUDE
- purpose: Prior EGLD position (wallet 171 / paper) and the profit-lock management events before trade 143's re-entry.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-19T18:11:00.4220751+03:00
- agent: CLAUDE
- purpose: Determine which build was deployed: Flyway history around 2026-10-10..13 and whether BUY-HTF assessment audit tables existed or had rows (U1).
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-10-19T18:11:00.8508600+03:00
- agent: CLAUDE
- purpose: Analysis and work timing for the six entry signals and the 162 exit signal (candle close ? work completion ? position).
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-010
- timestamp: 2026-10-19T18:11:01.4206554+03:00
- agent: CLAUDE
- purpose: Activity after the window. Were 1m LIVE signals still being produced from 2026-10-15 to the snapshot, and did any BUY get final_entry_allowed? (U7)
- SQL file: DBQ-CLAUDE-I2-010-CLAUDE.sql
- exit code: 0
- duration_ms: 311
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-19T18:24:42.0370919+03:00
- agent: CLAUDE
- purpose: Was the FIX-154 assessment live for these entries? Get the audit rows (band, effective and raw 5m/1h, final result) for each entry signal and for the 158 wakeup 5m signal.
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-19T18:24:42.3729618+03:00
- agent: CLAUDE
- purpose: Exact confluence branch that turned the ACEUSDT 5m STRONG_SELL signals into NEUTRAL (trade 182): strategy, trend scores, 1h context and explanation. Also the ACEUSDT 1h signals that drove the mask and later lifted it.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 44
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-19T18:24:54.0942766+03:00
- agent: CLAUDE
- purpose: Does a 1–3 s exit transaction stall price ingestion for other symbols? Per-event ingestion lag for all symbols around trade 143's exit transaction (22:43:35.0–36.3) and trade 137's (21:00:08.9–12.2).
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 11426
- approximate output lines: 63
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-19T18:24:54.4661066+03:00
- agent: CLAUDE
- purpose: Confirm that the wallet-to-paper lag goes on in-transaction bookkeeping: snapshot times relative to each wallet SELL and paper close.
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 39
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-19T18:24:59.0898066+03:00
- agent: CLAUDE
- purpose: Bound the runtime gaps (U7/U11): daily LIVE signal counts and the first and last signal per day, 2026-10-12 to 2026-10-14.
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 4341
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-19T18:24:59.4223024+03:00
- agent: CLAUDE
- purpose: Entry-price observation age for 137, 172 and 182 (U13), from the full execution messages and observed-at column.
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-19T18:30:44.7322426+03:00
- agent: CLAUDE
- purpose: U15. Get the plan, indexes and data size behind `currentPrice` (`WalletService.java:607`), which runs about 40 times inside every trading transaction.
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 76
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-19T18:30:52.5468187+03:00
- agent: CLAUDE
- purpose: U16. How often price ingestion stalls (lag > 500 ms) per minute during the live trading period of the six trades.
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 7536
- approximate output lines: 145
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-19T18:30:53.0563959+03:00
- agent: CLAUDE
- purpose: U14 / F4-1. For every executed BUY, check whether the 5m signal from the same candle-close boundary was stamped after the 1m execution signal, and compare its decision with the previous 5m.
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 210
- approximate output lines: 83
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-19T18:30:53.4424379+03:00
- agent: CLAUDE
- purpose: U17. Find existing Replay/regression tables and runs covering 2026-10-11/12, to check F4-1 and F4-6 parity without running a new Replay. This is a schema discovery step only.
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 77
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-19T18:43:31.6113066+03:00
- agent: CLAUDE
- purpose: U18 / F5-2 / F5-3. Entry Quality inputs for the entry signals and the relevant 5m signals (ATR basis, liquidity, regime, authority cap), plus the 30-minute 1m price window before each entry (reference low, recent high and when it printed).
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 177
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-19T18:43:32.5390613+03:00
- agent: CLAUDE
- purpose: U18. The opportunity anchor and best price that can override the 30-minute reference (`:898-926`), and the evidence counts, for each frozen entry.
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 631
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-19T18:43:32.9035358+03:00
- agent: CLAUDE
- purpose: F5-1. Does Entry Quality or R:R predict the outcome? Outcomes by entry-quality band and R:R band for all terminal positions from 2026-10-09 to the snapshot.
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 75
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I5-005
- timestamp: 2026-10-19T18:43:48.9488183+03:00
- agent: CLAUDE
- purpose: U19. Correlate the worst ingestion stalls with wallet transactions that were running at the same moment.
- SQL file: DBQ-CLAUDE-I5-005-CLAUDE.sql
- exit code: 0
- duration_ms: 15512
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I5-006
- timestamp: 2026-10-19T18:43:49.3476769+03:00
- agent: CLAUDE
- purpose: U17. Exact row counts behind the 0 estimates for the Replay tables.
- SQL file: DBQ-CLAUDE-I5-006-CLAUDE.sql
- exit code: 0
- duration_ms: 79
- approximate output lines: 1
- password: never logged

