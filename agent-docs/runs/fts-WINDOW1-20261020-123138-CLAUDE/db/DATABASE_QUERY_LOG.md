
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-20T15:02:54.9849031+03:00
- agent: CLAUDE
- purpose: F0, prove the wrapper session time zone and offset.
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-20T15:02:55.3553874+03:00
- agent: CLAUDE
- purpose: Re-confirm the frozen rows with true epoch times, prove the selection, check activity since trade 182 (F7).
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 19
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-20T15:02:55.6726159+03:00
- agent: CLAUDE
- purpose: Wallet executions, including the prior EGLD position 171 (F3 re-entry), with epoch times.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-20T15:02:56.0112489+03:00
- agent: CLAUDE
- purpose: Final managed-position protection state (highest price, lock, near-TP).
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-20T15:02:56.3879150+03:00
- agent: CLAUDE
- purpose: F3/U3, all price events in any status around the gap, the 1m candles for 143, and the prior EGLD paper row.
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-20T15:02:56.7467305+03:00
- agent: CLAUDE
- purpose: F4/U1, which stage downgraded the 5m SELL to NEUTRAL before trade 182's entry; also the entry signal's decision path.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-20T15:02:57.0868749+03:00
- agent: CLAUDE
- purpose: U2/U5, work-claim latency for the entry signals; the 5m candidates that could have been the authority for 143.
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-20T15:02:59.3461158+03:00
- agent: CLAUDE
- purpose: Protection coverage, delivery-status distribution, and the largest gap between consecutive observations per trade.
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 1967
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-20T15:08:53.2215040+03:00
- agent: CLAUDE
- purpose: Read the actual trade_signal schema before choosing the regime, strategy, trend and RSI columns for U1.
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 135
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-20T15:12:47.4259701+03:00
- agent: CLAUDE
- purpose: The 5m and 1h signals actually visible at each entry (by generated_at <= the entry signal's generated_at), their effective and original decisions, and their scores. Tests F1 and F3 across all six trades.
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 233453
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-20T15:12:48.6654639+03:00
- agent: CLAUDE
- purpose: U9. Gaps over 20 s for 172 and 182, with the price before and after each gap, plus the 1m candle trade counts in the same minutes.
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 330
- approximate output lines: 93
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-20T15:12:55.2364310+03:00
- agent: CLAUDE
- purpose: F7/U4. Last candle, price event and signal processing activity. Note: wrapper time literals and output are KSA.
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 6211
- approximate output lines: 19
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-20T15:12:55.5797503+03:00
- agent: CLAUDE
- purpose: F2 cross-check. Price events around the prior EGLD position 171's lock exit (lock 4.0255, exit 3.996), and its work row lineage. Also the 158 wake-up trigger lineage (F5).
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-20T15:22:13.0685362+03:00
- agent: CLAUDE
- purpose: T2. Signal generation latency after candle close, by interval and by whether the close falls on an hour boundary, for the trade window. Wrapper literals are KSA.
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 3853
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-20T15:22:20.6489099+03:00
- agent: CLAUDE
- purpose: T1/U11. How often a 5m signal for the same close boundary was generated after the 1m signal, across the population, and which wallet BUYs were exposed to that race.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 7161
- approximate output lines: 34
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-20T15:22:21.0403272+03:00
- agent: CLAUDE
- purpose: T4. Whether the lag between wallet execution and the paper row is systematic across all positions from 10-11 to 10-15.
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 71
- approximate output lines: 80
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-20T15:22:21.3876316+03:00
- agent: CLAUDE
- purpose: T3. The exit-signal timing for each trade, and the price event that triggered each exit made through the protection callback (137, 158, 182).
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-20T15:22:38.5687566+03:00
- agent: CLAUDE
- purpose: L4/U4. Whether every symbol stopped at the same moment on 10-17, the last price event overall with its epoch, and whether any work row is still pending.
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 16773
- approximate output lines: 28
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-20T15:30:15.2445259+03:00
- agent: CLAUDE
- purpose: R6/U6. Recent Flyway history with dates, and whether the FIX-154 audit table exists.
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 54
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-20T15:30:15.5872863+03:00
- agent: CLAUDE
- purpose: R3/U14. Validation code, size and explanation for each entry; 5m score inputs for 159285 versus 159407 to settle the remaining reversal-retracement doubt.
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-20T15:31:34.3898906+03:00
- agent: CLAUDE
- purpose: R2/U12. For exposed non-wake-up BUYs, the 5m actually used versus the later 5m for the same close, plus the position outcome.
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 78438
- approximate output lines: 21
- password: never logged

