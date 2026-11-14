
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-28T18:04:19.1548767+03:00
- agent: CLAUDE
- purpose: Read actual schemas before choosing columns (U7)
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 60
- approximate output lines: 259
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-28T18:04:19.5416353+03:00
- agent: CLAUDE
- purpose: Full lifecycle rows for the six frozen trades (U1); schema proven by DBQ-FTS-001
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 37
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-28T18:05:31.1670978+03:00
- agent: CLAUDE
- purpose: Entry and exit signal rows (U2/U3); SELECT * because columns are unproven, bounded to known IDs
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 71297
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-28T18:05:31.5580223+03:00
- agent: CLAUDE
- purpose: Check that the losses are recent and not an artefact of the CLOSED-only filter (F2/F3)
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 37
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-28T18:05:31.9398053+03:00
- agent: CLAUDE
- purpose: Trade 143 hard-floor loss check and entry-vs-SL distance per trade (F4)
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-28T18:05:32.3738842+03:00
- agent: CLAUDE
- purpose: Prior closed positions per symbol before each frozen entry, to test the EGLD re-entry pattern (F7)
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 38
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-28T18:05:32.8034245+03:00
- agent: CLAUDE
- purpose: Activity between trade 182's close and the snapshot (F3)
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 50
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-28T18:05:33.2552894+03:00
- agent: CLAUDE
- purpose: Raw rows for the managed position, management events and exit audit, bounded per symbol and window. SELECT * because the schema is unproven; a narrower rerun follows I1-001 if the output is too large.
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 56
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-28T18:11:55.6725621+03:00
- agent: CLAUDE
- purpose: Prove the session time zone (F0) and get true epochs for the six trades
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-28T18:11:56.1435219+03:00
- agent: CLAUDE
- purpose: Entry/exit signal freshness, HTF age, ATR plan (F1, F6, F8); narrow columns
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 58
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-28T18:11:56.7201640+03:00
- agent: CLAUDE
- purpose: Wallet fills, fees, wallet P&L vs paper P&L (U4, F2); includes trades 142 and 154 in the same windows
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 59
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-28T18:11:57.1808441+03:00
- agent: CLAUDE
- purpose: Position management lifecycle (lock activation, TP changes, rebase) and exit audit (F5, U5)
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 45
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-28T18:11:59.0984743+03:00
- agent: CLAUDE
- purpose: Price-event coverage per holding window: count, range, source/delivery-status mix (U6)
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 1532
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-28T18:11:59.5129678+03:00
- agent: CLAUDE
- purpose: Tick-level EGLD price events across trade 143's lock breach (F4): displayed 22:42:50–22:43:40 (19:42:50–19:43:40 UTC if F0 holds)
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 38
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-28T18:12:00.2681172+03:00
- agent: CLAUDE
- purpose: Newest 1m/5m/1h signal at each close and its age (iteration-1 F5 / U3)
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 380
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-28T18:12:13.3218860+03:00
- agent: CLAUDE
- purpose: Signal sequence during each holding window (were there earlier exit opportunities?) and the local 1m candles for the EGLD windows (U-New1)
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 12658
- approximate output lines: 223
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-10-28T18:12:13.6729018+03:00
- agent: CLAUDE
- purpose: Trade 142, which closed about 22 s before trade 143 re-entered (F6, U-New2)
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-28T18:22:40.5872288+03:00
- agent: CLAUDE
- purpose: Observe?persist latency by second-of-minute in each holding window; tests T6 contention around candle-close analysis
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 732
- approximate output lines: 66
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-28T18:23:49.6013315+03:00
- agent: CLAUDE
- purpose: Cross-symbol price-event density 19:42:50–19:43:45 UTC; a simultaneous gap on all symbols means a stream/consumer stall (T5)
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 68097
- approximate output lines: 21
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-28T18:23:50.1773417+03:00
- agent: CLAUDE
- purpose: Every tick between the moment the exit rule became true and the exit, for 137/158/172/182 (T2)
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 62
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-28T18:23:50.7571391+03:00
- agent: CLAUDE
- purpose: Trade 143's lock-activation tick (expected at about 19:40:23 UTC, price at least 4.0552) and the managed-position lock fields (T4)
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 65
- approximate output lines: 29
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-28T18:23:51.5700770+03:00
- agent: CLAUDE
- purpose: Wallet snapshot timing compared with exit/entry commits, plus asset count; tests the T6 transaction-duration hypothesis
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 78
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-28T18:24:09.4457107+03:00
- agent: CLAUDE
- purpose: Hourly distribution of 1m analysis latency (candle close ? generated_at), 10-11 16:00 to 10-12 08:00 UTC (T7)
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 17042
- approximate output lines: 32
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-28T18:33:54.7017956+03:00
- agent: CLAUDE
- purpose: Entry quality, allocation and plan for the six managed positions (R1 / U-I4-1)
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 44
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-28T18:33:55.1122413+03:00
- agent: CLAUDE
- purpose: Confluence branch that changed SELL/STRONG_SELL into NEUTRAL on 5m exit-context signals (R2 / U-I4-2)
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 53
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-28T18:33:55.7831359+03:00
- agent: CLAUDE
- purpose: System-wide outcome by entry-signal R:R bucket across all closed paper positions (R1 systemic test, U-I4-3)
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 277
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-28T18:40:28.2748105+03:00
- agent: CLAUDE
- purpose: Full execution explanation (route, validation code, entry-quality cap reasons, rejection zone) for the six entry BUYs (E1/E2, U-I5-1)
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 46
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-28T18:40:29.2691372+03:00
- agent: CLAUDE
- purpose: Opportunity evidence at each entry (anchor/best reference, age, buy/watch counts, 5m/1h context, decision code) (E1 FIX-119 path for 162, E2 reference, U-I5-2)
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 493
- approximate output lines: 60
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-28T18:40:29.7548818+03:00
- agent: CLAUDE
- purpose: Decompose the R:R>=1.5 loss concentration from I4-003 by month and close reason (C1, U-I5-4)
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 25
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-28T18:40:30.2480542+03:00
- agent: CLAUDE
- purpose: Entry-signal oscillator context not used by the entry-quality score (E3)
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 43
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-28T18:48:29.9551119+03:00
- agent: CLAUDE
- purpose: Re-run of the truncated I5-002 with time-zone-safe bounds: opportunities for 137, 143, 158 and 172, plus EGLD opportunities active before 143 (U-I6-1, U-I6-2)
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 249
- approximate output lines: 21
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-28T18:48:30.4385460+03:00
- agent: CLAUDE
- purpose: 1m signal prices in each 30-minute entry-quality window (rolling low/high and rejection zone) for 182, 158, 137 and 143 (F6-3, F6-4, F6-5)
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 149
- approximate output lines: 124
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-28T18:48:30.9053665+03:00
- agent: CLAUDE
- purpose: 5m setup signal that owned ATR authority for 143 (decision score 60 used its ATR) (U-I6-1)
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 50
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-10-28T18:58:07.0314170+03:00
- agent: CLAUDE
- purpose: Find the signal that opened DOGE opportunity 37952 and its candle time, to compare age by candle close with age by generation time (F7-3)
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 64
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-10-28T18:58:35.8129236+03:00
- agent: CLAUDE
- purpose: Gate fields of the 5m signals that could have set ATR for 143 (#159285, #159407) and 158 (#165415), plus EGLD and XRP 1h context (F7-2, F7-5)
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 28337
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-10-28T18:58:36.3298510+03:00
- agent: CLAUDE
- purpose: How often F7-2 happens: October paper positions whose 1m entry signal was generated before the same-boundary 5m signal, with that 5m decision and the outcome
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 91
- approximate output lines: 70
- password: never logged


## DBQ-CLAUDE-I8-001
- timestamp: 2026-10-28T19:06:04.3696969+03:00
- agent: CLAUDE
- purpose: Entry route for the 8 positions matching the CD-1 pattern (1m WATCH entry, later same-boundary 5m WATCH), to measure how often the outdated 5m BUY actually decided an entry (U-2)
- SQL file: DBQ-CLAUDE-I8-001-CLAUDE.sql
- exit code: 0
- duration_ms: 39
- approximate output lines: 8
- password: never logged

