
## DBQ-CLAUDE-I1-001
- timestamp: 2026-10-25T13:15:36.9540883+03:00
- agent: CLAUDE
- purpose: Get the actual schema of the tables involved, and the latest applied migrations (U-03, U-04).
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 417
- approximate output lines: 346
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-10-25T13:15:37.2539966+03:00
- agent: CLAUDE
- purpose: Get the frozen trade rows, and check that they match the selection at both candidate snapshot instants (F-I1-01).
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 19
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-10-25T13:15:37.5526802+03:00
- agent: CLAUDE
- purpose: Get the recorded exit trigger and exit audit for each trade.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-10-25T13:15:37.8802882+03:00
- agent: CLAUDE
- purpose: Get the entry and exit signals, including decision chain, regime, strategy, multi-timeframe and BTC context, and risk/reward. Large JSON columns are left out.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 44
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-004B
- timestamp: 2026-10-25T13:15:38.1789001+03:00
- agent: CLAUDE
- purpose: Get `signal_mode` (V106) and `data_quality_valid` (V107) for the entry signals. This is kept separate because the columns may not exist (F-I1-03).
- SQL file: DBQ-CLAUDE-I1-004B-CLAUDE.sql
- exit code: 0
- duration_ms: 26
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-10-25T13:15:38.4673139+03:00
- agent: CLAUDE
- purpose: Get the entry decision path and market-context snapshot (entry signals only, truncated).
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-006
- timestamp: 2026-10-25T13:15:38.7669713+03:00
- agent: CLAUDE
- purpose: Get the wallet executions in each trade's lifetime, to recompute P&L and check paper vs wallet (F-I1-02).
- SQL file: DBQ-CLAUDE-I1-006-CLAUDE.sql
- exit code: 0
- duration_ms: 26
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-10-25T13:15:39.0780541+03:00
- agent: CLAUDE
- purpose: Get the managed-position state: highest price and profit lock.
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-008
- timestamp: 2026-10-25T13:15:39.3959856+03:00
- agent: CLAUDE
- purpose: Get the work-claim timeline for the entry signal (signal ? claim ? entry latency).
- SQL file: DBQ-CLAUDE-I1-008-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-009
- timestamp: 2026-10-25T13:15:41.0294539+03:00
- agent: CLAUDE
- purpose: Get price-event coverage over each trade's lifetime: counts by delivery status, min/max price, and the first moment price was at/below the final stop or at/above the target.
- SQL file: DBQ-CLAUDE-I1-009-CLAUDE.sql
- exit code: 0
- duration_ms: 1362
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-010
- timestamp: 2026-10-25T13:15:42.2540665+03:00
- agent: CLAUDE
- purpose: Find the largest gap between price observations during each trade, to detect protection blind spots.
- SQL file: DBQ-CLAUDE-I1-010-CLAUDE.sql
- exit code: 0
- duration_ms: 950
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-011
- timestamp: 2026-10-25T13:15:43.9857105+03:00
- agent: CLAUDE
- purpose: Get the price events right around entry and exit, to check fill price against the latest price and the exit trigger tick.
- SQL file: DBQ-CLAUDE-I1-011-CLAUDE.sql
- exit code: 0
- duration_ms: 1465
- approximate output lines: 60
- password: never logged


## DBQ-CLAUDE-I1-012
- timestamp: 2026-10-25T13:15:47.0641220+03:00
- agent: CLAUDE
- purpose: Get the 1m and 5m candles that were available at entry (gaps, closed flag, provenance), plus FIX-154 audit rows if that table exists.
- SQL file: DBQ-CLAUDE-I1-012-CLAUDE.sql
- exit code: 0
- duration_ms: 2751
- approximate output lines: 89
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-10-25T13:21:35.8478074+03:00
- agent: CLAUDE
- purpose: Measure the excluded stop-loss losses and how active the system was after the newest frozen trade (F-I2-01, F-I2-02).
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 61
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-10-25T13:21:39.5792905+03:00
- agent: CLAUDE
- purpose: Check that the exit rules followed `validateSell`: the exit source signals, plus the latest 5m/1h signals stored at or before each exit (U-07).
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 3431
- approximate output lines: 127
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-10-25T13:21:39.8914159+03:00
- agent: CLAUDE
- purpose: Get position-management events (target extensions, profit lock, stop moves) for each trade, plus the prior EGLD position 171 (F-I2-06).
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 10
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-10-25T13:21:40.4862288+03:00
- agent: CLAUDE
- purpose: Characterise trade 143's 8-second price gap (U-08) and get exact in-lifetime lows for 143 and 162 (U-09).
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 315
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-10-25T13:21:40.8029047+03:00
- agent: CLAUDE
- purpose: Trace trade 158's 5m wake-up path and the execution opportunities behind each entry (F-I2-08, F-I2-09).
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-10-25T13:21:41.2303656+03:00
- agent: CLAUDE
- purpose: Get system-wide outcomes by exit type, and by stop basis (0.5% floor vs 2×ATR) and R:R bucket, to test F-I2-03 beyond the six-trade sample (U-11). Uses the entry signal's own stop/target because paper stops can move up.
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 146
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-10-25T13:36:14.1395183+03:00
- agent: CLAUDE
- purpose: Price-tick density and price path during each exit-decision window (F-I3-03, U-13).
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 15
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-10-25T13:36:14.5188004+03:00
- agent: CLAUDE
- purpose: Commit times of the exit-relevant signals, plus trade 137's missing 5m/1h context (U-07, F-I3-03).
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 27
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-10-25T13:36:16.7044844+03:00
- agent: CLAUDE
- purpose: Inter-arrival gaps between ticks during each frozen trade's life (F-I3-04 baseline, F-I3-03).
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 1863
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-10-25T13:36:17.3449810+03:00
- agent: CLAUDE
- purpose: Confirm that the wallet-to-paper lag is portfolio-snapshot work in the same transaction (F-I3-05). Reads the schema first.
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 313
- approximate output lines: 23
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-10-25T13:36:22.4044552+03:00
- agent: CLAUDE
- purpose: Was the system running after 12 Oct, and why did trading stop (F-I3-06, U-14)?
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 4671
- approximate output lines: 21
- password: never logged


## DBQ-CLAUDE-I3-006
- timestamp: 2026-10-25T13:36:22.7745002+03:00
- agent: CLAUDE
- purpose: How far stop-loss fills land past the stop, as a system-wide measure of tick-gap slippage (F-I3-07).
- SQL file: DBQ-CLAUDE-I3-006-CLAUDE.sql
- exit code: 0
- duration_ms: 30
- approximate output lines: 1
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-10-25T13:42:02.2545217+03:00
- agent: CLAUDE
- purpose: Check whether the per-asset candle lookup that the portfolio and snapshot work depend on is index-backed (F-I4-03, U-18).
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 62
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-10-25T13:42:02.6131264+03:00
- agent: CLAUDE
- purpose: Show what trade 143's evidence was made of: the entry signal, plus the EGLD 1m signals in the 30-minute evidence window before it, split at the exit of position 171 (F-I4-05, U-17).
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 59
- approximate output lines: 42
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-10-25T13:42:02.9532525+03:00
- agent: CLAUDE
- purpose: Opportunity rows for EGLD around the 171 ? 143 handover. Confirms whether a COMPLETED boundary row exists and when 36524 started (F-I4-05).
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 69
- approximate output lines: 20
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-10-25T13:42:03.2918057+03:00
- agent: CLAUDE
- purpose: Across the system, how often a re-entry on the same symbol within 5 minutes of a non-stop exit happens, and how those re-entries turn out (generalises F-I4-05).
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 53
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I4-005
- timestamp: 2026-10-25T13:42:03.6043689+03:00
- agent: CLAUDE
- purpose: Compare trade 162's signal-path fill price with the market ticks at the exit (F-I4-04).
- SQL file: DBQ-CLAUDE-I4-005-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-10-25T13:50:09.5164487+03:00
- agent: CLAUDE
- purpose: Check whether each BUY filled on the newest tick (F-I5-05, U-21). Reads the schema first.
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 45
- approximate output lines: 55
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-10-25T13:50:27.4601906+03:00
- agent: CLAUDE
- purpose: How often the 5m-boundary race happens: the 5m signal for the just-closed 5m candle being generated after the 1m signal at the same boundary (F-I5-01, U-20). First query: 159407's commit time. Second: the race rate across all boundary 1m signals on 11–12 Oct. Third: the race among executed entries.
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 17590
- approximate output lines: 68
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-10-25T13:50:27.9893064+03:00
- agent: CLAUDE
- purpose: System-wide entry outcomes by execution route, by overextension and by regime/strategy (U-22). Tests whether the frozen-set entry patterns generalise.
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 156
- approximate output lines: 34
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-10-25T13:50:28.3551070+03:00
- agent: CLAUDE
- purpose: How many closed trades had a target at or below a 0.20% round trip, and how they turned out (F-I5-06).
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-10-25T14:31:12.1442624+03:00
- agent: CLAUDE
- purpose: U-23 / F-I6-01. Find APPLIED ticks priced outside their final one-minute candle's range, by position within the minute. Also read the candle schema to find a trade-count column.
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 1930684
- approximate output lines: 42
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-10-25T14:31:12.7457808+03:00
- agent: CLAUDE
- purpose: U-24 / U-20. Compare all closed trades entered on boundary minutes with non-boundary ones. Split boundary trades by whether the same-boundary five-minute signal came after the one-minute signal, and by route.
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 204
- approximate output lines: 16
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-10-25T14:31:13.1074605+03:00
- agent: CLAUDE
- purpose: F-I6-04. Show how the wide-target trades exited, to explain why their targets were rarely reached.
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 34
- approximate output lines: 40
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-10-25T14:31:39.1968129+03:00
- agent: CLAUDE
- purpose: F-I6-02. Measure how often a newer tick was excluded at fill time because the host clock was behind Binance: the Binance time is after the fill's host time but the row was written before it.
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 25528
- approximate output lines: 25
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-10-25T14:39:58.8908731+03:00
- agent: CLAUDE
- purpose: F-I7-04. Wide- vs small-target outcomes grouped by structured close_reason (not free-text exit_reason), with realised R-multiple.
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 98
- approximate output lines: 26
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-10-25T14:40:12.2398278+03:00
- agent: CLAUDE
- purpose: F-I7-02. Daily host-vs-Binance lag (written time minus Binance time; negative means the host is behind) and the delay from price lookup to execution for BUY fills. Also the denominator for F-I7-01.
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 12980
- approximate output lines: 11
- password: never logged


## DBQ-CLAUDE-I7-004
- timestamp: 2026-10-25T14:40:17.7015048+03:00
- agent: CLAUDE
- purpose: U-28 / F-I7-01. Did stop-loss or profit-lock exits happen at prices outside the final 1-minute candle range (a possible protection trigger on a placeholder tick)? Also read the market_price_event schema for a trade-count column.
- SQL file: DBQ-CLAUDE-I7-004-CLAUDE.sql
- exit code: 0
- duration_ms: 5091
- approximate output lines: 20
- password: never logged

