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
