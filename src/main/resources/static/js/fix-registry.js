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
            status: "IMPLEMENTED",
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
            status: "IMPLEMENTED",
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
