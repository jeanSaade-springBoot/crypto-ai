
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-11T10:04:20.2688105+03:00
- agent: CLAUDE
- purpose: Full lifecycle rows for the frozen trades (prices, SL/TP, qty, reasons)
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-11T10:04:20.5688231+03:00
- agent: CLAUDE
- purpose: Entry signals - decision, scores, gates, freshness, data quality
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-11T10:04:23.3158284+03:00
- agent: CLAUDE
- purpose: All 5m/1h signals from 3h before entry to 1 min after close per trade (HTF state at entry vs exit)
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 2484
- approximate output lines: 261
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-11T10:04:23.6478431+03:00
- agent: CLAUDE
- purpose: Exit trigger audit for each position
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 22
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-11T10:04:23.9623238+03:00
- agent: CLAUDE
- purpose: Schema discovery before choosing columns (wallet / managed position / management events / HTF audit / price events / opportunity)
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 62
- approximate output lines: 126
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-11T10:04:24.2377565+03:00
- agent: CLAUDE
- purpose: Deployed migration level (is FIX-154 V107/V109 applied in Production DB?)
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 24
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-11T10:04:24.6165329+03:00
- agent: CLAUDE
- purpose: FIX-154 BUY HTF assessment evidence for the entry signals (only if table exists)
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 40
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-11T10:04:30.5650594+03:00
- agent: CLAUDE
- purpose: 1m signals during each holding window (exit context, trade 82 SELL confirmation, trade 101 lock path)
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 5659
- approximate output lines: 166
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-11T10:10:58.8993430+03:00
- agent: CLAUDE
- purpose: FIX-154 audit rows for any signal (1m or 5m) of each symbol within 10 min before entry; tests the SETUP_WAKEUP attribution (F-09)
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 609
- approximate output lines: 23
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-11T10:10:59.1946196+03:00
- agent: CLAUDE
- purpose: Wallet fills per position (entry route, scale-ins, decision vs fill price, fees, partial harvest)
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-11T10:10:59.4936016+03:00
- agent: CLAUDE
- purpose: Managed-position lifecycle state (allocation %, stage, profit lock / rebase, near-TP, entry decision path)
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 24
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-11T10:10:59.7854162+03:00
- agent: CLAUDE
- purpose: Position management events (TP extension, lock activation/rebase, scale-in) for the six wallet positions
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-11T10:11:01.3658187+03:00
- agent: CLAUDE
- purpose: Accepted price events in the 3 minutes before each exit (exit tick, gap size through floor for 101); for 101 the window is widened to cover the TP touch
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 1300
- approximate output lines: 177
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-11T10:11:01.7243355+03:00
- agent: CLAUDE
- purpose: Opportunity memory that the entries consumed (SETUP_WAKEUP precondition, execution_source, decision_code)
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 60
- approximate output lines: 25
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-10-11T10:11:16.9582086+03:00
- agent: CLAUDE
- purpose: Hour-boundary signal coverage across all symbols for 1h candle 17:00 and 5m candle 17:55 on 2026-10-10 (XLM gap, F-14) – global vs symbol-specific
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 14972
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-10-11T10:11:17.2654310+03:00
- agent: CLAUDE
- purpose: Schema discovery for candle table before checking whether the XLM 17:55 5m / 17:00 1h candles exist and are closed
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 36
- approximate output lines: 19
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-10-11T10:11:17.5469829+03:00
- agent: CLAUDE
- purpose: Prevalence of exit reasons since FIX-154 schema install (V107 at 2026-10-10 00:15:21 UTC; app deploy time may differ)
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 38
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I2-010
- timestamp: 2026-10-11T10:11:30.8342047+03:00
- agent: CLAUDE
- purpose: F-08 prevalence – HTF_SELL_CONFIRMED closes since V107 where no 1h signal was generated between open and close (same 1h at entry and exit)
- SQL file: DBQ-CLAUDE-I2-010-CLAUDE.sql
- exit code: 0
- duration_ms: 13005
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-11T10:25:41.5159757+03:00
- agent: CLAUDE
- purpose: Prove the wrapper session time zone (determines whether raw timestamps are KSA or UTC)
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-11T10:25:41.8045646+03:00
- agent: CLAUDE
- purpose: Epoch/UTC proof for the frozen trades' wallet fills, compared with the UTC Instant text in execution_message
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 24
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-11T10:25:44.4601273+03:00
- agent: CLAUDE
- purpose: Hourly local-vs-exchange clock skew history (created_at minus observed_at), to locate the ~2.9 s step (F-16)
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 2396
- approximate output lines: 18
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-11T10:25:47.1392270+03:00
- agent: CLAUDE
- purpose: Price-event rejections (FUTURE_TIMESTAMP/STALE/OUT_OF_ORDER/duplicates) during the study period
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 2405
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-11T10:25:49.6235918+03:00
- agent: CLAUDE
- purpose: Cross-symbol WebSocket-thread stall around each frozen exit (receipt lag of other symbols, F-17)
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 2187
- approximate output lines: 144
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-11T10:25:49.9295484+03:00
- agent: CLAUDE
- purpose: Immutable exit audit rows (audited_at timing, source signal) for the six frozen positions
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 32
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I3-007
- timestamp: 2026-10-11T10:25:50.2181861+03:00
- agent: CLAUDE
- purpose: Candle rows around the system-wide signal gap at raw 18:00 KSA on 2026-10-10 (F-14/U-12)
- SQL file: DBQ-CLAUDE-I3-007-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 36
- password: never logged


## DBQ-CLAUDE-I3-008
- timestamp: 2026-10-11T10:25:50.7921936+03:00
- agent: CLAUDE
- purpose: Analysis outcomes and reconciliation audit for the gap candles (recovered, historical-only or absent)
- SQL file: DBQ-CLAUDE-I3-008-CLAUDE.sql
- exit code: 0
- duration_ms: 291
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I3-009
- timestamp: 2026-10-11T10:26:03.4626775+03:00
- agent: CLAUDE
- purpose: 1m signal coverage per minute around the raw 18:00 boundary (restart/reconnect footprint)
- SQL file: DBQ-CLAUDE-I3-009-CLAUDE.sql
- exit code: 0
- duration_ms: 12411
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I3-010
- timestamp: 2026-10-11T10:26:03.8212872+03:00
- agent: CLAUDE
- purpose: Live price-event continuity per minute around the raw 18:00 boundary (WebSocket outage footprint)
- SQL file: DBQ-CLAUDE-I3-010-CLAUDE.sql
- exit code: 0
- duration_ms: 102
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I3-011
- timestamp: 2026-10-11T10:26:04.1265337+03:00
- agent: CLAUDE
- purpose: Re-run of the exit-reason prevalence query with the corrected snapshot bound in session time (10:00:03 KSA)
- SQL file: DBQ-CLAUDE-I3-011-CLAUDE.sql
- exit code: 0
- duration_ms: 38
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I3-012
- timestamp: 2026-10-11T10:26:18.0349939+03:00
- agent: CLAUDE
- purpose: Re-run of the F-08 prevalence query (same 1h at entry and exit) with the corrected bound
- SQL file: DBQ-CLAUDE-I3-012-CLAUDE.sql
- exit code: 0
- duration_ms: 13645
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-11T10:36:51.5750314+03:00
- agent: CLAUDE
- purpose: Deploy/restart evidence near 2026-10-10 18:00 KSA (15:00 UTC) and 02:43 KSA (23:43 UTC): latest Flyway installs
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-11T10:36:51.8842422+03:00
- agent: CLAUDE
- purpose: Schema discovery for candle_analysis_outcome before the orphan-candle prevalence query
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 52
- approximate output lines: 9
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-11T10:36:54.1739419+03:00
- agent: CLAUDE
- purpose: F-21 prevalence: candles closed with no token and with no terminal outcome and no signal (bootstrap-silenced) across the study period
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 2039
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-11T10:36:55.1768031+03:00
- agent: CLAUDE
- purpose: Per-symbol proof for the 1h candle 17:00 KSA (14:00 UTC): which 2 symbols were recovered vs which 19 (incl. XLM) were closed silently by bootstrap
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 561
- approximate output lines: 21
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-11T10:36:55.4875747+03:00
- agent: CLAUDE
- purpose: U-16: positions open during the 17:59:06–18:02:21 KSA ingestion outage (protection coverage during restart)
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 39
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I4-006
- timestamp: 2026-10-11T10:36:56.0764935+03:00
- agent: CLAUDE
- purpose: F-16/U-15: minute-level clock-skew step and ingestion continuity around 02:15–03:05 KSA on 2026-10-11 (restart vs host clock correction)
- SQL file: DBQ-CLAUDE-I4-006-CLAUDE.sql
- exit code: 0
- duration_ms: 334
- approximate output lines: 47
- password: never logged


## DBQ-CLAUDE-I4-007
- timestamp: 2026-10-11T10:36:56.6596701+03:00
- agent: CLAUDE
- purpose: Outcome-only (not entry assessment): after each exit, did price first touch the stored TP or SL within 120 min, plus 60-min extremes
- SQL file: DBQ-CLAUDE-I4-007-CLAUDE.sql
- exit code: 0
- duration_ms: 322
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I4-008
- timestamp: 2026-10-11T10:40:26.4689239+03:00
- agent: CLAUDE
- purpose: F-20 prevalence: outcome of all closed positions entered while the latest 1h was SELL/STRONG_SELL since V107 (2026-10-10 00:15:21 KSA)
- SQL file: DBQ-CLAUDE-I4-008-CLAUDE.sql
- exit code: 0
- duration_ms: 209123
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-11T10:50:28.3243311+03:00
- agent: CLAUDE
- purpose: Quality of the 5m triggers and 1h context signals used at entry (and 101's add signal)
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 75
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-11T10:50:28.6472784+03:00
- agent: CLAUDE
- purpose: Base rate (outcome-based prevalence, separate from ex-ante assessment): all closed positions since V107 by stored entry-quality band and signal reward/risk band
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 74
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-11T10:50:28.9393208+03:00
- agent: CLAUDE
- purpose: Opportunity anchor/best price, health and evidence for the consumed opportunities (entry-quality reference price, F-23 arithmetic)
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 25
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-005
- timestamp: 2026-10-11T10:50:29.3489538+03:00
- agent: CLAUDE
- purpose: U-19: did UNI (position 80, SL 7.628665) or AERO (81, SL 0.908416362414) breach the stop during the 17:59:06-18:02:21 KSA outage
- SQL file: DBQ-CLAUDE-I5-005-CLAUDE.sql
- exit code: 0
- duration_ms: 71
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-11T10:57:19.0929268+03:00
- agent: CLAUDE
- purpose: U-20: the active wallet-managed stop/TP/profit-lock state for UNI (paper 80) and AERO (paper 81), which drives live protection
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 43
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-11T10:57:19.4037009+03:00
- agent: CLAUDE
- purpose: U-20/F-30: per-minute min/max observed live price-event price compared with the candle low (wick visibility) for AERO and UNI while their positions were open
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 57
- approximate output lines: 36
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-11T10:57:19.7523728+03:00
- agent: CLAUDE
- purpose: U-17 (I5-001 returned no result): the last 30 closed 1m candles before each frozen entry, giving the 30m range, extension and volume baseline
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 97
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-10-11T10:57:20.0704431+03:00
- agent: CLAUDE
- purpose: U-20: stop/profit-lock management events for the AERO and UNI wallet positions during the outage window
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 0
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-10-11T11:04:26.8138830+03:00
- agent: CLAUDE
- purpose: U-22/F-34: count management events per wallet position (UNI 109, AERO 110 and the frozen trades' wallet positions) so that zero rows can be told apart from a failed query
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 39
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-10-11T11:04:27.0965771+03:00
- agent: CLAUDE
- purpose: SC-3/U-20: confirm where the stop ratchet came from. Stop and target of each BUY signal for UNI and AERO during their holds
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-10-11T11:04:27.3890159+03:00
- agent: CLAUDE
- purpose: F-34 applied to the frozen trades: final wallet stop/target, entry stage and last add time, to find any frozen trade whose active stop changed during the hold (expected: only 101)
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 25
- approximate output lines: 6
- password: never logged

