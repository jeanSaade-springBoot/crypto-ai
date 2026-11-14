
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-30T15:29:22.1909906+03:00
- agent: CLAUDE
- purpose: Full lifecycle rows for the six frozen positions (SL/TP, qty, entry/exit price, reasons, exit signal).
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-30T15:29:22.5135187+03:00
- agent: CLAUDE
- purpose: Actual schema of the related tables before deeper queries (AGENTS.md requires reading the schema first).
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 305
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-30T15:29:22.8403624+03:00
- agent: CLAUDE
- purpose: Entry and exit trade_signal rows for each position: decision chain, freshness inputs, and risk inputs.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 51
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-30T15:29:23.1513682+03:00
- agent: CLAUDE
- purpose: Wallet fills during each position's lifetime (fill vs. decision price, price-observation age, partial harvests, fees).
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 41
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-30T15:29:23.4494212+03:00
- agent: CLAUDE
- purpose: Managed-position protection state (highest price, profit lock, near-TP, entry stage/quality).
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-30T15:29:23.7593058+03:00
- agent: CLAUDE
- purpose: Authoritative close trigger per position.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-30T15:29:46.6287387+03:00
- agent: CLAUDE
- purpose: 1m/5m/1h signal sequence from 20 min before entry to exit. Tests H-01 (stale 5m used at the 5m boundary) and the multi-timeframe state at exit.
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 22583
- approximate output lines: 312
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-30T15:29:47.1098600+03:00
- agent: CLAUDE
- purpose: Tick-level price evidence for the two shortest EGLDUSDT trades (143 profit-lock exit at a loss; 137), with delivery status.
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 156
- approximate output lines: 106
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-10-30T15:29:47.4484821+03:00
- agent: CLAUDE
- purpose: TP-extension and protection management events for the six managed positions (trade 143 rebase-floor hypothesis).
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I1-010
- timestamp: 2026-10-30T15:29:48.3135240+03:00
- agent: CLAUDE
- purpose: Check the selection window (F-04): latest activity per paper status and the latest signal time.
- SQL file: DBQ-CLAUDE-I1-010-CLAUDE.sql
- exit code: 0
- duration_ms: 560
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-30T15:38:16.0405429+03:00
- agent: CLAUDE
- purpose: Show exactly which higher-timeframe signal each entry's confluence used, plus the ATR and executability fields (decisive for I2-F02).
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 47
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-30T15:38:16.3778596+03:00
- agent: CLAUDE
- purpose: Get the executability flags of the 5m candidates at each 5-minute-boundary entry (old vs. new 5m signal).
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 38
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-30T15:38:17.0890007+03:00
- agent: CLAUDE
- purpose: Get the execution-opportunity decisions recorded at each entry (entry path, the 5m/1h decisions used, explanation).
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 432
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-30T15:38:17.3940539+03:00
- agent: CLAUDE
- purpose: 1m EGLDUSDT candles around trade 143's gap exit (was the drop inside one candle, and were the candles finalised normally?).
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-30T15:38:23.2192042+03:00
- agent: CLAUDE
- purpose: Check whether price-event ingestion paused for all symbols between 22:43:27 and 22:43:35 UTC (U2-04).
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 5536
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-30T15:38:23.6631006+03:00
- agent: CLAUDE
- purpose: How often the 5-minute-boundary race (5m generated after the 1m that was executed) happens across all executed BUY entries (U2-05).
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 154
- approximate output lines: 86
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-30T15:38:24.0085870+03:00
- agent: CLAUDE
- purpose: Outcome by planned reward/risk band across all closed and stopped paper positions (tests I2-F01 beyond the six trades).
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-30T15:38:24.3337319+03:00
- agent: CLAUDE
- purpose: The EGLDUSDT position just before trade 143 (wallet position 171, profit-lock exit 22 s before re-entry) and its entry signal.
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-30T15:53:09.4382844+03:00
- agent: CLAUDE
- purpose: Prove the display time zone of the result files (I3-F01). Expected if the hypothesis holds: session zone SYSTEM or +03:00, and gen_epoch = 1791747601.633945 (UTC 2026-10-11 19:40:01.634).
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 24
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-30T15:53:09.7495088+03:00
- agent: CLAUDE
- purpose: Generated vs. saved order of the 1m entry signals and their boundary 5m signals (I3-F02/F03 visibility timing). Also confirms the type of trade_signal.created_at.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-30T15:53:10.0795976+03:00
- agent: CLAUDE
- purpose: When the boundary 5m candles were finalised, compared with the 1m signal start (was the closed 5m candle in the DB before the 1m analysis?).
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 54
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-30T15:53:10.4247622+03:00
- agent: CLAUDE
- purpose: Population impact of the boundary race (U3-05). For entries not triggered by a fresh 5m where the just-closed 5m was generated after the 1m, compare the previous 5m with the new 5m and show the position outcome.
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 71
- approximate output lines: 40
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-30T15:53:10.7409285+03:00
- agent: CLAUDE
- purpose: Which build/migrations were active around the trades (U3-03; current-tree code citations may differ from what ran).
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 39
- approximate output lines: 20
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-30T16:06:24.3800036+03:00
- agent: CLAUDE
- purpose: Prove which 5m/1h signals FIX-154 actually used per entry, and that FIX-154 ran on 10-11/12 (I4-F08, U4-03).
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 58
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-30T16:06:24.7001536+03:00
- agent: CLAUDE
- purpose: EGLD 1m evidence window for trade 143 (I4-F02). Which signals generated before the 22:39:41.052 exit fed evidence score 8?
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 51
- approximate output lines: 32
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-30T16:06:25.2201761+03:00
- agent: CLAUDE
- purpose: Opportunity lifecycle around position 171 exit and position 143 entry (I4-F02/F03). Was #36481 ever COMPLETED?
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 241
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-30T16:06:25.5752262+03:00
- agent: CLAUDE
- purpose: FIX-122 audit for 143 and 162 entries (expect NOT_APPLICABLE status, i.e. no stop boundary) and position 171 entry/exit legs.
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 63
- approximate output lines: 21
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-30T16:06:25.8920621+03:00
- agent: CLAUDE
- purpose: DOGE 1m evidence window for trade 162 (I4-F06). Locate the single BUY and any DOGE exit in the 30-minute window.
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 54
- approximate output lines: 33
- password: never logged


## DBQ-CLAUDE-I4-006
- timestamp: 2026-10-30T16:06:26.2071766+03:00
- agent: CLAUDE
- purpose: Risk geometry for trade 143 (U4-07) and the 5m/1m ATR basis behind the Entry Quality mismatch (I4-F02 side note).
- SQL file: DBQ-CLAUDE-I4-006-CLAUDE.sql
- exit code: 0
- duration_ms: 25
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-30T16:15:28.6699744+03:00
- agent: CLAUDE
- purpose: Anchor and best entry prices and lifecycle fields of the six entry opportunities. Verifies the Entry Quality reference low (I5-F01, U5-01).
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-30T16:15:28.9855638+03:00
- agent: CLAUDE
- purpose: Range-location decision path for the vetoed and passed BUYs around trades 137 and 172 (I5-F06).
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-30T16:15:29.3172636+03:00
- agent: CLAUDE
- purpose: Liquidity, regime and ATR inputs to the Entry Quality score for the six entry signals and the 5m trigger of trade 158 (I5-F01, I5-F07).
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 25
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-30T16:15:29.6138397+03:00
- agent: CLAUDE
- purpose: Existing Replay evaluations, if any, of the entry candles for trades 162, 143 and 182 (I5-F04 parity; no Replay is run).
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 0
- password: never logged


## DBQ-CLAUDE-I5-005
- timestamp: 2026-10-30T16:15:29.9399193+03:00
- agent: CLAUDE
- purpose: Population outcome by fill-time Entry Quality band (I5-F02/F03; U5-04).
- SQL file: DBQ-CLAUDE-I5-005-CLAUDE.sql
- exit code: 0
- duration_ms: 57
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I5-006
- timestamp: 2026-10-30T16:15:30.4587553+03:00
- agent: CLAUDE
- purpose: Executed opportunities whose age sat within 3 s of an Entry Quality age bucket boundary (20, 40 or 60 min), to measure how often jitter decides the bucket (I5-F04).
- SQL file: DBQ-CLAUDE-I5-006-CLAUDE.sql
- exit code: 0
- duration_ms: 241
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-30T16:23:22.7631725+03:00
- agent: CLAUDE
- purpose: Population outcome by planned signal reward/risk bucket, with average ATR% (U6-01, U6-04; tests I6-F01 against I6-F02). Schema check: confirm `trade_signal.risk_reward_ratio` first.
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 46
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-30T16:23:23.1040291+03:00
- agent: CLAUDE
- purpose: Population outcome by entry route (`wallet_managed_position.entry_stage`) (U6-03; ranks root causes 3 and 4).
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-30T16:23:23.4246406+03:00
- agent: CLAUDE
- purpose: Diagnose the empty I5-004 file and read the Replay table schemas before any Replay-parity query (U6-02).
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 199
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-10-30T16:23:23.7267715+03:00
- agent: CLAUDE
- purpose: Confirm the I6-F01 mechanism on the stored signal plan: stop distance %, target distance % and persisted reward/risk for the six entry signals.
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-10-30T16:29:11.0449923+03:00
- agent: CLAUDE
- purpose: Trade size and fee impact by reward/risk band (U7-01). First reads the paper_position columns (schema check), then computes size assuming entry_price × quantity. If those columns are absent, run part A only.
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 43
- approximate output lines: 22
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-10-30T16:29:11.3423250+03:00
- agent: CLAUDE
- purpose: Entry Quality predictiveness within each reward/risk band (U7-02, I7-F02).
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-10-30T16:29:11.6952748+03:00
- agent: CLAUDE
- purpose: Replay coverage for trade 162's opportunity moment (DOGE, 2026-10-12 00:35–01:05 UTC = 03:35–04:05 KSA) (U7-03). The literals are KSA because the session time zone is +03:00 for TIMESTAMP columns (I4-F01). The UNIX_TIMESTAMP columns let you verify that.
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 77
- approximate output lines: 0
- password: never logged

