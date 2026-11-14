
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-25T18:02:53.9933825+03:00
- agent: CLAUDE
- purpose: Get the actual live schema of the tables involved before choosing columns (AGENTS.md rule).
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 59
- approximate output lines: 325
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-25T18:02:54.4111938+03:00
- agent: CLAUDE
- purpose: Core lifecycle of the six frozen positions, with lifetime in seconds.
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 24
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-25T18:02:54.7886519+03:00
- agent: CLAUDE
- purpose: Entry and exit reason text for the six positions, length-bounded.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 38
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-25T18:02:55.0946319+03:00
- agent: CLAUDE
- purpose: Entry and exit signals behind each position (baseline columns only; extended columns will be requested after I1-001).
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-25T18:02:55.4188027+03:00
- agent: CLAUDE
- purpose: The close trigger that was actually recorded for each position.
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-25T18:02:55.7566394+03:00
- agent: CLAUDE
- purpose: Wallet fills around each position's lifetime (±2 min), used for P&L and fee reconstruction. The match is by symbol and time window until signal/execution_key confirms it.
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-25T18:02:57.2320474+03:00
- agent: CLAUDE
- purpose: How much price-event evidence each position has over its lifetime, and the price range (an aggregate only, before pulling individual rows).
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 1119
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-25T18:02:57.5793058+03:00
- agent: CLAUDE
- purpose: Managed-position state and management events (SL/TP moves, profit lock) for each position's symbol and lifetime.
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-25T18:08:18.3933106+03:00
- agent: CLAUDE
- purpose: Extended decision, quality and risk columns for the entry and exit-source signals.
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-25T18:08:18.7552615+03:00
- agent: CLAUDE
- purpose: Decision path, final and authority explanations, and sentiment breakdown for the six entry signals (WATCH overrides; repeated sentiment 0.380).
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 45
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-25T18:08:22.1996523+03:00
- agent: CLAUDE
- purpose: 5m and 1h context from 65 min before entry to close, to check the entry route and the exit confirmation rule.
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 3133
- approximate output lines: 116
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-25T18:08:22.7468493+03:00
- agent: CLAUDE
- purpose: 1m signal sequence during each position's life (the run-up to the exit).
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 157
- approximate output lines: 134
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-25T18:08:23.1256127+03:00
- agent: CLAUDE
- purpose: Managed-position state (profit lock, highest price, entry stage, allocation, near-TP) for the frozen trades, plus prior EGLD position 171.
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-25T18:08:23.4436031+03:00
- agent: CLAUDE
- purpose: Wallet fill provenance (decision price vs fill, price observation time, execution reason).
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-25T18:08:23.7628452+03:00
- agent: CLAUDE
- purpose: Full tick sequence for EGLDUSDT from 22:39:00 to 22:43:40 UTC (re-entry and the profit-lock collapse), plus trade 137 and trade 182.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 45
- approximate output lines: 143
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-25T18:08:25.1595667+03:00
- agent: CLAUDE
- purpose: Tick `delivery_status`/source distribution over each position's life (which ticks protection actually consumed).
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 1072
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-10-25T18:08:26.1443922+03:00
- agent: CLAUDE
- purpose: First 5 and last 15 ticks for 172, 162 and 158 (long-held positions).
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 685
- approximate output lines: 60
- password: never logged


## DBQ-CLAUDE-I2-010
- timestamp: 2026-10-25T18:08:26.5317262+03:00
- agent: CLAUDE
- purpose: Position-analysis history per wallet position (exit-score evolution; the REDUCE vs full-close question on 162).
- SQL file: DBQ-CLAUDE-I2-010-CLAUDE.sql
- exit code: 0
- duration_ms: 58
- approximate output lines: 165
- password: never logged


## DBQ-CLAUDE-I2-011
- timestamp: 2026-10-25T18:08:30.8068068+03:00
- agent: CLAUDE
- purpose: Outcome only (kept separate from the entry assessment): first touch of the original signal TP and of the SL within 4 h after open, and the 60-minute range after exit.
- SQL file: DBQ-CLAUDE-I2-011-CLAUDE.sql
- exit code: 0
- duration_ms: 3873
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-012
- timestamp: 2026-10-25T18:08:31.3360727+03:00
- agent: CLAUDE
- purpose: Population check of F-I2-01. Outcome of all closed positions bucketed by the stored risk/reward ratio of the entry signal.
- SQL file: DBQ-CLAUDE-I2-012-CLAUDE.sql
- exit code: 0
- duration_ms: 130
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I2-013
- timestamp: 2026-10-25T18:08:31.6594940+03:00
- agent: CLAUDE
- purpose: Processing-work lineage for entry and exit signals (origin, attempts, latency, failures).
- SQL file: DBQ-CLAUDE-I2-013-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I2-014
- timestamp: 2026-10-25T18:08:32.4414226+03:00
- agent: CLAUDE
- purpose: Execution-opportunity records behind the WATCH-signal entries (162, 158, 143) and the other entries.
- SQL file: DBQ-CLAUDE-I2-014-CLAUDE.sql
- exit code: 0
- duration_ms: 497
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I2-015
- timestamp: 2026-10-25T18:08:32.7920727+03:00
- agent: CLAUDE
- purpose: EGLDUSDT paper-position sequence around 137 and 143 (re-entry pattern after the prior profit-lock exit).
- SQL file: DBQ-CLAUDE-I2-015-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I2-016
- timestamp: 2026-10-25T18:08:33.1779257+03:00
- agent: CLAUDE
- purpose: Candle and data-quality table schema, so candle-level queries in iteration 3 use real column names.
- SQL file: DBQ-CLAUDE-I2-016-CLAUDE.sql
- exit code: 0
- duration_ms: 49
- approximate output lines: 28
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-25T18:19:33.0652295+03:00
- agent: CLAUDE
- purpose: Decide U-I3-01. Session/global zones, and the true epoch of known rows (expected epochs if storage is correct: 1791747604.857700 and 1791747815.021000).
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-25T18:19:42.4787713+03:00
- agent: CLAUDE
- purpose: U-I3-02. Did every symbol stop ticking during the EGLD gap (stream-wide stall) or only EGLD? Literals are in the same session zone as the earlier results.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 9083
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-25T18:19:42.8741038+03:00
- agent: CLAUDE
- purpose: U-I3-05. STOPPED losses at or after the oldest frozen close (selection-scope check only; no substitution).
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 47
- approximate output lines: 21
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-25T18:20:19.0737272+03:00
- agent: CLAUDE
- purpose: U-I3-04. Signal-path SELL fill price vs the freshest applied tick at fill time.
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 35771
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-25T18:20:19.5372686+03:00
- agent: CLAUDE
- purpose: Check that exit-signal `latest_price` is the price behind the SELL fills, and get the matching 1m candle closes.
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 70
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I3-007
- timestamp: 2026-10-25T18:20:19.9248420+03:00
- agent: CLAUDE
- purpose: Check that each entry and exit candle has exactly one recorded analysis outcome (durable admission evidence).
- SQL file: DBQ-CLAUDE-I3-007-CLAUDE.sql
- exit code: 0
- duration_ms: 88
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-25T18:30:59.1847345+03:00
- agent: CLAUDE
- purpose: U-I4-02/F-I4-01. Confluence evidence of the ACE 5m signals around entry and exit (which higher timeframe neutralized the SELL), plus any 15m ACE signals.
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 66
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-25T18:30:59.5332320+03:00
- agent: CLAUDE
- purpose: F-I4-02. Prove which 5m the 1m 159408 used, and the eligibility flags of 5m 159285 and 159407.
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-25T18:31:00.1567093+03:00
- agent: CLAUDE
- purpose: U-I4-04. For each executed initial BUY, compare the 5m the 1m confluence used with the 5m whose candle closed at the same boundary.
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 319
- approximate output lines: 86
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-25T18:31:00.7196134+03:00
- agent: CLAUDE
- purpose: U-I4-01. Which migrations, and so which release, were installed before and around the frozen trades (FIX-154 / FIX-11G deployment evidence).
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 60
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-25T18:42:45.2226952+03:00
- agent: CLAUDE
- purpose: U-I5-01. The 30-minute 1m price window before each entry signal (the EQ reference low and the recent/rejected high).
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 279
- approximate output lines: 182
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-25T18:42:45.5636056+03:00
- agent: CLAUDE
- purpose: U-I5-02. ATR and plan of the 5m authority signals used by the setup and wake-up routes, plus the latest 5m for the other entries.
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-25T18:42:45.9391843+03:00
- agent: CLAUDE
- purpose: U-I5-03. Outcome of all closed positions by entry route and by EQ parsed from the initial BUY message.
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 71
- approximate output lines: 32
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-25T18:42:48.0267786+03:00
- agent: CLAUDE
- purpose: U-I5-05 (lighter re-issue of I4-004). Positions whose latest 5m at or before the entry signal was masked bearish, with outcomes.
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 1804
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I5-005
- timestamp: 2026-10-25T18:42:48.3709764+03:00
- agent: CLAUDE
- purpose: U-I5-06. Migrations after rank 60 (release and deployment timeline around 10-09 to 10-12).
- SQL file: DBQ-CLAUDE-I5-005-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 51
- password: never logged


## DBQ-CLAUDE-I5-006
- timestamp: 2026-10-25T18:42:48.6626595+03:00
- agent: CLAUDE
- purpose: U-I5-04. Outcomes of the adverse boundary-race entries (wallet BUYs 358, 305, 255, 199, 119, 72).
- SQL file: DBQ-CLAUDE-I5-006-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-25T18:50:23.2341953+03:00
- agent: CLAUDE
- purpose: U-I6-01 / F-I6-05. FIX-154 audit evidence for the six entry signals: proves deployment and the exact 5m and 1h used.
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 44
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-25T18:50:23.5647506+03:00
- agent: CLAUDE
- purpose: U-I6-02. Whether the position and wallet tables model fees (schema only).
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-25T18:50:23.9131871+03:00
- agent: CLAUDE
- purpose: U-I6-04. 1m ATR, R:R, stop and TP of the six entry signals, plus position entry, stop and TP.
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-10-25T18:50:24.2377310+03:00
- agent: CLAUDE
- purpose: U-I6-03. Any existing Replay output covering the 143 (EGLD) and 182 (ACE) entry boundaries (read-only; Replay tables).
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 0
- password: never logged

