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
