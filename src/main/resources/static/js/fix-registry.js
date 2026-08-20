(() => {
    const FIXES = [
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

];

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
                        <ul class="fix-class-list">${fix.classes.map(c => `<li>${esc(c)}</li>`).join("")}</ul>
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
            `Java classes: ${fix.classes.join(", ")}`,
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
