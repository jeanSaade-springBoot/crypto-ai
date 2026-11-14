
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-19T12:18:56.7727444+03:00
- agent: CLAUDE
- purpose: Check that the frozen IDs meet the selection rule and get the core lifecycle for each trade.
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 116
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-001b
- timestamp: 2026-10-19T12:18:57.8621255+03:00
- agent: CLAUDE
- purpose: Check how the snapshot drift affects the selection (read-only comparison only; no substitution).
- SQL file: DBQ-CLAUDE-I1-001b-CLAUDE.sql
- exit code: 0
- duration_ms: 87
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-19T12:18:58.9754563+03:00
- agent: CLAUDE
- purpose: Get the entry and exit reasons and the trigger text.
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 106
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-19T12:19:00.1301100+03:00
- agent: CLAUDE
- purpose: Get the entry and exit signal decision chain, freshness, signal mode and data quality.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 122
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-19T12:19:01.1846466+03:00
- agent: CLAUDE
- purpose: Get the authoritative close trigger.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 82
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-19T12:19:02.2135950+03:00
- agent: CLAUDE
- purpose: Get the managed-position state (SL/TP, highest price, profit-lock and near-TP state).
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 90
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-19T12:19:03.9524307+03:00
- agent: CLAUDE
- purpose: Get the executed wallet fills (entry, scale-in, harvest, exit) for P&L and fill-latency checks.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 305
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-19T12:19:05.5979036+03:00
- agent: CLAUDE
- purpose: Get the management event timeline (SL/TP moves, profit lock, harvest) for each position.
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 120
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-19T12:19:06.8200663+03:00
- agent: CLAUDE
- purpose: Read the actual `market_price_event` schema before using its status or source columns.
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 115
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-10-19T12:19:09.2665885+03:00
- agent: CLAUDE
- purpose: Get the price-path extremes during each holding window, to compare adverse and favourable moves with SL/TP (outcome analysis only).
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 1400
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-19T12:31:06.1852678+03:00
- agent: CLAUDE
- purpose: Get the exact price path for 143, to tell a price gap apart from a held profit-lock breach (U-I2-01).
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 199
- approximate output lines: 61
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-19T12:31:07.3985213+03:00
- agent: CLAUDE
- purpose: Get the EGLD 1m/5m/1h signal states during 143, to see which profit-lock hold/exit branch applied.
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 138
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-19T12:32:39.7723592+03:00
- agent: CLAUDE
- purpose: Get the timeframe context at each exit (audit source signal plus the latest 5m and 1h at or before close) (U-I2-02).
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 91341
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-19T12:32:42.1756574+03:00
- agent: CLAUDE
- purpose: Get the last 3 price events at or before each wallet SELL, to check the exit price and timing against the ticks.
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 1470
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-19T12:32:43.3649332+03:00
- agent: CLAUDE
- purpose: Get the base rate across all production trades: R/R bucket of the entry signal vs close reason and outcome (U-I2-04).
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 297
- approximate output lines: 33
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-19T12:32:44.3756014+03:00
- agent: CLAUDE
- purpose: Get daily position activity since 2026-10-10, to see whether trading stopped after the last loss (U-I2-03).
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 93
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-19T12:32:45.3937244+03:00
- agent: CLAUDE
- purpose: Get the full managed-position row for 143 (rebase timestamps, near-TP streak), to check the lock state history.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 87
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-19T12:32:46.5812720+03:00
- agent: CLAUDE
- purpose: Find any audit tables for decisions or BUY evaluations, for the entry-route and entry-quality 70-vs-60 checks (U-I2-05/06).
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 114
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-10-19T12:33:13.2957751+03:00
- agent: CLAUDE
- purpose: Get the share of fresh, executable 1m signals with R/R below 1 since 2026-10-10 (how widespread F-I2-04 is).
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 25615
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-19T12:57:04.0807036+03:00
- agent: CLAUDE
- purpose: Per-event lag between Binance observation and database storage around each exit and the 143 gap (U-I3-01/02).
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 141
- approximate output lines: 39
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-19T12:57:23.2134392+03:00
- agent: CLAUDE
- purpose: Price-update coverage across all symbols in each gap window, in 2 s buckets. Tells a global WebSocket stall apart from quiet trading in one symbol (U-I3-02).
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 18114
- approximate output lines: 77
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-19T12:57:24.2206468+03:00
- agent: CLAUDE
- purpose: Stored ingestion stage timings (=1000 ms) and protection incidents in the exit windows (U-I3-02/03).
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 135
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-19T12:57:25.3521084+03:00
- agent: CLAUDE
- purpose: 1m candles around each exit (OHLC, trade count, ingestion source). Tests whether trading happened during the gaps and whether the 22:43 EGLD drop is real (U-I3-01/02).
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 161
- approximate output lines: 20
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-19T12:57:26.3645642+03:00
- agent: CLAUDE
- purpose: Commit times (`created_at`) of the exit-context signals, to measure when each became visible to protection and check Replay/Production visibility (U-I3-04).
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 99
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-19T12:57:27.3542499+03:00
- agent: CLAUDE
- purpose: Wallet trades on any symbol during the gap windows, to see whether another symbol's buy or sale coincided with an ingestion stall (F-I3-04).
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 97
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I3-007
- timestamp: 2026-10-19T12:57:28.4208972+03:00
- agent: CLAUDE
- purpose: Check that the database server and session clocks are UTC before relying on `created_at` lag values.
- SQL file: DBQ-CLAUDE-I3-007-CLAUDE.sql
- exit code: 0
- duration_ms: 91
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I3-008
- timestamp: 2026-10-19T12:57:44.3757809+03:00
- agent: CLAUDE
- purpose: Hourly ingestion-lag profile across the full study period, to tell whether stalls are routine or rare.
- SQL file: DBQ-CLAUDE-I3-008-CLAUDE.sql
- exit code: 0
- duration_ms: 15055
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-19T13:16:57.9993895+03:00
- agent: CLAUDE
- purpose: Directly prove how the session displays times (F-I4-01). UNIX_TIMESTAMP does not depend on the session time zone. Expected epochs: 1791747815.021 (event 1789409) and 1791765004.319 (event 1862211).
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 123
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-19T13:17:06.1394065+03:00
- agent: CLAUDE
- purpose: Find the stall of up to 15.2 s in 10-11 19:00–20:00 UTC (U-I4-01). The literals are written in KSA because this harness's session is SYSTEM (KSA); epoch_s is included so the output checks itself.
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 7074
- approximate output lines: 86
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-19T13:17:07.0966252+03:00
- agent: CLAUDE
- purpose: Stored slow stages (=1 s) and protection incidents in that hour, to find the cause of the stall (U-I4-01).
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 107
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-19T13:17:08.3763432+03:00
- agent: CLAUDE
- purpose: How often each entry route admits a risk/reward below 1, and the outcomes (U-I4-03, F-I4-05).
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 339
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-19T13:17:09.4809942+03:00
- agent: CLAUDE
- purpose: The wallet sale for 162's signal-path exit (price and freshness fields), to quantify F-I4-07. Literals are in KSA.
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 99
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-19T13:40:29.3080020+03:00
- agent: CLAUDE
- purpose: Rebuild the expansion reference, recent high and rejection count for each entry: the 1m signal prices in the 30 minutes before each entry signal (U-I5-01).
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 287
- approximate output lines: 182
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-19T13:40:30.8133741+03:00
- agent: CLAUDE
- purpose: Opportunity rows (start time = age, anchor and best price = reference) for each entry symbol in the 3 hours before entry (U-I5-01, F-I5-08). These show the latest state, not the state at entry.
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 559
- approximate output lines: 40
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-19T13:40:31.9716864+03:00
- agent: CLAUDE
- purpose: Compare the ATR of the latest 5m signal at or before each entry with the 1m entry ATR (143's 60 vs 70, and 158's wakeup route) (F-I5-08).
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 192
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-19T13:40:33.0439606+03:00
- agent: CLAUDE
- purpose: How score bucket and entry route relate to outcome across all closed and stopped trades (U-I5-02).
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 139
- approximate output lines: 28
- password: never logged


## DBQ-CLAUDE-I5-005
- timestamp: 2026-10-19T13:40:34.0523002+03:00
- agent: CLAUDE
- purpose: Base rates for FIX-11G vs FIX-112B, and for BUYs by regime, including downtrend (U-I5-03).
- SQL file: DBQ-CLAUDE-I5-005-CLAUDE.sql
- exit code: 0
- duration_ms: 114
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I5-006
- timestamp: 2026-10-19T13:40:35.2093862+03:00
- agent: CLAUDE
- purpose: How often the planned target sat below the 0.2% round-trip fee at entry, and the outcomes (U-I5-04, F-I5-06).
- SQL file: DBQ-CLAUDE-I5-006-CLAUDE.sql
- exit code: 0
- duration_ms: 118
- approximate output lines: 3
- password: never logged

