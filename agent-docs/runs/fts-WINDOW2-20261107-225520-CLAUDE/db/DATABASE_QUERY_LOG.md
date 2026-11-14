
## DBQ-CLAUDE-I1-001
- timestamp: 2026-11-07T23:00:27.3438010+03:00
- agent: CLAUDE
- purpose: Get the real table names before choosing evidence tables (signals, wallet, managed position, exit audit, price events, candles).
- SQL file: DBQ-CLAUDE-I1-001-CLAUDE.sql
- exit code: 0
- duration_ms: 65
- approximate output lines: 81
- password: never logged


## DBQ-CLAUDE-I1-002
- timestamp: 2026-11-07T23:00:27.6826789+03:00
- agent: CLAUDE
- purpose: Prove the schema of the evidence tables. Every later query that uses columns outside `paper_position` depends on this.
- SQL file: DBQ-CLAUDE-I1-002-CLAUDE.sql
- exit code: 0
- duration_ms: 53
- approximate output lines: 264
- password: never logged


## DBQ-CLAUDE-I1-003
- timestamp: 2026-11-07T23:00:27.9763708+03:00
- agent: CLAUDE
- purpose: Full lifecycle of the six frozen positions (prices, quantity, SL/TP, exit signal, reasons), using the schema already proven by DBQ-FTS-001. Reason text is truncated to keep the result bounded.
- SQL file: DBQ-CLAUDE-I1-003-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I1-004
- timestamp: 2026-11-07T23:01:11.7781629+03:00
- agent: CLAUDE
- purpose: Entry and exit signals for the six positions: decision chain, gates, data quality and timing. Columns follow the `TradeSignal` entity mapping; if a column fails, return the error so I can correct it against DBQ-CLAUDE-I1-002.
- SQL file: DBQ-CLAUDE-I1-004-CLAUDE.sql
- exit code: 0
- duration_ms: 43518
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I1-005
- timestamp: 2026-11-07T23:01:12.1046321+03:00
- agent: CLAUDE
- purpose: Wallet executions for the six positions' symbols inside each position's lifetime (±2 min), giving fees, decision vs fill price, price-observation age and execution reason (U4). Columns follow the `WalletTrade` entity mapping.
- SQL file: DBQ-CLAUDE-I1-005-CLAUDE.sql
- exit code: 0
- duration_ms: 25
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I1-007
- timestamp: 2026-11-07T23:01:12.7373734+03:00
- agent: CLAUDE
- purpose: Check U7. Are there any paper positions, of any status, after the last frozen close, and what is the latest activity overall?
- SQL file: DBQ-CLAUDE-I1-007-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I2-001
- timestamp: 2026-11-07T23:07:40.3611440+03:00
- agent: CLAUDE
- purpose: Immutable exit audit for the six positions plus the EGLD exit just before 143 (replaces the I1-006 query, which used the wrong timestamp column).
- SQL file: DBQ-CLAUDE-I2-001-CLAUDE.sql
- exit code: 0
- duration_ms: 65
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-002
- timestamp: 2026-11-07T23:07:40.6966132+03:00
- agent: CLAUDE
- purpose: Managed-position state: average entry, stop/TP, highest price, profit lock, near-TP state, scale-in. Includes wallet position 171 and the DOGE position. Columns are proven by I1-002.
- SQL file: DBQ-CLAUDE-I2-002-CLAUDE.sql
- exit code: 0
- duration_ms: 24
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-003
- timestamp: 2026-11-07T23:07:40.9963307+03:00
- agent: CLAUDE
- purpose: Every stop/TP/lock/near-TP change during each position's life (U-I2-3, U-I2-1).
- SQL file: DBQ-CLAUDE-I2-003-CLAUDE.sql
- exit code: 0
- duration_ms: 31
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I2-004
- timestamp: 2026-11-07T23:07:41.3069714+03:00
- agent: CLAUDE
- purpose: Prove the price-event and candle schemas, then get EGLD price ticks across 143's life to check for a gap through the floor versus a protected hold. Column names come from `MarketPriceEventService.java:62,71,109`.
- SQL file: DBQ-CLAUDE-I2-004-CLAUDE.sql
- exit code: 0
- duration_ms: 42
- approximate output lines: 120
- password: never logged


## DBQ-CLAUDE-I2-005
- timestamp: 2026-11-07T23:07:41.7888925+03:00
- agent: CLAUDE
- purpose: 1m candles from 10 minutes before entry to exit for all six positions: best and worst price reached, entry location, and whether candles existed (U-I2-6). Columns come from `Candle.java`; check them against I2-004.
- SQL file: DBQ-CLAUDE-I2-005-CLAUDE.sql
- exit code: 0
- duration_ms: 219
- approximate output lines: 194
- password: never logged


## DBQ-CLAUDE-I2-006
- timestamp: 2026-11-07T23:08:05.5582663+03:00
- agent: CLAUDE
- purpose: The 5m/1h signals (70 minutes before each close) and the last 1m signals that drove each exit (U-I2-2, the 43 s lag on 172).
- SQL file: DBQ-CLAUDE-I2-006-CLAUDE.sql
- exit code: 0
- duration_ms: 23498
- approximate output lines: 110
- password: never logged


## DBQ-CLAUDE-I2-007
- timestamp: 2026-11-07T23:08:05.9101995+03:00
- agent: CLAUDE
- purpose: Risk fields on the entry and exit signals, to confirm F-I2-02 from the signal's own TP (immune to later TP extension) and to explain 143's ATR target.
- SQL file: DBQ-CLAUDE-I2-007-CLAUDE.sql
- exit code: 0
- duration_ms: 48
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I2-008
- timestamp: 2026-11-07T23:08:06.4928829+03:00
- agent: CLAUDE
- purpose: How EGLD re-entered after 22 s: the previous paper position and the opportunity record behind each entry (U-I2-4).
- SQL file: DBQ-CLAUDE-I2-008-CLAUDE.sql
- exit code: 0
- duration_ms: 317
- approximate output lines: 19
- password: never logged


## DBQ-CLAUDE-I2-009
- timestamp: 2026-11-07T23:08:06.8802461+03:00
- agent: CLAUDE
- purpose: How common the inverted reward:risk is across all closed Production positions, using the entry signal's own stop and TP (U-I2-5).
- SQL file: DBQ-CLAUDE-I2-009-CLAUDE.sql
- exit code: 0
- duration_ms: 103
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I3-001
- timestamp: 2026-11-07T23:16:58.5916956+03:00
- agent: CLAUDE
- purpose: Prove the same-boundary 1m/5m order behind 143 (F-I3-03) and check the other five entries the same way. Columns are proven by I2-006 and I2-007.
- SQL file: DBQ-CLAUDE-I3-001-CLAUDE.sql
- exit code: 0
- duration_ms: 29
- approximate output lines: 14
- password: never logged


## DBQ-CLAUDE-I3-002
- timestamp: 2026-11-07T23:16:59.1660845+03:00
- agent: CLAUDE
- purpose: How often executed entries on a 5m boundary were admitted before that boundary's 5m signal existed, and what happened to those trades.
- SQL file: DBQ-CLAUDE-I3-002-CLAUDE.sql
- exit code: 0
- duration_ms: 291
- approximate output lines: 71
- password: never logged


## DBQ-CLAUDE-I3-003
- timestamp: 2026-11-07T23:16:59.4844593+03:00
- agent: CLAUDE
- purpose: Price-event timing around each live exit, to explain the 38.9 s, 14.6 s and 6.0 s lags (F-I3-04).
- SQL file: DBQ-CLAUDE-I3-003-CLAUDE.sql
- exit code: 0
- duration_ms: 28
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I3-004
- timestamp: 2026-11-07T23:23:26.0702667+03:00
- agent: CLAUDE
- purpose: Was the stream alive during the two EGLD gaps (U-I3-2)? Also lists the largest EGLD gaps between price events over the life of 142 and 143.
- SQL file: DBQ-CLAUDE-I3-004-CLAUDE.sql
- exit code: 0
- duration_ms: 386304
- approximate output lines: 39
- password: never logged


## DBQ-CLAUDE-I3-005
- timestamp: 2026-11-07T23:23:26.4696984+03:00
- agent: CLAUDE
- purpose: Size and date range of the STOPPED losses left out of the selection (F-I3-01). Columns are proven by I2-008.
- SQL file: DBQ-CLAUDE-I3-005-CLAUDE.sql
- exit code: 0
- duration_ms: 61
- approximate output lines: 22
- password: never logged


## DBQ-CLAUDE-I4-001
- timestamp: 2026-11-07T23:31:54.6455732+03:00
- agent: CLAUDE
- purpose: Date when FIX-154 and the other recent migrations were deployed, relative to the October trades (U-I4-1, F-I4-08). Uses the standard Flyway history table in the trader schema.
- SQL file: DBQ-CLAUDE-I4-001-CLAUDE.sql
- exit code: 0
- duration_ms: 33
- approximate output lines: 17
- password: never logged


## DBQ-CLAUDE-I4-002
- timestamp: 2026-11-07T23:31:54.9978277+03:00
- agent: CLAUDE
- purpose: Immutable record of the 5m and 1h signals used to approve each frozen entry, including 158's 5m trigger (U-I4-2, F-I4-04, F-I4-05). Columns come from V107.
- SQL file: DBQ-CLAUDE-I4-002-CLAUDE.sql
- exit code: 0
- duration_ms: 50
- approximate output lines: 5
- password: never logged


## DBQ-CLAUDE-I4-003
- timestamp: 2026-11-07T23:32:23.7493722+03:00
- agent: CLAUDE
- purpose: How often the 1m/5m race happens over all boundary signals, by UTC day (U-I4-3, F-I4-02, F-I4-03).
- SQL file: DBQ-CLAUDE-I4-003-CLAUDE.sql
- exit code: 0
- duration_ms: 28481
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I4-004
- timestamp: 2026-11-07T23:32:26.5007790+03:00
- agent: CLAUDE
- purpose: For every executed entry, the 5m signal the code would have selected (latest by generated_at at or before the 1m, the same rule as `latestAtOrBefore`), with its approval-quality fields and the paper outcome. Sizes F-I4-04 (5m `final_entry_allowed=0`) and F-I4-05 (bearish 5m masked as NEUTRAL).
- SQL file: DBQ-CLAUDE-I4-004-CLAUDE.sql
- exit code: 0
- duration_ms: 2435
- approximate output lines: 203
- password: never logged


## DBQ-CLAUDE-I5-001
- timestamp: 2026-11-07T23:41:42.0910206+03:00
- agent: CLAUDE
- purpose: Persisted entry_quality_score, stage and allocation for the six frozen entries (U-I5-1). `wallet_managed_position` has no signal_id, so this matches on symbol and opened_at within 15 s of paper_position.opened_at. Columns come from V29 and V46.
- SQL file: DBQ-CLAUDE-I5-001-CLAUDE.sql
- exit code: 0
- duration_ms: 52
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I5-002
- timestamp: 2026-11-07T23:41:42.4095922+03:00
- agent: CLAUDE
- purpose: Full untruncated decision explanations, to read the Entry Quality text for 143 (36524, and 36481 for comparison) and 158 (37621) (U-I5-1).
- SQL file: DBQ-CLAUDE-I5-002-CLAUDE.sql
- exit code: 0
- duration_ms: 27
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I5-003
- timestamp: 2026-11-07T23:41:42.9404724+03:00
- agent: CLAUDE
- purpose: The 30-minute 1m evidence window behind each entry, to reconstruct the Entry Quality reference low, the recent high, rejections, regime and liquidity status (F-I5-02, U-I5-1). Columns come from I2-007, V18 and V19.
- SQL file: DBQ-CLAUDE-I5-003-CLAUDE.sql
- exit code: 0
- duration_ms: 224
- approximate output lines: 192
- password: never logged


## DBQ-CLAUDE-I5-004
- timestamp: 2026-11-07T23:41:43.3119209+03:00
- agent: CLAUDE
- purpose: Does Entry Quality predict outcome? Groups all completed paper positions (CLOSED and STOPPED) by entry_quality_score band, using the same 15 s match as I5-001 (U-I5-2).
- SQL file: DBQ-CLAUDE-I5-004-CLAUDE.sql
- exit code: 0
- duration_ms: 91
- approximate output lines: 4
- password: never logged


## DBQ-CLAUDE-I5-005
- timestamp: 2026-11-07T23:41:43.6185123+03:00
- agent: CLAUDE
- purpose: Per-symbol view of the stop-floor geometry (F-I5-01, F-I5-05, U-I5-3): ATR %, how many entries had reward:risk below 1, and the outcome.
- SQL file: DBQ-CLAUDE-I5-005-CLAUDE.sql
- exit code: 0
- duration_ms: 26
- approximate output lines: 19
- password: never logged


## DBQ-CLAUDE-I5-006
- timestamp: 2026-11-07T23:41:43.9250932+03:00
- agent: CLAUDE
- purpose: ATR risk plans of the 5m signals each entry relied on, plus the superseding 5m 159407 (F-I5-04, U-I5-4). Columns are proven by I2-007.
- SQL file: DBQ-CLAUDE-I5-006-CLAUDE.sql
- exit code: 0
- duration_ms: 26
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I6-001
- timestamp: 2026-11-07T23:48:04.0680478+03:00
- agent: CLAUDE
- purpose: Maximum favourable and adverse price for each frozen trade between entry and exit (U-I6-1). The table and columns are proven by I3-003.
- SQL file: DBQ-CLAUDE-I6-001-CLAUDE.sql
- exit code: 0
- duration_ms: 1177
- approximate output lines: 6
- password: never logged


## DBQ-CLAUDE-I6-002
- timestamp: 2026-11-07T23:48:04.4310730+03:00
- agent: CLAUDE
- purpose: Entry Quality band by volatility band, with loss rate, stop-out rate and PnL as a percentage of cost (U-I6-3, F-I6-03). Uses the same 15 s match as I5-004.
- SQL file: DBQ-CLAUDE-I6-002-CLAUDE.sql
- exit code: 0
- duration_ms: 80
- approximate output lines: 12
- password: never logged


## DBQ-CLAUDE-I6-003
- timestamp: 2026-11-07T23:48:04.7370284+03:00
- agent: CLAUDE
- purpose: Stop distance in 1m ATR multiples for stopped versus closed positions (U-I6-4).
- SQL file: DBQ-CLAUDE-I6-003-CLAUDE.sql
- exit code: 0
- duration_ms: 35
- approximate output lines: 8
- password: never logged


## DBQ-CLAUDE-I6-004
- timestamp: 2026-11-07T23:48:05.0370633+03:00
- agent: CLAUDE
- purpose: Outcome by the 1m decision and regime at execution, across the population (U-I6-5, F-I6-02).
- SQL file: DBQ-CLAUDE-I6-004-CLAUDE.sql
- exit code: 0
- duration_ms: 25
- approximate output lines: 13
- password: never logged


## DBQ-CLAUDE-I6-005
- timestamp: 2026-11-07T23:48:07.7711714+03:00
- agent: CLAUDE
- purpose: How many winning paper trades would turn into losses at the assumed 0.2% round-trip fee (F-I6-05), and how often the 1m decision flips from BUY to bearish in the following minute.
- SQL file: DBQ-CLAUDE-I6-005-CLAUDE.sql
- exit code: 0
- duration_ms: 2457
- approximate output lines: 3
- password: never logged


## DBQ-CLAUDE-I7-001
- timestamp: 2026-11-07T23:55:25.6399648+03:00
- agent: CLAUDE
- purpose: For each STOPPED position, the price range in the 30 minutes after the stop-out compared with entry and target. This tests whether stops were hit by noise or by real moves (U-I7-1, F-I7-04). The market_price_event columns were proven by I3-003 and I6-001.
- SQL file: DBQ-CLAUDE-I7-001-CLAUDE.sql
- exit code: 0
- duration_ms: 13891
- approximate output lines: 75
- password: never logged


## DBQ-CLAUDE-I7-002
- timestamp: 2026-11-07T23:55:25.9719888+03:00
- agent: CLAUDE
- purpose: Check the unit of `atr_percent` and the ATR band of each frozen trade (U-I7-2, F-I7-08), and get total traded value for fee drag (F-I7-07).
- SQL file: DBQ-CLAUDE-I7-002-CLAUDE.sql
- exit code: 0
- duration_ms: 25
- approximate output lines: 7
- password: never logged


## DBQ-CLAUDE-I7-003
- timestamp: 2026-11-07T23:55:26.2864406+03:00
- agent: CLAUDE
- purpose: Count completed positions that had a progressive add, which changes `stop_loss` and `entry_price` and distorts I6-003 (U-I7-3). The column name `entry_reason` is inferred from `PaperPosition.setEntryReason` and should be checked against information_schema first; it is kept as a separate query so a schema mismatch does not block the others.
- SQL file: DBQ-CLAUDE-I7-003-CLAUDE.sql
- exit code: 0
- duration_ms: 47
- approximate output lines: 2
- password: never logged


## DBQ-CLAUDE-I7-004
- timestamp: 2026-11-08T00:00:43.2217776+03:00
- agent: CLAUDE
- purpose: Outcome of positions whose 1m execution signal came within 1–3 minutes after a 1m STRONG_SELL on the same symbol, against all other positions (U-I7-4, F-I7-06). Columns were proven by I6-005.
- SQL file: DBQ-CLAUDE-I7-004-CLAUDE.sql
- exit code: 0
- duration_ms: 316567
- approximate output lines: 2
- password: never logged

