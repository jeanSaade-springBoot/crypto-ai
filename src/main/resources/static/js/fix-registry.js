(() => {
    const FIXES = [
        {
            id: "FIX-051",
            title: "Administration coin configuration pagination",
            status: "ACTIVE · UI/ADMIN ONLY",
            scenario: "A newly configured enabled coin such as SUIUSDT existed in coin_configuration but could be difficult to reach/verify in Administration because the coin table had no pagination controls.",
            symbol: "SUIUSDT / all configured symbols", entry: "N/A", exit: "N/A",
            entryTime: "2026-08-22 KSA administration review", exitTime: "N/A",
            replayWindow: "No replay required; Administration UI-only change",
            location: "Administration > Coin configuration table",
            classes: ["administration.html", "administration.js", "administration.css"],
            cause: "The /api/administration/coins endpoint returned the full sorted configuration list, but the browser rendered it as one unpaged tbody with no paging state or range indicator.",
            solution: "Add client-side 10/25/50 row pagination with Previous/Next, page/range indicators, and automatic navigation to the page containing a newly added coin. Preserve the existing API and coin activation semantics.",
            behavior: "All configured coins remain loaded from coin_configuration and are reachable through paging. A newly added pair such as SUIUSDT is brought into view immediately after a successful add. Enable/disable/remove continue refreshing the current valid page. No trading, Replay, wallet or timestamp behavior changes.",
            regression: "With more than 10 coin_configuration rows, verify pages and range counts, change page size, add a new coin and confirm its page is shown, then enable/disable/remove and confirm pagination stays valid."
        },

        {
            id: "FIX-050",
            title: "Trade Activity natural mandatory-filter switching + compact menu",
            status: "ACTIVE · UI/AUDIT ONLY",
            scenario: "The final EXECUTED or BUY checkbox appeared impossible to clear because FIX-049 immediately re-checked the same box; the desktop left navigation was also slightly wider/spacier than desired.",
            symbol: "ALL / any activity symbol", entry: "N/A", exit: "N/A",
            entryTime: "2026-08-22 KSA observation", exitTime: "N/A",
            replayWindow: "No replay required; frontend-only interaction/layout change",
            location: "Trade Activity checkbox interaction + shared desktop sidebar CSS",
            classes: ["trade-activity.js", "dashboard.css"],
            cause: "FIX-049 preserved mandatory filter groups by restoring the checkbox that had just been unchecked. This met the backend rule but made EXECUTED/BLOCKED and BUY/SELL feel stuck. Desktop sidebar spacing also consumed more width than needed.",
            solution: "When the last selected checkbox in a mandatory group is cleared, select its peer instead of re-checking the same box. Keep one-or-both selection in each group. Reduce expanded desktop sidebar width from 240px to 220px and slightly tighten navigation padding/gaps; collapsed width remains 76px.",
            behavior: "Unchecking sole EXECUTED switches to BLOCKED; unchecking sole BLOCKED switches to EXECUTED. The same rule applies to BUY/SELL. Selecting both still works and either one can then be cleared. Filter semantics remain (BUY or SELL) AND (EXECUTED or BLOCKED) AND symbol. No trading behavior changes.",
            regression: "Verify each group can switch naturally, can hold both values, and can never become empty. Verify Search still returns the strict FIX-049 backend semantics. Confirm desktop sidebar is slightly narrower while mobile/collapsed layouts remain unchanged."
        },

        {
            id: "FIX-049",
            title: "Trade Activity strict direction AND state filters",
            status: "ACTIVE · UI/AUDIT ONLY",
            scenario: "Trade Activity filters were inconsistent because BUY/SELL and EXECUTED/BLOCKED behaved as additive or exclusive sources instead of two required filter dimensions.",
            symbol: "ALL / any activity symbol", entry: "N/A", exit: "N/A",
            entryTime: "2026-08-22 KSA observation", exitTime: "N/A",
            replayWindow: "No replay required; read-only activity-query semantics",
            location: "Trade Activity frontend/backend filtering + Dashboard connection-status cleanup",
            classes: ["TradeActivityService", "trade-activity.js", "trade-activity.html", "dashboard.js", "dashboard.html"],
            cause: "The UI allowed empty direction/state combinations and the backend treated BLOCKED/EXECUTED as alternate source modes. This did not match the operator's intended boolean filter: (BUY OR SELL) AND (BLOCKED OR EXECUTED) AND symbol.",
            solution: "Split filters into mandatory Direction and State groups. Require BUY and/or SELL, require EXECUTED and/or BLOCKED, apply the selected direction to both wallet executions and blocked opportunities, union the selected states, and apply the selected symbol to every branch. Prevent either checkbox group from becoming empty. Remove the dashboard 'API connected / Auto refresh every 10s' status and keep its refresh helper DOM-safe.",
            behavior: "BUY + EXECUTED returns executed BUYs only. SELL + BLOCKED returns blocked SELL opportunities only. BUY+SELL + EXECUTED+BLOCKED returns all matching executed and blocked activity. Symbol ALL keeps all symbols; a selected symbol restricts every result. Database timestamps remain UTC and frontend display remains Asia/Riyadh.",
            regression: "Verify all direction/state combinations against wallet_trade and execution_opportunity for one symbol and ALL. Confirm neither Direction nor State can become empty, no signal-only rows appear, and the dashboard no longer shows API connected / Auto refresh every 10s. No Production/Replay trading behavior changes."
        },

        {
            id: "FIX-048",
            title: "Trade Activity hierarchical filter semantics",
            status: "ACTIVE · UI/AUDIT ONLY",
            scenario: "Operator expects BUY + symbol to show all BUY signals, BUY + EXECUTED + symbol to show only real executed BUYs, EXECUTED alone to show all real wallet executions, and BLOCKED to show only blocked activity.",
            symbol: "ALL / any activity symbol", entry: "N/A", exit: "N/A",
            entryTime: "2026-08-22 KSA observation", exitTime: "N/A",
            replayWindow: "No replay required; read-only activity-query semantics",
            location: "Trade Activity backend filter semantics and frontend checkbox behavior",
            classes: ["TradeActivityService", "trade-activity.js", "trade-activity.html"],
            cause: "BUY, SELL, BLOCKED and EXECUTED were implemented as independent additive sources. That made combined filters ambiguous and did not let BUY distinguish matching wallet-executed signals from signals that were never executed.",
            solution: "Make BLOCKED exclusive; make EXECUTED a wallet-ledger narrowing filter for BUY/SELL; make EXECUTED alone return all executed BUY/SELL wallet actions; make BUY/SELL alone return all side signals and mark each signal EXECUTED only when a matching wallet_trade exists for its signal_id.",
            behavior: "BUY + PEPEUSDT shows every PEPE BUY signal with EXECUTED or SIGNAL status. BUY + EXECUTED + PEPEUSDT shows only real executed PEPE BUY wallet rows. EXECUTED + PEPEUSDT shows all executed PEPE BUY/SELL rows. BLOCKED + PEPEUSDT shows only blocked/cancelled PEPE execution opportunities. No trading behavior changes.",
            regression: "Verify the four filter combinations against direct trade_signal, wallet_trade and execution_opportunity queries for the same symbol/time window. Database timestamps remain UTC and frontend display remains Asia/Riyadh."
        },

        {
            id: "FIX-047",
            title: "Trade Activity activity-type filter refresh",
            status: "ACTIVE · UI/AUDIT ONLY",
            scenario: "Operator searches BUY + EXECUTED, then unchecks BUY, but stale BUY SIGNAL rows remain visible and appear to be EXECUTED results.",
            symbol: "ALL / any activity symbol", entry: "N/A", exit: "N/A",
            entryTime: "2026-08-22 KSA observation", exitTime: "N/A",
            replayWindow: "No replay required; read-only activity filtering regression",
            location: "Trade Activity activity-type checkbox refresh",
            classes: ["trade-activity.js"],
            cause: "Changing BUY/SELL/BLOCKED/EXECUTED checkboxes did not trigger a new query, so previously rendered rows stayed on screen until Search was pressed again.",
            solution: "Refresh immediately whenever an activity-type checkbox changes. When no activity type remains selected, clear the stale table and reset the result count.",
            behavior: "With only EXECUTED selected, SIGNAL rows disappear immediately. If the symbol has no wallet_trade EXECUTED rows, Trade Activity shows no matching activity. No Production, Replay, scoring, entry, exit, sizing or wallet behavior changes.",
            regression: "Search BUY + EXECUTED for PEPEUSDT, uncheck BUY and confirm all SIGNAL / INITIAL_SIGNAL rows disappear immediately. With only EXECUTED selected and zero PEPEUSDT wallet_trade rows, result must be empty."
        },

        {
            id: "FIX-046",
            title: "Trade Activity symbol filter source and refresh correction",
            status: "ACTIVE · UI/AUDIT ONLY",
            scenario: "Selecting a specific symbol in Trade Activity appeared not to work and some valid activity symbols could be missing from the dropdown.",
            symbol: "ALL / any activity symbol", entry: "N/A", exit: "N/A",
            entryTime: "2026-08-22 KSA observation", exitTime: "N/A",
            replayWindow: "No replay required; read-only activity filtering regression",
            location: "Trade Activity symbol metadata and frontend filter refresh",
            classes: ["TradeActivityService", "trade-activity.js"],
            cause: "The symbol selector was populated only from enabled wallet_asset rows even though Trade Activity reads trade_signal, execution_opportunity and wallet_trade. In addition, changing the dropdown had no change handler, so stale All-symbol results remained visible until Search was pressed again.",
            solution: "Build the dropdown from the union of symbols actually present in Trade Activity evidence sources and refresh the current query when the selected symbol changes and at least one activity type is selected.",
            behavior: "Selecting ACEUSDT, BTCUSDT or another activity symbol now immediately applies that symbol to the read-only activity query. No Production, Replay, scoring, entry, exit, sizing or wallet behavior changes. UTC database timestamps remain UTC and frontend display remains Asia/Riyadh.",
            regression: "Search with BUY/SELL/BLOCKED/EXECUTED, then switch between All symbols and a specific symbol and confirm every returned row matches the selected symbol. Confirm activity-only symbols not currently held in wallet_asset still appear in the dropdown."
        },

        {
            id: "FIX-045",
            title: "Trade Inspector optional diagnostics DOM guard",
            status: "ACTIVE · UI ONLY",
            scenario: "Trade Inspector crashed on page load after Trade Activity was separated into its own page.",
            symbol: "ALL", entry: "N/A", exit: "N/A",
            entryTime: "2026-08-22 16:35 KSA observation", exitTime: "N/A",
            replayWindow: "No replay required; frontend-only regression",
            location: "Trade Inspector frontend initialization",
            classes: ["trade-inspector.js", "trade-inspector.html"],
            cause: "Legacy FIX-039 blocked-signal diagnostic JavaScript still initialized blocked-signal-from / blocked-signal-to controls even though FIX-044 moved Trade Activity away from Trade Inspector and the corresponding DOM controls are no longer present.",
            solution: "Guard all optional blocked-signal and production-exit diagnostic helpers behind explicit DOM-presence checks. Missing optional panels now no-op instead of aborting the primary Trade Inspector load.",
            behavior: "Trade Inspector loads its normal completed-trade inspection UI even when legacy diagnostic controls are absent. No Production, Replay, scoring, entry, exit, sizing, wallet, database timestamp, or timezone behavior changes.",
            regression: "Open /trade-inspector and confirm there is no Cannot set properties of null error; completed trades load normally. UTC database timestamps continue to be parsed as UTC and rendered through the existing frontend time utilities."
        },

        {
            id: "FIX-044",
            title: "Trade Activity separated from Trade Inspector",
            status: "ACTIVE · UI/AUDIT ONLY",
            scenario: "Operator needs a compact on-demand view of BUY, SELL, BLOCKED and EXECUTED events without mixing operational activity into forensic Trade Inspector.",
            symbol: "ALL", entry: "N/A", exit: "N/A", entryTime: "N/A", exitTime: "N/A",
            location: "Trade Activity page / read-only API",
            classes: ["TradeActivityController", "TradeActivityService", "trade-activity.html", "trade-activity.js"],
            cause: "FIX-039 diagnostics made Trade Inspector carry operational signal/block/execution activity and loaded data automatically.",
            solution: "Move operational activity to its own left-menu page. Query only after Search with BUY/SELL/BLOCKED/EXECUTED, symbol and 1/2/4/24-hour filters.",
            behavior: "Read-only projection of persisted trade_signal, execution_opportunity and wallet_trade evidence. Reasons are short persisted keywords; wake-up versus initial execution source is normalized for fast diagnosis.",
            regression: "MUST NOT change AnalysisService, FinalDecisionService, ExecutionIntelligenceService, wake-up logic, wallet execution, position management, Replay or any proven trading fix."
        },

        {
            id: "FIX-043",
            title: "Restore true per-candle production analysis and chronological missed-candle recovery",
            status: "ACTIVE",
            area: "Market Data / Technical Indicators / Analysis Cadence / Replay Parity",
            scenario: "ACEUSDT and all enabled symbols · 22 Aug 2026 · nominal 1m engine was effectively analyzing only every ~5-6 minutes",
            symbol: "ALL",
            entry: "ACE example: underlying BUY 82 existed at 12:00 KSA near 0.2339, but technical analysis then skipped 12:01-12:07 while price accelerated",
            exit: "No exit rule changed; this fix restores analysis delivery before any BUY/SELL decision is made",
            entryTime: "Observed production window 12:00-13:30 KSA on 22 Aug 2026",
            replayWindow: "Replay must generate every closed candle chronologically; never sample at the recovery scheduler cadence",
            location: "Binance closed-candle event -> asynchronous indicator/signal pipeline + ScheduledAnalysisService chronological recovery",
            classes: [
                "com.crypto.config.CandleAnalysisAsyncConfig",
                "com.crypto.indicator.event.CandleAnalysisDispatcher",
                "com.crypto.indicator.event.CandleClosedAnalysisWorker",
                "com.crypto.indicator.event.CandleClosedEventListener",
                "com.crypto.service.ScheduledAnalysisService",
                "com.crypto.repository.CandleRepository",
                "com.crypto.service.AnalysisService",
                "com.crypto.regression.service.RegressionTestWorker",
                "com.crypto.service.CandleClosedEventListenerTest",
                "com.crypto.service.CandleAnalysisDispatcherTest",
                "com.crypto.service.CandleClosedAnalysisWorkerTest",
                "com.crypto.service.ScheduledAnalysisServiceTest"
            ],
            cause: "Production database evidence proved candle ingestion was healthy (96.7-100% of 1m candles) while technical_indicator/trade_signal coverage was only ~17-20% across every enabled symbol. The heavy AFTER_COMMIT listener executed indicator, analysis and wallet work synchronously, while the five-minute recovery job calculated only the newest closed candle. When event processing fell behind, recovery jumped over all intervening candles, so a nominal 1m strategy behaved like a ~5m/6m strategy and missed the exact transition candles needed for early entries, opportunity memory and wake-up evidence.",
            solution: "Hand committed candle-close events to a FIFO-per-symbol/timeframe dispatcher backed by a dedicated bounded executor, so Binance ingestion is not held hostage by indicator/scoring/wallet latency while adjacent candles can never be processed out of order. Convert ScheduledAnalysisService from latest-only calculation into a chronological missing-analysis scan sourced from persisted CLOSED candles. Reuse an existing technical_indicator when only trade_signal is missing. Recovered signals are rebuilt with AnalysisService as-of the original candle close so current/future context can never leak into historical production recovery. Scan every minute as a safety net. Historical recovered signals restore continuity/audit/context but are forbidden from executing at an old candle price; only the latest still-fresh recovered candle may reach PaperTradingService. Replay keeps its existing every-closed-candle chronological generation and is explicitly protected from scheduler-style sampling.",
            protectedBehavior: "NO scoring or strategy thresholds changed. Preserve FIX-014 SETUP_CONFIRMATION_WAKEUP, FIX-020 evidence reset, FIX-021 ACCUMULATED_EVIDENCE, FIX-026 recovery probe, FIX-041 late BALANCED_EARLY guard, FIX-042 RANGE entry-location veto, BTC/MTF/ATR/derivatives/liquidity vetoes, all SELL/TP/SL/profit-lock logic, and the shared production ExecutionIntelligenceService used by Replay.",
            behavior: "Normal production target is one indicator/signal evaluation for each eligible closed candle instead of ~17-20% coverage. If an event is temporarily missed, recovery fills the missing sequence oldest-to-newest rather than jumping directly to the latest candle. Recovery cannot create a stale historical BUY/SELL execution. Replay continues evaluating every candle in exact chronological order, so Production and Replay now share the same no-gap analysis contract.",
            regression: "CandleClosedEventListenerTest asserts the production listener only dispatches and never runs heavy work inline. CandleAnalysisDispatcherTest asserts same-stream FIFO scheduling. ScheduledAnalysisServiceTest proves multiple missing candles are recovered in chronological order, historical backfill cannot execute at stale prices, and only a fresh latest recovered candle may execute. Post-deploy SQL acceptance: 1m technical_indicator/trade_signal coverage should move from ~17-20% toward candle coverage (~97-100%) for enabled symbols."
        },
        {
            id: "FIX-042",
            title: "Wire proven RANGE entry-location guard into production/replay decision path",
            status: "ACTIVE",
            area: "Analysis / Final Decision / RANGE_MEAN_REVERSION",
            scenario: "ETHUSDT signal #109885 · 21 Aug 2026",
            entry: "Historical bad entry 2391.22 while price was ~64.26% through the Bollinger range",
            exit: "Historical position stopped out; this fix blocks the same high-range ordinary mean-reversion entry",
            cause: "FIX-036 RangeEntryLocationService existed and had unit coverage but was never invoked by AnalysisService, so live and replay signals bypassed the protection.",
            solution: "AnalysisService now evaluates RangeEntryLocationService after the final strategy score and FinalDecisionService records RANGE_ENTRY_LOCATION as a one-way veto stage. Scores and directional BUY/STRONG_BUY remain unchanged; only immediate entry authority is blocked when the proven range-location rule fails.",
            protectedBehavior: "Do not apply this guard to TREND_FOLLOWING/BREAKOUT/DEFENSIVE strategies. Preserve the strict expansion exception, SETUP_CONFIRMATION_WAKEUP, ACCUMULATED_EVIDENCE, recovery/scout probes, SELL logic, ATR authority and all existing context vetoes. Production and Replay must continue sharing AnalysisService/FinalDecisionService.",
            tests: "RangeEntryLocationServiceTest plus FinalDecisionServiceRangeEntryLocationTest: ETH #109885-style >55% range BUY blocks; lower/middle range BUY passes; strict expansion exception remains allowed.",
            files: [
                "AnalysisService.java",
                "FinalDecisionService.java",
                "RangeEntryLocationService.java",
                "FinalDecisionServiceRangeEntryLocationTest.java",
                "fix-registry.js"
            ]
        },
        {
            id: "FIX-041",
            status: "IMPLEMENTED",
            title: "BALANCED_EARLY cannot execute an already-late initial entry",
            scenario: "ACEUSDT 2026-08-22 13:28 KSA entered at 0.2603 after a long opportunity build and was already classified LATE_ENTRY 53/100",
            symbol: "ACEUSDT",
            entry: "Blocked replacement for late BALANCED_EARLY initial BUY at 0.2603",
            exit: "Historical trade stopped at 0.2540 (-2.42%); FIX-041 prevents this late initial entry path from opening",
            entryTime: "2026-08-22 13:28 KSA",
            exitTime: "2026-08-22 13:39 KSA",
            replayWindow: "2026-08-22 11:20 KSA → 2026-08-22 13:45 KSA",
            location: "Execution Intelligence direct BUY validation → BALANCED_EARLY entry-quality gate",
            classes: [
                "com.crypto.execution.service.ExecutionIntelligenceService",
                "com.crypto.execution.service.ExecutionIntelligenceServiceTest",
                "com.crypto.regression.service.ShadowProductionReplayService"
            ],
            cause: "BALANCED_EARLY correctly represents a reduced early entry when 5m and 1h are only WATCH, but the generic Entry Quality guard allowed a 50-54/100 LATE_ENTRY by merely capping size to 25%. ACE therefore opened at 0.2603 even though the same execution decision explicitly said LATE_ENTRY 53/100.",
            solution: "Only for the direct IMMEDIATE_VALIDATION + BALANCED_EARLY route, keep the opportunity alive instead of executing when Entry Quality classification is LATE_ENTRY. CHASE_ENTRY remains blocked by the existing generic guard. ACCEPTABLE/GOOD/EXCELLENT BALANCED_EARLY entries continue unchanged. Setup-timeframe ATR authority, setup wake-up, accumulated evidence, pressure/recovery probes, SELL logic and all other existing routes are untouched.",
            behavior: "A BALANCED_EARLY decision must now be early in both context and price. If price quality has already degraded to LATE_ENTRY, no new position is opened; opportunity memory remains available for a better price or later fresh confirmation.",
            regression: "ACE regression asserts LATE_ENTRY BALANCED_EARLY returns BUILDING with BALANCED_EARLY_LATE_ENTRY_BLOCKED. A control test proves an acceptable-or-better BALANCED_EARLY entry still executes. Production and Replay share ExecutionIntelligenceService, so no replay-only rule was added."
        },
        {
            id: "FIX-040",
            status: "IMPLEMENTED",
            title: "Trade graph uses explicit 24-hour KSA timestamps",
            scenario: "Trade Inspector chart review should use one unambiguous time convention for analysis",
            symbol: "ALL",
            entry: "Chart X-axis and crosshair time labels",
            exit: "Display-only formatting change; no execution behavior changed",
            entryTime: "KSA 24-hour display",
            exitTime: "KSA 24-hour display",
            location: "Trade Inspector chart presentation",
            classes: ["src/main/resources/static/js/trade-inspector.js"],
            cause: "The trade chart disabled AM/PM but still formatted through the browser locale/time zone, so analysis could require manual UTC/browser-local to KSA conversion.",
            solution: "Render Trade Inspector chart axis and crosshair timestamps explicitly in Asia/Riyadh using a 24-hour clock while preserving UTC timestamps internally for candle positioning.",
            behavior: "Trade graph timestamps now display in KSA 24-hour format consistently with Trade Path and blocked-signal diagnostics.",
            regression: "Open an inspected trade chart from any browser zone and verify axis/crosshair time matches KSA (UTC+03:00) in 24-hour format; candle positions and trading behavior remain unchanged."
        },
        {
            id: "FIX-001",
            status: "IMPLEMENTED",
            title: "Unconfirmed 1m Bollinger/RVOL breakout received full BREAKOUT BUY authority",
            scenario: "SHIBUSDT trade #166 / signal #81358",
            symbol: "SHIBUSDT",
            entry: "BUY 0.000004490000",
            exit: "SELL 0.000004460000 · STOP_LOSS · -0.668151%",
            entryTime: "2026-08-17 18:43:11 UTC (21:43:11 KSA)",
            exitTime: "2026-08-17 19:07:59 UTC (22:07:59 KSA)",
            location: "Regime detection → dynamic strategy scoring → early-breakout promotion",
            classes: [
                "com.crypto.service.MarketRegimeService",
                "com.crypto.service.TrendStructureService",
                "com.crypto.dto.TrendStructureResult",
                "com.crypto.service.MarketStrategyService",
                "com.crypto.service.AnalysisService",
                "com.crypto.domain.MarketRegime"
            ],
            cause: "MarketRegimeService classified BREAKOUT from price at/above the upper Bollinger band plus RVOL >= 1.50. On #81358 the close was only ~0.0051% above the upper band, RVOL was 3.377, and the recent high 0.00000449 had already been traded. TrendStructureService did not confirm bullish structural expansion, but that stronger evidence was not connected to regime authorization. BREAKOUT weighting then normalized 58/70 to BUY 83.",
            solution: "Introduce BREAKOUT_CANDIDATE for bullish Bollinger+RVOL events without structural confirmation. Reuse the already-computed TrendStructureResult and expose bullishExpansionConfirmed. Only structurally confirmed events receive full BREAKOUT regime authority. Candidate scores are capped to WATCH before execution; they may still use the existing early-breakout promotion path only when breakout-preparation structure is present and the existing HTF/ATR safety checks pass. This preserves legitimate early probes without treating a small Bollinger poke as a confirmed breakout.",
            behavior: "For a scenario like SHIB #81358, the signal remains visible as a high-scoring breakout candidate for diagnostics, but it cannot become a normal BUY solely because volume/momentum weighting normalizes above 80. With no compression/structural breakout preparation, it remains WATCH instead of opening the trade. Confirmed structural breakouts continue to use the normal BREAKOUT profile and thresholds.",
            regression: "Replay SHIBUSDT 1m at 2026-08-17 18:42 UTC: expect BREAKOUT_CANDIDATE and no ordinary BUY when bullishExpansionConfirmed=false and breakoutPreparationScore=0. Also rerun Proven Analyzed Trades to verify confirmed breakout entries are unchanged."
        },
        {
            id: "FIX-002",
            status: "REFINED BY FIX-006",
            title: "Early pressure/release entry path without changing normal 1m/5m/1h BUY confirmation",
            scenario: "SHIBUSDT early-move investigation before trade #166",
            symbol: "SHIBUSDT",
            entry: "Expected pressure probe around 0.00000445-0.00000446 when the production signal cadence next evaluates the confirmed release/retest",
            exit: "Managed by the existing position/exit engine; this fix does not replace normal exits",
            entryTime: "Historical evidence window: 2026-08-17 ~12:55-13:05 UTC (15:55-16:05 KSA)",
            exitTime: "Scenario-specific exit is intentionally not hard-coded; normal production exit logic remains authoritative",
            location: "Execution Intelligence early-entry side path, after existing normal/special entry routes and before accumulated-evidence fallback",
            classes: [
                "com.crypto.execution.service.PressureReadinessService",
                "com.crypto.execution.service.ExecutionIntelligenceService",
                "com.crypto.regression.service.ShadowProductionReplayService",
                "com.crypto.execution.service.PressureReadinessServiceTest",
                "com.crypto.execution.service.ExecutionIntelligenceServiceTest"
            ],
            cause: "The normal indicator path correctly stayed NEUTRAL/WATCH while 5m and 1h were still bearish/recovering, but raw closed-candle behavior showed an earlier sequence that the signal score did not consume: weighted taker pressure near resistance, bullish structural release, then heavy sell pressure that failed to reverse price. The existing engine therefore had no controlled way to take a small position while higher timeframes were transitioning; it could only wait for later normal confirmation.",
            solution: "Add a read-only PressureReadinessService that aggregates the real closed 1m candles into 5m pressure buckets using weighted taker-buy volume, 30m high/low structure, abnormal volume and price response. ExecutionIntelligenceService may open only a 15% PRESSURE_PROBE_ENTRY when a recent BULLISH_RELEASE exists, the current final 1m decision is non-bearish with minimum quality, fresh 5m context is no longer bearish and has recovered from recent bearish context (or is already WATCH/BUY), 1h is not STRONG_SELL, and every existing hard-risk/ATR/risk-plan/entry-quality authority still passes. A current normal direct BUY is explicitly excluded from the pressure route, so normal validation keeps priority.",
            behavior: "The old normal BUY path is unchanged. Pressure building alone never buys. The new path is a small early-positioning exception only after a proven release plus MTF recovery. PRESSURE_PROBE_ENTRY remains a BUILDING opportunity so the existing progressive confirmation logic can add later; it does not duplicate full normal sizing. FinalDecision remains authoritative and originalDecision is audit-only, preserving the prior decision-authority fix.",
            regression: "Tests call the exact production PressureReadinessService thresholds rather than a copied test algorithm. PressureReadinessServiceTest reproduces the analyzed SHIB bullish-release -> sell-absorption sequence. ExecutionIntelligenceServiceTest proves the 15% probe requires 5m recovery, cannot bypass a still-bearish 5m, and that a valid normal direct BUY wins over the pressure route. Proven/Administration replay already invokes the same production ExecutionIntelligenceService.evaluateBuy(...) path, so the new service is exercised by the same production code with shadow wallet/opportunity persistence rather than a separate replay rule."
        }        ,{
            id: "FIX-003",
            status: "IMPLEMENTED",
            title: "Proven Analysis lifecycle parity with production",
            scenario: "All Proven Analyzed Trades / regression reruns",
            symbol: "ALL",
            entry: "No scenario-specific entry; parity infrastructure fix",
            exit: "No scenario-specific exit; parity infrastructure fix",
            entryTime: "Applies to every historical replay timestamp",
            exitTime: "Applies to every historical replay timestamp",
            location: "Shared production policies used by live wallet/position lifecycle and ShadowProductionReplayService",
            classes: [
                "com.crypto.service.AnalysisService",
                "com.crypto.execution.service.ExecutionIntelligenceService",
                "com.crypto.service.TradeExecutionValidationService",
                "com.crypto.position.service.PositionContinuationPolicy",
                "com.crypto.position.service.PositionExitPolicy",
                "com.crypto.position.service.ProfitLockPolicy",
                "com.crypto.position.service.DynamicProfitLockService",
                "com.crypto.wallet.service.WalletExecutionSizingPolicy",
                "com.crypto.wallet.service.WalletAutoExecutionService",
                "com.crypto.regression.service.ShadowProductionReplayService"
            ],
            cause: "A parity audit found that Proven already reused production AnalysisService.buildSignal, ExecutionIntelligenceService.evaluateBuy, PositionContinuationPolicy and PositionExitPolicy, but shadow replay still duplicated Dynamic Profit Lock progression, used its own 10,000 x position-percent wallet spend calculation, and did not also invoke the production TradeExecutionValidationService SELL path. Those copies could drift from live behavior even when signal decisions were identical.",
            solution: "Centralize Dynamic Profit Lock progression in ProfitLockPolicy and wallet reserve/budget/allocation math in WalletExecutionSizingPolicy; call those same policies from both production and Proven. Proven also calls the same TradeExecutionValidationService.validateSell used by production signal exits. Analysis remains one shared buildSignal implementation; replay only swaps live market-context observations for historical/as-of observations to prevent future-data leakage. Shadow persistence remains isolated from production tables.",
            behavior: "Proven Analyzed Trades no longer carries independent trading formulas for the corrected areas. Production and replay share scoring/final decision, normal and pressure-probe entry intelligence, BUY/SELL MTF validation, position continuation/exit authority, Dynamic Profit Lock math, wallet allocation/budget/reserve math and progressive-add allocation semantics. Replay writes only to test tables.",
            regression: "Added direct tests for the shared ProfitLockPolicy and WalletExecutionSizingPolicy and updated DynamicProfitLockServiceTest to instantiate the shared policy. Existing ExecutionIntelligence/PressureReadiness tests continue to exercise the same production services. Jenkins/Maven must run the complete suite after deployment packaging."
        }
        ,{
            id: "FIX-004",
            status: "IMPLEMENTED",
            title: "Delayed higher-timeframe candle price caused false 4-second STOP_LOSS",
            scenario: "ALLOUSDT wallet trades #164/#165 · position analysis #622",
            symbol: "ALLOUSDT",
            entry: "BUY 0.282200000000 · ACCUMULATED_EVIDENCE · 25%",
            exit: "False SELL 0.280600000000 · POSITION_STOP_LOSS · -0.566974%",
            entryTime: "2026-08-17 18:23:26.470 UTC (21:23:26 KSA)",
            exitTime: "2026-08-17 18:23:30.320 UTC (21:23:30 KSA)",
            location: "Position price authority between delayed TradeSignal context and mechanical TP/SL/profit-lock protection",
            classes: [
                "com.crypto.position.service.PositionPriceAuthorityPolicy",
                "com.crypto.position.service.PositionManagementService",
                "com.crypto.service.PaperTradingService",
                "com.crypto.regression.service.ShadowProductionReplayService",
                "com.crypto.position.service.LivePositionProtectionService",
                "com.crypto.position.service.PositionPriceAuthorityPolicyTest"
            ],
            cause: "ALLOUSDT was legitimately bought at 0.2822. Two seconds later trade_signal #81252 was generated for the older 5m candle opened at 18:15 and closed at 18:19:59. That candle close was 0.2806. PositionManagementService treated signal.latestPrice as if it were the current market price, compared 0.2806 with the immutable stop 0.280789 and created STOP_LOSS analysis #622. WalletAutoExecutionService then sold at the same historical 0.2806 even though the actual 18:23 1m candle low was 0.2819 and ALLO subsequently traded higher. The defect was temporal price authority, not the stop calculation itself.",
            solution: "Introduce PositionPriceAuthorityPolicy as the single production/replay rule for whether a TradeSignal price may drive mechanical protection. It derives the signal's actual market observation time from candle_open_time + interval and forbids TP/SL/profit-lock use when that market observation predates the position open time. Delayed signals remain valid for trend/MTF/thesis context. PositionManagementService and PaperTradingService apply the guard before all signal-price mechanical protection. ShadowProductionReplayService applies the exact same policy so replay cannot retroactively stop a position using a pre-entry candle. LivePositionProtectionService remains the authoritative production mechanical-protection path because it consumes the current live Binance price.",
            behavior: "A delayed 5m/1h signal can still change thesis comparison, MTF state and validated SELL context, but its historical latestPrice cannot manufacture a P/L, TP, SL or profit-lock event for a position that did not exist when that candle price was observed. For ALLO #164, #81252 at 0.2806 is context-only for mechanical price protection, so it cannot generate the false four-second STOP_LOSS.",
            regression: "PositionPriceAuthorityPolicyTest reproduces the exact ALLO timing: 5m candle 18:15 + 5m = 18:20 observation, position opened 18:23:26, therefore 0.2806 is rejected for mechanical protection. A 1m candle whose close occurs after entry is accepted for historical replay. Proven uses this same production policy rather than a copied replay rule. Jenkins/Maven must run the full suite."
        },{
            id: "FIX-005",
            status: "REFINED BY FIX-007",
            title: "Trade Inspector 14-day context and precise crosshair labels",
            scenario: "Trade Inspector historical BUY → SELL chart usability",
            symbol: "ALL",
            entry: "7 days of historical context before BUY",
            exit: "7 days of historical context after SELL",
            entryTime: "UI chart context",
            exitTime: "UI chart context",
            location: "Trade Inspector historical chart rendering",
            classes: [
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/trade-inspector.html",
                "src/main/resources/static/css/trade-inspector.css"
            ],
            cause: "The inspected-trade chart only loaded one day before/after the trade, defaulted to 5m for the entire context, and used generic axis/tooltip formatting. On sparse historical data a literal datetime axis also makes missing bars appear as large visual gaps; fabricating candles to hide those gaps would make the historical chart incorrect.",
            solution: "Expand the requested chart range to seven days before BUY and seven days after SELL, default the wide overview to 1h while keeping 1m/5m/4h selectable, enable x-axis zoom with automatic y scaling, and add crosshair/tooltip formatters that show date/time to the minute and price with asset-sensitive precision. Preserve true datetime spacing so missing market data remains visible rather than inventing candles.",
            behavior: "Trade Inspector opens with a readable 14-day 1h overview. Users can switch to 5m or 1m and zoom into the entry/exit. Hovering shows the corresponding time to the minute on the x-axis and exact price on the y-axis, including sufficient precision for low-price assets such as SHIB/PEPE.",
            regression: "Static JavaScript syntax validation passes. No production trading, replay, signal scoring, execution, or persistence logic is changed by this UI-only fix."
        },{
            id: "FIX-006",
            status: "IMPLEMENTED",
            title: "Sequence-based pressure probe catches an early reversal without weakening the normal BUY path",
            scenario: "SOLUSDT missed early reversal on 2026-08-17 before later wallet trade signal #80012",
            symbol: "SOLUSDT",
            entry: "Expected exploratory probe near 74.62-74.71 around the 00:58-01:00 UTC evaluation window; later production BUY was 75.63 via signal #80012",
            exit: "No historical probe exit is hard-coded. Probe positions remain managed by the existing production position/exit engine and progressive confirmation path.",
            entryTime: "Analyzed early-entry window: 2026-08-17 ~00:58-01:00 UTC (03:58-04:00 KSA); actual later production entry: 14:14:06 UTC (17:14:06 KSA)",
            exitTime: "Not applicable to the missed historical probe; no synthetic exit is introduced by this fix",
            location: "Execution Intelligence pressure-probe side path + closed-candle pressure readiness; normal 1m/5m/1h BUY path remains unchanged",
            classes: [
                "com.crypto.execution.service.PressureReadinessService",
                "com.crypto.execution.service.ExecutionIntelligenceService",
                "com.crypto.regression.service.ShadowProductionReplayService",
                "com.crypto.execution.service.PressureReadinessServiceTest",
                "com.crypto.execution.service.ExecutionIntelligenceServiceTest"
            ],
            cause: "The existing signal engine did see the developing SOL reversal, but the early path could not act safely. A first 5m BREAKOUT/WATCH attempt around 00:48 UTC was rejected, then selling pushed price back without making a new structural low, buyers rebuilt from the higher-low retest, and the 1m returned to WATCH 69 with trend 13, volume 16 and momentum 14. The prior pressure-probe implementation required 5m to have already recovered and blocked 1h STRONG_SELL, so it could not represent this controlled counter-trend discovery phase. Waiting for normal higher-timeframe confirmation surrendered the early-entry advantage.",
            solution: "Keep every normal BUY/MTF/ATR/scoring rule untouched and replace only the pressure-probe detector with a sequence requirement: meaningful bullish burst, rejection with real sell pressure, retest that holds above the pre-burst structural low, repeated bullish pressure rebuild, and price reclaim. Execution remains restricted to current WATCH/NEUTRAL 1m signals with minimum production trend/volume/momentum quality, requires a recent 5m WATCH/BUY BREAKOUT setup, refuses a fresh 5m STRONG_SELL, keeps all existing FinalDecision/strategy/ATR/BTC/liquidity/derivatives gates, and caps exposure at 15%. A normal BUY is explicitly excluded from this route so the probe can never bypass normal validation.",
            behavior: "The old normal BUY path still has priority and behaves exactly as before. A single high taker-buy candle or first breakout attempt does not buy. The SOL false/early burst is observed only; the probe becomes eligible only after rejection, higher-low retest and pressure rebuild are complete. A bearish 1h can coexist with the 15% exploratory probe, but it still blocks normal/full-size confirmation. Existing progressive confirmation can add later; existing position management exits failures.",
            regression: "PressureReadinessServiceTest uses the real SOL 00:20-00:59 UTC candle sequence and proves the detector is NOT ready during the first burst but becomes ready after the higher-low retest/rebuild. It also verifies candle retrieval uses close_time <= generated_at to prevent replay look-ahead. ExecutionIntelligenceServiceTest proves the small SOL probe can coexist with a bearish 1h only when a prior 5m BREAKOUT setup exists, cannot invent that setup from candles alone, and that a valid normal direct BUY keeps priority. Proven/Regression continues to call the exact production ExecutionIntelligenceService.evaluateBuy(...) and PressureReadinessService; only persistence/wallet state is shadowed."
        }

        ,{
            id: "FIX-007",
            status: "IMPLEMENTED",
            title: "Trade Inspector full-history Binance-style navigation",
            scenario: "Trade Inspector forensic chart for all completed wallet trades",
            symbol: "ALL",
            entry: "BUY marker, entry-price line and jump-to-entry navigation",
            exit: "SELL marker, exit-price line and persistent BUY → SELL lifecycle path",
            entryTime: "UI historical navigation; no trading timestamp is modified",
            exitTime: "UI historical navigation; no trading timestamp is modified",
            location: "Trade Inspector chart API and browser rendering only",
            classes: [
                "com.crypto.inspector.service.TradeInspectorService",
                "com.crypto.inspector.controller.TradeInspectorController",
                "com.crypto.repository.CandleRepository",
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/trade-inspector.html",
                "src/main/resources/static/css/trade-inspector.css"
            ],
            cause: "The prior FIX-005 chart was still bounded to seven days before BUY and seven days after SELL. That made deep historical inspection awkward and forced the user to change context windows instead of panning naturally through the complete persisted history. The default 1h overview also hid the minute-level behavior needed for entry/exit diagnosis.",
            solution: "Keep the existing truthful candlestick rendering, BUY/SELL markers and lifecycle line, but make the chart endpoint return all real closed candles for the selected symbol/interval when no explicit range is supplied. Default the inspector to 1m, provide full-history navigation, initially focus around the selected trade, and expose Fit trade / Jump to entry navigation. FIX-009 later replaced complete-series rendering with lazy windows for performance. Add exact OHLC, volume, taker-buy percentage and number-of-trades hover details plus entry/exit/SL/TP horizontal reference lines. Missing candles are never synthesized.",
            behavior: "Opening a trade exposes the full stored history while displaying a focused BUY→SELL viewport. FIX-009 later optimized this into lazy windows so deep history remains reachable without rendering every candle at once. Switching interval reloads the same full-history model at 1m/5m/1h/4h granularity.",
            regression: "UI/API-only enhancement. Existing normal BUY, pressure-probe BUY, MTF confirmation, wallet execution, position protection and Replay/Proven Analysis logic are unchanged. JavaScript syntax validation passes. The chart endpoint remains read-only and only exposes persisted closed candles. Jenkins/Maven should run the complete application suite before deployment."
        },

        {
            id: "FIX-008",
            title: "Trade Inspector chart no longer captures interval controls",
            scenario: "Trade Inspector full-history chart interaction",
            symbol: "ALL",
            entry: "N/A",
            exit: "N/A",
            entryTime: "N/A",
            exitTime: "N/A",
            location: "Trade Inspector browser UI only",
            classes: [
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/css/trade-inspector.css",
                "src/main/resources/static/trade-inspector.html"
            ],
            cause: "FIX-007 opened ApexCharts with pan selected by default and applied a grab cursor to the complete chart host. On some layouts the interactive chart layer could visually/physically dominate the chart header area, making the interval selector feel captured by the hand/pan interaction.",
            solution: "Keep full-history navigation unchanged, but start ApexCharts in normal zoom mode instead of pan mode. Pan remains available explicitly from the chart toolbar. Raise the interval selector and navigation toolbar above the chart canvas with their own pointer-event layer, remove the global grab cursor, and restrict wheel handling to the chart host only.",
            behavior: "The 1m/5m/1h/4h dropdown and chart navigation buttons remain normally clickable. The hand cursor no longer covers the inspector controls. Users can zoom immediately and select the Apex pan tool when they want to drag left/right through full history.",
            regression: "UI interaction only. No Java decision, execution, wallet, position, pressure-probe, Replay or Proven Analysis class is changed.",
            status: "IMPLEMENTED"
        }
        ,{
            id: "FIX-009",
            title: "Trade Inspector lazy-window performance and native page scrolling",
            scenario: "Trade Inspector lag after enabling full-history 1m navigation",
            symbol: "ALL",
            entry: "N/A",
            exit: "N/A",
            entryTime: "N/A",
            exitTime: "N/A",
            location: "Trade Inspector chart API and browser rendering only",
            classes: [
                "com.crypto.inspector.service.TradeInspectorService",
                "com.crypto.repository.CandleRepository",
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/trade-inspector.html",
                "src/main/resources/static/css/trade-inspector.css"
            ],
            cause: "The browser fetched and rendered the complete closed-candle history at once. On 1m data this can mean tens of thousands of candlesticks, making ApexCharts and even normal page scrolling feel frozen. A custom wheel handler also called preventDefault(), so wheel/trackpad gestures over the chart could not scroll the page.",
            solution: "Preserve access to the complete stored history without rendering it all simultaneously. The chart API now returns bounded windows plus global first/last timestamps and total count. The browser keeps only a moving candle window and lazily replaces nearby history when panning approaches an edge. Remove custom wheel interception; use Apex zoom controls and explicit pan mode. Replace Full range with Earliest/Latest jumps so full-history navigation never forces every 1m candle into the browser.",
            behavior: "Trade Inspector opens around the selected BUY→SELL lifecycle with a manageable candle block. Choose the pan hand to move left/right; additional windows load automatically near an edge. Earliest/Latest jump across the complete stored history. Mouse-wheel and trackpad gestures scroll the page normally, so the rest of Trade Inspector remains responsive.",
            regression: "Read-only chart/UI performance fix only. No AnalysisService, FinalDecisionService, Execution Intelligence, pressure-probe, wallet, position management, Replay or Proven Analysis trading behavior was changed.",
            status: "IMPLEMENTED"
        }

        ,{
            id: "FIX-010",
            title: "Trade Inspector Y-axis hover price badge",
            scenario: "Binance-style chart hover should show the exact cursor price on the right Y axis",
            symbol: "ALL",
            entry: "N/A",
            exit: "N/A",
            entryTime: "N/A",
            exitTime: "N/A",
            location: "Trade Inspector chart hover/crosshair only",
            classes: [
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/css/trade-inspector.css"
            ],
            cause: "The horizontal crosshair was visible, but the Y-axis hover value was not visually prominent enough to behave like Binance/TradingView.",
            solution: "Keep the existing candlestick tooltip and X-axis time crosshair, explicitly place the price scale on the right like Binance/TradingView, and enable/style the ApexCharts Y-axis tooltip so the exact hovered price is displayed as a badge on that right price axis using the same adaptive candle-price precision.",
            behavior: "The price scale is displayed on the right. Hovering the candle plot shows a horizontal crosshair with a clearly visible exact-price badge attached to that Y axis. The badge disappears when the hover leaves the plot.",
            regression: "UI-only hover enhancement. No candle paging, zoom/pan behavior, signal logic, wallet logic, pressure-probe logic, Replay or Proven Analysis behavior is changed.",
            status: "IMPLEMENTED"
        }

        ,{
            id: "FIX-012",
            title: "Trade Inspector Binance-like chart cleanup",
            scenario: "Remove chart instructional/status clutter and keep the trading chart focused on candles, trade lifecycle and exact hover price",
            symbol: "ALL",
            entry: "N/A",
            exit: "N/A",
            entryTime: "N/A",
            exitTime: "N/A",
            location: "Trade Inspector chart UI only",
            classes: [
                "src/main/resources/static/trade-inspector.html",
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/css/trade-inspector.css"
            ],
            cause: "The Trade Inspector chart showed a long instructional paragraph and a moving-window/history-count status line above the plot, making the inspector feel unlike a focused exchange chart.",
            solution: "Remove both text blocks. Keep the detailed candlestick chart, BUY/SELL lifecycle, right-side price scale, horizontal/vertical crosshair, OHLC hover detail, lazy historical navigation and the exact hovered-price badge on the right Y axis. Add subtle horizontal price grid lines for a more exchange-like reading surface.",
            behavior: "Trade Inspector now presents the chart directly with controls and compact legend. Hovering the plot shows the exact cursor price on the right Y axis; candle OHLC detail remains available and historical navigation remains unchanged.",
            regression: "UI-only cleanup. No BUY, SELL, TP continuation, stop loss, wallet, Replay, candle API or trading decision logic is changed.",
            status: "IMPLEMENTED"
        }

        ,{
            id: "FIX-013",
            title: "Trade Inspector dedicated right-axis hover price",
            scenario: "Built-in ApexCharts Y-axis tooltip did not reliably show the Binance-style cursor price on the mixed candlestick/lifecycle chart",
            symbol: "ALL",
            entry: "N/A",
            exit: "N/A",
            entryTime: "N/A",
            exitTime: "N/A",
            location: "Trade Inspector chart pointer/crosshair UI only",
            classes: [
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/css/trade-inspector.css"
            ],
            cause: "FIX-010/012 relied on ApexCharts' built-in Y-axis tooltip. On the mixed candlestick plus BUY-to-SELL lifecycle series that tooltip is not consistently rendered, so the crosshair could move without showing the price badge on the right axis.",
            solution: "Add a dedicated display-only hover badge. On mouse move inside the actual plot grid, map the pointer's vertical position to the chart's current visible Y-axis min/max and render the exact adaptive-precision price at the right edge. Hide it outside the plot. The overlay uses pointer-events:none and does not intercept wheel, pan, zoom, dropdown or toolbar interactions.",
            behavior: "Hovering anywhere over the candle plot now shows the exact cursor price on the right Y axis, Binance-style, independent of ApexCharts' internal Y-axis tooltip behavior. Existing OHLC tooltip and X-axis time crosshair remain unchanged.",
            regression: "UI-only fix. No BUY, SELL, stop-loss, TP continuation, wallet, Replay, Proven Analysis, candle paging or execution behavior changes.",
            status: "IMPLEMENTED"
        }

        ,{
            id: "FIX-011",
            title: "TP continuation uses the same immutable BUY-thesis pressure as Position Management",
            scenario: "SOLUSDT first position on 2026-08-19: good BUY at 78.45 reached TP near 78.77 but continuation failed even though Position Analysis still said HOLD",
            symbol: "SOLUSDT",
            entry: "78.45 via trade signal #94801 (STRONG_BUY 88, confidence 78)",
            exit: "Historical exit 78.77 TAKE_PROFIT; corrected behavior extends TP when thesis pressure remains minor instead of failing only on the old binary 1m trend floor",
            entryTime: "2026-08-19 13:25:31 UTC / 16:25:31 KSA",
            exitTime: "Historical TP exit 2026-08-19 14:17:23 UTC / 17:17:23 KSA",
            location: "Shared position thesis pressure + live/replay take-profit continuation",
            classes: [
                "com.crypto.position.service.PositionThesisPressurePolicy",
                "com.crypto.position.service.PositionManagementService",
                "com.crypto.position.service.PositionContinuationPolicy",
                "com.crypto.position.service.LivePositionProtectionService",
                "com.crypto.regression.service.ShadowProductionReplayService",
                "src/test/java/com/crypto/position/service/PositionContinuationPolicyTest.java"
            ],
            cause: "Position Management and TP continuation evaluated the same immutable BUY thesis differently. Position Management scored SOL trend 21->16 and structure 5->2 as trend pressure 2/8 with momentum pressure 0/5 and HOLD, while PositionContinuationPolicy used a binary trend floor of entryTrend-3 (18) and failed continuation solely because current trend 16 was below 18. This closed the entire good entry even though 1m=WATCH, 5m=NEUTRAL, 1h=WATCH and momentum had improved 13->15.",
            solution: "Extract the proven immutable-thesis deterioration calculation into PositionThesisPressurePolicy and make both PositionManagementService and PositionContinuationPolicy call that exact shared policy. Add a narrow THESIS_INTACT_CONSOLIDATION continuation path requiring current supportive, 5m non-bearish, 1h non-bearish, trend pressure <=2/8 and momentum pressure <=1/5. Existing bearish timeframe vetoes remain first and absolute. Live production passes the full immutable entry thesis, and ShadowProductionReplayService now stores/passes entry structure as well so Replay exercises the exact production policy.",
            behavior: "For the exact SOL state (entry trend/structure/momentum/volume 21/5/13/19 -> TP state 16/2/15/7, 1m WATCH, 5m NEUTRAL, 1h WATCH), continuation now PASSes via THESIS_INTACT_CONSOLIDATION and the existing TP-extension mechanism pushes the target instead of selling 100%. Severe trend/structure deterioration, real momentum collapse, or SELL/STRONG_SELL on any monitored timeframe still fails continuation.",
            regression: "Exact SOL regression asserts continuation PASS with trend pressure 2/8 and momentum pressure 0/5. Negative controls assert severe trend/structure break still FAILs and a bearish 5m still vetoes. Existing continuation tests remain in place. BUY generation, entry validation, stop loss, normal SELL authority, ATR entry logic and execution intelligence are unchanged. Replay uses the same PositionContinuationPolicy and PositionThesisPressurePolicy as production; no test-only continuation formula is introduced.",
            status: "IMPLEMENTED"
        }

        ,{
            id: "FIX-014",
            title: "Fresh 5m BUY transition wakes an existing ATR-deferred 1m opportunity",
            scenario: "XRPUSDT 2026-08-19: early 1m BUY was deferred while 5m/1h were neutral; later 5m BUY + 1h WATCH appeared near 1.075 but remained context-only, and production finally entered much later at 1.1279",
            symbol: "XRPUSDT",
            entry: "Historical bad execution 1.1279 via #97515; target regression is a reduced SETUP_TIMEFRAME_ATR opportunity around the #96944/#96945 1m+5m handoff near 1.075-1.078 when all existing guards pass",
            exit: "Existing stop-loss, TP continuation, Dynamic Profit Lock and validated SELL logic remain unchanged",
            entryTime: "Key handoff 2026-08-19 20:22:06-20:22:07 UTC (23:22:06-23:22:07 KSA)",
            exitTime: "Scenario-specific; no exit rule is changed by this fix",
            replayWindow: "2026-08-19 19:50-20:35 UTC (22:50-23:35 KSA) · recommended Proven replay window for the XRP setup-timeframe wake-up",
            location: "Execution Intelligence setup-timeframe ATR handoff + PaperTradingService trigger routing + ShadowProductionReplayService parity",
            classes: [
                "com.crypto.execution.service.ExecutionIntelligenceService",
                "com.crypto.service.PaperTradingService",
                "com.crypto.regression.service.ShadowProductionReplayService",
                "src/test/java/com/crypto/execution/service/ExecutionIntelligenceServiceTest.java"
            ],
            cause: "The architecture intentionally allows only fresh 1m signals to trigger wallet BUY execution. XRP #96786 correctly produced a strong 1m BUY around 1.0672 but higher-timeframe confirmation was not ready. When 5m later transitioned to BUY (#96945/#96986) with 1h WATCH and valid 5m ATR near 1.075, those 5m signals were correctly treated as CONTEXT_ONLY and could not wake the already-live deferred opportunity. The next qualifying 1m execution arrived after substantial additional expansion at 1.1279. Existing SETUP_TIMEFRAME_ATR already modeled the safe handoff but was reachable only from a later 1m event.",
            solution: "Keep 5m/1h non-executable and keep the normal 1m BUY path unchanged. Add a narrow wake-up hook: only a fresh 5m transition from non-BUY to BUY/STRONG_BUY may re-evaluate an existing unexecuted BUY opportunity, only when the latest 1m signal is fresh (<=2 minutes), supportive/non-bearish, final-entry-allowed and still ATR-deferred, and fresh 1h authority is at least WATCH/BUY. The hook calls the existing SETUP_TIMEFRAME_ATR decision and Entry Quality guard using the 5m ATR authority; strategy/BTC/derivatives/liquidity/risk-plan vetoes remain mandatory. Repeated 5m BUY candles cannot retrigger the hook and an already-open allocation cannot use it.",
            behavior: "A 5m BUY never opens a trade by itself. It can only wake a previously recognized, still-unexecuted 1m BUY opportunity and request the existing conservative setup-timeframe ATR evaluation. Normal direct 1m BUYs, BALANCED_EARLY, HTF_TRANSITION, pressure probes, progressive adds and all SELL/position-management behavior retain their existing priority and rules.",
            regression: "Production and Administration replay call the same evaluateSetupTimeframeWakeup(...) method. Unit coverage verifies the XRP-shaped WATCH 1m / ATR-deferred + fresh 5m BUY + 1h WATCH handoff can produce SETUP_TIMEFRAME_WAKEUP, while repeated 5m BUY candles remain context-only. Full Jenkins Maven tests and an XRP replay around 2026-08-19 19:50-20:35 UTC should verify an earlier reduced entry without changing previously proven BUY/SELL scenarios.",
            status: "IMPLEMENTED"
        }

        ,{
            id: "FIX-015",
            title: "Trade Inspector hover-price survives interval and toolbar actions",
            scenario: "Trade Inspector Y-axis hover price disappears after changing 1m/5m/1h/4h interval or using zoom/pan/reset/selection controls",
            symbol: "ALL",
            entry: "N/A · UI-only chart interaction fix",
            exit: "N/A · UI-only chart interaction fix",
            entryTime: "N/A",
            exitTime: "N/A",
            replayWindow: "No Proven replay required; verify interactively by changing interval and using each chart toolbar action, then hover the candle plot",
            location: "Trade Inspector chart lifecycle / dedicated right-axis hover price badge",
            classes: [
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/css/trade-inspector.css"
            ],
            cause: "FIX-013 bound the dedicated hover-price badge to the ApexCharts instance that existed when the chart first rendered. Interval changes destroy/recreate that instance, while toolbar actions mutate its internal plot scale and interaction layers. The old pointer closure could therefore become stale or stop receiving normal bubbled mouse events.",
            solution: "Clean up the previous chart hover listeners before chart destruction, bind the badge to the current chart instance, listen to pointer movement in capture phase, rebind after Apex updated/selection/zoom/pan/reset events, and recalculate the visible Y range with a current-candle fallback. The badge remains pointer-events:none and cannot block graph or page controls.",
            behavior: "The exact cursor price continues to appear on the right Y axis after changing interval and after pressing zoom, pan, reset or selection actions in the chart itself.",
            regression: "UI-only interaction fix. No candle retrieval, BUY/SELL decision, wallet, position management, replay or Proven trading behavior changes. JavaScript syntax validation must pass.",
            status: "IMPLEMENTED"
        }

        ,{
            id: "FIX-016",
            title: "Proven Trades archives BUY and SELL execution legs independently",
            scenario: "Manual Proven review needs to preserve one BUY or one SELL point without manually archiving the entire regression test run",
            symbol: "ALL",
            entry: "Archive BUY snapshots only the reviewed entry execution",
            exit: "Archive SELL snapshots only the reviewed exit execution and its exit/P&L metadata",
            entryTime: "Uses the exact Proven trade BUY execution time",
            exitTime: "Uses the exact Proven trade SELL execution time",
            replayWindow: "No replay required; validate on any completed Proven trade by archiving BUY and SELL independently",
            location: "Proven Analyzed Trades persistence/API/UI; full-run Clear Data safety archive remains unchanged",
            classes: [
                "com.crypto.regression.service.RegressionTestService",
                "com.crypto.regression.controller.RegressionTestController",
                "src/main/resources/db/migration/V60__archive_proven_trade_legs.sql",
                "src/main/resources/static/js/proven-analyzed-trades.js",
                "src/main/resources/static/proven-analyzed-trades.html"
            ],
            cause: "The visible manual Archive action operated at regression-run level, so preserving one reviewed execution point unnecessarily archived the whole test dataset. Proven review is trade-centric and needs leg-level persistence.",
            solution: "Add a dedicated proven_trade_leg_archive table and API that accept only BUY or SELL for an existing Proven trade. Add separate Archive BUY / Archive SELL controls and a read-only archived-leg table. Remove the manual full-run Archive button from Current Test rows; automatic full-run archival before Clear Data remains intact for safety and diagnostics.",
            behavior: "A reviewer can archive BUY now and SELL later (or vice versa) without copying the whole tested run. Each leg is idempotent and records its own exact time/price; SELL also stores exit reason and realized P/L.",
            regression: "Persistence/UI-only Proven review change. Existing shadow replay tables, automatic Clear Data run archive, live wallet tables and trading algorithms are unchanged.",
            status: "IMPLEMENTED"
        }

        ,{
            id: "FIX-017",
            title: "Fix Registry includes explicit Proven replay guidance",
            scenario: "Scenario fixes need entry time, exit time and a ready-to-use replay window for future regression validation",
            symbol: "ALL",
            entry: "Registry metadata only",
            exit: "Registry metadata only",
            entryTime: "Existing scenario Entry time field retained",
            exitTime: "Existing scenario Exit time field retained",
            replayWindow: "Each fix now shows Suggested Proven replay window; FIX-014 explicitly recommends 2026-08-19 19:50-20:35 UTC / 22:50-23:35 KSA",
            location: "Fix Registry UI and Copy all fixes output",
            classes: [
                "src/main/resources/static/js/fix-registry.js",
                "src/main/resources/static/fix-registry.html"
            ],
            cause: "Entry and exit timestamps were already documented, but there was no dedicated field telling a future reviewer what From/To window to enter in Proven Analyzed Trades.",
            solution: "Keep Entry time and Exit time unchanged and add Suggested Proven replay window to every rendered/copied registry record. Scenario fixes can provide an explicit wider replayWindow; older records safely fall back to their documented entry/exit timestamps when no dedicated window exists.",
            behavior: "Future debugging can copy the exact scenario timing directly from Fix Registry before rerunning Proven Analysis, reducing accidental test-window mismatch.",
            regression: "Documentation/UI metadata only. No production or replay decision logic changes.",
            status: "IMPLEMENTED"
        }

        ,{
            id: "FIX-018",
            title: "Proven/Test trade chart opens as focused modal with persistent X/Y crosshair",
            scenario: "Manual review of one replay/proven/archive trade should keep the parent row visible while giving a Binance-like focused chart",
            symbol: "ALL",
            entry: "Selected trade BUY marker and price remain visible inside the focused chart",
            exit: "Selected trade SELL marker and BUY → SELL trade path remain visible inside the focused chart",
            entryTime: "Uses the exact selected replay/proven trade BUY execution time",
            exitTime: "Uses the exact selected replay/proven trade SELL execution time when closed",
            replayWindow: "No strategy replay is required; validate on any Current Test, Proven or Archived trade by opening View Chart, switching intervals and using zoom/pan/reset before hovering",
            location: "Proven Analyzed Trades browser UI only",
            classes: [
                "src/main/resources/static/proven-analyzed-trades.html",
                "src/main/resources/static/js/proven-analyzed-trades.js",
                "src/main/resources/static/css/administration.css"
            ],
            cause: "Trade review reused the persistent combined Proven chart and scrolled the page away from the selected row. Hover information depended on ordinary Apex axis tooltip behavior, which is not reliable enough after chart recreation or toolbar state changes and did not provide a full Binance-style X/Y cursor overlay.",
            solution: "Keep the combined Proven chart unchanged and add a separate trade-focused modal overlay for Current Test, Proven and Archive View Chart actions. Dim/blur the parent page while retaining it underneath, render seven hours before BUY through seven hours after SELL, and add a dedicated pointer-driven vertical/horizontal crosshair. Map the pointer against Apex's current visible min/max after every zoom/pan state so the right-side badge always shows exact adaptive-precision price and the bottom badge shows browser-local date/time. Interval changes destroy/recreate only the popup chart and then rebind the dedicated crosshair cleanly.",
            behavior: "View Chart opens above the trade being reviewed instead of navigating away. The parent test remains visible but faded. Hover anywhere inside the candle plot shows a vertical time line, horizontal price line, exact Y-axis price badge and X-axis date/time badge. The behavior continues after changing 1m/5m/1h/4h and after zoom/pan/reset actions.",
            regression: "UI-only review enhancement. No signal scoring, BUY/SELL authority, opportunity lifecycle, wallet execution, position management, FIX-014 setup wake-up or replay calculation is changed. JavaScript syntax validation must pass.",
            status: "IMPLEMENTED"
        },
        {
            id: "FIX-019",
            title: "Trade Inspector uses focused modal chart with persistent Binance-style X/Y crosshair",
            scenario: "Inspecting a completed wallet trade should keep the parent Trade Inspector visible while chart hover continues to show exact price and date/time after interval and toolbar changes",
            symbol: "ALL",
            entry: "Selected Trade Inspector BUY marker remains visible in the popup chart",
            exit: "Selected Trade Inspector SELL marker and BUY → SELL lifecycle line remain visible in the popup chart",
            entryTime: "Uses the exact selected wallet trade BUY execution time",
            exitTime: "Uses the exact selected wallet trade SELL execution time",
            replayWindow: "No strategy replay required; validate on any Trade Inspector row by opening View chart, switching 1m/5m/1h/4h, using zoom/pan/reset, then hovering across the plot",
            location: "Trade Inspector browser UI only",
            classes: [
                "src/main/resources/static/trade-inspector.html",
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/css/trade-inspector.css"
            ],
            cause: "Trade Inspector still used an inline chart panel and a Y-only custom hover badge. That differed from FIX-018 Proven/Test review and could lose useful hover context after interval recreation or Apex toolbar actions. It also did not provide a dedicated X-axis date/time badge paired with the Y-axis price.",
            solution: "Reuse the FIX-018 interaction pattern in Trade Inspector: open the selected trade chart in a centered modal overlay that dims the parent page, preserve the existing real candle endpoint, BUY/SELL markers and lifecycle line, and replace the Y-only helper with a lifecycle-safe dedicated vertical/horizontal crosshair. The crosshair maps the pointer to Apex's current visible X/Y ranges and renders browser-local date/time below the X axis plus adaptive-precision price beside the Y axis. Old listeners are removed whenever the interval recreates the chart and are rebound after zoom/pan/reset updates.",
            behavior: "Trade Inspector View chart now opens above the selected trade instead of scrolling to an inline panel. Hover anywhere inside the candle plot shows both crosshair lines, exact Y price and X date/time, including after interval changes and chart toolbar actions. Close button, backdrop click and Escape return to the unchanged parent inspector.",
            regression: "UI-only enhancement. Existing Trade Inspector candle loading, BUY/SELL annotations, trade-path rendering, history navigation, signal scoring, execution intelligence, opportunity lifecycle, wallet execution, replay and position management are unchanged. JavaScript syntax validation must pass.",
            status: "IMPLEMENTED"
        },
        {
            id: "FIX-020",
            title: "Completed position consumes its opportunity evidence",
            scenario: "ENAUSDT 2026-08-20: a good scout opened at 0.1069 and closed profitably at 0.1082, but the same BUILDING opportunity survived the completed position and reused its pre-exit evidence to re-enter 73 seconds later at 0.1082",
            symbol: "ENAUSDT",
            entry: "Good first position: 0.1069 via #103770 SCOUT_ENTRY; stale second entry: 0.1082 via #103889 ACCUMULATED_EVIDENCE",
            exit: "First position TAKE_PROFIT at 0.1082; stale second position later stopped at 0.1079",
            entryTime: "DB/Binance 2026-08-20 18:19:10 UTC / 21:19:10 KSA for the good scout; stale re-entry 18:44:24 UTC / 21:44:24 KSA",
            exitTime: "Good position closed 2026-08-20 18:43:11 UTC / 21:43:11 KSA",
            replayWindow: "2026-08-20 18:10-19:05 DB/Binance UTC (21:10-22:05 KSA) · proves TP boundary and blocks stale post-exit re-entry",
            location: "Execution opportunity lifecycle + PaperTradingService terminal closes + ShadowProductionReplayService parity",
            classes: [
                "com.crypto.execution.service.ExecutionIntelligenceService",
                "com.crypto.service.PaperTradingService",
                "com.crypto.regression.service.ShadowProductionReplayService",
                "src/test/java/com/crypto/execution/service/ExecutionIntelligenceServiceTest.java"
            ],
            cause: "Scout/probe/confirmation decisions intentionally keep an opportunity BUILDING while the same position is open. Terminal position close paths did not consume that opportunity, so pre-exit evidence could cross a completed-trade boundary and finance a brand-new position.",
            solution: "Add one shared completePositionOpportunity(...) lifecycle method. Every terminal production close (TP, SL, validated SELL, profit lock, manual/other completeClose path) and replay terminal close calls it. The active opportunity becomes COMPLETED with POSITION_CLOSED_EVIDENCE_BOUNDARY; no pre-exit evidence can be reused for a new position. Progressive adds remain unchanged while the position is open.",
            behavior: "The successful ENA scout can still add progressively while open. Once it closes, the old opportunity is consumed; any later ENA BUY must create/build fresh post-exit evidence.",
            regression: "Unit regression verifies opportunity #14829 changes BUILDING -> COMPLETED on terminal close. Proven/Regression replay calls the same production lifecycle method before any later BUY evaluation.",
            status: "IMPLEMENTED"
        },
        {
            id: "FIX-021",
            title: "Accumulated evidence cannot bypass direct-BUY HTF authority",
            scenario: "BICOUSDT #102491 and ETHUSDT #103638: BALANCED direct BUY authority rejected/required stronger HTF context, but ACCUMULATED_EVIDENCE later entered with 5m/1h combinations that the normal BUY profile would not approve",
            symbol: "BICOUSDT / ETHUSDT",
            entry: "BICO stale entry 0.01938 via #102491; ETH later entry 2345.77 via #103638",
            exit: "BICO STOP_LOSS 0.01894; ETH STOP_LOSS 2333.78",
            entryTime: "BICO DB 2026-08-20 14:06 UTC / 17:06 KSA; ETH DB 17:54 UTC / 20:54 KSA",
            exitTime: "BICO DB 14:18:50 UTC / 17:18:50 KSA; ETH DB 18:02:56 UTC / 21:02:56 KSA",
            replayWindow: "BICO 2026-08-20 13:30-14:25 DB UTC (16:30-17:25 KSA); ETH 17:20-18:10 DB UTC (20:20-21:10 KSA)",
            location: "TradeExecutionValidationService shared HTF profile + ExecutionIntelligenceService accumulated evidence",
            classes: [
                "com.crypto.service.TradeExecutionValidationService",
                "com.crypto.execution.service.ExecutionIntelligenceService",
                "src/test/java/com/crypto/service/TradeExecutionValidationServiceTest.java",
                "src/test/java/com/crypto/execution/service/ExecutionIntelligenceServiceTest.java"
            ],
            cause: "ACCUMULATED_EVIDENCE checked only that 5m/1h were non-bearish. That made historical evidence a second, weaker execution authority: BALANCED could reject 5m WATCH/NEUTRAL + 1h NEUTRAL on the normal path, yet accumulated evidence could later approve it.",
            solution: "Add validateBuyContext(...) to TradeExecutionValidationService and call it before accumulated evidence can execute. The method reuses the configured CONSERVATIVE/BALANCED/AGGRESSIVE 5m/1h policy without requiring a fresh 1m BUY transition. Historical evidence remains memory only; it cannot override the configured HTF authority.",
            behavior: "Insufficient HTF authority keeps the opportunity BUILDING with ACCUMULATED_AUTHORITY_WAIT instead of opening a position. When the same configured profile later becomes valid, accumulated evidence may proceed normally.",
            regression: "Regression covers the BICO-shaped 5m NEUTRAL + 1h NEUTRAL rejection and a valid 5m BUY + 1h NEUTRAL BALANCED_STRONG context. Replay uses the same shared validation service.",
            status: "IMPLEMENTED"
        },
        {
            id: "FIX-022",
            title: "Ultra-close shrinking ask wall is evidence, not an automatic hard veto",
            scenario: "ETHUSDT 2026-08-20 around 18:11 KSA: otherwise valid BUY setup was vetoed only by a 2294.04 ask wall 0.097% above price; the wall had already shrunk 16.6% and price consumed it minutes later",
            symbol: "ETHUSDT",
            entry: "Blocked setup around 2291.81; strategy BUY, MTF PASS, BTC CONFIRMED, ATR STANDARD_ENTRY",
            exit: "No entry occurred at the blocked point; later execution was materially higher",
            entryTime: "DB/Binance 2026-08-20 15:11 UTC / 18:11 KSA",
            exitTime: "N/A · this fix changes entry veto interpretation only",
            replayWindow: "2026-08-20 14:55-15:35 DB/Binance UTC (17:55-18:35 KSA)",
            location: "OrderBookLiquidityService wall lifecycle hard-veto classification",
            classes: [
                "com.crypto.service.OrderBookLiquidityService"
            ],
            cause: "Wall persistence/strength dominated the veto even when the wall was ultra-close and already shrinking materially. ETH had strength 90/100 but size change -16.6% at only 0.097% distance, so the wall behaved more like consumable breakout liquidity than static resistance.",
            solution: "Keep TARGET_BLOCKED and its confidence penalty, but suppress the hard veto only when the target wall is <=0.10% away AND size has already contracted by at least 10%. Stable/growing or non-shrinking strong walls keep the exact existing hard veto. The explanation explicitly records the consumable-wall exception.",
            behavior: "Order-book protection remains active. Only the narrow ultra-close + materially shrinking wall case stops being an automatic BUY->WATCH veto; it remains negative evidence and still needs fresh execution confirmation.",
            regression: "Use the ETH 18:11 KSA Proven window to verify the shrinking-wall case no longer hard-vetoes while persistent non-shrinking walls still do. No SELL/order-book downside veto rule is changed.",
            status: "IMPLEMENTED"
        },
        {
            id: "FIX-023",
            title: "Fresh 5m confirmation wakes a live opportunity after blocker clears",
            scenario: "ETHUSDT 2026-08-20: after the early liquidity concern, a fresh 5m BUY 83 / confidence 78 / BREAKOUT / STANDARD_ENTRY appeared near 2301 around 18:24 KSA, but 5m remained context-only and execution waited until ~2313.78",
            symbol: "ETHUSDT",
            entry: "Target re-evaluation uses latest fresh 1m timing near 2301.32 when #102890 5m BUY confirms; 5m itself never becomes wallet price authority",
            exit: "Existing position-management, TP, SL and SELL logic unchanged",
            entryTime: "DB/Binance 2026-08-20 15:24:02-15:24:05 UTC / 18:24:02-18:24:05 KSA",
            exitTime: "Scenario-specific; no exit rule changes",
            replayWindow: "2026-08-20 14:55-15:35 DB/Binance UTC (17:55-18:35 KSA) · validates blocker-clear handoff",
            location: "ExecutionIntelligenceService confirmation wake-up + PaperTradingService trigger routing + ShadowProductionReplayService parity",
            classes: [
                "com.crypto.execution.service.ExecutionIntelligenceService",
                "com.crypto.service.PaperTradingService",
                "com.crypto.regression.service.ShadowProductionReplayService",
                "src/test/java/com/crypto/execution/service/ExecutionIntelligenceServiceTest.java"
            ],
            cause: "A fresh 5m BUY transition could update context but could not trigger immediate reconsideration unless the older FIX-014 ATR-deferred conditions were met. ETH therefore had a strong newly executable 5m confirmation while the live 1m opportunity was still supportive, yet execution waited for a later 1m/accumulated event.",
            solution: "Add a second narrow 5m wake-up route for an existing unexecuted opportunity. It requires a real non-BUY->BUY 5m transition, final/ATR/liquidity permission on that 5m, latest 1m <=2 minutes old and supportive/immediately executable, no hard 1m risk veto, and the same configured HTF profile via validateBuyContext(...). Position is capped at 25% before the existing Entry Quality guard. The returned 1m signal owns price/SL/TP; 5m remains confirmation-only.",
            behavior: "When a prior blocker clears and 5m becomes a fresh executable BUY, the existing opportunity is re-evaluated immediately instead of waiting for accumulated evidence. Repeated 5m BUYs, stale 1m state, open positions or insufficient HTF authority cannot use the hook.",
            regression: "Production and Proven/Regression call the same evaluateConfirmedSetupWakeup(...) method. Unit regression models ETH #102889 + #102890 and asserts a reduced SETUP_CONFIRMATION_WAKEUP using the 1m execution signal, plus the existing FIX-014 route remains unchanged.",
            status: "IMPLEMENTED"
        }

        ,{
            id: "FIX-024",
            status: "IMPLEMENTED",
            title: "Trade Inspector timestamped decision-state View Path",
            scenario: "Trade Inspector diagnostic visualization for every completed BUY -> SELL trade",
            symbol: "ALL",
            entry: "Read-only visualization; no trading entry behavior changed",
            exit: "Read-only visualization; no trading exit behavior changed",
            entryTime: "Uses each selected trade's actual wallet BUY timestamp and persisted decision timestamps",
            exitTime: "Uses each selected trade's actual wallet SELL timestamp and displays exact holding duration",
            replayWindow: "N/A - UI/diagnostic-only fix; no replay/trading behavior changed",
            location: "Trade Inspector card action next to View Chart -> View Path overlay",
            classes: [
                "com.crypto.inspector.service.TradeInspectorService",
                "com.crypto.inspector.controller.TradeInspectorController",
                "com.crypto.execution.repository.ExecutionOpportunityRepository",
                "src/main/resources/static/trade-inspector.html",
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/css/trade-inspector.css"
            ],
            cause: "Trade Inspector exposed the trade chart and summary metrics but debugging a trade still required manual SQL to reconstruct 1m/5m/1h authority, opportunity age/evidence, ATR, BTC, order book, derivatives, ordered FinalDecision checks, timestamps and holding time.",
            solution: "Add a View Path button beside View Chart. A read-only endpoint returns the persisted entry/exit signals, the latest 1m/5m/1h states available at wallet execution, the linked/overlapping execution opportunity, position-management snapshot and the original ordered decision_path. The overlay renders a timestamped state timeline in KSA, exact holding time, opportunity age, timeframe cards and contributor diagnostics including order-book statistics.",
            behavior: "Selecting View Path dims the Trade Inspector and opens a focused decision-state overlay for only that trade. All timestamps are displayed in KSA (UTC+3), elapsed time is shown between lifecycle states, and holding time is prominent. No score, decision, wallet, replay or execution state is recomputed or modified.",
            regression: "UI-only diagnostic regression: verify View Path opens beside View Chart for normal, accumulated-evidence and scout/progressive trades; confirm opportunity fallback works when latest_signal_id changed after the initial scout; verify 1m/5m/1h, ATR, BTC, order book, derivatives, decision checks, BUY/SELL timestamps and holding duration match persisted production records."
        },
        {
            id: "FIX-025",
            title: "Trade Inspector View Path continues through the full BUY -> SELL lifecycle",
            status: "Implemented",
            scenario: "Trade Inspector diagnostic path for completed trades, including adds, open-position signal states and mechanical exits",
            symbol: "ALL",
            entry: "Read-only visualization; no trading entry behavior changed",
            exit: "Shows the actual wallet SELL plus the latest persisted 1m/5m/1h context immediately before exit",
            entryTime: "Uses the selected wallet BUY timestamp",
            exitTime: "Uses the selected wallet SELL timestamp and freezes the final holding duration",
            replayWindow: "N/A - UI/diagnostic-only fix; no production/replay trading behavior changed",
            location: "Trade Inspector -> View Path overlay",
            classes: [
                "com.crypto.inspector.service.TradeInspectorService",
                "com.crypto.wallet.repository.WalletTradeRepository",
                "src/main/resources/static/js/trade-inspector.js"
            ],
            cause: "FIX-024 explained the entry decision correctly but its timeline jumped from Wallet BUY to the terminal SELL. It did not return the wallet scale-ins/confirmation adds or the persisted 1m/5m/1h states observed while the position was open, so the visual path appeared to stop before the SELL decision lifecycle.",
            solution: "Extend the read-only path endpoint across the exact BUY-to-SELL time window. Return all executed wallet events for the symbol, meaningful persisted trade signals while the position is open, and the latest 1m/5m/1h context at the SELL timestamp. Render them chronologically with elapsed time between states. Mechanical STOP_LOSS/TAKE_PROFIT exits remain visible even when wallet_trade.signal_id is null because exit-time context is resolved independently.",
            behavior: "View Path now continues from opportunity/entry through confirmation or scale-in BUYs, changing 1m/5m/1h market states, profit-lock activation when present, exit context and the final wallet SELL. The overlay also shows separate entry and exit timeframe cards so the user can compare what changed during the holding period.",
            regression: "Diagnostic-only protection. Verify a simple BUY->SELL trade, a scout plus confirmation-add trade, and a mechanical STOP_LOSS/TAKE_PROFIT trade. The final timeline must end at the selected wallet SELL, holding time must match BUY->SELL duration, and no production/replay decision code may be invoked or mutated by View Path."
        },
        {
            id: "FIX-026",
            title: "Recovery transition probe catches absorption -> buyer-pressure recovery before normal BUY",
            status: "Implemented",
            scenario: "ENAUSDT 2026-08-20 12:55-13:04 KSA - STRONG_SELL recovery into WATCH 75 while taker buyers repeatedly absorbed supply",
            symbol: "ENAUSDT",
            entry: "Suggested recovery probe around 0.0971 at 13:04:51 KSA; initial exposure capped at 25%",
            exit: "Not an exit-policy fix; ordinary position management remains authoritative after entry",
            entryTime: "2026-08-20 13:04:51 KSA (DB/Binance 10:04:51 UTC)",
            exitTime: "Scenario validation should continue through the later expansion/normal exit; this fix changes entry detection only",
            replayWindow: "KSA 2026-08-20 12:45-13:15 (DB/Binance 09:45-10:15 UTC). Proven replay must reproduce Production using only closed candles as-of each signal.",
            location: "Execution Intelligence early-entry routes -> RecoveryTransitionService",
            classes: [
                "com.crypto.execution.service.RecoveryTransitionService",
                "com.crypto.execution.service.ExecutionIntelligenceService",
                "com.crypto.regression.service.ShadowProductionReplayService",
                "com.crypto.execution.service.RecoveryTransitionServiceTest",
                "com.crypto.execution.service.ExecutionIntelligenceServiceTest"
            ],
            cause: "The normal strategy snapshot correctly remained RANGE_MEAN_REVERSION/WATCH at signal #101305, but it underweighted the speed of the state transition. ENA had recently been STRONG_SELL 15, then stabilized near 0.0963, printed strong taker-buy absorption, pulled back, and the last three fully closed candles before #101305 showed 85.26%, 87.47% and 73.91% taker-buy pressure while closes rose 0.0967 -> 0.0969 -> 0.0971. Trend recovered to 21/25 and momentum to 15/15, yet no reduced entry was available until conventional confirmation later.",
            solution: "Add a separate Recovery Transition Entry path without lowering normal BUY thresholds or rewriting market regime. It requires WATCH/NEUTRAL current state with score >=72, confidence >=68, trend >=20 and momentum >=14; a recent <=35 bearish 1m state; non-bearish fresh 5m/1h context; all existing FinalDecision/strategy/confluence/ATR/BTC/liquidity/derivatives entry gates open; and a closed-candle sequence proving prior >=80% taker-buy absorption, a <=25% taker-buy pullback/test, then three consecutive >=70% taker-buy recovery candles with rising closes and >=0.30% price advance. Approved exposure is only 25% and later adds still require ordinary confirmation.",
            behavior: "A fast bearish -> absorption -> recovery transition can open a small RECOVERY_TRANSITION_ENTRY probe while the static strategy remains WATCH. A single high taker-buy candle is never enough. Hard context/risk vetoes still block, current bearish signals never use the route, and normal BUY/Pressure Probe paths keep priority.",
            regression: "ENA anchor: at #101305 around 13:04:51 KSA use only candles closed through 13:03 KSA; the still-open 13:04 candle and 13:06-13:07 expansion must be invisible. Expected source=RECOVERY_TRANSITION_ENTRY, code=ABSORPTION_RECOVERY_PROBE, size<=25%. Negative regression: three recovery candles below 70% taker buy must not trigger. Production and Proven/Replay call the same ExecutionIntelligenceService -> RecoveryTransitionService path; no replay-only formula is permitted."
        },
        {
            id: "FIX-027",
            title: "Trade Inspector one-look sequential state path",
            status: "Implemented",
            scenario: "Trade Inspector View Path simplified into a sequential ERD/state-machine story for every completed trade",
            symbol: "ALL",
            entry: "Read-only visualization; no production BUY behavior changed",
            exit: "Read-only visualization; no production SELL behavior changed",
            entryTime: "Uses actual persisted signal/wallet timestamps and displays them in KSA",
            exitTime: "Uses actual wallet SELL timestamp and exact holding time",
            replayWindow: "N/A - diagnostic/UI-only fix; trading and replay formulas are unchanged",
            location: "Trade Inspector -> View Path beside View Chart",
            classes: [
                "com.crypto.inspector.service.TradeInspectorService",
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/css/trade-inspector.css"
            ],
            cause: "FIX-024/025 contained the needed evidence but spread it across timeline, timeframe cards and contributor panels. The user could not understand the complete trade lifecycle in one look, and pre-entry recovery phases such as STABILIZING/RECOVERING were absent because the diagnostic lifecycle began at wallet BUY.",
            solution: "Replace the primary View Path body with one compact sequential state-machine flow. Each phase shows KSA timestamp, elapsed time to the next phase, displayed score, base technical raw/max, trend, momentum, closed-candle taker BUY pressure, volume, RSI, MACD, decision/regime/strategy, 5m/1h authority, ATR action and any hard veto. Read a bounded 45-minute pre-entry signal window and attach the exact signal candle so recovery states are grounded in persisted closed-candle evidence. Recovery executions can therefore display STABILIZING -> RECOVERING -> RECOVERY_PROBE -> EXPANSION_CONFIRMED -> NORMAL_POSITION -> EXIT when those persisted phases exist. Raw decision checks remain available in a collapsed diagnostic section.",
            behavior: "View Path is now a one-look sequential trade story instead of a dashboard of separate cards. Recovery labels are diagnostic summaries only; they never create or modify a trading state. Ordinary trades use ENTRY/confirmation/position/deterioration/EXIT phases without inventing recovery states. All timestamps are KSA and holding/phase durations are visible directly between nodes.",
            regression: "UI/read-only verification: validate a FIX-026 recovery trade, a normal immediate BUY, an accumulated-evidence trade, a scout+add trade and a mechanical STOP_LOSS/TAKE_PROFIT. Confirm BUY pressure/volume comes from the exact persisted candle_open_time, no future candle is used, raw SELL->NEUTRAL transitions can explain pre-entry stabilization, the path always ends at actual wallet SELL, and no production/replay trading method is invoked by View Path."
        }
        ,{
            id: "FIX-028",
            status: "IMPLEMENTED",
            title: "Production exit audit preserves the real trigger",
            scenario: "BTC TAKE_PROFIT was misleadingly displayed as SIGNAL_SELL from WATCH signal #105688",
            symbol: "BTCUSDT",
            entry: "73,156.13 from BUY signal #105616",
            exit: "73,393.85; actual production trigger TAKE_PROFIT",
            entryTime: "2026-08-21 03:03:47 KSA",
            exitTime: "2026-08-21 03:15:09 KSA",
            location: "Production exit audit + wallet audit metadata + Trade Inspector View Path",
            classes: [
                "com.crypto.audit.service.ProductionExitAuditService",
                "com.crypto.audit.domain.ProductionExitAudit",
                "com.crypto.wallet.service.WalletAutoExecutionService",
                "com.crypto.service.PaperTradingService",
                "com.crypto.position.service.LivePositionProtectionService",
                "com.crypto.inspector.service.TradeInspectorService",
                "src/main/resources/static/js/trade-inspector.js",
                "V61__create_production_exit_audit.sql"
            ],
            cause: "The production position correctly closed as TAKE_PROFIT, while the latest signal was WATCH and Position Analysis was HOLD. PaperTradingService reused that signal as the execution carrier and called a wallet method whose audit metadata was hard-coded to SIGNAL_SELL, making history falsely imply that WATCH #105688 was a SELL decision.",
            solution: "Keep every exit condition and liquidation mechanic unchanged, but preserve the real terminal trigger in wallet metadata and in a new immutable production_exit_audit row. Store the source signal's real decision and the latest Position Analysis recommendation separately. View Path now labels the real trigger first and explicitly identifies WATCH/BUY signals as MARKET_CONTEXT_AT_EXIT unless the trigger is a genuine SELL/STRONG_SELL.",
            behavior: "Future TP/SL/Profit-Lock exits show their true production reason. Historical trades fall back to paper_position, so the investigated BTC trade renders TAKE_PROFIT @ 73,393.85 with context WATCH #105688 rather than SELL SIGNAL. No trading behavior changes.",
            regression: "ProductionExitAuditServiceTest proves TAKE_PROFIT + WATCH source signal + HOLD Position Analysis remain three independent audit facts. JavaScript syntax validation passes. Replay is intentionally unchanged because this is diagnostic/audit-only."
        },
        {
            id: "FIX-029",
            status: "IMPLEMENTED",
            title: "Trade Path human-readable decision meaning",
            scenario: "PEPE #108246 WATCH 66 had supportive direction/momentum but insufficient participation; Trade Inspector showed the numbers without stating that conclusion",
            symbol: "ALL",
            entry: "Read-only explanation of persisted decision evidence; no BUY behavior changed",
            exit: "Read-only explanation of persisted decision evidence; no SELL behavior changed",
            entryTime: "Every Trade Inspector path phase uses its actual persisted KSA timestamp",
            exitTime: "Every Trade Inspector path phase uses its actual persisted KSA timestamp",
            location: "Trade Inspector -> View Path -> each sequential phase",
            classes: [
                "src/main/resources/static/js/trade-inspector.js",
                "src/main/resources/static/css/trade-inspector.css"
            ],
            cause: "FIX-027 exposed the correct Component / Result / Interpretation evidence, but the user still had to mentally combine score, trend, momentum, volume, HTF, ATR and veto state to understand why a phase stayed WATCH or became BUY/STRONG_BUY.",
            solution: "Add a prominent 'What this means' sentence above every path evidence table. The sentence is derived only from the persisted decision and already-displayed evidence. Example: WATCH with supportive trend/momentum but weak volume becomes 'Direction and momentum look good, but participation/confirmation is not strong enough yet.' STRONG_BUY with strong trend/volume/momentum becomes a concise confirmation explanation. Blocked and ATR-wait states explain the actual blocker instead of sounding bullish.",
            behavior: "The sequential path now reads as a trader-friendly story: state + KSA time + one-line meaning + detailed evidence. The interpretation is diagnostic UI only and never writes a signal, changes a score, changes Replay, or changes execution.",
            regression: "PEPE #108246 should explain WATCH 66 as supportive direction/momentum with insufficient participation/confirmation. PEPE #108276 should explain STRONG_BUY 86 as strong aligned trend/participation/momentum. Verify blocked entries and ATR WAIT phases explain the blocker. node --check trade-inspector.js passes."
        },
        {
            id: "FIX-031",
            status: "IMPLEMENTED",
            title: "Logged-in-user Crypto Account configuration",
            scenario: "Prepare LIVE_MICRO safely without changing the proven trading engine; each authenticated user must own a separate Binance account configuration",
            symbol: "ALL",
            entry: "No trading entry logic changed; account configuration only",
            exit: "No trading exit logic changed; account configuration only",
            entryTime: "N/A",
            exitTime: "N/A",
            location: "Administration -> Crypto Account + /api/crypto-account + Flyway V62",
            classes: [
                "com.crypto.account.controller.CryptoAccountConfigurationController",
                "com.crypto.account.service.CryptoAccountConfigurationService",
                "com.crypto.account.service.CryptoCredentialCipher",
                "com.crypto.account.domain.CryptoAccountConfiguration",
                "src/main/resources/db/migration/V62__create_user_crypto_account_configuration.sql"
            ],
            cause: "The application had one authenticated login system but no user-owned exchange-account boundary. Future Binance LIVE_MICRO configuration would otherwise be global and could mix credentials/risk limits between users.",
            solution: "Create one BINANCE crypto_account_configuration row per app_user and resolve ownership only from Principal.getName(). Store execution mode and micro-live safety limits per user. Encrypt API key/secret with AES-GCM using CRYPTO_ACCOUNT_MASTER_KEY; return only a masked key hint and never return the API secret. Keep shared market data, signal scoring, wallet strategy and all Production/Replay behavior unchanged.",
            behavior: "Each logged-in user sees and edits only their own Crypto Account configuration. Defaults are PAPER, max order 10 USDT, max exposure 50 USDT, max 3 open positions and max 10 USDT daily loss. LIVE_MICRO is prepared as configuration metadata only; FIX-031 does not send Binance orders.",
            regression: "Create two app_user accounts, GET /api/crypto-account under each session and confirm different rows/user_id values. Update one account and confirm the other is unchanged. Verify raw API secret is never returned. Verify saving credentials fails clearly when CRYPTO_ACCOUNT_MASTER_KEY is missing/invalid. Run existing Production/Replay tests unchanged."
        },
        {
            id: "FIX-032",
            status: "IMPLEMENTED",
            title: "Separate Wallet/Binance navigation and configure LIVE_MICRO safety isolation",
            scenario: "Keep the proven trading brain unchanged while separating shadow Wallet from user-owned Binance configuration and preparing loss circuit breakers",
            symbol: "ALL",
            entry: "No entry logic changed",
            exit: "No exit logic changed; future safety blocks must never block risk-reducing exits",
            entryTime: "N/A",
            exitTime: "N/A",
            location: "Left menu -> Wallet / Binance; Trade Inspector -> Venue filter; Flyway V63",
            classes: [
                "com.crypto.account.service.CryptoAccountConfigurationService",
                "com.crypto.account.domain.CryptoAccountConfiguration",
                "com.crypto.inspector.service.TradeInspectorService",
                "src/main/resources/db/migration/V63__add_binance_live_micro_safety_controls.sql"
            ],
            cause: "Paper/shadow Wallet and real Binance configuration were mixed inside Administration, and Trade Inspector had no execution-venue identity. LIVE_MICRO also needed user-scoped rolling-loss and isolation thresholds before any real execution bridge is enabled.",
            solution: "Move Wallet and Binance to separate first-class left-menu pages. Keep existing wallet trades explicitly tagged WALLET and provide ALL/WALLET/BINANCE filtering. Persist per-user safety settings: max order/exposure/positions, 3-loss pause for 120m, 4-loss manual resume, 240m rolling loss limit 10 USDT, daily loss 20 USDT, same-symbol 2-loss quarantine for 240m, 0.30% slippage pause and 2 Binance failures pause.",
            behavior: "Administration contains system/market configuration only. Wallet remains the existing internal execution/shadow account. Binance contains only the logged-in user's credentials and LIVE_MICRO safety configuration. BINANCE Trade Inspector results remain empty until genuine Binance fills exist; shadow rows are never relabeled as real executions.",
            regression: "Verify /wallet preserves current wallet configuration/assets/trades; /binance persists only the authenticated user's V63 limits; /administration contains no wallet/binance account forms; Trade Inspector WALLET shows current trades and BINANCE shows none before the live bridge. Existing Production/Replay strategy tests must remain unchanged."
        }
,
        {
            id: "FIX-035",
            title: "Independent BUY/SELL signal refresh and execution filters",
            status: "DONE",
            scenario: "Dashboard BUY and SELL signal evidence needs its own cadence and short rolling windows without coupling to the heavy dashboard refresh.",
            symbol: "ALL",
            entry: "N/A",
            exit: "N/A",
            entryTime: "N/A",
            exitTime: "N/A",
            replayWindow: "No replay required; display/data-loading behavior only",
            location: "Dashboard BUY and SELL signals panel + /api/dashboard/signals",
            classes: ["DashboardApiController", "WalletTradeRepository", "dashboard.js", "dashboard.html"],
            cause: "Signal evidence shared the dashboard lifecycle and offered broad day-based periods only. Executed and blocked BUY states could not be isolated for focused review.",
            solution: "Give the signal evidence board its own timer and Load action; add KSA-aware Today/4h/2h/1h/all-time windows and batched execution-state filtering.",
            behavior: "Users can review all actionable signals, wallet-executed BUY/SELL positions, or BUY positions blocked by the final entry gate using an independent Off/10s/1m/5m refresh cadence.",
            regression: "Diagnostic/data-loading only. Production, Replay, scoring, BUY/SELL, Execution Intelligence, TP/SL, Wallet, Binance and position management remain unchanged."
        }
,
        {
            id: "FIX-038",
            title: "Blocked BUY diagnostics and truthful production exit table",
            status: "DONE",
            scenario: "Trade Inspector forensic visibility for non-executed BUY signals and real production close triggers.",
            symbol: "ALL",
            entry: "Blocked BUY/STRONG_BUY signals remain non-executed; display only",
            exit: "Production exits show TAKE_PROFIT / STOP_LOSS / PROFIT_LOCK / SIGNAL_SELL or other real close trigger",
            entryTime: "N/A",
            exitTime: "N/A",
            replayWindow: "No replay required; diagnostic/read-only feature only",
            location: "Trade Inspector + read-only TradeSignal and ProductionExitAudit projections",
            classes: ["TradeInspectorService", "TradeInspectorController", "TradeSignalRepository", "ProductionExitAuditRepository", "trade-inspector.html", "trade-inspector.js", "trade-inspector.css"],
            cause: "A BUY could be visibly strong in scoring but never execute, while Trade Inspector had no dedicated place to show the exact persisted blocking gate. Completed exits could also look like generic SELL decisions even when production actually closed by TP, SL, Profit Lock or mechanical protection.",
            solution: "Add a blocked-BUY table sourced from persisted final_entry_allowed=false BUY/STRONG_BUY signals, including score, confidence, strategy/regime, primary blocking authority and the persisted explanation. Add a separate production-exit table sourced from immutable production_exit_audit rows and intentionally omit the generic decision column, showing the true close trigger, source signal id, position recommendation and close explanation instead.",
            behavior: "No signal is recalculated and no trading authority changes. BUY/SELL scoring, Execution Intelligence, sizing, TP/SL, position management, Wallet, Binance and Replay remain unchanged.",
            regression: "Read-only/API/UI change. JavaScript syntax validation passes; endpoints use persisted production evidence only. Existing strategy/replay tests are expected to remain unchanged."
        }
,
        {
            id: "FIX-037",
            title: "Atomic wallet USDT balance mutations",
            status: "DONE",
            scenario: "Concurrent wallet executions could overwrite a just-completed USDT credit and make principal disappear from the paper wallet.",
            symbol: "UNIUSDT / DOGEUSDT / PEPEUSDT",
            entry: "DOGE $125 + PEPE $250",
            exit: "UNI $251.970005083884",
            entryTime: "2026-08-21 14:50:42–14:50:59 DB time",
            exitTime: "2026-08-21 14:50:41 DB time",
            replayWindow: "No strategy replay required; accounting-only concurrency regression",
            location: "WalletAssetRepository + WalletAutoExecutionService + WalletService",
            classes: ["WalletAssetRepository", "WalletAutoExecutionService", "WalletService", "WalletAssetRepositoryAtomicMutationTest"],
            cause: "USDT used a read-modify-save entity pattern. A BUY could persist an older balance after a concurrent SELL credited proceeds, erasing the SELL credit. The Aug-21 incident erased exactly 251.970005083884 USDT, matching the UNI SELL proceeds.",
            solution: "Use atomic database quantity +/- updates for every wallet USDT credit/debit, including automatic BUY/SELL exits, manual trades, deposits and withdrawals. Debit also checks sufficient funds in the same SQL statement.",
            behavior: "Trading decisions, scoring, sizing, TP/SL and exit reasons are unchanged. Only wallet cash mutation semantics are hardened against lost updates.",
            regression: "Exact incident arithmetic is covered: 9749.900237510953 + 251.970005083884 - 125 - 250 = 9626.870242594837. Repository tests also guard the atomic SQL contract. Replay behavior is intentionally unchanged because this is not a strategy change."
        }
,
        {
            id: "FIX-039",
            title: "Trade Activity interval_code schema correction",
            status: "DONE",
            scenario: "Trade Activity API failed with MySQL Unknown column ts.interval after the Trade Activity UI was introduced.",
            symbol: "ALL / ACEUSDT observed",
            entry: "N/A — read-only activity feed",
            exit: "N/A — read-only activity feed",
            entryTime: "2026-08-22 16:27 KSA observation",
            exitTime: "N/A",
            replayWindow: "No replay required; read-only SQL/UI regression",
            location: "TradeActivityService",
            classes: ["TradeActivityService", "trade-activity.js"],
            cause: "Native SQL selected ts.interval, but trade_signal physically stores the timeframe in interval_code. The Java TradeSignal field is named interval only through JPA mapping, so that field name cannot be used as a MySQL column in JdbcTemplate SQL.",
            solution: "Replace ts.interval with ts.interval_code in BUY/SELL, BLOCKED and EXECUTED Trade Activity queries. Preserve UTC database timestamps and leave timezone conversion to the frontend display layer.",
            behavior: "Trade Activity loads normally and shows the persisted timeframe without changing signal generation, Replay, Production execution, wallet accounting or strategy behavior. Database timestamps remain UTC; the page displays them in Asia/Riyadh for the current KSA UI requirement.",
            regression: "Verify BUY/SELL, BLOCKED and EXECUTED filters independently and together. Confirm no SQLSyntaxErrorException occurs, timeframe is populated, and a UTC event timestamp is displayed as KSA UTC+3 on Trade Activity."
        },
        {
            id: "FIX-052",
            title: "Exact Production live-price replay parity and UTC backend clock",
            status: "DONE",
            scenario: "Replay shared Production decision policies but mechanical TP/SL/profit-lock checks were driven by replay signal/candle-close prices, while Production protects positions on every live 1m Binance price update. Backend wallet/replay day boundaries also depended on JVM system timezone in several places.",
            symbol: "ALL",
            entry: "Production BUY logic unchanged",
            exit: "Replay mechanical exits now consume persisted Production live-price observations when available",
            entryTime: "UTC internally",
            exitTime: "UTC internally; frontend remains responsible for KSA/local display",
            replayWindow: "Any window after V64 deployment with persisted market_price_event rows",
            location: "BinanceKlineService / MarketPriceEventService / RegressionTestWorker / ShadowProductionReplayService / WalletService / WalletAutoExecutionService",
            classes: ["BinanceKlineService", "MarketPriceEventService", "RegressionTestWorker", "ShadowProductionReplayService", "WalletService", "WalletAutoExecutionService"],
            cause: "Production calls LivePositionProtectionService for every canonical 1m kline live-price update before candle-close analysis. Replay previously saw only generated signal prices, so an intra-minute stop/profit-lock/TP event could differ. Replay fallback also evaluated profit lock before stop loss, unlike Production. ZoneId.systemDefault() could additionally shift wallet/replay trading-day boundaries by server timezone.",
            solution: "Persist each canonical Production 1m live-price observation with its Binance UTC event timestamp, feed those observations into Replay before same-time candle-close signals, mirror Production protection order TP -> SL -> profit lock -> normal exit, prevent same-signal SELL->BUY reopen, and force backend wallet/replay day calculations to UTC. Exact-parity runs are not marked passed when the historical window predates persisted live-price events.",
            behavior: "Production trading decisions are unchanged. Replay becomes materially closer to the actual Production event sequence and can reproduce live mechanical exits using the same observed prices. Database/backend timestamps remain UTC; KSA conversion stays in the presentation layer.",
            regression: "After V64 deployment, run Production long enough to collect market_price_event rows, then replay the same UTC window. Verify the run reports live-price parity active, BUY decisions match, TP/SL/profit-lock/normal exits occur on persisted Production price timestamps, and signal-driven SELL cannot reopen on the same signal invocation."
        }
,
        {
            id: "FIX-053",
            title: "Active-position analysis path and chart position overlay",
            status: "DONE",
            scenario: "Dashboard BUY/SELL panel should focus only on the selected symbol's active wallet position, show its recent management analysis path, preserve every future take-profit extension, and display the open position directly on the price chart without an obstructive hover card.",
            symbol: "ALL active wallet positions",
            entry: "Current wallet_managed_position average entry",
            exit: "N/A — active position monitoring",
            entryTime: "Position opened_at (UTC in DB, local/KSA in UI)",
            exitTime: "N/A",
            replayWindow: "No trading-behavior replay required; this is dashboard/audit persistence only. Verify Production TP revision persistence independently.",
            location: "Dashboard active-position panel + LivePositionProtectionService TP-extension audit",
            classes: ["DashboardApiController", "LivePositionProtectionService", "PositionManagementEvent", "PositionManagementEventRepository", "dashboard.js", "dashboard.css"],
            cause: "The previous dashboard mixed unrelated recent BUY/SELL signals, exposed broad signal filters, showed only the latest TP value, and used a fixed hover tooltip that obscured chart candles. Production TP extensions existed only in logs, so the UI could not reconstruct the real target path reliably.",
            solution: "Center the panel on OPEN wallet_managed_position only; add 15m/1h/4h/1d/1w analysis windows; return persisted position_analysis steps and decision paths; persist TAKE_PROFIT_EXTENDED old/new targets as position_management_event rows; draw ENTRY/SL/TP/LOCK levels and the OPEN point on the chart; replace the fixed OHLC hover card with a compact cursor-following price label.",
            behavior: "Dashboard now explains the live position lifecycle without changing BUY/SELL scoring, execution, sizing, exits or Replay. TP revisions after deployment are auditable as old target → new target. Database timestamps remain UTC and CryptoTime performs local/KSA presentation.",
            regression: "With no open position, panel must show no active position. With an open position, switch 15m/1h/4h/1d/1w and confirm only that position's analyses appear. Trigger/fixture a TP extension and verify old/new target history. Confirm chart shows position levels and hover displays only price near the cursor."
        }        ,
        {
            id: "FIX-054",
            title: "BUY/SELL analysis graph-only drill-down",
            status: "DONE",
            scenario: "Active-position BUY/SELL analysis should stay compact. Remove the duplicated Entry decision path and replace the per-row analysis-path details with a single View graph action that locates the exact persisted analysis price/time on the dashboard chart.",
            symbol: "ALL active wallet positions",
            entry: "No trading behavior change",
            exit: "No trading behavior change",
            entryTime: "Analysis timestamps remain UTC in backend/database and local/KSA in UI",
            exitTime: "N/A",
            replayWindow: "No Replay regression required; dashboard presentation only.",
            location: "Dashboard active-position BUY/SELL analysis panel and chart deep-link presentation",
            classes: ["dashboard.html", "dashboard.js", "dashboard.css"],
            cause: "FIX-053 exposed the full entry decision path in the active-position summary and repeated detailed decision paths per analysis row. The user needs the dashboard to answer where the analysis happened on price first, without dense text obscuring the trading location.",
            solution: "Remove Entry decision path from the summary, replace the final analysis-path column with View graph, and deep-link the dashboard to a focused 5m history window centered around the exact persisted UTC analysis timestamp/price. BUY, SELL and neutral analysis locations receive distinct graph markers.",
            behavior: "Each active-position analysis row now provides one visual drill-down. Clicking View graph loads the relevant chart window and highlights the exact location. No signal scoring, execution, wallet, TP/SL, Production or Replay code is changed.",
            regression: "Open an active position, choose any 15m/1h/4h/1d/1w analysis window, verify Entry decision path is absent, click View graph on BUY/SELL/neutral rows, and confirm the graph focuses on and highlights the correct stored timestamp/price while timestamps display in local/KSA time."
        }


        ,
        {
            id: "FIX-055",
            title: "Long-lived opportunity price memory and STOP_EXPOSED entry-quality penalty",
            status: "IMPLEMENTED",
            scenario: "PEPEUSDT 22 Aug 2026 · opportunity started 21:57 KSA and executed 23:08 KSA at 0.00000418 after earlier BUY/STRONG_BUY evidence around 0.00000411-0.00000416",
            symbol: "PEPEUSDT",
            entry: "Historical BUY 0.000004180000 · wallet trade #636 · IMMEDIATE_VALIDATION",
            exit: "Historical SELL 0.000004140000 · STOP_LOSS · wallet trade #659",
            entryTime: "2026-08-22 23:08:03 KSA (20:08:03 UTC)",
            exitTime: "2026-08-22 23:16:08 KSA (20:16:08 UTC)",
            replayWindow: "2026-08-22 21:50 KSA → 2026-08-22 23:20 KSA",
            location: "Shared ExecutionIntelligenceService Entry Quality + execution_opportunity price memory",
            classes: [
                "com.crypto.execution.service.ExecutionIntelligenceService",
                "com.crypto.execution.domain.ExecutionOpportunity",
                "com.crypto.execution.service.ExecutionReplayScope",
                "V66__execution_opportunity_entry_price_memory.sql",
                "ExecutionIntelligenceServiceTest",
                "fix-registry.js"
            ],
            cause: "Entry Quality used a rolling recent-signal reference, so a long-lived opportunity could forget substantially cheaper prices as the window moved upward with the market. STOP_EXPOSED was visible in liquidity diagnostics but did not reduce Entry Quality. PEPE therefore remained GOOD_ENTRY 75/100 at 0.00000418 even though the opportunity had existed for about 71 minutes and its stop was explicitly exposed.",
            solution: "Persist the opportunity's first valid BUY price and best/lowest observed price. Entry Quality now uses those values when they are better than the rolling reference, so confirmation cannot erase earlier cheaper opportunity prices. STOP_EXPOSED applies a 15-point soft Entry Quality penalty; TARGET_BLOCKED remains the existing hard liquidity veto. The existing Entry Quality guard then reduces size or blocks CHASE_ENTRY without introducing a separate execution path.",
            behavior: "A strengthening signal can still execute, but confirmation no longer makes a late price look artificially fresh. Long-lived breakouts that have moved several ATR from their opportunity base and also expose the stop can fall below the chase cutoff and remain unexecuted. Production and Replay use the same ExecutionIntelligenceService and replay-scoped ExecutionOpportunity state.",
            regression: "PEPE-style regression asserts an opportunity anchored near 0.00000407, current price 0.00000418, ~71-minute age and STOP_EXPOSED produces CHASE_ENTRY quality below the execution cutoff. A control without opportunity memory/liquidity warning keeps ordinary Entry Quality behavior. V66 stores only prices; all database/Binance timestamps remain UTC and frontend display remains local/KSA."
        }

        ,
        {
            id: "FIX-056",
            title: "Fresh execution-price authority and current-cycle setup wake-up",
            status: "IMPLEMENTED",
            scenario: "SOLUSDT wallet trade #617 exposed two coupled gaps: a 5m confirmation accepted 1m signal #133530 that was 63 seconds old, then WalletAutoExecutionService filled at that signal snapshot price instead of the current market price.",
            symbol: "SOLUSDT / all BUY executions",
            entry: "Historical SOL #617 decision snapshot 94.79; execution occurred ~65 seconds later",
            exit: "N/A — execution correctness fix",
            entryTime: "2026-08-22 22:45:08 KSA (19:45:08 UTC)",
            exitTime: "N/A",
            replayWindow: "SOL #617 exact UTC window plus PEPE FIX-055 execution-price revalidation regression",
            location: "Shared execution-price authority, Production PaperTradingService, WalletAutoExecutionService, ExecutionIntelligenceService and ShadowProductionReplayService",
            classes: [
                "com.crypto.execution.service.ExecutionPriceAuthorityService",
                "com.crypto.execution.service.ExecutionReplayScope",
                "com.crypto.execution.service.ExecutionIntelligenceService",
                "com.crypto.market.service.MarketPriceEventService",
                "com.crypto.service.PaperTradingService",
                "com.crypto.wallet.service.WalletAutoExecutionService",
                "com.crypto.regression.service.ShadowProductionReplayService",
                "com.crypto.wallet.domain.WalletTrade",
                "V67__fresh_execution_price_authority.sql",
                "ExecutionIntelligenceServiceTest",
                "REPLAY_PRODUCTION_PARITY.md"
            ],
            cause: "TradeSignal.latestPrice is decision-time evidence, but wallet BUY execution reused it as the fill price. SETUP_CONFIRMATION_WAKEUP also allowed a prior 1m cycle up to two minutes old, so SOL #617 used a 63-second-old 1m signal when the 5m trigger arrived. Risk sizing was also calculated from that stale snapshot.",
            solution: "Keep signal price immutable; resolve a fresh canonical Binance 1m market_price_event at execution time; reject missing/stale (>15s) execution prices; tighten confirmation wake-up 1m freshness to 45s; re-run Entry Quality and stop/target sanity against the actual execution price; size and persist the BUY from that same price; store decision_price_usdt and execution_price_observed_at for audit. Replay consumes the same persisted UTC market-price events through ExecutionReplayScope and runs the identical fresh-price revalidation/sizing path.",
            behavior: "A valid signal can no longer fill at an old snapshot. If price moved to a poor/chase location, below stop, or above target before execution, the BUY is rejected or resized by the shared Entry Quality guard. SOL #617's 63-second-old confirmation authority is rejected. Production and exact Replay use the same execution-price source semantics while keeping wallet persistence isolated.",
            regression: "Proven tests cover SOL #617 stale 63-second wake-up rejection and execution-time price revalidation while preserving TradeSignal.latestPrice. Replay parity documentation requires the same UTC market_price_event stream for exact execution-price parity. FIX-055 PEPE anchor/STOP_EXPOSED logic is recalculated again at the fresh execution price."
        },
        {
            id: "FIX-057",
            title: "Fix Registry numeric ascending order",
            status: "ACTIVE · UI/DOCUMENTATION ONLY",
            scenario: "Fix Registry records were stored in mixed insertion order, so newer fixes could appear before older fixes and the history was difficult to follow chronologically.",
            symbol: "ALL / documentation only",
            entry: "N/A",
            exit: "N/A",
            entryTime: "2026-08-23 KSA registry review",
            exitTime: "N/A",
            replayWindow: "No replay required; Fix Registry presentation only",
            location: "Fix Registry ordering",
            classes: ["fix-registry.js"],
            cause: "The FIXES array contains records added at different times and was rendered in physical source order instead of by numeric FIX id.",
            solution: "Sort registry records numerically by the number in FIX-### before rendering and before Copy all fixes output. This keeps existing records intact and places FIX-001 first through the latest available FIX id.",
            behavior: "The Bug Fixes/Fix Registry page and copied registry output now show existing fixes in ascending numeric order. Missing historical IDs are not invented or renumbered.",
            regression: "Open Fix Registry and verify FIX ids increase numerically from the earliest existing record through FIX-057; verify Copy all fixes uses the same ascending order. No Production, Replay, scoring, execution, wallet, database-time or KSA display behavior changes."
        }
        ,{
            id: "FIX-058",
            title: "Trade Activity completed WIN/LOST couples",
            status: "ACTIVE · UI/AUDIT ONLY",
            scenario: "Operator needs to review completed BUY→SELL trade couples and filter them by realized WIN or LOST without changing the existing Trade Activity grid.",
            symbol: "ALL / any traded symbol", entry: "Opening wallet BUY", exit: "Closing wallet SELL",
            entryTime: "Any selected Trade Activity window", exitTime: "Any selected Trade Activity window",
            replayWindow: "No replay required; read-only Production wallet audit feature",
            location: "Trade Activity filters and completed-position read model",
            classes: ["TradeActivityService", "trade-activity.html", "trade-activity.js"],
            cause: "The existing Trade Activity page could filter individual BUY/SELL EXECUTED/BLOCKED rows but could not return the two legs of a completed trade together or classify that completed lifecycle by realized profit/loss.",
            solution: "Add exclusive COUPLE mode beside BUY/SELL and WIN/LOST result filters beside EXECUTED/BLOCKED. Resolve couples from CLOSED wallet_managed_position lifecycle authority, map the opening BUY from entry_signal_id/opened_at, map the closing SELL near the position close timestamp, and classify outcome from the SELL wallet_trade.realized_pnl_usdt. Return both rows adjacent in the existing seven-column grid.",
            behavior: "COUPLE + LOST returns the BUY and SELL rows for completed losing positions; COUPLE + WIN returns both rows for completed winners; WIN+LOST returns both outcome classes. Normal mode remains (BUY or SELL) AND (EXECUTED or BLOCKED) AND symbol. Database timestamps remain UTC and frontend display remains Asia/Riyadh.",
            regression: "For a known completed winner and loser, verify each couple returns exactly two adjacent rows (BUY then SELL), the outcome agrees with wallet_trade.realized_pnl_usdt, symbol/time filters apply, and normal BUY/SELL EXECUTED/BLOCKED searches remain unchanged. No trading, wallet execution, Replay or scoring behavior changes."
        }
        ,{
            id: "FIX-059",
            title: "Trade Activity forensic technical-analysis graph",
            status: "ACTIVE · UI/AUDIT ONLY",
            scenario: "Operator wants to see the real market path, every persisted technical analysis and completed BUY→SELL couples together on a chart inside Trade Activity.",
            symbol: "One selected symbol", entry: "Real executed BUY marker", exit: "Real executed SELL marker",
            entryTime: "Selected Trade Activity window", exitTime: "Selected Trade Activity window",
            replayWindow: "No replay required; read-only persisted Production evidence visualization",
            location: "Trade Activity graph endpoint, page, JS and CSS",
            classes: ["TradeActivityController", "TradeActivityService", "trade-activity.html", "trade-activity.js", "trade-activity.css"],
            cause: "Trade Activity exposed filtered rows but did not provide a single visual timeline showing what the market did, what the technical engine concluded at every persisted analysis point, and where completed wallet BUY/SELL lifecycles actually occurred.",
            solution: "Add a Trade Activity-only read endpoint that returns real closed 1m candles, every persisted trade_signal analysis across timeframes, and completed wallet_managed_position BUY/SELL pairs resolved from real wallet_trade fills. Render a mixed candlestick/scatter forensic chart; keep permanent labels off the plot and show full persisted details when the operator clicks a marker.",
            behavior: "Selecting one symbol loads its real 1m candle path for the chosen time range. BUY/SELL/WATCH/NEUTRAL analysis markers show score, confidence, regime, strategy, confluence, ATR, BTC, liquidity, derivatives and explanations. Executed BUY/SELL couple markers show entry/exit prices, reasons and realized P/L, with the holding interval visually highlighted. Grid filters remain independent so the chart preserves surrounding context. UTC database timestamps are explicitly parsed as UTC and displayed in KSA.",
            regression: "Select a symbol with known signals and a completed trade. Verify candle OHLC matches the candle table, every signal in the range appears once at its persisted generated_at/latest_price, couple markers match wallet_trade fills and realized P/L, clicking markers shows persisted details, ALL-symbol mode does not attempt a misleading multi-symbol price chart, and no Production/Replay/trading writes occur."
        }
        ,{
            id: "FIX-060",
            title: "SELL row View on graph forensic focus",
            status: "IMPLEMENTED",
            scenario: "Trade Activity operator wants to click a SELL row and immediately inspect the exact completed trade on the forensic chart together with the technical-analysis path around entry and exit.",
            symbol: "ALL",
            entry: "Persisted wallet BUY fill",
            exit: "Persisted wallet SELL fill",
            entryTime: "UTC in DB; KSA on UI",
            exitTime: "UTC in DB; KSA on UI",
            location: "Trade Activity grid + forensic graph",
            classes: ["TradeActivityService", "trade-activity.html", "trade-activity.js", "trade-activity.css"],
            cause: "FIX-059 exposed the forensic graph, but SELL rows in the activity grid had no direct navigation to the exact trade lifecycle. Operators had to manually locate the SELL marker on the chart, which is difficult when many analyses/trades exist in the same window.",
            solution: "Expose persisted wallet trade/pair identifiers in the read-only activity projection and add a View on graph action to SELL rows. Resolve the exact completed pair by sell_trade_id/pair_id, zoom from pre-entry setup through exit, highlight the SELL fill, and keep all persisted technical-analysis markers visible in the focused interval.",
            behavior: "Clicking View on graph on an executed SELL selects the row symbol, loads the Trade Activity forensic chart, focuses the matching BUY→SELL lifecycle, highlights the SELL and shows persisted pair details plus the related analyses. Non-paired/blocked SELL rows fall back to a focused event window with surrounding persisted analyses. No Dashboard/Inspector chart behavior changes.",
            regression: "Verify an executed SELL with a known closed wallet_managed_position focuses the exact pair by wallet_trade id, the BUY and SELL markers match persisted fills, all trade_signal analyses in the focus window remain plotted, KSA display is UTC+3 from DB timestamps, and the action performs no trading/Replay writes."
        }

        ,{
            id: "FIX-061",
            title: "Trade Activity readable forensic price/indicator timeline",
            status: "IMPLEMENTED · UI/AUDIT ONLY",
            scenario: "Trade Activity forensic chart was difficult to interpret because the price/timeline context and persisted EMA/Bollinger/retracement evidence were not drawn as continuous series, completed trades did not show start/end KSA labels or failure percentage directly on the graph, and the activity grid required horizontal scrolling.",
            symbol: "One selected Trade Activity symbol",
            entry: "Real wallet BUY fill with START marker",
            exit: "Real wallet SELL fill with END marker and WIN/FAIL percentage",
            entryTime: "MySQL/Binance UTC; rendered explicitly in Asia/Riyadh",
            exitTime: "MySQL/Binance UTC; rendered explicitly in Asia/Riyadh",
            replayWindow: "No replay required; persisted Production evidence visualization only",
            location: "Trade Activity graph read model + page-scoped chart/grid presentation",
            classes: ["TradeActivityService", "trade-activity.html", "trade-activity.js", "trade-activity.css"],
            cause: "FIX-059/060 plotted candles and analysis markers but did not expose the persisted 1m technical_indicator series. The operator therefore could not visually follow EMA20/50/200, SMA20, Bollinger Bands or the persisted ATR retracement level against price. Trade couples also lacked explicit KSA start/end labels and realized percentage on the plot, while nowrap table cells forced a horizontal grid scrollbar.",
            solution: "Return persisted 1m technical_indicator snapshots and atr_retracement_entry_price in the read-only graph payload. Overlay a price-close line, EMA20/50/200, SMA20, Bollinger upper/middle/lower and persisted ATR retracement on the existing candle timeline. Label each completed pair with START/END KSA time and wallet realized return percentage, and make the grid fixed-layout/responsive with wrapping and selective low-priority column hiding instead of horizontal scrolling.",
            behavior: "The Trade Activity-only chart now visibly explains price versus EMA/Bollinger/retracement context on an explicit KSA timeline. Completed trades show START BUY and END SELL markers plus WIN/FAIL percentage calculated from persisted realized P/L over committed BUY gross. Marker detail still exposes the full persisted analyses. The grid fits the viewport without a horizontal scroller. No indicators are recalculated and no trading/Replay behavior changes.",
            regression: "For a selected symbol with technical_indicator data and a completed trade, verify indicator lines match persisted 1m rows, retracement points match trade_signal.atr_retracement_entry_price, chart start/end labels are KSA conversions of UTC DB timestamps, WIN/FAIL percentage matches realized_pnl_usdt / BUY gross, SELL View on graph retains START/END labels, and the activity grid fits desktop/mobile width without horizontal scrolling."
        },
        {
            id: "FIX-062",
            title: "Trade Activity forensic cockpit redesign",
            status: "IMPLEMENTED · UI/AUDIT ONLY",
            scenario: "Trade Activity contained the required forensic data but the chart was visually opaque, BUY/SELL labels covered candles, technical context was hard to scan, and the result grid did not resemble a compact operator cockpit.",
            symbol: "One selected Trade Activity symbol",
            entry: "Persisted wallet BUY fill shown as full-width horizontal BUY price authority",
            exit: "Persisted wallet SELL fill shown as full-width horizontal SELL price authority",
            entryTime: "UTC database timestamp rendered in KSA",
            exitTime: "UTC database timestamp rendered in KSA",
            replayWindow: "No replay required; read-only visualization of persisted Production evidence",
            location: "Trade Activity read model and page-scoped HTML/CSS/ApexCharts presentation",
            classes: ["TradeActivityService", "trade-activity.html", "trade-activity.js", "trade-activity.css"],
            cause: "Permanent START/END point labels and low-contrast chart defaults obscured the candle path. The screen also lacked a compact selected-trade header, volume strip, entry/exit technical summaries and a concise activity list with persisted fill/P&L values.",
            solution: "Rebuild Trade Activity as a responsive forensic cockpit. Use explicit high-contrast ApexCharts colors, real candlesticks plus persisted price/EMA/SMA/Bollinger/retracement series, a synchronized real-volume chart, clean analysis markers, horizontal BUY/SELL price annotations on the y-axis, vertical start/end guides without floating text boxes, selected-trade KSA/result cards, a persisted execution-facts strip, a window win-rate indicator, and entry/exit technical summaries derived from persisted trade_signal rows. Extend the read-only activity projection with persisted fill price and realized P/L fields for the compact list.",
            behavior: "Selecting a completed trade makes BUY/SELL price levels immediately visible without covering candles. Start/end KSA times, duration, realized P/L and WIN/FAIL percentage appear in dedicated cards. The price chart and volume timeline remain readable, analysis markers stay clickable, and the activity list fits the screen without horizontal scrolling. All technical values remain persisted Production evidence; nothing is recalculated for trading.",
            regression: "Verify BUY/SELL horizontal lines equal wallet_trade.price_usdt, result percentage equals persisted realized P/L divided by BUY gross, START/END cards are UTC-to-KSA conversions, entry/exit technical cards resolve persisted trade_signal evidence, volume bars use real candle.volume, indicator lines use technical_indicator values, SELL View on graph focuses the exact lifecycle, and no Production/Replay service is invoked by the graph endpoint."
        }

];

    // FIX-057: registry history must be chronological by FIX number, not by the
    // physical insertion position in this source file. Numeric parsing also avoids
    // lexical ordering surprises if the identifier width ever changes.
    FIXES.sort((a, b) => {
        const numberOf = fix => Number.parseInt(String(fix.id || "").replace(/\D/g, ""), 10) || 0;
        return numberOf(a) - numberOf(b);
    });

    const list = document.getElementById("fix-registry-list");
    const count = document.getElementById("fix-count");
    const copyButton = document.getElementById("copy-fixes-button");
    const copyStatus = document.getElementById("copy-fixes-status");

    const esc = value => String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");

    // FIX-055: the registry contains records from multiple generations. Some use
    // `classes`, while documentation/UI-only fixes use `files`. Normalize here so
    // one older/newer record shape can never crash the entire Bug Fixes page.
    const codeLocations = fix => Array.isArray(fix.classes)
        ? fix.classes
        : (Array.isArray(fix.files) ? fix.files : []);

    const field = (label, value, wide = false) => `
        <div class="fix-field${wide ? " wide" : ""}">
            <small>${esc(label)}</small>
            <span>${esc(value)}</span>
        </div>`;

    // Proven replay guidance is display/audit metadata only. Existing fixes already
    // contain entry/exit timestamps; use them as a safe fallback and let scenario
    // fixes provide a wider explicit replayWindow when a better regression window is known.
    const suggestedReplayWindow = fix => fix.replayWindow ||
        ((fix.entryTime && fix.exitTime && fix.entryTime !== "N/A" && fix.exitTime !== "N/A")
            ? `${fix.entryTime} → ${fix.exitTime}`
            : "Use the scenario regression/protection notes; no fixed trade window is required.");

    function render() {
        count.textContent = `${FIXES.length} ${FIXES.length === 1 ? "fix" : "fixes"}`;
        list.innerHTML = FIXES.map(fix => `
            <li class="fix-entry" data-fix-id="${esc(fix.id)}">
                <div class="fix-entry-head">
                    <div>
                        <h3 class="fix-entry-title">${esc(fix.id)} · ${esc(fix.title)}</h3>
                        <div class="fix-entry-meta">${esc(fix.scenario)}</div>
                    </div>
                    <span class="fix-status">${esc(fix.status)}</span>
                </div>
                <div class="fix-grid">
                    ${field("Trade scenario", fix.scenario)}
                    ${field("Symbol", fix.symbol)}
                    ${field("Entry", fix.entry)}
                    ${field("Exit", fix.exit)}
                    ${field("Entry time", fix.entryTime)}
                    ${field("Exit time", fix.exitTime)}
                    ${field("Suggested Proven replay window", suggestedReplayWindow(fix), true)}
                    ${field("Fix location", fix.location, true)}
                    <div class="fix-field wide">
                        <small>Java classes</small>
                        <ul class="fix-class-list">${codeLocations(fix).map(c => `<li>${esc(c)}</li>`).join("")}</ul>
                    </div>
                    ${field("Cause", fix.cause, true)}
                    ${field("Solution", fix.solution, true)}
                    ${field("What the fix does", fix.behavior, true)}
                    ${field("Regression / protection", fix.regression, true)}
                </div>
            </li>`).join("");
    }

    function asText() {
        return FIXES.map(fix => [
            `• ${fix.id} - ${fix.title}`,
            `Status: ${fix.status}`,
            `Trade scenario: ${fix.scenario}`,
            `Symbol: ${fix.symbol}`,
            `Entry: ${fix.entry}`,
            `Exit: ${fix.exit}`,
            `Entry time: ${fix.entryTime}`,
            `Exit time: ${fix.exitTime}`,
            `Suggested Proven replay window: ${suggestedReplayWindow(fix)}`,
            `Fix location: ${fix.location}`,
            `Java classes/files: ${codeLocations(fix).join(", ")}`,
            `Cause: ${fix.cause}`,
            `Solution: ${fix.solution}`,
            `What the fix does: ${fix.behavior}`,
            `Regression / protection: ${fix.regression}`
        ].join("\n")).join("\n\n");
    }

    copyButton?.addEventListener("click", async () => {
        try {
            await navigator.clipboard.writeText(asText());
            copyStatus.textContent = "Copied";
        } catch (error) {
            const area = document.createElement("textarea");
            area.value = asText();
            document.body.appendChild(area);
            area.select();
            document.execCommand("copy");
            area.remove();
            copyStatus.textContent = "Copied";
        }
        window.setTimeout(() => { copyStatus.textContent = ""; }, 1800);
    });

    render();
})();
