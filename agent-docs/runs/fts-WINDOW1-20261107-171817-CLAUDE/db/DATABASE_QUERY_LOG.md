
## DBQ-CLAUDE-I1-001
- timestamp: 2026-11-07T17:24:02.7520415+03:00
- agent: CLAUDE
- purpose: Read the actual schema before choosing columns (AGENTS.md trade-investigation rule).
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 111
- approximate output lines: 147
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-11-07T17:24:03.7891596+03:00
- agent: CLAUDE
- purpose: Core lifecycle of the 6 frozen positions (identity, prices, SL/TP, P&L, close reason, duration). Long text is truncated.
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 102
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-11-07T17:24:04.7812570+03:00
- agent: CLAUDE
- purpose: The recorded close trigger for each position, separate from the context signal.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 96
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-11-07T17:24:05.8346186+03:00
- agent: CLAUDE
- purpose: Entry and exit signals: decision chain, freshness, data quality, BTC/MTF context, and risk/reward available at decision time. Large JSON is excluded.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 102
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-11-07T17:24:06.9542625+03:00
- agent: CLAUDE
- purpose: Wallet executions inside each position's lifetime (entry, adds, partial harvests, exit), for fill authority, fill delay, and P&L recomputation.
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 101
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-11-07T17:24:08.0629433+03:00
- agent: CLAUDE
- purpose: Managed-position state (SL/TP changes, highest price, profit lock, near-TP harvest) if the row still exists, plus management events during each position's lifetime.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 118
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-11-07T17:24:10.3424402+03:00
- agent: CLAUDE
- purpose: Summary of price observations during each position's lifetime (count, range, first/last, biggest gap between observations) to find protection gaps and the max favourable/adverse move. Per-row detail comes in iteration 2.
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 1348
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-11-07T17:24:11.3342314+03:00
- agent: CLAUDE
- purpose: Freshness and admission of each entry signal: the persisted 1m candle and its provenance/closed flag, compared with the signal time.
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 103
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-11-07T17:32:37.0761672+03:00
- agent: CLAUDE
- purpose: Confirm the trade_signal columns used in the queries below.
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 126
- approximate output lines: 135
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-11-07T17:32:38.0896484+03:00
- agent: CLAUDE
- purpose: The exit signals behind each close (U-I2-02).
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 96
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-11-07T17:32:39.7923400+03:00
- agent: CLAUDE
- purpose: Trade 143: every price event around the give-back, with delivery status (U-I2-01). Also the delivery-status breakdown for all 6 holding periods.
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 880
- approximate output lines: 76
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-11-07T17:32:41.6485142+03:00
- agent: CLAUDE
- purpose: Price path in the 60 minutes after each exit, to judge whether exiting was right (U-I2-03).
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 1017
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-11-07T17:32:42.6946351+03:00
- agent: CLAUDE
- purpose: The latest 5m and 1h signals at entry and at exit, for each position (U-I2-02, entry context).
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 198
- approximate output lines: 24
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-11-07T17:32:43.6445986+03:00
- agent: CLAUDE
- purpose: The full explanation text for each entry decision, plus the earlier EGLD exit (trade 357) before 143 (U-I2-04).
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 81
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-11-07T17:32:44.6057607+03:00
- agent: CLAUDE
- purpose: All management events for these managed positions, looked up by wallet position ID (the I1 query matched by symbol and time). Includes position 171, the one before 143.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 81
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-11-07T17:32:45.6666696+03:00
- agent: CLAUDE
- purpose: Test whether F-I2-01 is systemic: losses and P&L by reward:risk band and by close reason, across all Production positions (U-I2-05, F-I2-14).
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 201
- approximate output lines: 19
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-11-07T17:32:46.6220597+03:00
- agent: CLAUDE
- purpose: Work admission and analysis outcome for the entry and exit signals (U-I2-06), and activity after 2026-10-12 (F-I2-15).
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 109
- approximate output lines: 18
- password: never logged

