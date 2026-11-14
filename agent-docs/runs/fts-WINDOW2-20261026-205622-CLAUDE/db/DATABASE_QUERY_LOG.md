
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-26T21:12:30.1439305+03:00
- agent: CLAUDE
- purpose: Core facts for the six frozen positions (lifecycle, prices, SL/TP, close reason, linked signals). Long text is truncated.
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 88
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-26T21:12:31.1377648+03:00
- agent: CLAUDE
- purpose: Check for selection bias and the snapshot boundary. Lists all losing closed positions, including STOPPED, from the oldest frozen trade's close up to 2026-10-26 18:10 UTC.
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 87
- approximate output lines: 27
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-26T21:12:32.1722380+03:00
- agent: CLAUDE
- purpose: Actual close trigger for each position, from the exit audit.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 98
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-26T21:12:33.1997721+03:00
- agent: CLAUDE
- purpose: Read the live schema before choosing detailed columns.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 110
- approximate output lines: 214
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-26T21:13:14.0186199+03:00
- agent: CLAUDE
- purpose: Entry and exit signal facts, using base columns only.
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 39967
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-26T21:13:15.0687081+03:00
- agent: CLAUDE
- purpose: Wallet executions that fall inside each position's lifetime (for paper-vs-wallet match, fees and partial harvests).
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 97
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-26T21:13:16.1401238+03:00
- agent: CLAUDE
- purpose: The managed position for each trade (SL/TP actually in force, profit-lock and near-TP state).
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 129
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-26T21:20:24.6223201+03:00
- agent: CLAUDE
- purpose: Entry and exit signals: candle identity, freshness, data quality, ATR plan, higher-timeframe agreement and final-gate fields (U-8, F-3, F-5).
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 92
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-26T21:20:25.5842457+03:00
- agent: CLAUDE
- purpose: Every stop-loss, target, profit-lock and near-target change on the six managed positions (U-7, F-7).
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 92
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-26T21:20:26.5465494+03:00
- agent: CLAUDE
- purpose: Every live price observation during trade 143's life, to tell a missed lock exit from a price jump (U-6). Bounded to 3.5 minutes.
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 93
- approximate output lines: 61
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-26T21:20:28.6776548+03:00
- agent: CLAUDE
- purpose: Price-observation summary for each of the six trades (count, sources, min/max, first and last), to check the exit price against observed prices.
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 1309
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-26T21:20:33.1460471+03:00
- agent: CLAUDE
- purpose: 5m and 1h signals for each symbol from 2 hours before entry to exit, to judge whether higher-timeframe context supported the entry and confirmed the exit (U-8).
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 3582
- approximate output lines: 187
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-26T21:20:34.0844699+03:00
- agent: CLAUDE
- purpose: Activity from 2026-10-10 to the snapshot: positions per day, latest signal, latest wallet trade (U-9, F-0).
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 95
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-26T21:20:35.0715181+03:00
- agent: CLAUDE
- purpose: How each entry signal was claimed and processed, and the wallet-side execution route.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 105
- approximate output lines: 31
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-26T21:20:36.0397257+03:00
- agent: CLAUDE
- purpose: Check whether the stop-wider-than-target pattern holds across all positions, 2026-10-11 to 2026-10-15 (U-10, F-5).
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 98
- approximate output lines: 95
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-26T21:33:01.3582590+03:00
- agent: CLAUDE
- purpose: Price observations, all statuses, from the last exit-signal candle close to just after each live exit, to test F-14 (U-17).
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 1419
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-26T21:33:02.3376457+03:00
- agent: CLAUDE
- purpose: `generated_at` and `created_at` for the 5m/1h signals that sit on the entry and exit boundaries (F-12, F-13, F-14; U-13).
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 96
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-26T21:33:03.2658677+03:00
- agent: CLAUDE
- purpose: Processing timing for the boundary 5m signals: did 165415 trigger the wake-up, and did 159407 run after the 143 entry?
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 86
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-26T21:33:04.2239172+03:00
- agent: CLAUDE
- purpose: Wallet execution timing and price lineage for all 12 rows (F-19, FIX-056 check).
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 78
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-26T21:33:05.1844842+03:00
- agent: CLAUDE
- purpose: Why `original_decision` ? `decision` on the 182 5m signals and 162 #167746 (F-16, U-14). Schema-safe; long text is truncated.
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 76
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-26T21:33:06.2083037+03:00
- agent: CLAUDE
- purpose: 1m candles around the trade 143 drop, to cross-check the external trade data (F-11).
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 103
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-26T21:44:45.6121171+03:00
- agent: CLAUDE
- purpose: Entry route and profile code recorded for each wallet BUY (U-18, F-22, F-25).
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 81
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-26T21:45:26.3965509+03:00
- agent: CLAUDE
- purpose: The higher-timeframe context that neutralised the ACE 5m SELLs, and the 1m entry signal's view of it (F-22, U-19).
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 39878
- approximate output lines: 31
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-26T21:45:28.3650873+03:00
- agent: CLAUDE
- purpose: Index coverage for the portfolio-revaluation candle query, asset count, and whether any 15m signals exist (F-22, F-23; U-19, U-20). Read-only metadata and EXPLAIN.
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 1156
- approximate output lines: 20
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-26T21:45:29.3533363+03:00
- agent: CLAUDE
- purpose: Snapshot timestamps around each live exit, to split the 1.2–3.2 s gap between wallet SELL and paper close (F-23, U-20).
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 112
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-26T21:45:30.3242092+03:00
- agent: CLAUDE
- purpose: ACE 1m decisions and prices while trade 182 was open, to test whether exits were held back by the neutralised 5m (U-23). Use only for outcome, not entry judgement.
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 98
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I4-006
- timestamp: 2026-10-26T21:45:31.3625321+03:00
- agent: CLAUDE
- purpose: Trade activity in the candles that contain the price-update gaps (U-22).
- SQL file: DBQ-CLAUDE-I4-006-CLAUDE.sql
- exit code: 0
- duration_ms: 95
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-26T21:56:39.5127200+03:00
- agent: CLAUDE
- purpose: Outcome by entry route and higher-timeframe authority code for all closed positions (F-29, F-32, F-33, U-26).
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 121
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-26T21:56:40.5006134+03:00
- agent: CLAUDE
- purpose: Outcome by planned reward:risk band and by stored Entry Quality band, with notional for fee estimates (F-27, F-28, U-26).
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 117
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-26T21:56:41.4442478+03:00
- agent: CLAUDE
- purpose: Why the RANGE 5m BUYs had final_entry_allowed=0, and liquidity/authority fields for the six entry signals (F-28, F-32, U-25).
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 89
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-26T21:56:47.1520182+03:00
- agent: CLAUDE
- purpose: Sentiment availability by symbol around trade 137's entry and across 10-11/10-12, to test the F-30 counterfactual (U-24).
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 4798
- approximate output lines: 42
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-26T22:04:22.0958394+03:00
- agent: CLAUDE
- purpose: Realized versus planned payoff by status and exit reason, to test whether winners are cut short (F-37, U-29). Note: stop and target may have been moved after entry.
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 86
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-26T22:04:23.1235368+03:00
- agent: CLAUDE
- purpose: How far STOPPED losses overshoot their stop (F-41, U-30).
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 93
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-26T22:04:25.3161252+03:00
- agent: CLAUDE
- purpose: Outcome by whether the entry signal had sentiment/fundamentals, the hourly sentiment-availability timeline, and a split of NULL EQ out of eq85+ (F-37, F-39, U-31).
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 1272
- approximate output lines: 57
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-10-26T22:04:28.8112164+03:00
- agent: CLAUDE
- purpose: How often F-38 occurs, and its outcome. For each 1m-signal position, take the latest 5m signal at or before the entry signal (10-minute bound) and flag a hidden bearish 5m or a blocked bullish 5m (U-32).
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 2634
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I6-005
- timestamp: 2026-10-26T22:04:29.7697609+03:00
- agent: CLAUDE
- purpose: Outcome by the entry signal's market regime and strategy decision, to test whether long entries in downtrend regimes lose more (F-31, U-33).
- SQL file: DBQ-CLAUDE-I6-005-CLAUDE.sql
- exit code: 0
- duration_ms: 86
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-10-26T22:17:42.9638982+03:00
- agent: CLAUDE
- purpose: Outcome only (not used to judge the entries). Price path for 120 minutes after each frozen exit, and whether the target or the stop was touched first (U-34).
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 3684
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-10-26T22:17:52.9444062+03:00
- agent: CLAUDE
- purpose: Outcome only. For every `CLOSED` exit other than `TAKE_PROFIT`: after the exit, which was touched first within 120 minutes, the target or the stop? (U-35; tests whether profit locks help or hurt.)
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 9109
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-10-26T22:17:53.8793354+03:00
- agent: CLAUDE
- purpose: Stop overshoot cost per symbol, to test the price-step hypothesis (F-42, U-36).
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 81
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I7-004
- timestamp: 2026-10-26T22:17:57.5555552+03:00
- agent: CLAUDE
- purpose: List the positions flagged in I6-004 and confirm whether 172 and 182 are among them (U-37).
- SQL file: DBQ-CLAUDE-I7-004-CLAUDE.sql
- exit code: 0
- duration_ms: 2807
- approximate output lines: 12
- password: never logged

