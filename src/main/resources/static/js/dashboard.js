const authenticatedFetch = window.fetch.bind(window);
window.fetch = async (...args) => {
    const response = await authenticatedFetch(...args);
    if (response.status === 401) {
        window.location.assign('/login.html');
        throw new Error('Authentication session expired');
    }
    return response;
};

let candleChart;
let volumeChart;
let atrChart;
let dashboardRefreshTimer;
let dashboardRefreshInFlight = false;
let dashboardOverviewAbortController = null;
let dashboardSelectionRequestId = 0;
let cachedDashboardWallet = {};
let cachedSentimentOverview = {};
let cachedSentimentProviders = [];
let cachedSentimentSystemStatus = { enabled: false, message: 'Loading sentiment status' };
let cachedScoreDiagnostics = {};
let lastSentimentMetadataRefreshAt = 0;
let lastScoreDiagnosticsRefreshAt = 0;
let walletRefreshInFlight = false;
let executionIntelligenceRefreshInFlight = false;
let cachedExecutionSummary = {};
let cachedActiveOpportunities = [];
let cachedActivePositions = [];
let latestWalletExecutions = new Map();
const numberFormatter = new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 });
const moneyFormatter = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 8 });

const el = id => document.getElementById(id);
const value = v => v === null || v === undefined || v === '' ? '—' : numberFormatter.format(Number(v));
const money = v => v === null || v === undefined ? '—' : '$' + moneyFormatter.format(Number(v));
const headerNumber = v => {
    if (v === null || v === undefined || v === '') return '—';
    const n = Number(v);
    if (!Number.isFinite(n)) return '—';
    return new Intl.NumberFormat('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 3 }).format(n);
};
const headerMoney = v => v === null || v === undefined ? '—' : '$' + headerNumber(v);
const dateTime = v => window.CryptoTime.formatLocal(v);
const preciseDateTime = v => window.CryptoTime.formatLocal(v, {year:'numeric', month:'numeric', day:'numeric', hour:'2-digit', minute:'2-digit', second:'2-digit'});
const openSignalAnalysisIds = new Set();
const signalAnalysisDetailCache = new Map();
let pinnedSignalId = localStorage.getItem('cryptoPinnedSignalId');
let aiPerformancePeriod = localStorage.getItem('cryptoAiPerformancePeriod') || 'ALL_TIME';
let signalEvidenceAbortController = null;
// FIX-035: BUY/SELL evidence owns its refresh lifecycle. The main dashboard refresh
// must not silently reload this table because users can run it on demand or at an
// independent 10s / 1m / 5m cadence.
let signalEvidenceRefreshTimer = null;
let signalEvidenceLoadedContextKey = null;
let latestSignalEvidenceContext = null;

// Dashboard chart navigation is deliberately presentation-only. It never feeds
// historical candles back into analysis, signal generation or execution.
let chartHistoryActive = false;
let chartHistoryLoading = false;
let chartNavigationBound = false;
let chartLoadedCandles = [];
let chartLoadedExecutions = [];
let chartLoadedActivePosition = null;
// FIX-092: Persisted Bollinger/ATR rows travel with chart history exactly like candles.
let chartLoadedIndicators = [];
let chartViewport = { min: null, max: null };
let chartDragState = null;
// FIX-092C: Keep only stable, persisted-indicator overlays. Trend-line and Fibonacci
// retracement rendering were removed completely because their browser-derived series
// could corrupt the mixed candlestick chart and obscure View Chart signal focus.
const chartOverlayState = {
    bollinger: localStorage.getItem('dashboardOverlayBollinger') !== '0',
    atr: localStorage.getItem('dashboardOverlayAtr') !== '0'
};

// Debug-only deep link from Administration > Market Move Tracker.
// This only controls dashboard navigation/chart rendering and never feeds back into trading logic.
const dashboardUrlParams = new URLSearchParams(window.location.search);
const requestedDashboardSymbol = String(dashboardUrlParams.get('symbol') || '').trim().toUpperCase();
const requestedDashboardInterval = String(dashboardUrlParams.get('interval') || '').trim().toLowerCase();
const debugFocusStart = dashboardUrlParams.get('focusStart');
const debugFocusEnd = dashboardUrlParams.get('focusEnd');
const debugFocusDirection = String(dashboardUrlParams.get('focusDirection') || '').trim().toUpperCase();
const debugFocusChange = dashboardUrlParams.get('focusChange');
const debugTradeEnabled = dashboardUrlParams.get('debugTrade') === '1';
const debugTradeLabel = String(dashboardUrlParams.get('debugTradeLabel') || 'Replay trade');
const debugEntryTime = dashboardUrlParams.get('debugEntryTime');
const debugEntryPrice = dashboardUrlParams.get('debugEntryPrice');
const debugExitTime = dashboardUrlParams.get('debugExitTime');
const debugExitPrice = dashboardUrlParams.get('debugExitPrice');
// FIX-054: A generic analysis point lets View graph highlight WATCH/HOLD context without
// mislabelling it as a BUY or SELL. Existing entry/exit deep links remain supported.
const debugPointTime = dashboardUrlParams.get('debugPointTime');
const debugPointPrice = dashboardUrlParams.get('debugPointPrice');
const debugPointSide = String(dashboardUrlParams.get('debugPointSide') || 'ANALYSIS').trim().toUpperCase();
const debugTradePoints = (() => {
    if (!debugTradeEnabled) return [];
    const points = [];
    const add = (side, timeValue, priceValue) => {
        if (!timeValue || priceValue == null || priceValue === '') return;
        const time = window.CryptoTime.parseUtc(timeValue);
        const price = Number(priceValue);
        if (Number.isNaN(time.getTime()) || !Number.isFinite(price)) return;
        points.push({side, time, price});
    };
    add('BUY', debugEntryTime, debugEntryPrice);
    add('SELL', debugExitTime, debugExitPrice);
    add(debugPointSide, debugPointTime, debugPointPrice);
    return points;
})();
const debugMoveFocus = (() => {
    if (!debugFocusStart || !debugFocusEnd) return null;
    const start = window.CryptoTime.parseUtc(debugFocusStart);
    const end = window.CryptoTime.parseUtc(debugFocusEnd);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) return null;
    return { start, end, direction: debugFocusDirection, change: debugFocusChange };
})();

function applyDashboardDeepLinkSelection() {
    const symbolSelect = el('symbol-select');
    const intervalSelect = el('interval-select');
    if (requestedDashboardSymbol && [...symbolSelect.options].some(option => option.value === requestedDashboardSymbol)) {
        symbolSelect.value = requestedDashboardSymbol;
    }
    if (requestedDashboardInterval && [...intervalSelect.options].some(option => option.value === requestedDashboardInterval)) {
        intervalSelect.value = requestedDashboardInterval;
    }
}

async function loadSymbols() {
    try {
        const response = await fetch('/api/dashboard/symbols');
        if (!response.ok) throw new Error('Could not load symbols');
        const symbols = await response.json();
        el('symbol-select').innerHTML = symbols.map(symbol => `<option>${escapeHtml(symbol)}</option>`).join('');
    } catch (_) {
        // Keep BTCUSDT fallback.
    }
}

const DASHBOARD_INTERVAL_LABELS = { '1m': '1m', '5m': '5m', '1h': '1h', '4h': '4h', '1d': '1D' };

function applyConfiguredDashboardIntervals(settings) {
    const select = el('interval-select');
    const allowed = ['1m', '5m', '1h', '4h', '1d'];
    const configured = String(settings?.dashboardIntervals || allowed.join(','))
        .split(',')
        .map(value => value.trim().toLowerCase())
        .filter(value => allowed.includes(value));
    const intervals = configured.length ? configured : ['1m', '5m', '1h'];
    const previous = select.value;
    select.innerHTML = intervals.map(value => `<option value="${value}">${DASHBOARD_INTERVAL_LABELS[value]}</option>`).join('');
    if (intervals.includes(previous)) {
        select.value = previous;
        return false;
    }
    select.value = intervals[0];
    return previous !== select.value;
}

async function refreshDashboard() {
    const requestId = ++dashboardSelectionRequestId;
    if (dashboardOverviewAbortController) dashboardOverviewAbortController.abort();
    dashboardOverviewAbortController = new AbortController();
    dashboardRefreshInFlight = true;

    const symbol = el('symbol-select').value;
    const interval = el('interval-select').value;
    el('refresh-button').disabled = true;
    try {
        const overviewParams = new URLSearchParams({symbol, interval});
        if (debugMoveFocus && interval === '5m') {
            overviewParams.set('focusStart', debugMoveFocus.start.toISOString());
            overviewParams.set('focusEnd', debugMoveFocus.end.toISOString());
        }
        const response = await fetch(`/api/dashboard/overview?${overviewParams.toString()}`, {signal: dashboardOverviewAbortController.signal});
        if (!response.ok) throw new Error(`Dashboard API returned ${response.status}`);

        const data = await response.json();
        if (requestId !== dashboardSelectionRequestId) return;
        data.sentiment = cachedSentimentOverview;
        data.sentimentProviderStatuses = cachedSentimentProviders;
        data.sentimentSystemStatus = cachedSentimentSystemStatus;
        data.scoreDiagnostics = cachedScoreDiagnostics;
        data.wallet = cachedDashboardWallet;

        updateConnection(true);
        el('error-banner').classList.add('hidden');
        render(data);

        // Secondary data must never delay the market dashboard. Wallet and provider
        // metadata refresh independently after the main chart/signal payload renders.
        void refreshDashboardWallet();
        void refreshSentimentMetadata(symbol);
        void refreshExecutionIntelligence();
    } catch (error) {
        if (error?.name === 'AbortError') return;
        updateConnection(false);
        el('error-banner').textContent = error.message;
        el('error-banner').classList.remove('hidden');
    } finally {
        if (requestId === dashboardSelectionRequestId) {
            dashboardRefreshInFlight = false;
            dashboardOverviewAbortController = null;
            el('refresh-button').disabled = false;
        }
    }
}

async function refreshDashboardForSelection() {
    // A symbol/timeframe change starts from the latest market view. Historical
    // panning is local to the currently selected symbol/timeframe.
    resetChartHistoryNavigation();
    const symbol = el('symbol-select').value;
    const interval = el('interval-select').value;
    const requestId = dashboardSelectionRequestId + 1;

    // Render the chart from a deliberately small endpoint first. The full
    // overview contains many independent diagnostics and wallet queries and
    // continues in the background after the market chart is already visible.
    try {
        const params = new URLSearchParams({symbol, interval});
        if (debugMoveFocus && interval === '5m') {
            params.set('focusStart', debugMoveFocus.start.toISOString());
            params.set('focusEnd', debugMoveFocus.end.toISOString());
        }
        const response = await fetch(`/api/dashboard/chart?${params.toString()}`);
        if (response.ok && symbol === el('symbol-select').value && interval === el('interval-select').value) {
            const data = await response.json();
            renderFastMarket(data);
        }
    } catch (_) {
        // The full overview below remains the fallback if the fast chart call fails.
    }

    // Do not await this from the select change handler: chart interaction stays responsive.
    void refreshDashboard();
}

function renderFastMarket(data) {
    if (!data) return;
    const lastUpdated = el('last-updated');
    if (lastUpdated) lastUpdated.textContent = `Updated ${preciseDateTime(data.updatedAt)}`;
    el('market-subtitle').textContent = `${data.symbol} · ${displayInterval(data.interval)}${data.displayOnlyInterval ? ' · display only' : ''}${debugMoveFocus && data.interval === '5m' ? ' · DEBUG MOVE ZONE' : ''}`;
    const latestPrice = data.livePrice ?? data.candles?.at(-1)?.close;
    el('header-live-symbol').textContent = data.symbol || '—';
    el('header-live-price').textContent = headerMoney(latestPrice);
    // Avoid showing execution markers from the previously selected symbol while
    // the full dashboard payload is still loading.
    renderCharts(data.candles || [], [], {activePosition: data.activePosition || null, indicatorSeries: data.indicatorSeries || []});
}

async function refreshDashboardWallet() {
    if (walletRefreshInFlight) return;
    walletRefreshInFlight = true;
    try {
        const response = await fetch('/api/wallet/dashboard');
        if (!response.ok) return;
        cachedDashboardWallet = await response.json();
        const intervalChanged = applyConfiguredDashboardIntervals(cachedDashboardWallet.settings || {});
        renderWalletHeader(cachedDashboardWallet);
        if (intervalChanged && !dashboardRefreshInFlight) {
            window.setTimeout(refreshDashboard, 0);
        }
    } catch (_) {
        // Keep the last successful wallet summary; market data remains usable.
    } finally {
        walletRefreshInFlight = false;
    }
}

async function refreshSentimentMetadata(symbol) {
    const now = Date.now();
    if (now - lastSentimentMetadataRefreshAt < 60000) return;
    lastSentimentMetadataRefreshAt = now;
    try {
        const [overviewResponse, providerResponse, statusResponse] = await Promise.all([
            fetch(`/api/sentiment/${encodeURIComponent(symbol)}`),
            fetch(`/api/sentiment/providers/${encodeURIComponent(symbol)}`),
            fetch('/api/sentiment/status')
        ]);
        if (overviewResponse.ok) cachedSentimentOverview = await overviewResponse.json();
        if (providerResponse.ok) cachedSentimentProviders = await providerResponse.json();
        if (statusResponse.ok) cachedSentimentSystemStatus = await statusResponse.json();
        renderSentiment(cachedSentimentOverview, cachedSentimentProviders, cachedSentimentSystemStatus);
    } catch (_) {
        // Sentiment metadata is supplemental and must not block Dashboard refresh.
    }
}

async function refreshScoreDiagnostics() {
    const now = Date.now();
    if (now - lastScoreDiagnosticsRefreshAt < 60000) return;
    lastScoreDiagnosticsRefreshAt = now;
    try {
        const response = await fetch('/api/dashboard/score-diagnostics');
        if (!response.ok) return;
        cachedScoreDiagnostics = await response.json();
        renderScoreDiagnostics(cachedScoreDiagnostics);
    } catch (_) {
        // Diagnostics are supplemental and never block live market rendering.
    }
}

function render(data) {
    const s = data.summary;
    const lastUpdated = el('last-updated');
    if (lastUpdated) lastUpdated.textContent = `Updated ${preciseDateTime(data.updatedAt)}`;
    renderHeaderLivePrice(data);
    el('market-subtitle').textContent = `${data.symbol} · ${displayInterval(data.interval)}${data.displayOnlyInterval ? ' · display only' : ''}${debugMoveFocus && data.interval === '5m' ? ' · DEBUG MOVE ZONE' : ''}`;
    renderWalletHeader(data.wallet || {});
    renderPipeline(data.pipeline);
    renderIndicators(data.indicator || {});
    renderAiAnalysis(data.signals || [], data.indicator || {});
    renderSentiment(data.sentiment || {}, data.sentimentProviderStatuses || [], data.sentimentSystemStatus || {});
    renderSchedules(data.schedule || {});
    applyDashboardRefreshSchedule(data.schedule || {});
    renderCharts(data.candles || [], data.executions || [], {activePosition: data.activePosition || null, indicatorSeries: data.indicatorSeries || []});
    latestSignalEvidenceContext = {
        symbol: data.symbol,
        interval: data.interval,
        displayOnlyInterval: Boolean(data.displayOnlyInterval),
        timeframeSnapshot: data.timeframeSnapshot || {},
        executions: data.executions || [],
        openPositions: data.openPositions || [],
        closedPositions: data.closedPositions || []
    };
    // FIX-035: do not let the normal dashboard renderer overwrite the independently
    // loaded BUY/SELL evidence table. A new symbol/timeframe gets one dedicated load;
    // subsequent updates happen only via its Load button or its own refresh timer.
    const signalContextKey = `${data.symbol}|${el('signal-evidence-period')?.value || '1H'}|${el('signal-evidence-mode')?.value || 'OPEN_POSITION'}`;
    if (signalEvidenceLoadedContextKey !== signalContextKey) {
        const signalBody = el('signals-body');
        if (signalBody) signalBody.innerHTML = '<tr><td colspan="7" class="empty">Loading active-position analysis path…</td></tr>';
        void refreshSignalEvidence(false);
    }
    renderTradeHistory(data.closedPositions || []);
    window.requestAnimationFrame(syncDashboardHeaderOffset);
}


async function refreshSignalEvidence(showLoading = true) {
    const context = latestSignalEvidenceContext;
    const periodSelect = el('signal-evidence-period');
    const modeSelect = el('signal-evidence-mode');
    const status = el('signal-evidence-status');
    const body = el('signals-body');
    const summary = el('active-position-analysis-summary');
    if (!context || !periodSelect || !body) return;

    if (signalEvidenceAbortController) signalEvidenceAbortController.abort();
    signalEvidenceAbortController = new AbortController();
    const requestedSymbol = context.symbol;
    const windowValue = periodSelect.value || '1H';
    const mode = modeSelect?.value || 'OPEN_POSITION';
    if (showLoading) body.innerHTML = '<tr><td colspan="7" class="empty">Loading signals…</td></tr>';
    if (status) status.textContent = 'Loading…';

    try {
        if (mode === 'OPEN_POSITION') {
            const params = new URLSearchParams({symbol: requestedSymbol, window: windowValue});
            const response = await fetch(`/api/dashboard/active-position-analysis?${params.toString()}`, {signal: signalEvidenceAbortController.signal});
            if (!response.ok) throw new Error(`Active-position analysis API returned ${response.status}`);
            const result = await response.json();
            const current = latestSignalEvidenceContext;
            if (!current || current.symbol !== requestedSymbol) return;
            renderActivePositionAnalysis(result);
            if (status) {
                const labels = {'15M':'15 minutes', '1H':'1 hour', '4H':'4 hours', '1D':'1 day', '1W':'1 week'};
                status.textContent = result.active
                    ? `${labels[windowValue] || '1 hour'} · ${Number(result.analysisPath?.length || 0)} analysis step${Number(result.analysisPath?.length || 0) === 1 ? '' : 's'}`
                    : `No active ${requestedSymbol} position`;
            }
        } else {
            // FIX-092: Non-position signal views reuse the existing read-only /signals API.
            // They do not query or modify wallet state and preserve the same persisted decisions.
            const periodMap = {'15M':'15M', '1H':'1H', '4H':'4H', '1D':'TODAY', '1W':'1W'};
            const params = new URLSearchParams({
                symbol: requestedSymbol,
                interval: context.interval || '1m',
                period: periodMap[windowValue] || '1H',
                executionFilter: mode,
                limit: '250'
            });
            const response = await fetch(`/api/dashboard/signals?${params.toString()}`, {signal: signalEvidenceAbortController.signal});
            if (!response.ok) throw new Error(`Signals API returned ${response.status}`);
            const result = await response.json();
            const current = latestSignalEvidenceContext;
            if (!current || current.symbol !== requestedSymbol) return;
            renderSignalEvidenceList(result, mode);
            if (status) status.textContent = `${signalModeLabel(mode)} · ${Number(result.count || 0)} signal${Number(result.count || 0) === 1 ? '' : 's'}`;
        }
        signalEvidenceLoadedContextKey = `${requestedSymbol}|${windowValue}|${mode}`;
    } catch (error) {
        if (error?.name === 'AbortError') return;
        if (status) status.textContent = 'Could not load signals';
        if (summary) summary.innerHTML = '';
        body.innerHTML = `<tr><td colspan="7" class="empty">${escapeHtml(error.message)}</td></tr>`;
    } finally {
        signalEvidenceAbortController = null;
    }
}

function signalModeLabel(mode) {
    return ({BUY_BLOCKED:'Blocked BUY', SELL_SIGNAL:'SELL signals', BUY_SELL:'BUY / SELL signals'})[mode] || 'Open positions';
}

function renderSignalEvidenceList(result, mode) {
    const body = el('signals-body');
    const summary = el('active-position-analysis-summary');
    if (!body || !summary) return;
    const rows = Array.isArray(result?.signals) ? result.signals : [];
    summary.innerHTML = `<div class="active-position-summary-grid"><div><span>Signal view</span><strong>${escapeHtml(signalModeLabel(mode))}</strong><small>${escapeHtml(result?.symbol || '—')} · ${escapeHtml(displayInterval(result?.interval || '1m'))}</small></div><div><span>Signals</span><strong>${rows.length}</strong><small>Persisted pre-wallet decisions</small></div></div>`;
    if (!rows.length) {
        body.innerHTML = `<tr><td colspan="7" class="empty">No ${escapeHtml(signalModeLabel(mode).toLowerCase())} in this window.</td></tr>`;
        return;
    }
    body.innerHTML = rows.map(signal => {
        const decision = String(signal.decision || '—').toUpperCase();
        const blocked = Boolean(signal.buyPositionBlocked);
        const badgeClass = decision.includes('BUY') ? 'positive' : decision.includes('SELL') ? 'negative' : 'neutral';
        const recommendation = blocked ? 'BLOCKED BUY' : (signal.executionState === 'EXECUTED' ? `EXECUTED ${signal.executedSide || decision}` : decision);
        return `<tr class="active-analysis-row ${blocked ? 'blocked-signal-row' : ''}">
            <td>${dateTime(signal.generatedAt || signal.candleOpenTime)}</td>
            <td><strong>${escapeHtml(displayInterval(signal.interval || '—'))}</strong></td>
            <td><span class="badge ${badgeClass}">${escapeHtml(decision.replaceAll('_',' '))}</span></td>
            <td><strong>${escapeHtml(recommendation.replaceAll('_',' '))}</strong>${blocked && signal.primaryBlockingStage ? `<small>${escapeHtml(signal.primaryBlockingStage)}</small>` : ''}</td>
            <td><strong>${escapeHtml(signal.totalScore ?? '—')}</strong><small>${escapeHtml(signal.effectiveConfidence ?? signal.confidenceScore ?? '—')}% confidence</small></td>
            <td>${money(signal.latestPrice)}</td>
            <td><a class="analysis-view-graph" href="${escapeHtml(signalGraphUrl(signal, mode))}">View chart</a></td>
        </tr>`;
    }).join('');
}

function signalGraphUrl(signal, mode) {
    // FIX-092: Deep-link to the exact immutable signal candle and price. A blocked BUY
    // is labelled distinctly; BUY/SELL signals use their persisted side on the chart.
    const at = window.CryptoTime.parseUtc(signal?.candleOpenTime || signal?.generatedAt);
    const price = Number(signal?.latestPrice);
    if (!at || Number.isNaN(at.getTime()) || !Number.isFinite(price)) return '#market';
    const decision = String(signal?.decision || 'ANALYSIS').toUpperCase();
    const side = decision.includes('SELL') ? 'SELL' : decision.includes('BUY') ? 'BUY' : 'ANALYSIS';
    const label = mode === 'BUY_BLOCKED' ? `Blocked BUY #${signal.id}` : `${side} signal #${signal.id}`;
    const params = new URLSearchParams({
        symbol: String(signal?.symbol || el('symbol-select')?.value || 'BTCUSDT').toUpperCase(),
        interval: String(signal?.interval || el('interval-select')?.value || '1m').toLowerCase(),
        focusStart: new Date(at.getTime() - 30 * 60 * 1000).toISOString(),
        focusEnd: new Date(at.getTime() + 30 * 60 * 1000).toISOString(),
        focusDirection: side === 'SELL' ? 'DOWN' : side === 'BUY' ? 'UP' : '',
        debugTrade: '1',
        debugTradeLabel: label,
        debugPointTime: at.toISOString(),
        debugPointPrice: String(price),
        debugPointSide: side
    });
    return `/dashboard?${params.toString()}#market`;
}

function renderActivePositionAnalysis(result) {
    const body = el('signals-body');
    const summary = el('active-position-analysis-summary');
    if (!body || !summary) return;
    if (!result?.active || !result.position) {
        summary.innerHTML = '<div class="empty">The selected symbol has no active wallet position.</div>';
        body.innerHTML = '<tr><td colspan="7" class="empty">Only analysis for an active position is shown here.</td></tr>';
        return;
    }

    const p = result.position;
    const initialTp = Number(p.initialTakeProfit || 0);
    const currentTp = Number(p.takeProfit || 0);
    const tpChanged = initialTp > 0 && currentTp > 0 && Math.abs(initialTp - currentTp) > Math.max(1e-12, Math.abs(initialTp) * 1e-10);
    const management = result.managementEvents || [];
    const tpEvents = management.filter(e => e.type === 'TAKE_PROFIT_EXTENDED');

    summary.innerHTML = `
        <div class="active-position-summary-grid">
            <div><span>Position</span><strong>${escapeHtml(p.symbol)} #${escapeHtml(p.id)}</strong><small>Opened ${dateTime(p.openedAt)}</small></div>
            <div><span>Entry</span><strong>${money(p.entryPrice)}</strong><small>Signal #${escapeHtml(p.entrySignalId ?? '—')} · ${escapeHtml(p.entryDecision || 'BUY')}</small></div>
            <div><span>Current</span><strong>${money(p.currentPrice)}</strong><small>${escapeHtml(p.entryStage || 'OPEN')} · ${Number(p.allocatedPositionPercent || 0)}%</small></div>
            <div><span>Stop loss</span><strong class="negative">${money(p.stopLoss)}</strong></div>
            <div class="${tpChanged ? 'target-revised' : ''}"><span>Take profit</span><strong class="positive">${money(p.takeProfit)}</strong><small>${tpChanged ? `Initial ${money(p.initialTakeProfit)} → current ${money(p.takeProfit)}` : 'Current target'}</small></div>
            <div><span>Profit lock</span><strong>${p.profitLockActive ? money(p.profitLockPrice) : 'Waiting'}</strong><small>${p.profitLockActive ? 'ACTIVE' : 'Not active'}</small></div>
        </div>
        ${tpEvents.length ? `<div class="take-profit-history"><strong>Take-profit revisions</strong>${tpEvents.map(e => `<div><span>${dateTime(e.occurredAt)}</span><b>${money(e.oldValue)} → ${money(e.newValue)}</b><small>${escapeHtml(e.reason || 'Target extended by continuation policy')}</small></div>`).join('')}</div>` : (tpChanged ? '<div class="take-profit-history"><small>Current TP differs from the original entry target. Structured TP revision history starts after FIX-053 deployment.</small></div>' : '')}`;

    const path = result.analysisPath || [];
    if (!path.length) {
        body.innerHTML = '<tr><td colspan="7" class="empty">No position-management analysis was produced inside this window.</td></tr>';
        return;
    }
    body.innerHTML = path.map(a => {
        // FIX-054: Keep the active-position analysis table visual and actionable instead of
        // duplicating the full decision path. The graph deep-link uses the persisted UTC
        // analysis timestamp and price, then the dashboard's existing local-time renderer
        // presents that location in the user's timezone.
        const graphUrl = activePositionAnalysisGraphUrl(p.symbol, a);
        return `<tr class="active-analysis-row">
            <td>${dateTime(a.analyzedAt)}</td>
            <td><strong>${escapeHtml(displayInterval(a.interval || '—'))}</strong></td>
            <td><span class="badge ${String(a.decision || '').includes('BUY') ? 'positive' : String(a.decision || '').includes('SELL') ? 'negative' : 'neutral'}">${escapeHtml(String(a.decision || 'CONTEXT').replaceAll('_',' '))}</span></td>
            <td><strong>${escapeHtml(String(a.recommendation || 'HOLD').replaceAll('_',' '))}</strong></td>
            <td><strong>${escapeHtml(a.score ?? '—')}</strong><small>${escapeHtml(a.confidence ?? '—')}% confidence · exit ${escapeHtml(a.exitScore ?? '—')}/25</small></td>
            <td>${money(a.currentPrice)}<small>${Number(a.unrealizedPnlPercent || 0) >= 0 ? '+' : ''}${Number(a.unrealizedPnlPercent || 0).toFixed(2)}%</small></td>
            <td><a class="analysis-view-graph" href="${escapeHtml(graphUrl)}">View graph</a></td>
        </tr>`;
    }).join('');
}

function activePositionAnalysisGraphUrl(symbol, analysis) {
    // FIX-054: Reuse the dashboard's existing 5m focused-history endpoint purely for presentation.
    // The source timestamp remains UTC from the backend; CryptoTime converts it only when rendered.
    const at = window.CryptoTime.parseUtc(analysis?.analyzedAt);
    const price = Number(analysis?.currentPrice);
    if (!at || Number.isNaN(at.getTime()) || !Number.isFinite(price)) return '#market';
    const rawDirection = String(analysis?.decision || analysis?.recommendation || 'ANALYSIS').toUpperCase();
    const side = rawDirection.includes('SELL') ? 'SELL' : rawDirection.includes('BUY') ? 'BUY' : 'ANALYSIS';
    const params = new URLSearchParams({
        symbol: String(symbol || el('symbol-select')?.value || 'BTCUSDT').toUpperCase(),
        interval: '5m',
        focusStart: new Date(at.getTime() - 45 * 60 * 1000).toISOString(),
        focusEnd: new Date(at.getTime() + 45 * 60 * 1000).toISOString(),
        focusDirection: side === 'SELL' ? 'DOWN' : side === 'BUY' ? 'UP' : '',
        debugTrade: '1',
        debugTradeLabel: `${side === 'ANALYSIS' ? 'Analysis' : side} location`,
        debugPointTime: at.toISOString(),
        debugPointPrice: String(price),
        debugPointSide: side
    });
    return `/dashboard?${params.toString()}#market`;
}

function configureSignalEvidenceRefreshTimer() {
    // FIX-053: Active-position analysis is refreshed by the dashboard itself or on demand.
    // No second independent timer is needed for this focused panel.
}


function renderHeaderLivePrice(data) {
    const livePrice = data.livePrice ?? data.summary?.latestPrice;
    el('header-live-symbol').textContent = data.symbol || '—';
    el('header-live-price').textContent = headerMoney(livePrice);
    el('header-live-timeframe').textContent = `1m market feed · view ${displayInterval(data.interval)}`;
}

function syncDashboardHeaderOffset() {
    const header = document.querySelector('main > .topbar');
    if (!header || window.innerWidth <= 760) {
        document.documentElement.style.removeProperty('--dashboard-fixed-header-height');
        return;
    }
    const height = Math.ceil(header.getBoundingClientRect().height);
    document.documentElement.style.setProperty('--dashboard-fixed-header-height', `${height}px`);
}

function renderTradePerformance(performance) {
    // This dashboard section may be moved/removed independently of the shared JS.
    // Never let an optional card break the main dashboard render.
    if (!el('trade-performance-label')) return;
    const pnl = Number(performance.netPnlUsdt || 0);
    const wins = Number(performance.wins || 0);
    const losses = Number(performance.losses || 0);
    const breakeven = Number(performance.breakeven || 0);
    const count = Number(performance.closedTrades || 0);
    const rate = Number(performance.winRatePercent || 0);
    el('trade-performance-label').textContent = performance.label || 'Recent trades';
    el('trade-performance-pnl').textContent = `${pnl >= 0 ? '+' : ''}${money(pnl)}`;
    el('trade-performance-pnl').className = pnl >= 0 ? 'positive' : 'negative';
    el('trade-performance-record').textContent = `${wins}W / ${losses}L${breakeven ? ` / ${breakeven}B` : ''}`;
    el('trade-performance-win-rate').textContent = `${rate.toFixed(1)}% win rate`;
    el('trade-performance-count').textContent = count === 0
        ? 'No closed wallet trades in this window'
        : `${count} executed closed trade${count === 1 ? '' : 's'} · realized P&L`;

    renderCoinLeader('top-winner', performance.topWinner, true);
    renderCoinLeader('top-loser', performance.topLoser, false);
}

function renderCoinLeader(elementSuffix, leader, winner) {
    const symbolElement = el(`trade-performance-${elementSuffix}`);
    const pnlElement = el(`trade-performance-${elementSuffix}-pnl`);
    if (!symbolElement || !pnlElement) return;
    if (!leader || !leader.symbol) {
        symbolElement.textContent = '—';
        pnlElement.textContent = winner ? 'No profit yet' : 'No loss yet';
        return;
    }
    const pnl = Number(leader.netPnlUsdt || 0);
    const tradeCount = Number(leader.closedTrades || 0);
    symbolElement.textContent = leader.symbol;
    pnlElement.textContent = `${pnl >= 0 ? '+' : ''}${money(pnl)} · ${tradeCount} trade${tradeCount === 1 ? '' : 's'}`;
}

function renderWalletHeader(wallet) {
    const portfolio = Number(wallet.portfolioValueUsdt || 0);
    const available = Number(wallet.availableUsdt || 0);
    const invested = Math.max(0, portfolio - available);
    const totalPnl = Number(wallet.totalPnlUsdt || 0);
    const daily = wallet.dailyTrading || {};
    const startingPortfolio = Number(daily.startingPortfolioUsdt ?? portfolio);
    const todayPnl = portfolio - startingPortfolio;
    const active = Array.isArray(cachedActivePositions) ? cachedActivePositions.length : 0;

    const setMoney = (id, amount, signed = false) => {
        const node = el(id);
        if (!node) return;
        node.textContent = signed ? `${amount >= 0 ? '+' : ''}${money(amount)}` : money(amount);
        if (signed) node.className = amount >= 0 ? 'positive' : 'negative';
    };
    const setPnlMoney = (id, amount) => {
        const node = el(id);
        if (!node) return;
        const value = Number(amount);
        node.textContent = `${value >= 0 ? '+' : ''}$${headerNumber(value)}`;
        node.className = value >= 0 ? 'positive' : 'negative';
    };
    const setHeaderMoney = (id, amount) => {
        const node = el(id);
        if (!node) return;
        node.textContent = headerMoney(amount);
    };
    setHeaderMoney('header-wallet-value', portfolio);
    setHeaderMoney('header-wallet-available', available);
    setHeaderMoney('header-wallet-invested', invested);
    setPnlMoney('header-today-pnl', todayPnl);
    setPnlMoney('header-overall-pnl', totalPnl);
    if (el('header-active-positions')) el('header-active-positions').textContent = active;
    if (el('nav-position-count')) el('nav-position-count').textContent = active;
}

async function refreshExecutionIntelligence() {
    if (executionIntelligenceRefreshInFlight) return;
    executionIntelligenceRefreshInFlight = true;
    try {
        const [summaryResponse, opportunitiesResponse, positionsResponse] = await Promise.all([
            fetch(`/api/execution-intelligence/summary?period=${encodeURIComponent(aiPerformancePeriod)}`),
            fetch('/api/execution-intelligence/opportunities/active'),
            fetch('/api/dashboard/active-positions')
        ]);
        if (summaryResponse.ok) cachedExecutionSummary = await summaryResponse.json();
        if (opportunitiesResponse.ok) cachedActiveOpportunities = await opportunitiesResponse.json();
        if (positionsResponse.ok) cachedActivePositions = await positionsResponse.json();
        renderExecutionIntelligence(cachedExecutionSummary, cachedActiveOpportunities);
        renderActivePositionsKpi(cachedActivePositions);
        renderWalletHeader(cachedDashboardWallet || {});
    } catch (_) {
        // Execution intelligence is operational metadata and must not block live market rendering.
    } finally {
        executionIntelligenceRefreshInFlight = false;
    }
}

function renderExecutionIntelligence(summary = {}, opportunities = []) {
    const active = opportunities.length;
    const building = opportunities.filter(o => String(o.status || '').toUpperCase() === 'BUILDING').length;
    const recovering = opportunities.filter(o => String(o.status || '').toUpperCase() === 'WEAKENING' && Number(o.healthMomentum || 0) > 0).length;
    const weakening = opportunities.filter(o => String(o.status || '').toUpperCase() === 'WEAKENING' && Number(o.healthMomentum || 0) <= 0).length;
    const blocked = opportunities.filter(o => String(o.status || '').toUpperCase() === 'BLOCKED').length;
    const confirmed = opportunities.filter(o => String(o.status || '').toUpperCase() === 'CONFIRMED').length;
    const values = {
        'intel-active': active,
        'intel-building': building,
        'intel-recovering': recovering,
        'intel-weakening': weakening,
        'intel-blocked': blocked,
        'intel-confirmed': confirmed,
        'pipeline-coins-scanned': summary.coinsScanned || 0,
        'pipeline-opportunities-found': summary.opportunitiesFound || 0,
        'pipeline-building': summary.buildingNow ?? building,
        'pipeline-weakening': summary.weakeningNow ?? weakening,
        'pipeline-recovering': summary.recoveringNow ?? recovering,
        'pipeline-ready': summary.readyNow ?? confirmed,
        'pipeline-blocked-rejected': summary.blockedRejected || 0,
        'pipeline-executed': summary.executed || 0,
        'pipeline-managed': summary.activePositions || 0,
        'pipeline-closed': summary.closedTrades || 0,
        'ai-executed': summary.executed || 0,
        'ai-wins': summary.wins || 0,
        'ai-losses': summary.losses || 0,
        'ai-open': summary.activePositions || 0,
        'ai-building-now': summary.buildingNow ?? building,
        'ai-coins-analyzed': summary.coinsScanned || 0
    };
    Object.entries(values).forEach(([id, value]) => { const node = el(id); if (node) node.textContent = value; });
    if (el('ai-win-rate')) el('ai-win-rate').textContent = `${headerNumber(summary.winRatePercent || 0)}%`;
    if (el('ai-profit-factor')) el('ai-profit-factor').textContent = summary.profitFactor == null ? (Number(summary.wins || 0) > 0 ? '∞' : '—') : headerNumber(summary.profitFactor);
    const realized = Number(summary.realizedPnlUsdt || 0);
    if (el('ai-realized-pnl')) { el('ai-realized-pnl').textContent = `${realized >= 0 ? '+' : ''}${money(realized)}`; el('ai-realized-pnl').className = realized >= 0 ? 'positive' : 'negative'; }
    const periodLabels = {ALL_TIME:'All Time', TODAY:'Today', LAST_24_HOURS:'Last 24 Hours', LAST_7_DAYS:'Last 7 Days', LAST_30_DAYS:'Last 30 Days'};
    const activePeriod = String(summary.period || aiPerformancePeriod || 'ALL_TIME').toUpperCase();
    if (el('ai-operations-period')) el('ai-operations-period').textContent = periodLabels[activePeriod] || 'All Time';
    if (el('execution-intelligence-updated')) el('execution-intelligence-updated').textContent = summary.updatedAt ? `Updated ${preciseDateTime(summary.updatedAt)}` : 'Waiting for evidence';
    if (el('nav-opportunity-count')) el('nav-opportunity-count').textContent = active;
    if (el('nav-position-count')) el('nav-position-count').textContent = Number(summary.activePositions || 0);

    const queue = [...opportunities].sort((a,b) => {
        const statusRank = value => ({CONFIRMED:5, BUILDING:4, WEAKENING:3, BLOCKED:2}[String(value || '').toUpperCase()] || 1);
        const statusDiff = statusRank(b.status) - statusRank(a.status);
        if (statusDiff !== 0) return statusDiff;
        const healthDiff = Number(b.opportunityHealth || 0) - Number(a.opportunityHealth || 0);
        return healthDiff !== 0 ? healthDiff : Number(b.evidenceScore || 0) - Number(a.evidenceScore || 0);
    }).slice(0, 12);

    if (el('opportunity-queue')) {
        el('opportunity-queue').innerHTML = queue.length ? queue.map(o => renderOpportunityCenterCard(o)).join('') : '<div class="empty">No active opportunities right now.</div>';
    }
}

function renderOpportunityCenterCard(o) {
    const rawStatus = String(o.status || 'BUILDING').toUpperCase();
    const status = rawStatus.replaceAll('_',' ');
    const recovering = rawStatus === 'WEAKENING' && Number(o.healthMomentum || 0) > 0;
    const displayStatus = recovering ? 'RECOVERING' : status;
    const tone = rawStatus === 'BLOCKED' ? 'reject' : rawStatus === 'CONFIRMED' ? 'buy' : recovering ? 'recovering' : rawStatus === 'WEAKENING' ? 'weakening' : 'watch';
    const health = Math.max(0, Math.min(100, Number(o.opportunityHealth || 0)));
    const evidence = Math.max(0, Number(o.evidenceScore || 0));
    const evidenceTarget = 7;
    const evidenceProgress = Math.max(0, Math.min(100, (evidence / evidenceTarget) * 100));
    const healthMomentum = Number(o.healthMomentum || 0);
    const evidenceMomentum = Number(o.evidenceMomentum || 0);
    const ageMinutes = o.startedAt ? Math.max(0, Math.round((Date.now() - window.CryptoTime.parseUtc(o.startedAt)?.getTime()) / 60000)) : null;
    const freshness = ageMinutes == null ? 'Age unavailable' : ageMinutes < 10 ? `${ageMinutes}m · fresh` : ageMinutes < 30 ? `${ageMinutes}m · active` : `${ageMinutes}m · aging`;
    const stage = opportunityStage(o, recovering);
    const missing = opportunityMissingRequirements(o, recovering);
    const nextAction = opportunityNextAction(o, recovering);
    const timeline = opportunityEvidenceTimeline(o);

    return `<article class="opportunity-card-center ${tone}">
        <div class="opportunity-card-head">
            <div><strong>${escapeHtml(o.symbol || '—')}</strong><span class="badge ${tone}">${escapeHtml(displayStatus)}</span></div>
            <div class="opportunity-age"><span>Age</span><strong>${escapeHtml(freshness)}</strong></div>
        </div>

        <div class="opportunity-progress-grid">
            <div class="progress-metric">
                <div><span>Opportunity Health</span><strong>${health}/100</strong></div>
                <div class="metric-bar"><i style="width:${health}%"></i></div>
                <small>Health Δ ${healthMomentum >= 0 ? '+' : ''}${healthMomentum}</small>
            </div>
            <div class="progress-metric">
                <div><span>Evidence Progress</span><strong>${evidence}/${evidenceTarget}</strong></div>
                <div class="metric-bar evidence"><i style="width:${evidenceProgress}%"></i></div>
                <small>Evidence momentum ${evidenceMomentum >= 0 ? '+' : ''}${evidenceMomentum}</small>
            </div>
        </div>

        <div class="opportunity-stage-line"><span>Current stage</span><strong>${escapeHtml(stage)}</strong></div>
        <div class="opportunity-context opportunity-context-primary">
            <span>1H <b>${escapeHtml(o.oneHourDecision || '—')}</b></span>
            <span>5M <b>${escapeHtml(o.fiveMinuteDecision || '—')}</b></span>
            <span>Avg score <b>${Number(o.averageSignalScore || 0) || '—'}</b></span>
            <span>Avg confidence <b>${Number(o.averageConfidence || 0) || '—'}</b></span>
        </div>
        <div class="opportunity-context">
            <span>${Number(o.buyCount || 0)} BUY</span>
            <span>${Number(o.watchCount || 0)} WATCH</span>
            <span>${Number(o.neutralCount || 0)} NEUTRAL</span>
            <span>${Number(o.bearishCount || 0)} bearish interruptions</span>
        </div>
        <div class="evidence-timeline" title="Recent evidence composition">${timeline}</div>

        <div class="opportunity-needs">
            <span>Progress / blockers</span>
            <div>${missing.map(item => `<em class="${item.ok ? 'ok' : 'missing'}">${item.ok ? '✓' : '•'} ${escapeHtml(item.label)}</em>`).join('')}</div>
        </div>
        <div class="opportunity-next-action"><span>Next expected action</span><strong>${escapeHtml(nextAction)}</strong></div>
        <small class="opportunity-explanation">${escapeHtml(o.decisionExplanation || o.decisionCode || 'Accumulating fresh execution evidence.')}</small>
    </article>`;
}

function opportunityStage(o, recovering) {
    const status = String(o.status || '').toUpperCase();
    if (status === 'CONFIRMED') return 'READY TO EXECUTE';
    if (status === 'BLOCKED') return `BLOCKED · ${String(o.decisionCode || 'RISK GATE').replaceAll('_',' ')}`;
    if (recovering) return 'RECOVERING EVIDENCE';
    if (status === 'WEAKENING') return 'THESIS WEAKENING';
    const evidence = Number(o.evidenceScore || 0);
    if (evidence >= 7) return 'CONFIRMATION CHECK';
    if (evidence >= 4) return 'BUILDING CONFIRMATION';
    return 'SCOUTING / BUILDING';
}

function opportunityMissingRequirements(o, recovering) {
    const status = String(o.status || '').toUpperCase();
    const five = String(o.fiveMinuteDecision || '').toUpperCase();
    const one = String(o.oneHourDecision || '').toUpperCase();
    const supportive = value => ['WATCH','BUY','STRONG_BUY'].includes(value);
    if (status === 'BLOCKED') {
        return [{ok:false, label:String(o.decisionCode || 'Hard risk block').replaceAll('_',' ')}];
    }
    if (status === 'CONFIRMED') {
        return [{ok:true,label:'Execution conditions confirmed'}];
    }
    return [
        {ok:Number(o.evidenceScore || 0) >= 7, label:`Evidence ${Number(o.evidenceScore || 0)}/7`},
        {ok:Number(o.opportunityHealth || 0) >= 40, label:`Health ${Number(o.opportunityHealth || 0)}/40 minimum`},
        {ok:supportive(five), label:`5m ${five || 'missing'}`},
        {ok:supportive(one), label:`1h ${one || 'missing'}`},
        {ok:recovering || Number(o.evidenceMomentum || 0) >= 0, label:`Momentum ${Number(o.evidenceMomentum || 0) >= 0 ? 'supportive' : 'needs recovery'}`}
    ];
}

function opportunityNextAction(o, recovering) {
    const status = String(o.status || '').toUpperCase();
    if (status === 'CONFIRMED') return `Execute approved ${Number(o.recommendedPositionPercent || 0)}% position when wallet checks pass.`;
    if (status === 'BLOCKED') return `Wait for ${String(o.decisionCode || 'the blocking risk').replaceAll('_',' ').toLowerCase()} to clear.`;
    if (recovering) return 'Wait for a fresh supportive 1m signal to rebuild evidence and confirm recovery.';
    if (status === 'WEAKENING') return 'Require new bullish evidence before this opportunity can return to building.';
    if (Number(o.evidenceScore || 0) < 7) return `Need ${Math.max(0, 7 - Number(o.evidenceScore || 0))} more evidence point${Math.max(0, 7 - Number(o.evidenceScore || 0)) === 1 ? '' : 's'} plus supportive current context.`;
    return 'Evaluate confirmation, entry quality, and hard-risk gates for execution.';
}

function opportunityEvidenceTimeline(o) {
    const buy = Math.min(6, Number(o.buyCount || 0));
    const watch = Math.min(6, Number(o.watchCount || 0));
    const neutral = Math.min(6, Number(o.neutralCount || 0));
    const bearish = Math.min(6, Number(o.bearishCount || 0));
    const dots = [
        ...Array(buy).fill('<i class="buy" title="BUY"></i>'),
        ...Array(watch).fill('<i class="watch" title="WATCH"></i>'),
        ...Array(neutral).fill('<i class="neutral" title="NEUTRAL"></i>'),
        ...Array(bearish).fill('<i class="bearish" title="Bearish interruption"></i>')
    ].slice(-12);
    return dots.length ? dots.join('') : '<span>No qualifying evidence yet</span>';
}


function renderActivePositionsKpi(positions = []) {
    const count = Array.isArray(positions) ? positions.length : 0;
    const kpi = el('active-positions-kpi');
    const countNode = el('header-active-positions');
    const symbolsNode = el('header-active-symbols');
    if (countNode) countNode.textContent = count;
    if (el('nav-position-count')) el('nav-position-count').textContent = count;
    if (symbolsNode) {
        symbolsNode.textContent = count
            ? positions.slice(0, 3).map(p => String(p.symbol || '').replace('USDT','')).join(' · ') + (count > 3 ? ` +${count - 3}` : '')
            : 'None';
    }
    if (kpi) {
        kpi.classList.toggle('has-active-position', count > 0);
        kpi.setAttribute('aria-label', count ? `${count} active positions. Click to inspect.` : 'No active positions');
    }
    renderActivePositionsModal(positions);
}

function renderActivePositionsModal(positions = []) {
    const list = el('active-positions-list');
    const subtitle = el('active-positions-subtitle');
    if (!list) return;
    const rows = Array.isArray(positions) ? positions : [];
    if (subtitle) subtitle.textContent = rows.length ? `${rows.length} wallet-managed position${rows.length === 1 ? '' : 's'} currently open` : 'No open positions';
    if (!rows.length) {
        list.innerHTML = '<div class="empty">No active positions right now.</div>';
        return;
    }
    list.innerHTML = rows.map(p => {
        const pnl = Number(p.unrealizedPnlUsdt || 0);
        const pnlPct = Number(p.unrealizedPnlPercent || 0);
        const entry = Number(p.entryPrice || 0);
        const current = Number(p.currentPrice || 0);
        const tp = Number(p.takeProfit || 0);
        const sl = Number(p.stopLoss || 0);
        const progress = tp > entry ? Math.max(0, Math.min(100, ((current - entry) / (tp - entry)) * 100)) : 0;
        return `<article class="active-position-card ${pnl >= 0 ? 'winning' : 'losing'}">
            <div class="active-position-head">
                <div><strong>${escapeHtml(p.symbol || '—')}</strong><span>${escapeHtml(String(p.symbol || '').replace('USDT',''))} position</span></div>
                <div class="active-position-pnl"><strong class="${pnl >= 0 ? 'positive' : 'negative'}">${pnl >= 0 ? '+' : ''}${money(pnl)}</strong><span class="${pnlPct >= 0 ? 'positive' : 'negative'}">${pnlPct >= 0 ? '+' : ''}${pnlPct.toFixed(3)}%</span></div>
            </div>
            <div class="active-position-metrics">
                <div><span>Entry</span><strong>${money(p.entryPrice)}</strong></div>
                <div><span>Current</span><strong>${money(p.currentPrice)}</strong></div>
                <div><span>Quantity</span><strong>${value(p.quantity)}</strong></div>
                <div><span>Opened</span><strong>${dateTime(p.openedAt)}</strong></div>
            </div>
            <div class="active-position-progress"><div class="active-position-progress-label"><span>TP progress</span><strong>${progress.toFixed(1)}%</strong></div><div class="active-position-track"><i style="width:${progress}%"></i></div></div>
            <div class="active-position-levels"><span>SL <b>${money(p.stopLoss)}</b></span><span>${p.profitLockActive ? '🔒 Profit Lock' : 'Profit Lock'} <b>${p.profitLockActive ? money(p.profitLockPrice) : 'Waiting'}</b></span><span>TP <b>${money(p.takeProfit)}</b></span></div>
            <div class="active-position-stage"><span>Position stage <b>${p.entryStage || 'ENTRY'}</b></span><span>Allocated <b>${Number(p.allocatedPositionPercent || 0).toFixed(0)}%</b></span><span>Entry quality <b>${Number(p.entryQualityScore || 0).toFixed(0)}/100</b></span></div>
            ${p.profitLockActive ? `<div class="active-position-lock">Profit Lock active · protected at <strong>${money(p.profitLockPrice)}</strong> · best progress ${Number(p.profitLockProgressPercent || 0).toFixed(1)}%</div>` : ''}
            <a class="active-position-inspect" href="/trade-inspector?signalId=${encodeURIComponent(p.entrySignalId || '')}">Inspect trade</a>
        </article>`;
    }).join('');
}

function openActivePositionsModal() {
    const modal = el('active-positions-modal');
    if (!modal) return;
    modal.classList.remove('hidden');
    document.body.classList.add('position-modal-open');
}

function closeActivePositionsModal() {
    const modal = el('active-positions-modal');
    if (!modal) return;
    modal.classList.add('hidden');
    document.body.classList.remove('position-modal-open');
}

function renderAiAnalysis(signals, indicator) {
    const signal = Array.isArray(signals) && signals.length ? signals[0] : null;
    if (!signal) {
        ['trend','momentum','volatility'].forEach(prefix => {
            if (el(`${prefix}-analysis-status`)) el(`${prefix}-analysis-status`).textContent = 'Waiting';
            if (el(`${prefix}-analysis-score`)) el(`${prefix}-analysis-score`).textContent = '—';
            if (el(`${prefix}-analysis-bar`)) el(`${prefix}-analysis-bar`).style.width = '0%';
            if (el(`${prefix}-analysis-text`)) el(`${prefix}-analysis-text`).textContent = 'No fresh analyzed signal exists for this market view.';
        });
        return;
    }
    const trend = Number(signal.trendScore || 0);
    const momentum = Number(signal.momentumScore || 0);
    const volume = Number(signal.volumeScore || 0);
    updateAnalysisCard('trend', trend, 25,
        trend >= 21 ? 'Strong Bullish' : trend >= 17 ? 'Constructive' : trend >= 12 ? 'Mixed' : 'Weak',
        trend >= 21 ? 'Moving-average structure and price location strongly support the current direction.' : trend >= 17 ? 'Trend structure is supportive, but confirmation is not yet complete.' : 'Trend evidence is mixed or weak; execution should rely on stronger confirmation.');
    const rsi = Number(indicator.rsi14 || 0);
    updateAnalysisCard('momentum', momentum, 15,
        momentum >= 12 ? 'Healthy' : momentum >= 8 ? 'Moderate' : 'Weak',
        `${rsi ? `RSI ${rsi.toFixed(1)}. ` : ''}${momentum >= 12 ? 'Momentum is supporting continuation.' : momentum >= 8 ? 'Momentum is usable but not decisive.' : 'Momentum is not currently supporting an aggressive entry.'}`);
    const rvol = Number(indicator.relativeVolume || 0);
    const volLabel = signal.volatilityLevel ? String(signal.volatilityLevel).replaceAll('_',' ') : (rvol >= 1.5 ? 'Expanding' : 'Normal');
    updateAnalysisCard('volatility', volume, 20, volLabel,
        `${rvol ? `Relative volume ${rvol.toFixed(2)}x. ` : ''}${volume >= 15 ? 'Volume participation is strong enough to support the move.' : volume >= 10 ? 'Participation is moderate; breakout quality still needs confirmation.' : 'Volume confirmation is currently weak.'}`);
}

function updateAnalysisCard(prefix, score, maximum, status, text) {
    if (el(`${prefix}-analysis-status`)) el(`${prefix}-analysis-status`).textContent = status;
    if (el(`${prefix}-analysis-score`)) el(`${prefix}-analysis-score`).textContent = `${score}/${maximum}`;
    if (el(`${prefix}-analysis-bar`)) el(`${prefix}-analysis-bar`).style.width = `${Math.max(0, Math.min(100, score / maximum * 100))}%`;
    if (el(`${prefix}-analysis-text`)) el(`${prefix}-analysis-text`).textContent = text;
}


function renderScoreDiagnostics(diagnostics) {
    // Score Diagnostics now has its own page. Keep this renderer harmless when
    // the old dashboard diagnostics section is not present.
    if (!el('diagnostics-count')) return;
    const score = diagnostics.score || {};
    const count = Number(diagnostics.signalCount || 0);
    el('diagnostics-count').textContent = count.toLocaleString();
    el('diagnostics-average').textContent = score.average == null ? '—' : `${Number(score.average).toFixed(2)}/100`;
    el('diagnostics-range').textContent = score.minimum == null ? '—' : `${score.minimum}–${score.maximum}`;
    const mismatches = Number(score.normalizationMismatches || 0);
    el('diagnostics-normalization').textContent = mismatches === 0 ? 'PASS' : `${mismatches} mismatch${mismatches === 1 ? '' : 'es'}`;
    el('diagnostics-normalization').className = mismatches === 0 ? 'positive' : 'negative';
    el('diagnostics-window').textContent = diagnostics.from ? `${dateTime(diagnostics.from)} → now` : 'No 24-hour data';

    const warnings = diagnostics.warnings || [];
    el('diagnostics-warnings').innerHTML = warnings.length
        ? warnings.map(warning => `<div class="diagnostic-warning">⚠ ${escapeHtml(warning)}</div>`).join('')
        : '<div class="diagnostic-ok">✓ No score-distribution warning detected.</div>';

    el('diagnostics-categories').innerHTML = (diagnostics.categories || []).map(category => `
        <div class="diagnostic-row ${String(category.status || '').toLowerCase()}">
            <span>${escapeHtml(category.name)}</span>
            <strong>${Number(category.average || 0).toFixed(2)}/${category.maximum}</strong>
            <small>${Number(category.utilizationPercent || 0).toFixed(1)}%</small>
        </div>`).join('') || '<div class="empty">No category data.</div>';

    const original = diagnostics.originalDecisions || {};
    el('diagnostics-decisions').innerHTML = Object.entries(original).map(([decision, value]) => `
        <div class="diagnostic-row"><span>${escapeHtml(decision.replaceAll('_', ' '))}</span><strong>${Number(value).toLocaleString()}</strong></div>`).join('') || '<div class="empty">No decisions.</div>';

    el('diagnostics-strategies').innerHTML = (diagnostics.strategies || []).map(strategy => `
        <div class="diagnostic-row">
            <span>${escapeHtml(String(strategy.strategy).replaceAll('_', ' '))}<small>${strategy.count} signals</small></span>
            <strong>${Number(strategy.averageScore || 0).toFixed(1)}</strong>
            <small>${strategy.finalBuyCount}/${strategy.buyCount} final/base buys</small>
        </div>`).join('') || '<div class="empty">No strategy data.</div>';

    el('diagnostics-symbols').innerHTML = (diagnostics.symbolIntervals || []).slice(0, 8).map(item => `
        <div class="diagnostic-row">
            <span>${escapeHtml(item.symbol)} · ${escapeHtml(displayInterval(item.interval))}<small>${item.count} signals</small></span>
            <strong>${Number(item.averageScore || 0).toFixed(1)}</strong>
            <small>${item.minimumScore}–${item.maximumScore} · ${item.buyCount} buys</small>
        </div>`).join('') || '<div class="empty">No symbol data.</div>';
}

function renderSchedules(schedule) {
    const target = el('schedule-groups');
    if (!target) return;
    const groups = schedule.groups || [];
    target.innerHTML = groups.length ? groups.map(group => `
        <article class="schedule-group">
            <h3>${escapeHtml(group.name || 'Schedule')}</h3>
            <div class="schedule-entry-list">
                ${(group.entries || []).map(item => `
                    <div class="schedule-entry">
                        <div class="schedule-entry-heading">
                            <strong>${escapeHtml(item.name || '—')}</strong>
                            <span class="badge ${item.enabled ? 'buy' : 'reject'}">${item.enabled ? 'ENABLED' : 'DISABLED'}</span>
                        </div>
                        <span class="schedule-cadence">${escapeHtml(item.cadence || '—')}</span>
                        <small>${escapeHtml(item.detail || '')}</small>
                        ${item.delayMs == null ? '' : `<code>${Number(item.delayMs).toLocaleString()} ms</code>`}
                    </div>`).join('')}
            </div>
        </article>`).join('') : '<div class="empty">No schedule configuration was returned.</div>';
}

function applyDashboardRefreshSchedule(schedule) {
    const refreshMs = Number(schedule.dashboardRefreshMs || 10000);
    const safeRefreshMs = Math.max(2000, refreshMs);
    const connectionSmall = document.querySelector('.connection small');
    if (connectionSmall) {
        connectionSmall.textContent = `Auto refresh every ${formatDurationMs(safeRefreshMs)}`;
    }
    if (!dashboardRefreshTimer || dashboardRefreshTimer.delayMs !== safeRefreshMs) {
        if (dashboardRefreshTimer) clearInterval(dashboardRefreshTimer.id);
        dashboardRefreshTimer = {
            delayMs: safeRefreshMs,
            id: setInterval(refreshDashboard, safeRefreshMs)
        };
    }
}

function formatDurationMs(ms) {
    if (ms % 3600000 === 0) return `${ms / 3600000}h`;
    if (ms % 60000 === 0) return `${ms / 60000}m`;
    if (ms % 1000 === 0) return `${ms / 1000}s`;
    return `${ms}ms`;
}

function renderPipeline(pipeline) {
    Object.entries(pipeline).forEach(([name, state]) => {
        const step = document.querySelector(`[data-step="${name}"]`);
        if (!step) return;
        step.classList.toggle('complete', state.complete);
        step.classList.toggle('pending', !state.complete);
        step.querySelector('p').textContent = state.detail;
    });
}

function renderIndicators(i) {
    const mapping = {
        'sma20': i.sma20, 'ema20': i.ema20, 'ema50': i.ema50, 'ema200': i.ema200,
        'rsi14': i.rsi14, 'macd': i.macd, 'macd-signal': i.macdSignal,
        'macd-histogram': i.macdHistogram, 'bb-upper': i.bollingerUpper,
        'bb-middle': i.bollingerMiddle, 'bb-lower': i.bollingerLower,
        'atr14': i.atr14, 'relative-volume': i.relativeVolume == null ? null : `${value(i.relativeVolume)}x`
    };
    Object.entries(mapping).forEach(([id, v]) => el(id).textContent = v === null || v === undefined ? '—' : (typeof v === 'string' ? v : value(v)));
}

function candleTooltipTime(value) {
    if (value === null || value === undefined || value === '') return '—';
    const date = window.CryptoTime.parseUtc(value);
    if (Number.isNaN(date.getTime())) return '—';
    // FIX-070: Dashboard open/close labels remain KSA but omit the redundant GMT+3 suffix.
    return new Intl.DateTimeFormat('en-GB', {
        timeZone: 'Asia/Riyadh', year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    }).format(date).replace(',', '');
}

function updateFixedCandleSummary(candle) {
    if (!candle) return;
    const values = Array.isArray(candle.y) ? candle.y : [candle.open, candle.high, candle.low, candle.close];
    const [open, high, low, close] = values.map(Number);
    const set = (id, value) => { const node = el(id); if (node) node.textContent = value; };
    set('candle-fixed-open-time', candleTooltipTime(candle.openTime ?? candle.x));
    set('candle-fixed-close-time', candleTooltipTime(candle.closeTime));
    set('candle-fixed-open', candleTooltipPrice(open));
    set('candle-fixed-high', candleTooltipPrice(high));
    set('candle-fixed-low', candleTooltipPrice(low));
    set('candle-fixed-close', candleTooltipPrice(close));
}

function candleTooltipPrice(value) {
    return Number.isFinite(Number(value))
        ? Number(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 8 })
        : '—';
}

function candleTooltipHtml(point) {
    if (!point) return '';
    const y = Array.isArray(point.y) ? point.y : [];
    // Dashboard hover is intentionally minimal: show only the candle timestamp
    // and the price at that hovered candle (the candle close / plotted price).
    const price = point.close ?? y[3] ?? point.y;
    const timestamp = point.openTime ?? point.x;
    return `
        <div class="candle-hover-tooltip candle-hover-tooltip-minimal">
            <div class="candle-hover-time"><span>Time</span><strong>${candleTooltipTime(timestamp)}</strong></div>
            <div class="candle-hover-price"><span>Price</span><strong>${candleTooltipPrice(price)}</strong></div>
        </div>`;
}

function candlePriceTooltipHtml(point) {
    if (!point) return '';
    const y = Array.isArray(point.y) ? point.y : [];
    const price = point.close ?? y[3] ?? point.y;
    return `<div class="candle-cursor-price">${candleTooltipPrice(price)}</div>`;
}

function formatChartPrice(value) {
    return candleTooltipPrice(value);
}

function normalizeChartCandle(c) {
    return {
        ...c,
        time: c.time,
        openTime: c.openTime ?? c.time,
        closeTime: c.closeTime
    };
}

function mergeChartCandles(existing, incoming) {
    const byTime = new Map();
    [...(existing || []), ...(incoming || [])].forEach(candle => {
        const key = window.CryptoTime.parseUtc(candle.time ?? candle.openTime)?.getTime();
        if (Number.isFinite(key)) byTime.set(key, normalizeChartCandle(candle));
    });
    return [...byTime.values()].sort((a, b) =>
        window.CryptoTime.parseUtc(a.time ?? a.openTime).getTime() - window.CryptoTime.parseUtc(b.time ?? b.openTime).getTime());
}

function mergeChartIndicators(existing, incoming) {
    const byTime = new Map();
    [...(existing || []), ...(incoming || [])].forEach(row => {
        const time = window.CryptoTime.parseUtc(row.time)?.getTime();
        if (Number.isFinite(time)) byTime.set(time, {...row, time});
    });
    return [...byTime.values()].sort((a,b) => Number(a.time) - Number(b.time));
}

function updateChartLatestButton() {
    const button = el('chart-go-latest');
    if (!button) return;
    button.classList.toggle('hidden', !chartHistoryActive);
}

function resetChartHistoryNavigation() {
    chartHistoryActive = false;
    chartHistoryLoading = false;
    chartLoadedCandles = [];
    chartLoadedExecutions = [];
    chartLoadedIndicators = [];
    chartViewport = { min: null, max: null };
    chartDragState = null;
    updateChartLatestButton();
}

function chartCandleBounds() {
    if (!chartLoadedCandles.length) return null;
    const first = window.CryptoTime.parseUtc(chartLoadedCandles[0].time ?? chartLoadedCandles[0].openTime).getTime();
    const last = window.CryptoTime.parseUtc(chartLoadedCandles.at(-1).time ?? chartLoadedCandles.at(-1).openTime).getTime();
    return Number.isFinite(first) && Number.isFinite(last) ? { first, last } : null;
}

async function loadOlderChartCandles(beforeMillis) {
    if (chartHistoryLoading || !Number.isFinite(beforeMillis)) return false;
    chartHistoryLoading = true;
    const symbol = el('symbol-select').value;
    const interval = el('interval-select').value;
    try {
        const params = new URLSearchParams({
            symbol,
            interval,
            before: new Date(beforeMillis).toISOString(),
            limit: '180'
        });
        const response = await fetch(`/api/dashboard/chart-history?${params.toString()}`);
        if (!response.ok) throw new Error(`Historical chart API returned ${response.status}`);
        const data = await response.json();
        if (symbol !== el('symbol-select').value || interval !== el('interval-select').value) return false;
        const older = data.candles || [];
        if (!older.length) return false;
        chartLoadedCandles = mergeChartCandles(chartLoadedCandles, older);
        chartLoadedIndicators = mergeChartIndicators(chartLoadedIndicators, data.indicatorSeries || []);
        renderCharts(chartLoadedCandles, chartLoadedExecutions, { force: true, preserveViewport: true, indicatorSeries: chartLoadedIndicators });
        return true;
    } catch (error) {
        console.warn('Unable to load older dashboard candles', error);
        return false;
    } finally {
        chartHistoryLoading = false;
    }
}

async function applyChartViewport(min, max) {
    const bounds = chartCandleBounds();
    if (!bounds || !Number.isFinite(min) || !Number.isFinite(max) || max <= min) return;

    const latest = bounds.last;
    const span = max - min;
    if (max > latest) {
        max = latest;
        min = max - span;
    }

    // Fetch history only when the requested viewport moves beyond what is
    // already in the browser. This keeps the initial dashboard payload small.
    if (min < bounds.first) {
        await loadOlderChartCandles(bounds.first - 1);
    }

    const refreshed = chartCandleBounds();
    if (!refreshed) return;
    min = Math.max(min, refreshed.first);
    max = Math.min(max, refreshed.last);
    if (max <= min) return;

    chartHistoryActive = max < refreshed.last - Math.max(1000, span * 0.01) || min > refreshed.first;
    chartViewport = { min, max };
    updateChartLatestButton();
    if (candleChart) candleChart.zoomX(min, max);
    if (volumeChart) volumeChart.zoomX(min, max);
    if (atrChart) atrChart.zoomX(min, max);
}

function goToLatestChart() {
    // Return to live mode and request a fresh lightweight market payload so the
    // newest candle is current even after the user spent time browsing history.
    resetChartHistoryNavigation();
    void refreshDashboardForSelection();
}

function bindChartNavigation() {
    if (chartNavigationBound) return;
    const host = el('candlestick-chart');
    if (!host) return;
    chartNavigationBound = true;

    host.addEventListener('pointerdown', event => {
        if (event.button !== 0) return;
        const bounds = chartCandleBounds();
        if (!bounds) return;
        const min = Number.isFinite(chartViewport.min) ? chartViewport.min : bounds.first;
        const max = Number.isFinite(chartViewport.max) ? chartViewport.max : bounds.last;
        chartDragState = { pointerId: event.pointerId, startX: event.clientX, min, max, moved: false };
        host.setPointerCapture?.(event.pointerId);
    });

    host.addEventListener('pointermove', event => {
        if (!chartDragState || chartDragState.pointerId !== event.pointerId) return;
        const dx = event.clientX - chartDragState.startX;
        if (Math.abs(dx) < 4) return;
        chartDragState.moved = true;
        host.classList.add('is-panning');
        event.preventDefault();
    });

    const finishDrag = async event => {
        if (!chartDragState || chartDragState.pointerId !== event.pointerId) return;
        const state = chartDragState;
        chartDragState = null;
        host.classList.remove('is-panning');
        try { host.releasePointerCapture?.(event.pointerId); } catch (_) {}
        if (!state.moved) return;
        const width = Math.max(1, host.getBoundingClientRect().width);
        const dx = event.clientX - state.startX;
        const span = state.max - state.min;
        // Dragging the candles to the right reveals older history, matching
        // Binance/TradingView-style chart navigation.
        const shift = -(dx / width) * span;
        await applyChartViewport(state.min + shift, state.max + shift);
    };
    host.addEventListener('pointerup', finishDrag);
    host.addEventListener('pointercancel', finishDrag);

    host.addEventListener('wheel', async event => {
        if (!candleChart || !chartLoadedCandles.length) return;
        event.preventDefault();
        const bounds = chartCandleBounds();
        const min = Number.isFinite(chartViewport.min) ? chartViewport.min : bounds.first;
        const max = Number.isFinite(chartViewport.max) ? chartViewport.max : bounds.last;
        const span = max - min;
        const rect = host.getBoundingClientRect();
        const ratio = Math.max(0, Math.min(1, (event.clientX - rect.left) / Math.max(1, rect.width)));
        const anchor = min + span * ratio;
        const factor = event.deltaY < 0 ? 0.80 : 1.25;
        const minSpan = 5 * 60 * 1000;
        const maxSpan = Math.max(minSpan, bounds.last - bounds.first);
        const nextSpan = Math.max(minSpan, Math.min(maxSpan, span * factor));
        const nextMin = anchor - nextSpan * ratio;
        const nextMax = anchor + nextSpan * (1 - ratio);
        await applyChartViewport(nextMin, nextMax);
    }, { passive: false });

    const latestButton = el('chart-go-latest');
    if (latestButton) latestButton.addEventListener('click', goToLatestChart);
}


// FIX-092C: Trend-line and Fibonacci helper functions intentionally removed.
// The market chart now renders only candles, persisted Bollinger, signal/position annotations, volume and ATR.

function bindChartOverlayControls() {
    const controls = [
        ['chart-toggle-bollinger', 'bollinger', 'dashboardOverlayBollinger'],
        ['chart-toggle-atr', 'atr', 'dashboardOverlayAtr']
    ];
    controls.forEach(([id, key, storageKey]) => {
        const input = el(id);
        if (!input) return;
        input.checked = chartOverlayState[key];
        input.addEventListener('change', () => {
            chartOverlayState[key] = input.checked;
            localStorage.setItem(storageKey, input.checked ? '1' : '0');
            const atrHost = el('atr-chart');
            if (atrHost) atrHost.classList.toggle('hidden', !chartOverlayState.atr);
            renderCharts(chartLoadedCandles, chartLoadedExecutions, {
                force: true,
                preserveViewport: true,
                activePosition: chartLoadedActivePosition,
                indicatorSeries: chartLoadedIndicators
            });
        });
    });
    const atrHost = el('atr-chart');
    if (atrHost) atrHost.classList.toggle('hidden', !chartOverlayState.atr);
}

function renderCharts(candles, executions = [], options = {}) {
    const { force = false, preserveViewport = false, activePosition = undefined, indicatorSeries = undefined } = options;
    // While the user is inspecting history, periodic dashboard refreshes must
    // not snap the chart back to the newest candles.
    if (chartHistoryActive && !force) return;

    chartLoadedCandles = mergeChartCandles(force ? chartLoadedCandles : [], candles || []);
    if (!force) chartLoadedExecutions = executions || [];
    if (indicatorSeries !== undefined) chartLoadedIndicators = mergeChartIndicators(force ? chartLoadedIndicators : [], indicatorSeries || []);
    if (activePosition !== undefined) chartLoadedActivePosition = activePosition;
    const renderCandlesList = chartLoadedCandles;
    const renderExecutions = force ? chartLoadedExecutions : (executions || []);
    const renderPosition = activePosition === undefined ? chartLoadedActivePosition : activePosition;
    const renderIndicators = indicatorSeries === undefined ? chartLoadedIndicators : mergeChartIndicators(chartLoadedIndicators, indicatorSeries || []);

    const candleSeries = renderCandlesList.map(c => ({
        x: window.CryptoTime.parseUtc(c.time),
        y: [Number(c.open), Number(c.high), Number(c.low), Number(c.close)],
        openTime: c.openTime ?? c.time,
        closeTime: c.closeTime,
        open: Number(c.open), high: Number(c.high), low: Number(c.low), close: Number(c.close)
    }));
    const volumeSeries = renderCandlesList.map(c => ({ x: window.CryptoTime.parseUtc(c.time), y: Number(c.volume) }));
    // FIX-092: Bollinger uses the price axis; ATR uses a separate synchronized chart so
    // volatility magnitude cannot distort the candlestick Y scale.
    const indicatorPoint = (key) => renderIndicators
        .filter(row => row[key] !== null && row[key] !== undefined && Number.isFinite(Number(row[key])))
        .map(row => ({x: new Date(Number(row.time)), y: Number(row[key])}));
    const bollingerUpperSeries = indicatorPoint('bollingerUpper');
    const bollingerMiddleSeries = indicatorPoint('bollingerMiddle');
    const bollingerLowerSeries = indicatorPoint('bollingerLower');
    const atrSeries = indicatorPoint('atr14');
    const bollingerSeries = chartOverlayState.bollinger ? [
        { name: 'BB Upper', type: 'line', data: bollingerUpperSeries },
        { name: 'BB Middle', type: 'line', data: bollingerMiddleSeries },
        { name: 'BB Lower', type: 'line', data: bollingerLowerSeries }
    ] : [];
    // FIX-092C: No browser-derived trend/Fibonacci series are mixed into the candlestick chart.
    // This protects the exact View Chart B/S/blocked-BUY annotation from overlay corruption.
    const priceChartSeries = [{ name: 'Price', type: 'candlestick', data: candleSeries }, ...bollingerSeries];
    latestWalletExecutions = new Map((renderExecutions || []).map(execution => [String(execution.id), execution]));
    const annotations = (renderExecutions || []).map(execution => {
        const isBuy = String(execution.side || '').toUpperCase() === 'BUY';
        return {
            x: window.CryptoTime.parseUtc(execution.executedAt)?.getTime(),
            y: Number(execution.price),
            marker: { size: 3, fillColor: isBuy ? '#39d98a' : '#ff6b72', strokeColor: '#071018', strokeWidth: 1, radius: 2 },
            label: {
                text: isBuy ? 'B' : 'S',
                borderColor: isBuy ? '#39d98a' : '#ff6b72',
                offsetY: isBuy ? 13 : -8,
                style: { background: isBuy ? '#39d98a' : '#ff6b72', color: '#071018', fontSize: '9px', fontWeight: 900, padding: { left: 3, right: 3, top: 1, bottom: 1 } },
                cssClass: `wallet-execution-marker compact-trade-marker execution-marker-${execution.id} ${isBuy ? 'buy-marker' : 'sell-marker'}`
            }
        };
    });
    debugTradePoints.forEach((point, index) => {
        const isBuy = point.side === 'BUY';
        const isSell = point.side === 'SELL';
        const markerColor = isBuy ? '#39d98a' : isSell ? '#ff6b72' : '#f4c95d';
        annotations.push({
            x: point.time.getTime(),
            y: point.price,
            marker: { size: 6, fillColor: markerColor, strokeColor: '#071018', strokeWidth: 2, radius: 6 },
            label: {
                text: isBuy ? 'B' : isSell ? 'S' : '•',
                borderColor: markerColor,
                offsetY: isBuy ? 14 : isSell ? -10 : 12,
                style: { background: markerColor, color: '#071018', fontSize: '9px', fontWeight: 900, padding: { left: 3, right: 3, top: 1, bottom: 1 } },
                cssClass: `debug-trade-marker debug-trade-dot debug-trade-marker-${index}`
            }
        });
    });
    // FIX-053: Show the currently active Production position directly on the price chart.
    // These are presentation-only annotations; they never feed back into analysis/execution.
    const positionYAnnotations = [];
    if (renderPosition) {
        const levels = [
            ['ENTRY', renderPosition.entryPrice, '#8da2b1'],
            ['SL', renderPosition.stopLoss, '#ff6b72'],
            ['TP', renderPosition.takeProfit, '#39d98a'],
            ['LOCK', renderPosition.profitLockActive ? renderPosition.profitLockPrice : null, '#f4c95d']
        ];
        levels.forEach(([label, value, color]) => {
            const y = Number(value);
            if (!Number.isFinite(y) || y <= 0) return;
            positionYAnnotations.push({
                y,
                borderColor: color,
                strokeDashArray: label === 'ENTRY' ? 3 : 5,
                label: {text: `${label} ${formatChartPrice(y)}`, borderColor: color, style: {background: '#0d1820', color, fontSize: '10px', fontWeight: 700}}
            });
        });
        const openedAt = window.CryptoTime.parseUtc(renderPosition.openedAt);
        const entry = Number(renderPosition.entryPrice);
        if (openedAt && Number.isFinite(entry)) {
            annotations.push({
                x: openedAt.getTime(), y: entry,
                marker: {size: 5, fillColor: '#39d98a', strokeColor: '#071018', strokeWidth: 2, radius: 4},
                label: {text: 'OPEN', borderColor: '#39d98a', offsetY: 14, style: {background: '#39d98a', color: '#071018', fontSize: '9px', fontWeight: 900}}
            });
        }
    }

    // FIX-092C: Only stable position price levels use Y annotations. Trend-line and
    // retracement overlays are removed; View Chart signal annotations remain authoritative.
    const displayYAnnotations = [...positionYAnnotations];

    const debugZoneAnnotations = debugMoveFocus ? [{
        x: debugMoveFocus.start.getTime(),
        x2: debugMoveFocus.end.getTime(),
        borderColor: debugMoveFocus.direction === 'DOWN' ? '#ff6b72' : '#39d98a',
        fillColor: debugMoveFocus.direction === 'DOWN' ? '#ff6b72' : '#39d98a',
        opacity: 0.12,
        label: {
            text: `${debugTradeEnabled ? debugTradeLabel : `Debug ${debugMoveFocus.direction || 'MOVE'}`}${debugMoveFocus.change ? ` ${Number(debugMoveFocus.change) >= 0 ? '+' : ''}${Number(debugMoveFocus.change).toFixed(2)}%` : ''}`,
            style: { background: '#132430', color: '#dce9f2', fontSize: '11px', fontWeight: 700 }
        }
    }] : [];
    if (candleSeries.length) updateFixedCandleSummary(candleSeries.at(-1));
    const bounds = chartCandleBounds();
    if (bounds && (!preserveViewport || !Number.isFinite(chartViewport.min))) chartViewport = { min: bounds.first, max: bounds.last };
    const candleEvents = {
        mouseMove: (_event, _chart, config) => {
            const index = config?.dataPointIndex;
            if (Number.isInteger(index) && index >= 0 && candleSeries[index]) updateFixedCandleSummary(candleSeries[index]);
        },
        dataPointSelection: (_event, _chart, config) => {
            const index = config?.dataPointIndex;
            if (Number.isInteger(index) && index >= 0 && candleSeries[index]) updateFixedCandleSummary(candleSeries[index]);
        },
        zoomed: (_chart, axes) => {
            if (Number.isFinite(axes?.xaxis?.min) && Number.isFinite(axes?.xaxis?.max)) {
                chartViewport = { min: axes.xaxis.min, max: axes.xaxis.max };
            }
        }
    };
    const common = { chart: { background: 'transparent', foreColor: '#8da2b1', toolbar: { show: false }, animations: { enabled: false } }, theme: { mode: 'dark' }, grid: { borderColor: '#203342' }, xaxis: { type: 'datetime', labels: { datetimeUTC: false }, tooltip: { enabled: false } }, noData: { text: 'Waiting for closed candles' } };
    if (!candleChart) {
        candleChart = new ApexCharts(el('candlestick-chart'), { ...common, chart: { ...common.chart, type: 'candlestick', height: 390, events: candleEvents }, series: priceChartSeries, annotations: { points: annotations, xaxis: debugZoneAnnotations, yaxis: displayYAnnotations }, dataLabels: { enabled: false }, markers: { size: 0 }, tooltip: { enabled:false }, yaxis: { tooltip: { enabled: false }, decimalsInFloat: 4 }, stroke: { width: 1.5, dashArray: 0 }, legend: { show: true }, plotOptions: { candlestick: { colors: { upward: '#39d98a', downward: '#ff6b72' } } } });
        candleChart.render().then(() => {
            bindExecutionMarkerClicks();
            bindDebugTradeDotTitles();
            bindChartNavigation();
            // FIX-070: Dashboard uses the same display-only X/Y pointer layer as every trade chart.
            window.CryptoChartCrosshair?.bind(el('candlestick-chart'), candleChart, { valueFormatter: candleTooltipPrice });
        });
        volumeChart = new ApexCharts(el('volume-chart'), { ...common, chart: { ...common.chart, type: 'bar', height: 150 }, series: [{ name: 'Volume', data: volumeSeries }], dataLabels: { enabled: false }, yaxis: { labels: { formatter: v => Number(v).toLocaleString(undefined, { notation: 'compact' }) } } });
        volumeChart.render();
        atrChart = new ApexCharts(el('atr-chart'), { ...common, chart: { ...common.chart, type: 'line', height: 125 }, series: [{ name: 'ATR 14', data: atrSeries }], dataLabels: { enabled: false }, stroke: { width: 2 }, yaxis: { labels: { formatter: v => formatChartPrice(v) }, title: { text: 'ATR 14' } } });
        atrChart.render();
        el('atr-chart')?.classList.toggle('hidden', !chartOverlayState.atr);
    } else {
        // FIX-092C: Clear Apex's runtime annotation cache before rebuilding the chart so
        // the immutable View Chart B/S/blocked-BUY marker is always reconstructed cleanly.
        // No trend-line or retracement overlay is allowed to share this annotation layer.
        if (typeof candleChart.clearAnnotations === 'function') candleChart.clearAnnotations();
        candleChart.updateSeries(priceChartSeries, false);
        candleChart.updateOptions({
            chart: { events: candleEvents },
            annotations: { points: annotations, xaxis: debugZoneAnnotations, yaxis: displayYAnnotations },
            dataLabels: { enabled: false },
            markers: { size: 0 },
            tooltip: { enabled:false }
        }, false, true, false).then(async () => {
            bindExecutionMarkerClicks();
            bindDebugTradeDotTitles();
            if (preserveViewport && Number.isFinite(chartViewport.min) && Number.isFinite(chartViewport.max)) {
                candleChart.zoomX(chartViewport.min, chartViewport.max);
                if (volumeChart) volumeChart.zoomX(chartViewport.min, chartViewport.max);
                if (atrChart) atrChart.zoomX(chartViewport.min, chartViewport.max);
            }
        });
        volumeChart.updateSeries([{ name: 'Volume', data: volumeSeries }], false);
        if (atrChart) atrChart.updateSeries([{ name: 'ATR 14', data: atrSeries }], false);
    }
    updateChartLatestButton();
}

function bindDebugTradeDotTitles() {
    debugTradePoints.forEach((point, index) => {
        const label = document.querySelector(`.debug-trade-marker-${index}`);
        const target = label?.parentElement?.querySelector('circle') || label;
        if (!target) return;
        target.setAttribute('title', `${point.side} ${Number(point.price).toLocaleString(undefined,{maximumFractionDigits:8})} · ${point.time.toLocaleString()}`);
    });
}

function bindExecutionMarkerClicks() {
    document.querySelectorAll('.wallet-execution-marker').forEach(marker => {
        marker.style.cursor = 'pointer';
        marker.setAttribute('role', 'button');
        marker.setAttribute('tabindex', '0');
        const className = marker.getAttribute('class') || '';
        const match = className.match(/execution-marker-(\d+)/);
        if (!match) return;
        const id = match[1];
        const open = () => openExecutionMarker(id);
        marker.onclick = open;
        marker.onkeydown = event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(); } };
    });
}

function openExecutionMarker(id) {
    const execution = latestWalletExecutions.get(String(id));
    if (!execution) return;
    const side = String(execution.side || '').toUpperCase();
    const isSell = side === 'SELL';
    const pnl = execution.realizedPnlUsdt == null ? null : Number(execution.realizedPnlUsdt);
    el('execution-marker-title').textContent = `${execution.symbol || ''} ${side} execution`;
    el('execution-marker-content').innerHTML = `
        <div class="execution-detail-grid">
            <div><span>Action</span><strong class="${isSell ? 'negative' : 'positive'}">${escapeHtml(side)} ${isSell ? '↓' : '↑'}</strong></div>
            <div><span>Exact time</span><strong>${preciseDateTime(execution.executedAt)}</strong></div>
            <div><span>Signal timeframe</span><strong>${escapeHtml(execution.timeframe || '—')}</strong></div>
            <div><span>Executed price</span><strong>${money(execution.price)}</strong></div>
            <div><span>Quantity</span><strong>${value(execution.quantity)}</strong></div>
            <div><span>Wallet amount</span><strong>${money(execution.amountUsdt)}</strong></div>
            <div><span>Signal decision</span><strong>${escapeHtml(String(execution.decision || '—').replaceAll('_',' '))}</strong></div>
            <div><span>Score / confidence</span><strong>${execution.score ?? '—'}/100 · ${execution.confidence ?? '—'}/100</strong></div>
            ${isSell ? `<div><span>Realized P&L</span><strong class="${pnl != null && pnl >= 0 ? 'positive' : 'negative'}">${pnl == null ? '—' : signedMoney(pnl)}</strong></div>` : ''}
            ${isSell ? `<div><span>P&L %</span><strong>${execution.realizedPnlPercent == null ? '—' : signedPercent(execution.realizedPnlPercent)}</strong></div>` : ''}
            <div><span>Execution reason</span><strong>${escapeHtml(String(execution.executionReason || execution.executionType || '—').replaceAll('_',' '))}</strong></div>
        </div>`;
    el('execution-marker-dialog').showModal();
}



function signalExecutionRoleHtml(signal) {
    const interval = String(signal.interval || '').toLowerCase();
    const decision = String(signal.decision || '').toUpperCase();
    const higherInterval = String(signal.confluenceHigherInterval || '').toLowerCase();
    const higherDecision = String(signal.confluenceHigherDecision || '').toUpperCase();
    const bullish = decision === 'BUY' || decision === 'STRONG_BUY';
    const bearish = decision === 'SELL' || decision === 'STRONG_SELL';

    let tone = 'waiting';
    let title = 'Context only';
    let detail = 'This signal does not execute the wallet directly.';

    if (interval === '1h') {
        tone = bullish ? 'strategic-buy' : bearish ? 'strategic-sell' : 'waiting';
        title = bullish ? 'Strategic BUY' : bearish ? 'Strategic SELL' : 'Strategic context';
        detail = bullish
            ? 'Higher-timeframe support. Waiting for 5m bullish confirmation and a 1m BUY trigger.'
            : bearish
                ? 'Higher-timeframe bearish filter. It can block BUYs but does not execute a wallet trade itself.'
                : 'Higher-timeframe context only. Lower frames still decide whether an execution setup exists.';
    } else if (interval === '5m') {
        tone = bullish ? 'confirmation-buy' : bearish ? 'confirmation-sell' : 'waiting';
        title = bullish ? 'BUY confirmation' : bearish ? 'SELL confirmation' : 'Confirmation frame';
        detail = bullish
            ? '5m confirms bullish direction. Waiting for a valid 1m BUY execution trigger.'
            : bearish
                ? '5m confirms bearish direction. A managed position still needs a valid 1m exit trigger unless a risk exit fires.'
                : '5m is not an execution frame. It confirms or rejects the 1m trigger.';
    } else if (interval === '1m') {
        if (bullish) {
            const higherBullish = ['BUY', 'STRONG_BUY'].includes(higherDecision);
            const higherOpposed = ['SELL', 'STRONG_SELL'].includes(higherDecision);
            tone = higherBullish ? 'ready' : higherOpposed ? 'blocked' : 'waiting';
            title = higherBullish ? 'BUY execution candidate' : higherOpposed ? 'BUY blocked by confirmation' : 'BUY candidate · reduced confirmation';
            detail = higherBullish
                ? `1m trigger is present and ${higherInterval || 'higher frame'} is bullish. Execution Intelligence decides whether to execute immediately, accumulate evidence, or wait.`
                : higherOpposed
                    ? `1m BUY exists, but ${higherInterval || 'the higher frame'} is ${higherDecision.replaceAll('_', ' ')}.`
                    : `1m BUY exists while ${higherInterval || '5m'} is ${higherDecision ? higherDecision.replaceAll('_', ' ') : 'not fully bullish'}. The active Execution Profile decides whether to enter at reduced size.`;
        } else if (bearish) {
            const higherBearish = ['SELL', 'STRONG_SELL'].includes(higherDecision);
            tone = higherBearish ? 'ready' : 'waiting';
            title = higherBearish ? 'SELL execution candidate' : 'SELL waiting for confirmation';
            detail = higherBearish
                ? `1m exit trigger is present and ${higherInterval || 'higher frame'} confirms bearish direction. It can close only an active managed position.`
                : `1m SELL exists, but ${higherInterval || '5m'} has not confirmed a bearish exit. Risk exits remain independent.`;
        }
    }

    return `<div class="signal-role ${tone}"><strong>${escapeHtml(title)}</strong><small>${escapeHtml(detail)}</small></div>`;
}

function frameDecisionCard(frame, fallbackInterval) {
    const interval = String(frame?.interval || fallbackInterval || '—');
    const decision = String(frame?.decision || 'NO_SIGNAL').toUpperCase();
    const score = frame?.score == null ? '—' : `${frame.score}/100`;
    const tone = decision.includes('BUY') ? 'buy' : decision.includes('SELL') ? 'reject' : 'neutral';
    return `<div class="frame-decision-card ${tone}">
        <span>${escapeHtml(frame?.frame || '')}</span>
        <strong>${escapeHtml(interval.toUpperCase())}</strong>
        <b>${escapeHtml(decision.replaceAll('_',' '))}</b>
        <small>${score}${frame?.confidence == null ? '' : ` · C${frame.confidence}`}</small>
    </div>`;
}

function threeFrameDecisionHtml(snapshot) {
    return `<div class="frame-chain-label">Current confirmation chain</div><div class="three-frame-strip">
        ${frameDecisionCard(snapshot['1h'], '1h')}
        <span class="frame-arrow">→</span>
        ${frameDecisionCard(snapshot['5m'], '5m')}
        <span class="frame-arrow">→</span>
        ${frameDecisionCard(snapshot['1m'], '1m')}
    </div>`;
}

function signalTradePlanHtml(signal, execution, position) {
    const entryType = String(signal.atrEntryType || 'MARKET').replaceAll('_', ' ');
    const plannedPct = Number(signal.atrRecommendedPositionPercent ?? 100);
    const executed = Boolean(execution);
    const closed = position && String(position.status || '').toUpperCase() !== 'OPEN';
    return `<div class="trade-plan-card">
        <div class="trade-plan-title"><span>PLAN</span><strong>${escapeHtml(entryType)}</strong></div>
        <div class="trade-plan-grid">
            <div><span>Signal price</span><strong>${money(signal.latestPrice)}</strong></div>
            <div><span>Stop loss</span><strong class="negative">${money(signal.stopLoss)}</strong></div>
            <div><span>Take profit</span><strong class="positive">${money(signal.takeProfit)}</strong></div>
            <div><span>R / R</span><strong>${signal.riskRewardRatio == null ? '—' : `1 : ${Number(signal.riskRewardRatio).toFixed(2)}`}</strong></div>
            <div><span>ATR size</span><strong>${plannedPct}%</strong></div>
            <div><span>Actual entry</span><strong>${executed ? money(execution.price) : 'Not executed'}</strong></div>
            <div><span>Quantity</span><strong>${executed ? value(execution.quantity) : '—'}</strong></div>
            <div><span>Exit price</span><strong>${closed ? money(position.exitPrice) : executed ? 'Position open' : '—'}</strong></div>
        </div>
    </div>`;
}

function signalExecutionStatusHtml(signal, execution, position) {
    if (execution) {
        const open = position && String(position.status || '').toUpperCase() === 'OPEN';
        return `<div class="execution-state executed"><strong>WALLET EXECUTED</strong><small>Trade #${escapeHtml(execution.id ?? '—')} · ${money(execution.amountUsdt)}</small>${open ? '<span>Position Manager active</span>' : ''}</div>`;
    }
    const decision = String(signal.decision || '').toUpperCase();
    const buy = ['BUY','STRONG_BUY'].includes(decision);
    const actionable = buy || ['SELL','STRONG_SELL'].includes(decision);
    if (buy && (signal.buyPositionBlocked === true || signal.finalEntryAllowed === false)) {
        return `<div class="execution-state blocked"><strong>BUY POSITION BLOCKED</strong><small>Final entry gate prevented wallet execution for this signal.</small></div>`;
    }
    return `<div class="execution-state ${actionable ? 'waiting' : 'idle'}"><strong>${actionable ? 'NOT EXECUTED' : 'ANALYSIS ONLY'}</strong><small>${actionable ? 'Execution Intelligence / opportunity evidence decides next.' : 'No wallet action required.'}</small></div>`;
}

function dashboardSignalChartUrl(signal) {
    if (!signal?.generatedAt || signal?.latestPrice == null) return '#';
    const at = window.CryptoTime.parseUtc(signal.generatedAt);
    if (Number.isNaN(at.getTime())) return '#';
    const start = new Date(at.getTime() - 20 * 60 * 1000);
    const end = new Date(at.getTime() + 40 * 60 * 1000);
    const decision = String(signal.decision || 'BUY').replaceAll('_', ' ');
    const params = new URLSearchParams({
        symbol: String(signal.symbol || el('symbol-select')?.value || 'BNBUSDT').toUpperCase(),
        interval: '5m',
        focusStart: start.toISOString(),
        focusEnd: end.toISOString(),
        focusDirection: 'UP',
        debugTrade: '1',
        debugTradeLabel: `${decision} signal`,
        debugEntryTime: at.toISOString(),
        debugEntryPrice: String(signal.latestPrice)
    });
    return `/dashboard?${params.toString()}#market`;
}

function renderSignals(signals, displayOnlyInterval = false, timeframeSnapshot = {}, executions = [], openPositions = [], closedPositions = []) {
    const body = el('signals-body');
    const actionableSignals = (signals || []).filter(signal => {
        const decision = String(signal.decision || '').toUpperCase();
        return decision === 'BUY' || decision === 'STRONG_BUY' || decision === 'SELL' || decision === 'STRONG_SELL';
    });

    if (!actionableSignals.length) {
        body.innerHTML = displayOnlyInterval
            ? '<tr><td colspan="6" class="empty">4h and 1D are display-only market views. Trading signals remain generated only on 1m / 5m / 1h.</td></tr>'
            : '<tr><td colspan="6" class="empty">No actionable BUY or SELL signals for the selected symbol and interval. NEUTRAL and WATCH signals are hidden.</td></tr>';
        return;
    }

    signals = actionableSignals;
    const executionBySignal = new Map((executions || []).filter(x => x.signalId != null).map(x => [String(x.signalId), x]));
    const positionByEntrySignal = new Map([...(openPositions || []), ...(closedPositions || [])]
        .filter(x => x.entrySignalId != null).map(x => [String(x.entrySignalId), x]));
    const availableIds = new Set(signals.map(s => String(s.id)));
    if (pinnedSignalId && !availableIds.has(String(pinnedSignalId))) {
        pinnedSignalId = null;
        localStorage.removeItem('cryptoPinnedSignalId');
    }

    body.innerHTML = signals.map(s => {
        const signalId = String(s.id);
        const detailId = `signal-detail-${signalId}`;
        const isOpen = openSignalAnalysisIds.has(signalId) || pinnedSignalId === signalId;
        const isPinned = pinnedSignalId === signalId;
        const execution = executionBySignal.get(signalId) || (String(s.executionState || '').toUpperCase() === 'EXECUTED' ? {
            id: s.executionId,
            side: s.executedSide,
            price: s.executedPrice,
            quantity: s.executedQuantity,
            amountUsdt: s.executedAmountUsdt,
            executionReason: s.executionReason,
            executedAt: s.executedAt
        } : null);
        const position = positionByEntrySignal.get(signalId);
        return `
            <tr class="signal-row decision-board-row" data-detail-id="${detailId}">
                <td>
                    <div class="signal-identity">
                        <strong>${escapeHtml(s.symbol || '—')}</strong>
                        <span class="badge ${String(s.decision).toLowerCase()}">${escapeHtml(String(s.decision).replaceAll('_',' '))}</span>
                        <div class="signal-generated-meta">
                            <span class="signal-meta-pill"><small>TIMEFRAME</small><strong>${escapeHtml(String(s.interval || '—').toUpperCase())}</strong></span>
                            <span class="signal-meta-pill generated-at"><small>GENERATED</small><strong>${dateTime(s.generatedAt)}</strong></span>
                        </div>
                        <b>${s.totalScore}/100 <em>Raw ${s.rawScore}/${s.maximumAvailableScore}</em></b>
                    </div>
                </td>
                <td>${threeFrameDecisionHtml(timeframeSnapshot)}</td>
                <td>${signalTradePlanHtml(s, execution, position)}</td>
                <td>${signalExecutionStatusHtml(s, execution, position)}</td>
                <td>${signalExecutionRoleHtml(s)}</td>
                <td>
                    <div class="signal-row-actions">
                        <button type="button" class="signal-detail-button" data-signal-id="${signalId}" data-detail-id="${detailId}">${isOpen ? 'Hide analysis' : 'View analysis'}</button>
                        ${['BUY','STRONG_BUY'].includes(String(s.decision || '').toUpperCase()) ? `<a class="signal-detail-button signal-chart-button" href="${dashboardSignalChartUrl(s)}">View chart</a>` : ''}
                    </div>
                </td>
            </tr>
            <tr id="${detailId}" class="signal-detail-row ${isOpen ? '' : 'hidden'}">
                <td colspan="6">
                    <div class="analysis-view ${isPinned ? 'pinned' : ''}" data-signal-id="${signalId}">
                        <div class="analysis-toolbar">
                            <div><strong>${escapeHtml(s.symbol || '—')} · Signal #${escapeHtml(signalId)}</strong><small>Planned trade + full AI analysis</small></div>
                            <div class="analysis-toolbar-actions">
                                <button type="button" class="analysis-pin-button ${isPinned ? 'active' : ''}" data-signal-id="${signalId}">${isPinned ? 'Unpin analysis' : 'Pin analysis'}</button>
                                <button type="button" class="analysis-close-button" data-signal-id="${signalId}" data-detail-id="${detailId}">Close</button>
                            </div>
                        </div>
                        <section class="signal-live-plan-summary">
                            <div><span>First frame</span><strong>1H · ${escapeHtml(String(timeframeSnapshot['1h']?.decision || 'NO SIGNAL').replaceAll('_',' '))}</strong></div>
                            <div><span>Second frame</span><strong>5M · ${escapeHtml(String(timeframeSnapshot['5m']?.decision || 'NO SIGNAL').replaceAll('_',' '))}</strong></div>
                            <div><span>Third frame</span><strong>1M · ${escapeHtml(String(timeframeSnapshot['1m']?.decision || 'NO SIGNAL').replaceAll('_',' '))}</strong></div>
                            <div><span>Planned SL</span><strong>${money(s.stopLoss)}</strong></div>
                            <div><span>Planned TP</span><strong>${money(s.takeProfit)}</strong></div>
                            <div><span>Wallet entry</span><strong>${execution ? money(execution.price) : 'Not executed'}</strong></div>
                        </section>
                        ${scoreBreakdownHtml(s)}
                        <div id="signal-runtime-${signalId}" class="signal-runtime-analysis" data-signal-id="${signalId}">
                            ${signalRuntimeSummaryPlaceholderHtml(execution, position)}
                        </div>
                    </div>
                </td>
            </tr>`;
    }).join('');

    body.querySelectorAll('.signal-detail-button').forEach(button => {
        button.addEventListener('click', () => {
            const signalId = button.dataset.signalId;
            const detail = document.getElementById(button.dataset.detailId);
            const opening = detail.classList.contains('hidden');
            detail.classList.toggle('hidden');
            if (opening) {
                openSignalAnalysisIds.add(signalId);
                loadSignalRuntimeAnalysis(signalId);
            } else {
                openSignalAnalysisIds.delete(signalId);
            }
            button.textContent = opening ? 'Hide analysis' : 'View analysis';
        });
    });
    body.querySelectorAll('.analysis-close-button').forEach(button => {
        button.addEventListener('click', () => {
            const signalId = button.dataset.signalId;
            openSignalAnalysisIds.delete(signalId);
            if (pinnedSignalId === signalId) { pinnedSignalId = null; localStorage.removeItem('cryptoPinnedSignalId'); }
            document.getElementById(button.dataset.detailId)?.classList.add('hidden');
            body.querySelector(`.signal-detail-button[data-signal-id="${CSS.escape(signalId)}"]`)?.replaceChildren(document.createTextNode('View analysis'));
        });
    });
    body.querySelectorAll('.analysis-pin-button').forEach(button => {
        button.addEventListener('click', () => {
            const signalId = button.dataset.signalId;
            pinnedSignalId = pinnedSignalId === signalId ? null : signalId;
            if (pinnedSignalId) { openSignalAnalysisIds.add(signalId); localStorage.setItem('cryptoPinnedSignalId', pinnedSignalId); }
            else localStorage.removeItem('cryptoPinnedSignalId');
            renderSignals(signals, displayOnlyInterval, timeframeSnapshot, executions, openPositions, closedPositions);
        });
    });

    // View analysis is intentionally lazy: execution-opportunity/position details are
    // fetched only for rows the user opens, so recent execution-intelligence additions
    // do not slow normal dashboard symbol/timeframe refreshes.
    body.querySelectorAll('.signal-detail-row:not(.hidden) .signal-runtime-analysis').forEach(container => {
        loadSignalRuntimeAnalysis(container.dataset.signalId);
    });
}

function signalRuntimeSummaryPlaceholderHtml(execution, position) {
    const executionText = execution
        ? `${escapeHtml(String(execution.side || 'EXECUTED'))} ${money(execution.price)} · ${dateTime(execution.executedAt)}`
        : 'No wallet execution linked to this signal';
    const positionText = position
        ? `${escapeHtml(String(position.status || 'POSITION'))} · entry ${money(position.entryPrice ?? position.averageEntryPrice)} `
        : 'No managed position linked to this entry signal';
    return `<section class="runtime-analysis-card loading">
        <div class="confluence-heading"><div><span>Execution layers added after signal creation</span><h3>Execution Intelligence & Position Lifecycle</h3></div><span class="confirmation-badge neutral">LOADING</span></div>
        <div class="runtime-analysis-preview"><div><span>Wallet</span><strong>${executionText}</strong></div><div><span>Position</span><strong>${positionText}</strong></div></div>
        <p>Loading opportunity health, accumulated evidence, execution decision, entry quality and position protection on demand…</p>
    </section>`;
}

async function loadSignalRuntimeAnalysis(signalId) {
    if (!signalId) return;
    const container = document.getElementById(`signal-runtime-${signalId}`);
    if (!container) return;

    const cached = signalAnalysisDetailCache.get(String(signalId));
    if (cached) {
        container.innerHTML = signalRuntimeAnalysisHtml(cached);
        return;
    }
    if (container.dataset.loading === 'true') return;
    container.dataset.loading = 'true';
    try {
        const response = await fetch(`/api/dashboard/signal-analysis-details?signalId=${encodeURIComponent(signalId)}`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();
        signalAnalysisDetailCache.set(String(signalId), data);
        if (document.getElementById(`signal-runtime-${signalId}`)) {
            document.getElementById(`signal-runtime-${signalId}`).innerHTML = signalRuntimeAnalysisHtml(data);
        }
    } catch (error) {
        const current = document.getElementById(`signal-runtime-${signalId}`);
        if (current) current.innerHTML = `<section class="runtime-analysis-card warning"><div class="confluence-heading"><div><span>Execution drill-down</span><h3>Execution Intelligence & Position Lifecycle</h3></div><span class="confirmation-badge warning">UNAVAILABLE</span></div><p>Could not load the newer execution layers for this signal. The immutable signal analysis above is still valid.</p></section>`;
    } finally {
        const current = document.getElementById(`signal-runtime-${signalId}`);
        if (current) current.dataset.loading = 'false';
    }
}

function signalRuntimeAnalysisHtml(data) {
    if (!data?.found) {
        return `<section class="runtime-analysis-card warning"><div class="confluence-heading"><div><span>Execution drill-down</span><h3>Execution Intelligence & Position Lifecycle</h3></div><span class="confirmation-badge warning">NO SIGNAL</span></div><p>The persisted signal could not be loaded.</p></section>`;
    }

    const o = data.opportunity;
    const x = data.execution;
    const p = data.position;
    const healthTone = !o ? 'neutral' : Number(o.opportunityHealth || 0) >= 60 ? 'bullish' : Number(o.opportunityHealth || 0) >= 40 ? 'warning' : 'bearish';
    const opportunityHtml = o ? `
        <section class="runtime-analysis-card ${healthTone}">
            <div class="confluence-heading">
                <div><span>Accumulated execution evidence</span><h3>Execution Opportunity</h3></div>
                <span class="confirmation-badge ${healthTone}">${escapeHtml(String(o.status || 'UNKNOWN').replaceAll('_',' '))}</span>
            </div>
            <div class="runtime-analysis-grid">
                <div><span>Health</span><strong>${o.opportunityHealth ?? 0}/100</strong><small>momentum ${signedNumber(o.healthMomentum)}</small></div>
                <div><span>Evidence score</span><strong>${o.evidenceScore ?? 0}</strong><small>momentum ${signedNumber(o.evidenceMomentum)}</small></div>
                <div><span>Evidence count</span><strong>${o.evidenceCount ?? 0}</strong><small>BUY ${o.buyCount ?? 0} · WATCH ${o.watchCount ?? 0}</small></div>
                <div><span>Bearish evidence</span><strong>${o.bearishCount ?? 0}</strong><small>Neutral ${o.neutralCount ?? 0}</small></div>
                <div><span>5m / 1h</span><strong>${escapeHtml(String(o.fiveMinuteDecision || '—'))} / ${escapeHtml(String(o.oneHourDecision || '—'))}</strong></div>
                <div><span>Avg score / confidence</span><strong>${o.averageSignalScore ?? 0} / ${o.averageConfidence ?? 0}</strong></div>
                <div><span>Execution source</span><strong>${escapeHtml(String(o.executionSource || '—').replaceAll('_',' '))}</strong></div>
                <div><span>Recommended size</span><strong>${o.recommendedPositionPercent ?? 0}%</strong></div>
                <div><span>Started</span><strong>${dateTime(o.startedAt)}</strong></div>
                <div><span>Last evidence</span><strong>${dateTime(o.lastEvidenceAt)}</strong></div>
            </div>
            <div class="runtime-decision-code"><span>Decision code</span><strong>${escapeHtml(String(o.decisionCode || '—').replaceAll('_',' '))}</strong><p>${escapeHtml(o.decisionExplanation || 'No execution-opportunity explanation was persisted.')}</p></div>
        </section>` : `
        <section class="runtime-analysis-card neutral">
            <div class="confluence-heading"><div><span>Accumulated execution evidence</span><h3>Execution Opportunity</h3></div><span class="confirmation-badge neutral">NO EXACT SNAPSHOT</span></div>
            <p>No execution_opportunity row currently points to this exact signal. This is normal for older signals or opportunities whose latest evidence later moved to another signal.</p>
        </section>`;

    const executionHtml = x ? `
        <section class="runtime-analysis-card bullish">
            <div class="confluence-heading"><div><span>Actual financial action</span><h3>Wallet Execution</h3></div><span class="confirmation-badge bullish">${escapeHtml(String(x.side || 'EXECUTED'))}</span></div>
            <div class="runtime-analysis-grid">
                <div><span>Execution time</span><strong>${dateTime(x.executedAt)}</strong></div>
                <div><span>Execution price</span><strong>${money(x.price)}</strong></div>
                <div><span>Quantity</span><strong>${value(x.quantity)}</strong></div>
                <div><span>Net amount</span><strong>${money(x.amountUsdt)}</strong></div>
                <div><span>Execution type</span><strong>${escapeHtml(String(x.executionType || '—').replaceAll('_',' '))}</strong></div>
                <div><span>Execution reason</span><strong>${escapeHtml(String(x.executionReason || '—').replaceAll('_',' '))}</strong></div>
            </div>
        </section>` : `
        <section class="runtime-analysis-card neutral"><div class="confluence-heading"><div><span>Actual financial action</span><h3>Wallet Execution</h3></div><span class="confirmation-badge neutral">NOT EXECUTED</span></div><p>This signal has no direct executed wallet_trade row.</p></section>`;

    const positionTone = p?.profitLockActive ? 'bullish' : p ? 'warning' : 'neutral';
    const positionHtml = p ? `
        <section class="runtime-analysis-card ${positionTone}">
            <div class="confluence-heading"><div><span>Progressive entry + protection</span><h3>Managed Position</h3></div><span class="confirmation-badge ${positionTone}">${escapeHtml(String(p.status || 'UNKNOWN'))}</span></div>
            <div class="runtime-analysis-grid">
                <div><span>Entry stage</span><strong>${escapeHtml(String(p.entryStage || '—').replaceAll('_',' '))}</strong></div>
                <div><span>Allocated</span><strong>${p.allocatedPositionPercent ?? 0}%</strong></div>
                <div><span>Entry quality</span><strong>${p.entryQualityScore ?? 0}/100</strong></div>
                <div><span>Average entry</span><strong>${money(p.averageEntryPrice)}</strong></div>
                <div><span>Stop / Target</span><strong>${money(p.stopLoss)} / ${money(p.takeProfit)}</strong></div>
                <div><span>Highest price</span><strong>${money(p.highestPrice)}</strong></div>
                <div><span>Profit lock</span><strong>${p.profitLockActive ? 'ACTIVE' : 'INACTIVE'}</strong><small>${p.profitLockPrice == null ? '—' : money(p.profitLockPrice)}</small></div>
                <div><span>Profit-lock progress</span><strong>${p.profitLockProgressPercent == null ? '—' : `${Number(p.profitLockProgressPercent).toFixed(2)}%`}</strong></div>
                <div><span>Last scale-in</span><strong>${p.lastScaleInAt ? dateTime(p.lastScaleInAt) : '—'}</strong></div>
                <div><span>Opened</span><strong>${dateTime(p.openedAt)}</strong></div>
            </div>
        </section>` : `
        <section class="runtime-analysis-card neutral"><div class="confluence-heading"><div><span>Progressive entry + protection</span><h3>Managed Position</h3></div><span class="confirmation-badge neutral">NO POSITION</span></div><p>No wallet-managed position was opened directly from this signal.</p></section>`;

    return `<div class="runtime-analysis-heading"><span>Current execution architecture</span><h3>Post-analysis layers</h3><p>This section reflects the newer opportunity health/evidence, execution decision, progressive sizing, entry quality and Dynamic Profit Lock layers without rewriting the original signal snapshot.</p></div>${opportunityHtml}${executionHtml}${positionHtml}`;
}

function signedNumber(v) {
    const n = Number(v ?? 0);
    return `${n > 0 ? '+' : ''}${Number.isFinite(n) ? n : 0}`;
}

function scoreBreakdownHtml(signal) {
    const b = signal.scoreBreakdown || {};
    const category = (title, data) => `
        <section class="score-category">
            <div class="score-category-heading"><strong>${escapeHtml(title)}</strong><span>${data?.score ?? 0}/${data?.maximum ?? 0}</span></div>
            <div class="score-progress"><span style="width:${Math.min(100, ((data?.score || 0) * 100) / Math.max(1, data?.maximum || 1))}%"></span></div>
            ${(data?.components || []).map(c => `
                <div class="score-component score-component-detailed">
                    <div>
                        <span>${escapeHtml(c.label)}</span>
                        ${c.status ? `<small>${escapeHtml(String(c.status))}</small>` : ''}
                        ${c.metric ? `<small class="score-metric">${escapeHtml(String(c.metric))}</small>` : ''}
                    </div>
                    <div class="score-component-value">
                        <strong>${c.score}/${c.maximum}</strong>
                        ${c.value ? `<small>${escapeHtml(String(c.value))}</small>` : ''}
                    </div>
                </div>`).join('')}
        </section>`;

    const providers = (b.sentiment?.providers || []).map(p => {
        const contribution = Number(p.score || 0) * Number(p.effectiveWeight || 0);
        return `<div class="score-component"><span>${escapeHtml(String(p.provider || 'Provider'))}${p.enabled ? '' : ' (disabled)'}</span><strong>${contribution >= 0 ? '+' : ''}${contribution.toFixed(3)}</strong></div>`;
    }).join('') || '<div class="score-component"><span>No active sentiment samples</span><strong>—</strong></div>';

    const entryPrice = Number(signal.latestPrice || 0);
    const atr = Number(signal.atr14 || 0);
    const stopPrice = Number(signal.stopLoss || 0);
    const targetPrice = Number(signal.takeProfit || 0);
    const stopDistance = entryPrice > 0 && stopPrice > 0 ? entryPrice - stopPrice : null;
    const targetDistance = entryPrice > 0 && targetPrice > 0 ? targetPrice - entryPrice : null;
    const stopMultiplier = atr > 0 && stopDistance != null ? stopDistance / atr : null;
    const targetMultiplier = atr > 0 && targetDistance != null ? targetDistance / atr : null;
    const stopPercent = entryPrice > 0 && stopDistance != null ? (stopDistance / entryPrice) * 100 : null;
    const targetPercent = entryPrice > 0 && targetDistance != null ? (targetDistance / entryPrice) * 100 : null;
    const decision = String(signal.decision || 'NEUTRAL');
    const confirmations = keyConfirmationsHtml(b);
    const confluenceStatus = String(signal.confluenceStatus || 'UNAVAILABLE');
    const confluenceTone = confluenceStatus.includes('CONFLICT') ? 'bearish'
        : confluenceStatus.includes('AGREEMENT') ? 'bullish'
        : confluenceStatus === 'MIXED' ? 'warning' : 'neutral';
    const btcRelationshipType = String(signal.btcRelationshipType || 'UNAVAILABLE');
    const btcContextStatus = String(signal.btcContextStatus || 'UNAVAILABLE');
    const btcTone = btcContextStatus.includes('CONFLICT') ? 'bearish'
        : btcContextStatus === 'CONFIRMED' ? 'bullish'
        : btcContextStatus === 'LEARNING' ? 'warning' : 'neutral';
    const correlation = signal.btcCorrelation == null ? '—' : `${Number(signal.btcCorrelation) >= 0 ? '+' : ''}${Number(signal.btcCorrelation).toFixed(3)}`;
    const beta = signal.btcBeta == null ? '—' : `${Number(signal.btcBeta) >= 0 ? '+' : ''}${Number(signal.btcBeta).toFixed(3)}`;
    const influence = signal.btcInfluenceFactor == null ? '0%' : `${(Number(signal.btcInfluenceFactor) * 100).toFixed(0)}%`;
    const derivativesStatus = String(signal.derivativesStatus || 'UNAVAILABLE');
    const derivativesTone = ['FRESH_LONG_BUILDUP','FRESH_SHORT_BUILDUP','HEALTHY_BULLISH','HEALTHY_BEARISH'].includes(derivativesStatus) ? 'supportive'
        : ['LONGS_CROWDED','SHORTS_CROWDED'].includes(derivativesStatus) ? 'risk'
        : ['LEARNING','SHORT_COVERING','LONG_LIQUIDATION','LOW_CONVICTION'].includes(derivativesStatus) ? 'caution' : 'neutral';
    const fundingDisplay = signal.fundingRate == null ? '—' : `${(Number(signal.fundingRate) * 100).toFixed(4)}%`;
    const fundingPercentileDisplay = signal.fundingPercentile == null ? '—' : `${Number(signal.fundingPercentile).toFixed(1)}%`;
    const oiChangeDisplay = signal.openInterestChangePercent == null ? '—' : `${Number(signal.openInterestChangePercent) >= 0 ? '+' : ''}${Number(signal.openInterestChangePercent).toFixed(2)}%`;
    const derivativesPriceChangeDisplay = signal.derivativesPriceChangePercent == null ? '—' : `${Number(signal.derivativesPriceChangePercent) >= 0 ? '+' : ''}${Number(signal.derivativesPriceChangePercent).toFixed(2)}%`;
    const liquidityStatus = String(signal.liquidityStatus || 'UNAVAILABLE');
    const liquidityTone = liquidityStatus === 'BULLISH_SUPPORT' ? 'bullish'
        : ['BEARISH_PRESSURE', 'TARGET_BLOCKED', 'STOP_EXPOSED', 'THIN_LIQUIDITY'].includes(liquidityStatus) ? 'bearish'
        : liquidityStatus === 'LEARNING' ? 'warning' : 'neutral';
    const compactDepth = value => {
        if (value == null || Number.isNaN(Number(value))) return '—';
        return new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 2 }).format(Number(value));
    };
    const strategy = signal.strategyBreakdown || {};
    const marketContext = signal.marketContextSnapshot || {};
    const activeStrategy = strategy.active || {};
    const strategyTone = signal.strategyEntryAllowed === false ? 'bearish'
        : String(signal.selectedStrategy || '').includes('DEFENSIVE') ? 'warning' : 'bullish';
    const evidence = Array.isArray(strategy.regimeEvidence) ? strategy.regimeEvidence : [];
    const strategyName = escapeHtml(String(signal.selectedStrategy || strategy.selectedStrategy || 'LEGACY').replaceAll('_', ' '));
    const confidenceScore = Number(signal.confidenceScore ?? 0);
    const confidenceTone = confidenceScore >= 75 ? 'bullish' : confidenceScore >= 50 ? 'warning' : 'bearish';
    const decisionPath = Array.isArray(signal.decisionPath) ? signal.decisionPath : [];
    const decisionPathHtml = decisionPath.length ? `<section class="decision-path-card ${confidenceTone}">
        <div class="confluence-heading">
            <div><span>Immutable ordered audit trail</span><h3>Final Decision Path</h3></div>
            <span class="confirmation-badge ${confidenceTone}">${confidenceScore}/100 confidence</span>
        </div>
        <ol class="decision-path-list">
            ${decisionPath.map(step => `<li class="decision-path-step ${String(step.type || 'PASS').toLowerCase()}">
                <div><strong>${escapeHtml(String(step.source || 'CHECK').replaceAll('_', ' '))}</strong><span>${escapeHtml(String(step.type || 'PASS').replaceAll('_', ' '))}</span></div>
                <small>${escapeHtml(String(step.beforeDecision || '—').replaceAll('_', ' '))} → ${escapeHtml(String(step.afterDecision || '—').replaceAll('_', ' '))} · Entry ${step.entryAllowedAfter === false ? 'blocked' : 'allowed'}</small>
                <p>${escapeHtml(step.reason || 'No explanation was stored.')}</p>
            </li>`).join('')}
        </ol>
        <p>${escapeHtml(signal.finalDecisionExplanation || 'The final decision was produced by the ordered safety pipeline.')}</p>
    </section>` : '';
    const dataQualityValid = marketContext.dataValid !== false;
    const dataQualityLabel = dataQualityValid ? 'VALID' : 'BLOCKED';
    const dataQualityTone = dataQualityValid ? 'complete' : 'blocked';
    const isolatedDecision = String(signal.originalDecision || signal.decision || 'NEUTRAL').replaceAll('_', ' ');
    const finalDecision = String(signal.decision || 'NEUTRAL').replaceAll('_', ' ');
    const entryAllowed = signal.finalEntryAllowed !== false;
    const walletAction = !entryAllowed
        ? 'NO ENTRY'
        : ['BUY', 'STRONG_BUY'].includes(String(signal.decision || ''))
            ? 'OPEN / HOLD'
            : ['SELL', 'STRONG_SELL'].includes(String(signal.decision || ''))
                ? 'CLOSE / AVOID'
                : 'HOLD / WAIT';
    const sentimentCoverage = marketContext.sentimentEnabled === false
        ? 'DISABLED'
        : `${marketContext.sentimentProvidersContributing ?? 0}/${marketContext.sentimentProvidersEnabled ?? 0}`;
    const safetyWarnings = decisionPath.filter(step => ['WARNING', 'DOWNGRADE', 'VETO', 'BLOCK'].includes(String(step.type || '').toUpperCase())).length;
    const safetyLabel = entryAllowed ? (safetyWarnings ? `${safetyWarnings} WARNING${safetyWarnings === 1 ? '' : 'S'}` : 'PASSED') : 'ENTRY BLOCKED';
    const safetyTone = entryAllowed ? (safetyWarnings ? 'warning' : 'complete') : 'blocked';
    const transformationSteps = decisionPath.length
        ? decisionPath.map(step => {
            const source = escapeHtml(String(step.source || 'CHECK').replaceAll('_', ' '));
            const after = escapeHtml(String(step.afterDecision || signal.decision || '—').replaceAll('_', ' '));
            const changed = String(step.beforeDecision || '') !== String(step.afterDecision || '');
            const blocked = step.entryAllowedAfter === false;
            return `<span class="decision-transform-step ${blocked ? 'blocked' : changed ? 'warning' : 'complete'}"><small>${source}</small><strong>${after}</strong></span>`;
        }).join('<span class="decision-transform-arrow">→</span>')
        : `<span class="decision-transform-step complete"><small>Base score</small><strong>${escapeHtml(isolatedDecision)}</strong></span><span class="decision-transform-arrow">→</span><span class="decision-transform-step ${entryAllowed ? 'complete' : 'blocked'}"><small>Final</small><strong>${escapeHtml(finalDecision)}</strong></span>`;

    const strategyFlowHtml = `<section class="signal-pipeline-card">
        <div class="strategy-flow-heading">
            <div>
                <span>How this signal was produced</span>
                <h3>Analysis & Execution Pipeline</h3>
            </div>
            <span class="confirmation-badge ${strategyTone}">${strategyName}</span>
        </div>
        <div class="signal-pipeline-grid">
            <article class="signal-pipeline-step complete">
                <span class="pipeline-number">1</span><div><strong>Market Data</strong><small>${escapeHtml(signal.symbol || '—')} · ${escapeHtml(displayInterval(signal.interval))}</small><p>Closed candle at ${money(entryPrice)}</p></div>
            </article>
            <article class="signal-pipeline-step ${dataQualityTone}">
                <span class="pipeline-number">2</span><div><strong>Data Quality</strong><small>${dataQualityLabel}</small><p>${dataQualityValid ? 'Required indicator history is available.' : 'Analysis inputs failed validation.'}</p></div>
            </article>
            <article class="signal-pipeline-step complete">
                <span class="pipeline-number">3</span><div><strong>Technical Indicators</strong><small>${signal.rawScore ?? 0}/${signal.maximumAvailableScore ?? 85} raw</small><p>Trend, volume, momentum, sentiment and fundamentals.</p></div>
            </article>
            <article class="signal-pipeline-step ${Number(signal.marketRegimeConfidence ?? strategy.regimeConfidence ?? 0) >= 60 ? 'complete' : 'warning'}">
                <span class="pipeline-number">4</span><div><strong>Market Regime</strong><small>${escapeHtml(String(signal.marketRegime || strategy.regime || 'UNKNOWN').replaceAll('_', ' '))}</small><p>${signal.marketRegimeConfidence ?? strategy.regimeConfidence ?? 0}% confidence</p></div>
            </article>
            <article class="signal-pipeline-step ${marketContext.dataValid === false ? 'blocked' : 'complete'}">
                <span class="pipeline-number">5</span><div><strong>Market Context</strong><small>HTF · BTC · Funding · Liquidity</small><p>${escapeHtml(String(marketContext.higherTimeframeStatus || signal.confluenceStatus || 'UNAVAILABLE').replaceAll('_', ' '))} · Sentiment ${sentimentCoverage}</p></div>
            </article>
            <article class="signal-pipeline-step ${signal.strategyEntryAllowed === false ? 'blocked' : strategyTone === 'warning' ? 'warning' : 'complete'}">
                <span class="pipeline-number">6</span><div><strong>Strategy Selection</strong><small>${strategyName}</small><p>Version ${escapeHtml(String(signal.strategyVersion || strategy.strategyVersion || '—'))}</p></div>
            </article>
            <article class="signal-pipeline-step complete">
                <span class="pipeline-number">7</span><div><strong>Strategy Scoring</strong><small>${signal.totalScore ?? 0}/100</small><p>Isolated decision: ${escapeHtml(isolatedDecision)}</p></div>
            </article>
            <article class="signal-pipeline-step ${safetyTone}">
                <span class="pipeline-number">8</span><div><strong>Risk & Safety</strong><small>${safetyLabel}</small><p>ATR, timeframe, BTC, funding/OI and order book.</p></div>
            </article>
            <article class="signal-pipeline-step ${entryAllowed ? 'complete' : 'blocked'}">
                <span class="pipeline-number">9</span><div><strong>Final Decision</strong><small>${escapeHtml(finalDecision)}</small><p>${confidenceScore}/100 confidence · Entry ${entryAllowed ? 'allowed' : 'blocked'}</p></div>
            </article>
            <article class="signal-pipeline-step ${entryAllowed ? 'complete' : 'blocked'}">
                <span class="pipeline-number">10</span><div><strong>Execution Intelligence</strong><small>Immediate · consolidated · accumulated evidence</small><p>${entryAllowed ? 'Fresh signal can proceed to intelligent execution evaluation.' : 'Signal may still be observed as opportunity evidence unless a hard veto applies.'}</p></div>
            </article>
            <article class="signal-pipeline-step ${entryAllowed ? 'complete' : 'blocked'}">
                <span class="pipeline-number">11</span><div><strong>Wallet Execution</strong><small>${walletAction}</small><p>${entryAllowed ? 'Wallet rules decide whether the recommendation becomes an executed BUY or SELL.' : 'No wallet execution is permitted.'}</p></div>
            </article>
            <article class="signal-pipeline-step complete">
                <span class="pipeline-number">12</span><div><strong>Position Manager</strong><small>Open-position lifecycle</small><p>Stop loss, take profit and Dynamic Profit Lock protect executed positions.</p></div>
            </article>
            <article class="signal-pipeline-step complete">
                <span class="pipeline-number">13</span><div><strong>Wallet Trade</strong><small>Executed ledger</small><p>Executed BUY/SELL activity becomes the financial source of truth and Trade History.</p></div>
            </article>
            <article class="signal-pipeline-step complete">
                <span class="pipeline-number">14</span><div><strong>Trade Inspector</strong><small>Quality analysis</small><p>Closed wallet trades are evaluated for entry, exit, MFE/MAE and post-exit behavior.</p></div>
            </article>
        </div>
        <div class="decision-transformation">
            <div class="decision-transformation-heading"><span>Decision transformation</span><strong>${escapeHtml(isolatedDecision)} → ${escapeHtml(finalDecision)}</strong></div>
            <div class="decision-transformation-track">${transformationSteps}</div>
        </div>
    </section>`;

    const derivativesHtml = `<section class="derivatives-positioning-card ${derivativesTone}">
        <div class="confluence-heading">
            <div><span>Leverage positioning snapshot</span><h3>Funding Rate & Open Interest</h3></div>
            <span class="confirmation-badge ${derivativesTone === 'risk' ? 'bearish' : derivativesTone === 'supportive' ? 'bullish' : derivativesTone === 'caution' ? 'warning' : 'neutral'}">${escapeHtml(derivativesStatus.replaceAll('_', ' '))}</span>
        </div>
        <div class="derivatives-grid">
            <div><span>Funding rate</span><strong>${fundingDisplay}</strong><small>Percentile ${fundingPercentileDisplay}</small></div>
            <div><span>Open interest</span><strong>${compactDepth(signal.openInterestValue ?? signal.openInterest)}</strong><small>${oiChangeDisplay} over ${escapeHtml(signal.derivativesPeriod || signal.interval || '—')}</small></div>
            <div><span>Price movement</span><strong>${derivativesPriceChangeDisplay}</strong><small>${signal.fundingSampleSize ?? 0} funding samples</small></div>
            <div><span>Confidence impact</span><strong>${Number(signal.derivativesConfidenceAdjustment || 0) >= 0 ? '+' : ''}${signal.derivativesConfidenceAdjustment || 0}</strong><small>Entry ${signal.derivativesEntryAllowed === false ? 'blocked' : 'allowed'}</small></div>
        </div>
        <p>${escapeHtml(signal.derivativesExplanation || 'No derivatives positioning explanation was stored.')}</p>
    </section>`;

    const strategyHtml = `<section class="dynamic-strategy-card ${strategyTone}">
        <div class="confluence-heading">
            <div><span>Market-driven analysis profile</span><h3>Dynamic Strategy</h3></div>
            <span class="confirmation-badge ${strategyTone}">${escapeHtml(String(signal.selectedStrategy || 'LEGACY').replaceAll('_', ' '))}</span>
        </div>
        <div class="confluence-grid">
            <div><span>Market regime</span><strong>${escapeHtml(String(signal.marketRegime || strategy.regime || 'UNKNOWN').replaceAll('_', ' '))}</strong></div>
            <div><span>Regime confidence</span><strong>${signal.marketRegimeConfidence ?? strategy.regimeConfidence ?? 0}%</strong></div>
            <div><span>Strategy version</span><strong>${escapeHtml(String(signal.strategyVersion || strategy.strategyVersion || '—'))}</strong></div>
            <div><span>Strategy entry</span><strong>${signal.strategyEntryAllowed === false ? 'DISABLED' : 'ALLOWED'}</strong></div>
            <div><span>Active raw score</span><strong>${activeStrategy.raw ?? signal.rawScore ?? 0}/${activeStrategy.maximum ?? signal.maximumAvailableScore ?? 85}</strong></div>
            <div><span>Excluded categories</span><strong>${Object.keys(signal.excludedCategories || {}).length ? Object.keys(signal.excludedCategories).join(", ") : "None"}</strong></div>
            <div><span>Normalized score</span><strong>${activeStrategy.normalized ?? signal.totalScore ?? 0}/100</strong></div>
        </div>
        <div class="dynamic-weight-grid">
            <div><span>Trend</span><strong>${activeStrategy.trend?.score ?? signal.trendScore ?? 0}/${signal.strategyTrendMaximum ?? activeStrategy.trend?.maximum ?? 25}</strong></div>
            <div><span>Volume</span><strong>${activeStrategy.volume?.score ?? signal.volumeScore ?? 0}/${signal.strategyVolumeMaximum ?? activeStrategy.volume?.maximum ?? 20}</strong></div>
            <div><span>Momentum</span><strong>${activeStrategy.momentum?.score ?? signal.momentumScore ?? 0}/${signal.strategyMomentumMaximum ?? activeStrategy.momentum?.maximum ?? 15}</strong></div>
            <div><span>Sentiment</span><strong>${signal.sentimentAvailable === false ? 'EXCLUDED' : `${activeStrategy.sentiment?.score ?? signal.sentimentScore ?? 0}/${signal.strategySentimentMaximum ?? activeStrategy.sentiment?.maximum ?? 15}`}</strong></div>
            <div><span>Fundamentals</span><strong>${signal.fundamentalAvailable === false ? 'EXCLUDED' : `${activeStrategy.fundamentals?.score ?? signal.fundamentalScore ?? 0}/${signal.strategyFundamentalMaximum ?? activeStrategy.fundamentals?.maximum ?? 10}`}</strong></div>
        </div>
        <div class="market-context-grid">
            <div><span>Higher timeframe</span><strong>${escapeHtml(String(marketContext.higherTimeframeStatus || signal.confluenceStatus || 'UNAVAILABLE').replaceAll('_', ' '))}</strong></div>
            <div><span>BTC relationship</span><strong>${escapeHtml(String(marketContext.btcRelationshipType || signal.btcRelationshipType || 'UNAVAILABLE').replaceAll('_', ' '))}</strong></div>
            <div><span>Liquidity used</span><strong>${escapeHtml(String(marketContext.liquidityStatus || signal.liquidityStatus || 'UNAVAILABLE').replaceAll('_', ' '))}</strong></div>
            <div><span>Funding & OI</span><strong>${escapeHtml(String(marketContext.derivativesStatus || signal.derivativesStatus || 'UNAVAILABLE').replaceAll('_', ' '))}</strong></div>
            <div><span>Sentiment coverage</span><strong>${marketContext.sentimentEnabled === false ? 'DISABLED' : `${marketContext.sentimentProvidersContributing ?? 0}/${marketContext.sentimentProvidersEnabled ?? 0}`}</strong></div>
        </div>
        ${evidence.length ? `<ul class="strategy-evidence">${evidence.map(item => `<li>${escapeHtml(String(item))}</li>`).join('')}</ul>` : ''}
        ${Array.isArray(marketContext.evidence) && marketContext.evidence.length ? `<ul class="strategy-evidence context-evidence">${marketContext.evidence.map(item => `<li>${escapeHtml(String(item))}</li>`).join('')}</ul>` : ''}
        <p>${escapeHtml(signal.strategyExplanation || strategy.explanation || 'This legacy signal was generated before dynamic strategy selection was enabled.')}</p>
    </section>`;

    const liquidityHtml = `<section class="liquidity-card ${liquidityTone}">
        <div class="confluence-heading">
            <div><span>Stored live depth snapshot</span><h3>Order Book & Liquidity</h3></div>
            <span class="confirmation-badge ${liquidityTone}">${escapeHtml(liquidityStatus.replaceAll('_', ' '))}</span>
        </div>
        <div class="confluence-grid">
            <div><span>Bid depth</span><strong>${compactDepth(signal.orderBookBidDepth)}</strong></div>
            <div><span>Ask depth</span><strong>${compactDepth(signal.orderBookAskDepth)}</strong></div>
            <div><span>Imbalance</span><strong>${signal.orderBookImbalance == null ? '—' : Number(signal.orderBookImbalance).toFixed(3)}</strong></div>
            <div><span>Spread</span><strong>${signal.orderBookSpreadPercent == null ? '—' : `${Number(signal.orderBookSpreadPercent).toFixed(4)}%`}</strong></div>
            <div><span>Bid wall</span><strong>${signal.nearestBidWallPrice == null ? '—' : money(signal.nearestBidWallPrice)}</strong><small>${signal.nearestBidWallSize == null ? '' : compactDepth(signal.nearestBidWallSize)}</small></div>
            <div><span>Ask wall</span><strong>${signal.nearestAskWallPrice == null ? '—' : money(signal.nearestAskWallPrice)}</strong><small>${signal.nearestAskWallSize == null ? '' : compactDepth(signal.nearestAskWallSize)}</small></div>
            <div><span>Target blocked</span><strong>${signal.orderBookTargetBlocked ? 'YES' : 'NO'}</strong></div>
            <div><span>Stop exposed</span><strong>${signal.orderBookStopExposed ? 'YES' : 'NO'}</strong></div>
            <div><span>Signal timeframe</span><strong>${escapeHtml(displayInterval(signal.interval))}</strong></div>
            <div><span>Depth window</span><strong>${signal.orderBookWindowSeconds ? `${signal.orderBookWindowSeconds}s` : '—'}</strong></div>
            <div><span>Wall persistence</span><strong>${signal.orderBookWallPersistenceSeconds ? `${signal.orderBookWallPersistenceSeconds}s` : '—'}</strong></div>
            <div><span>Influence</span><strong>${signal.orderBookInfluenceFactor == null ? '—' : `${Math.round(Number(signal.orderBookInfluenceFactor) * 100)}%`}</strong></div>
            <div><span>Veto allowed</span><strong>${signal.orderBookVetoAllowed ? 'YES' : 'NO'}</strong></div>
            <div><span>Observations</span><strong>${signal.orderBookObservations ?? 0}</strong></div>
            <div><span>Entry allowed</span><strong>${signal.liquidityEntryAllowed === false ? 'NO' : 'YES'}</strong></div>
            <div><span>Evaluated at</span><strong>${dateTime(signal.liquidityEvaluatedAt || signal.generatedAt)}</strong></div>
        </div>
        <p>${escapeHtml(signal.liquidityExplanation || 'Order-book liquidity was not captured when this legacy signal was created.')}</p>
    </section>`;
    const btcRelationshipHtml = `<section class="btc-relationship-card ${btcTone}">
        <div class="confluence-heading">
            <div><span>Stored BTC relationship snapshot</span><h3>BTC Relationship & Market Context</h3></div>
            <span class="confirmation-badge ${btcTone}">${escapeHtml(btcContextStatus.replaceAll('_', ' '))}</span>
        </div>
        <div class="btc-context-columns">
            <div class="btc-context-block">
                <h4>How this asset behaves versus BTC</h4>
                <div class="confluence-grid">
                    <div><span>Relationship</span><strong>${escapeHtml(btcRelationshipType.replaceAll('_', ' '))}</strong></div>
                    <div><span>Correlation</span><strong>${correlation}</strong></div>
                    <div><span>Beta</span><strong>${beta}</strong></div>
                    <div><span>Aligned samples</span><strong>${signal.btcRelationshipSampleSize ?? 0}</strong></div>
                    <div><span>BTC influence</span><strong>${influence}</strong></div>
                    <div><span>Relationship stable</span><strong>${signal.btcRelationshipStable ? 'YES' : 'NO'}</strong></div>
                </div>
            </div>
            <div class="btc-context-block">
                <h4>How BTC affected this signal</h4>
                <div class="confluence-grid">
                    <div><span>BTC timeframe</span><strong>${escapeHtml(displayInterval(signal.btcContextInterval, signal.interval || 'Unavailable at creation'))}</strong></div>
                    <div><span>BTC decision</span><strong>${escapeHtml(String(signal.btcContextDecision || '—').replaceAll('_', ' '))}</strong></div>
                    <div><span>BTC trend</span><strong>${signal.btcContextTrendScore == null ? '—' : `${signal.btcContextTrendScore}/25`}</strong></div>
                    <div><span>Entry allowed</span><strong>${signal.btcContextEntryAllowed === false ? 'NO' : 'YES'}</strong></div>
                    <div><span>Evaluated at</span><strong>${dateTime(signal.btcContextEvaluatedAt || signal.generatedAt)}</strong></div>
                </div>
            </div>
        </div>
        <p>${escapeHtml(signal.btcContextExplanation || 'BTC relationship was not captured when this legacy signal was created.')}</p>
    </section>`;

    const confluenceHtml = `<section class="confluence-card ${confluenceTone}">
        <div class="confluence-heading">
            <div><span>Stored higher-timeframe snapshot</span><h3>Multi-Timeframe Confluence</h3></div>
            <span class="confirmation-badge ${confluenceTone}">${escapeHtml(confluenceStatus.replaceAll('_', ' '))}</span>
        </div>
        <div class="confluence-grid">
            <div><span>Isolated decision</span><strong>${escapeHtml(String(signal.originalDecision || signal.decision || '—').replaceAll('_',' '))}</strong></div>
            <div><span>Final recommendation</span><strong>${escapeHtml(String(signal.decision || '—').replaceAll('_',' '))}</strong></div>
            <div><span>Higher timeframe</span><strong>${escapeHtml(displayInterval(signal.confluenceHigherInterval))}</strong></div>
            <div><span>Higher trend</span><strong>${signal.confluenceHigherTrendScore == null ? '—' : `${signal.confluenceHigherTrendScore}/25`}</strong></div>
            <div><span>Higher decision</span><strong>${escapeHtml(String(signal.confluenceHigherDecision || '—').replaceAll('_',' '))}</strong></div>
            <div><span>Entry allowed</span><strong>${signal.confluenceEntryAllowed === false ? 'NO' : 'YES'}</strong></div>
            <div><span>Evaluated at</span><strong>${dateTime(signal.confluenceEvaluatedAt || signal.generatedAt)}</strong></div>
        </div>
        <p>${escapeHtml(signal.confluenceExplanation || 'Confluence was not captured when this legacy signal was created.')}</p>
    </section>`;

    return `
        ${strategyFlowHtml}
        <section class="signal-decision-summary">
            <div class="decision-summary-main">
                <span class="badge ${decision.toLowerCase()}">${escapeHtml(decision.replaceAll('_', ' '))}</span>
                <div><strong>${signal.totalScore ?? 0}/100</strong><small>Final normalized score</small></div>
                <div><strong>${signal.rawScore ?? 0}/${signal.maximumAvailableScore ?? 85}</strong><small>Raw category score</small></div>
                <div><strong>${money(entryPrice)}</strong><small>Signal / entry price</small></div>
            </div>
            ${confirmations.badges}
            <div class="trade-plan-summary">
                <div class="trade-plan-level stop">
                    <span>Stop loss</span>
                    <strong>${money(signal.stopLoss)}</strong>
                    <small>${stopDistance == null ? 'Unavailable' : `${money(stopDistance)} below entry · ${stopPercent.toFixed(2)}% risk`}</small>
                </div>
                <div class="trade-plan-formula">
                    <span>How it was calculated</span>
                    <strong>${stopMultiplier == null ? 'Entry − (ATR × stop multiplier)' : `${money(entryPrice)} − (${money(atr)} × ${stopMultiplier.toFixed(2)})`}</strong>
                    <small>ATR14 adapts the stop distance to current volatility.</small>
                </div>
                <div class="trade-plan-level target">
                    <span>Take profit</span>
                    <strong>${money(signal.takeProfit)}</strong>
                    <small>${targetDistance == null ? 'Unavailable' : `${money(targetDistance)} above entry · ${targetPercent.toFixed(2)}% target`}</small>
                </div>
                <div class="trade-plan-formula">
                    <span>How it was calculated</span>
                    <strong>${targetMultiplier == null ? 'Entry + (ATR × target multiplier)' : `${money(entryPrice)} + (${money(atr)} × ${targetMultiplier.toFixed(2)})`}</strong>
                    <small>Risk / reward ${signal.riskRewardRatio == null ? '—' : `1 : ${Number(signal.riskRewardRatio).toFixed(2)}`}.</small>
                </div>
            </div>
        </section>
        ${strategyHtml}
        ${decisionPathHtml}
        ${confirmations.cards}
        ${confluenceHtml}
        ${btcRelationshipHtml}
        ${derivativesHtml}
        ${liquidityHtml}
        <section class="analysis-score-overview">
            <div><span>Trend</span><strong>${activeStrategy.trend?.score ?? b.movingAverages?.score ?? 0}<small>/${signal.strategyTrendMaximum ?? 25}</small></strong></div>
            <div><span>Volume</span><strong>${activeStrategy.volume?.score ?? b.bandsVolume?.score ?? 0}<small>/${signal.strategyVolumeMaximum ?? 20}</small></strong></div>
            <div><span>Momentum</span><strong>${activeStrategy.momentum?.score ?? b.momentum?.score ?? 0}<small>/${signal.strategyMomentumMaximum ?? 15}</small></strong></div>
            <div><span>Sentiment</span><strong>${signal.sentimentAvailable === false ? 'EXCLUDED' : `${activeStrategy.sentiment?.score ?? b.sentiment?.score ?? 0}<small>/${signal.strategySentimentMaximum ?? 15}</small>`}</strong></div>
            <div><span>Fundamentals</span><strong>${signal.fundamentalAvailable === false ? 'EXCLUDED' : `${activeStrategy.fundamentals?.score ?? b.fundamentals?.score ?? 0}<small>/${signal.strategyFundamentalMaximum ?? 10}</small>`}</strong></div>
        </section>
        <div class="indicator-key-list">
            <span>EMA20</span><span>EMA50</span><span>EMA200</span><span>SMA20</span><span>RSI</span><span>MACD</span><span>Bollinger Bands</span><span>ATR</span><span>Relative Volume</span><span>Volume SMA20</span>
        </div>
        <div class="signal-analysis-grid">
            ${category('Trend · Direction, Structure, Strength, Price Location', b.movingAverages)}
            ${category('Momentum · RSI14 and MACD', b.momentum)}
            ${category('Volume & Bands · Bollinger, Relative Volume, Volume SMA20', b.bandsVolume)}
            <section class="score-category">
                <div class="score-category-heading"><strong>Sentiment</strong><span>${b.sentiment?.score ?? 0}/${b.sentiment?.maximum ?? 15}</span></div>
                ${providers}
            </section>
            <section class="score-category">
                <div class="score-category-heading"><strong>Fundamentals</strong><span>${b.fundamentals?.score ?? 0}/${b.fundamentals?.maximum ?? 10}</span></div>
                <div class="score-progress"><span style="width:${Math.min(100, ((b.fundamentals?.score || 0) * 100) / Math.max(1, b.fundamentals?.maximum || 10))}%"></span></div>
                <div class="fundamental-final-score">
                    <span>Final Fundamental Score</span>
                    <strong>${b.fundamentals?.score ?? 0}/${b.fundamentals?.maximum ?? 10}</strong>
                </div>
                <div class="fundamental-risk">Risk: <strong>${escapeHtml(String(b.fundamentals?.riskLevel || 'UNKNOWN'))}</strong></div>
                ${(b.fundamentals?.components || []).map(c => `
                    <div class="score-component score-component-detailed">
                        <div>
                            <span>${escapeHtml(c.label || c.code || 'Fundamental metric')}</span>
                            ${c.status ? `<small>${escapeHtml(String(c.status))}</small>` : ''}
                            ${c.metric ? `<small class="score-metric">${escapeHtml(String(c.metric))}</small>` : ''}
                        </div>
                        <div class="score-component-value">
                            <strong>${c.score ?? 0}/${c.maximum ?? 2}</strong>
                        </div>
                    </div>`).join('') || '<div class="score-component"><span>Older signal: no detailed fundamental snapshot</span><strong>—</strong></div>'}
                ${fundamentalOwnershipSection(b.fundamentals?.ownership)}
            </section>
        </div>
        <section class="score-category atr-risk-card">
            <div class="score-category-heading"><strong>ATR14 Risk & Volatility</strong><span>${escapeHtml(String(signal.volatilityLevel || 'UNKNOWN'))}</span></div>
            <div class="score-component"><span>ATR14</span><strong>${signal.atr14 == null ? '—' : money(signal.atr14)}</strong></div>
            <div class="score-component"><span>ATR as % of price</span><strong>${signal.atrPercent == null ? '—' : `${Number(signal.atrPercent).toFixed(2)}%`}</strong></div>
            <div class="score-component"><span>Stop-loss formula</span><strong>${stopMultiplier == null ? 'Entry − (ATR × multiplier)' : `Entry − (${stopMultiplier.toFixed(2)} × ATR)`}</strong></div>
            <div class="score-component"><span>Calculated stop</span><strong>${money(signal.stopLoss)}</strong></div>
            <div class="score-component"><span>Take-profit formula</span><strong>${targetMultiplier == null ? 'Entry + (ATR × multiplier)' : `Entry + (${targetMultiplier.toFixed(2)} × ATR)`}</strong></div>
            <div class="score-component"><span>Calculated target</span><strong>${money(signal.takeProfit)}</strong></div>
            <div class="score-component"><span>Risk / Reward</span><strong>${signal.riskRewardRatio == null ? '—' : `1 : ${Number(signal.riskRewardRatio).toFixed(2)}`}</strong></div>
            <div class="score-component"><span>Distance from SMA20</span><strong>${signal.candleRangeAtrMultiple == null ? '—' : `${Number(signal.candleRangeAtrMultiple).toFixed(2)} ATR`}</strong></div>
            <div class="score-component"><span>Entry type</span><strong>${escapeHtml(String(signal.atrEntryType || (signal.atrOverextended ? 'WAIT_FOR_RETRACEMENT' : 'STANDARD_ENTRY')).replaceAll('_', ' '))}</strong></div>
            <div class="score-component"><span>Immediate entry</span><strong>${signal.atrImmediateEntryAllowed === false ? 'NO — WAIT FOR RETRACEMENT' : 'YES'}</strong></div>
            <div class="score-component"><span>Recommended position</span><strong>${signal.atrRecommendedPositionPercent == null ? '100%' : `${signal.atrRecommendedPositionPercent}%`}</strong></div>
            <div class="score-component"><span>Retracement entry reference</span><strong>${signal.atrRetracementEntryPrice == null ? '—' : money(signal.atrRetracementEntryPrice)}</strong></div>
            <small>${escapeHtml(signal.atrExplanation || 'ATR risk details are available for newly generated signals.')}</small>
        </section>
        <div class="signal-explanation"><strong>Why this signal:</strong> ${escapeHtml(signal.explanation || 'No explanation available.')}</div>`;
}

function keyConfirmationsHtml(breakdown) {
    const macd = breakdown?.momentum?.components?.find(component =>
        String(component?.label || '').toLowerCase().includes('macd'));
    const volume = breakdown?.bandsVolume?.components?.find(component => {
        const label = String(component?.label || '').toLowerCase();
        return label.includes('volume sma20') || label.includes('directional confirmation');
    });

    const classify = (type, component) => {
        if (!component) {
            return {
                tone: 'unavailable',
                icon: '○',
                label: type === 'macd' ? 'MACD unavailable' : 'Volume unavailable',
                summary: 'No confirmation snapshot is available for this older signal.',
                detail: 'Generate a new signal to capture this confirmation.'
            };
        }

        const status = String(component.status || '').toLowerCase();
        const score = Number(component.score || 0);
        const maximum = Math.max(1, Number(component.maximum || 1));
        const ratio = score / maximum;

        if (status.includes('bearish') || status.includes('does not confirm') || status.includes('falling')) {
            return {
                tone: 'bearish', icon: '↓',
                label: type === 'macd' ? 'Bearish MACD confirmation' : 'Volume not bullishly confirmed',
                summary: component.status,
                detail: component.metric || component.value || ''
            };
        }
        if (status.includes('strongly confirms') || status.includes('bullish macd confirmation') || ratio >= 0.75) {
            return {
                tone: 'bullish', icon: '✓',
                label: type === 'macd' ? 'Strong MACD confirmation' : 'Bullish volume confirmation',
                summary: component.status,
                detail: component.metric || component.value || ''
            };
        }
        if (status.includes('moderately') || status.includes('not confirmed') || status.includes('improving') || ratio > 0) {
            return {
                tone: 'weak', icon: '!',
                label: type === 'macd' ? 'Partial MACD confirmation' : 'Weak volume confirmation',
                summary: component.status,
                detail: component.metric || component.value || ''
            };
        }
        return {
            tone: 'neutral', icon: '—',
            label: type === 'macd' ? 'Neutral MACD' : 'Neutral volume',
            summary: component.status || 'No directional confirmation.',
            detail: component.metric || component.value || ''
        };
    };

    const macdState = classify('macd', macd);
    const volumeState = classify('volume', volume);
    const states = [volumeState, macdState];

    return {
        badges: `<div class="key-confirmation-badges">${states.map(state =>
            `<span class="confirmation-badge ${state.tone}"><b>${state.icon}</b>${escapeHtml(state.label)}</span>`
        ).join('')}</div>`,
        cards: `<section class="key-confirmations-section">
            <div class="key-confirmations-heading">
                <div><span>High-value evidence</span><h3>Key Confirmations</h3></div>
                <small>Volume must agree with price. MACD needs crossover and histogram confirmation.</small>
            </div>
            <div class="key-confirmation-grid">${states.map(state => `
                <article class="key-confirmation-card ${state.tone}">
                    <div class="confirmation-icon">${state.icon}</div>
                    <div>
                        <strong>${escapeHtml(state.label)}</strong>
                        <p>${escapeHtml(String(state.summary || ''))}</p>
                        ${state.detail ? `<small>${escapeHtml(String(state.detail))}</small>` : ''}
                    </div>
                </article>`).join('')}
            </div>
        </section>`
    };
}

function fundamentalOwnershipSection(ownership) {
    if (!ownership || Object.keys(ownership).length === 0) {
        return `<div class="ownership-section"><strong>Ownership & Supply Allocation</strong><small>No ownership allocation snapshot is available for this signal.</small></div>`;
    }

    const pct = value => value == null ? '—' : `${(Number(value) * 100).toFixed(2)}%`;
    const amount = value => value == null ? '—' : Number(value).toLocaleString(undefined, { maximumFractionDigits: 4 });

    return `
        <div class="ownership-section">
            <div class="ownership-heading">
                <strong>Ownership & Supply Allocation</strong>
                <span>${escapeHtml(String(ownership.referenceLabel || 'Supply reference'))}</span>
            </div>
            <div class="ownership-grid">
                <div><span>Public circulating</span><strong>${amount(ownership.circulatingSupply)}</strong><small>${pct(ownership.publicCirculatingRatio)} of reference supply</small></div>
                <div><span>Not circulating</span><strong>${amount(ownership.nonCirculatingSupply)}</strong><small>Not automatically company-owned</small></div>
                <div><span>Team allocation</span><strong>${amount(ownership.teamSupply)}</strong></div>
                <div><span>Treasury allocation</span><strong>${amount(ownership.treasurySupply)}</strong></div>
                <div><span>Private investors</span><strong>${amount(ownership.privateInvestorSupply)}</strong></div>
                <div><span>Locked supply</span><strong>${amount(ownership.lockedSupply)}</strong></div>
                <div class="ownership-total"><span>Known company/insider controlled</span><strong>${amount(ownership.knownCompanyControlledSupply)}</strong><small>${pct(ownership.knownCompanyControlledRatio)} of reference supply</small></div>
            </div>
            <small class="ownership-note">${escapeHtml(String(ownership.status || 'Ownership data is informational and is not double-counted in the final score.'))}</small>
        </div>`;
}

function formatHoldingTime(totalSeconds) {
    const seconds = Math.max(0, Number(totalSeconds || 0));
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (days > 0) return `${days}d ${hours}h`;
    if (hours > 0) return `${hours}h ${minutes}m`;
    return `${minutes}m`;
}

function signedMoney(v) {
    if (v === null || v === undefined) return '—';
    const number = Number(v);
    return `${number >= 0 ? '+' : '-'}$${moneyFormatter.format(Math.abs(number))}`;
}

function signedPercent(v) {
    if (v === null || v === undefined) return '—';
    const number = Number(v);
    return `${number >= 0 ? '+' : ''}${number.toFixed(3)}%`;
}

function renderOpenTrades(positions) {
    const container = el('open-trades');
    container.innerHTML = positions.length ? positions.map(p => {
        const pnl = Number(p.unrealizedPnl || 0);
        const pnlClass = pnl > 0 ? 'positive' : pnl < 0 ? 'negative' : '';
        const currentDecision = String(p.currentDecision || 'NO_SIGNAL');
        return `<article class="trade-card">
            <div class="trade-card-heading">
                <div><strong>${escapeHtml(p.symbol || '—')}</strong><small>Position #${p.id}</small></div>
                <span class="badge open">OPEN</span>
            </div>
            <div class="trade-metrics">
                <div><span>Entry</span><strong>${money(p.entryPrice)}</strong><small>${dateTime(p.openedAt)}</small></div>
                <div><span>Current price</span><strong>${money(p.currentPrice)}</strong><small>Latest closed candle</small></div>
                <div><span>Live P&amp;L</span><strong class="${pnlClass}">${signedMoney(p.unrealizedPnl)}</strong><small class="${pnlClass}">${signedPercent(p.pnlPercentage)}</small></div>
                <div><span>Holding time</span><strong>${formatHoldingTime(p.holdingSeconds)}</strong><small>Quantity ${value(p.quantity)}</small></div>
            </div>
            <div class="trade-signal-strip">
                <div><span>Entry signal</span><strong>${escapeHtml(String(p.entryDecision || '—').replaceAll('_',' '))} · ${p.entryScore ?? '—'}/100</strong></div>
                <div><span>Current AI signal</span><strong><span class="badge ${currentDecision.toLowerCase()}">${escapeHtml(currentDecision.replaceAll('_',' '))}</span> ${p.currentScore ?? '—'}/100</strong></div>
            </div>
            ${p.profitLockActive ? `<div class="profit-lock-banner active"><div><span>PROFIT LOCK ACTIVE</span><strong>${money(p.profitLockPrice)}</strong></div><div><span>Best TP progress</span><strong>${Number(p.profitLockProgressPercent || 0).toFixed(1)}%</strong></div><div><span>Highest price</span><strong>${money(p.highestPriceSinceEntry)}</strong></div><small>Protected profit is now managed by the trailing lock. The protected level can move up but never back down.</small></div>` : `<div class="profit-lock-banner waiting"><div><span>Profit Lock</span><strong>Waiting</strong></div><div><span>Best TP progress</span><strong>${p.profitLockProgressPercent == null ? '—' : `${Number(p.profitLockProgressPercent).toFixed(1)}%`}</strong></div><small>Activates after the configured TP-progress threshold is reached.</small></div>`}
            <div class="trade-levels">
                <span>Stop loss <strong>${money(p.stopLoss)}</strong><small>-${Number(p.stopLossPercent || 0).toFixed(2)}% · ${money(p.stopLossDistance)} risk/unit</small></span>
                <span>Take profit <strong>${money(p.takeProfit)}</strong><small>+${Number(p.takeProfitPercent || 0).toFixed(2)}% · ${money(p.takeProfitDistance)} reward/unit</small></span>
            </div>
            <section class="risk-logic-panel">
                <div class="risk-logic-heading"><strong>ATR stop-loss logic</strong><span>${escapeHtml(String(p.entryVolatilityLevel || 'UNKNOWN'))}</span></div>
                <div class="risk-logic-grid">
                    <div><span>ATR14 at entry</span><strong>${p.entryAtr14 == null ? '—' : money(p.entryAtr14)}</strong></div>
                    <div><span>ATR %</span><strong>${p.entryAtrPercent == null ? '—' : `${Number(p.entryAtrPercent).toFixed(2)}%`}</strong></div>
                    <div><span>Risk / Reward</span><strong>${p.entryRiskRewardRatio == null ? '—' : `1 : ${Number(p.entryRiskRewardRatio).toFixed(2)}`}</strong></div>
                    <div><span>Room to stop</span><strong>${p.currentToStopDistance == null ? '—' : money(p.currentToStopDistance)}</strong></div>
                    <div><span>Room to target</span><strong>${p.currentToTargetDistance == null ? '—' : money(p.currentToTargetDistance)}</strong></div>
                    <div><span>Trigger state</span><strong>${p.stopLossTriggered ? 'STOP HIT' : p.takeProfitTriggered ? 'TARGET HIT' : p.profitLockActive ? 'PROFIT LOCK ACTIVE' : 'ACTIVE'}</strong></div>
                    <div><span>Profit lock</span><strong>${p.profitLockPrice == null ? '—' : money(p.profitLockPrice)}</strong></div>
                    <div><span>Best TP progress</span><strong>${p.profitLockProgressPercent == null ? '—' : `${Number(p.profitLockProgressPercent).toFixed(1)}%`}</strong></div>
                </div>
                <small>${escapeHtml(p.riskLogic || 'ATR risk logic unavailable for this older position.')}</small>
            </section>
            <div class="trade-recommendation"><strong>What this means:</strong> ${escapeHtml(p.recommendation || 'Waiting for the next signal.')}</div>
            <details><summary>Why the position was opened</summary><p>${escapeHtml(p.entryReason || 'No entry explanation stored.')}</p></details>
        </article>`;
    }).join('') : `<div class="empty trade-empty">
        No open wallet position for this symbol. A validated BUY signal can open one wallet position; repeated BUY signals will not create duplicates.
    </div>`;
}

function renderTradeHistory(positions) {
    el('trade-history-body').innerHTML = positions.length ? positions.map(p => {
        const pnl = Number(p.realizedPnl || 0);
        const pnlClass = pnl > 0 ? 'positive' : pnl < 0 ? 'negative' : '';
        const predictionClass = p.predictionResult === 'SUCCESS' ? 'buy' : p.predictionResult === 'FAILED' ? 'reject' : 'neutral';
        return `<tr>
            <td><strong class="trade-id">#${escapeHtml(p.id ?? '—')}</strong></td>
            <td><small class="signal-id-pair">#${escapeHtml(p.entrySignalId ?? '—')} → #${escapeHtml(p.exitSignalId ?? '—')}</small></td>
            <td><strong>${escapeHtml(p.symbol || '—')}</strong></td>
            <td class="trade-action-cell"><span class="badge buy">BUY ↑</span><span class="trade-action-arrow">→</span><span class="badge sell">SELL ↓</span></td>
            <td><span class="interval-chip">${escapeHtml(p.entryInterval || '—')}</span><span class="trade-action-arrow">→</span><span class="interval-chip">${escapeHtml(p.exitInterval || p.entryInterval || '—')}</span></td>
            <td>${preciseDateTime(p.openedAt)}</td>
            <td>${preciseDateTime(p.closedAt)}</td>
            <td>${money(p.entryPrice)}<small>${escapeHtml(String(p.entryDecision || '—'))} ${p.entryScore ?? '—'}/100</small></td>
            <td>${money(p.exitPrice)}<small>${escapeHtml(String(p.exitDecision || p.closeReason || '—'))} ${p.exitScore ?? '—'}/100</small></td>
            <td>${value(p.quantity)}</td>
            <td class="${pnlClass}"><strong>${signedMoney(p.realizedPnl)}</strong><small>${signedPercent(p.pnlPercentage)}</small></td>
            <td title="${escapeHtml(p.exitReason || '')}">${escapeHtml(String(p.closeReason || p.status || '—').replaceAll('_',' '))}</td>
            <td><span class="badge ${predictionClass}">${escapeHtml(p.predictionResult || 'PENDING')}</span></td>
            <td><button type="button" class="replay-button" onclick="openTradeReplay(${p.id})">Inspect</button></td>
        </tr>`;
    }).join('') : '<tr><td colspan="14" class="empty">No completed wallet trades yet. Closed wallet trades will appear here with their final P&amp;L and result.</td></tr>';
}


function renderSentiment(sentiment, providerStatuses, systemStatus) {
    const score = Number(sentiment.weightedScore || 0);
    const clamped = Math.max(-1, Math.min(1, score));
    const label = sentiment.label || 'NEUTRAL';
    const masterEnabled = Boolean(systemStatus.enabled);
    const enabledProviders = providerStatuses.filter(p => p.enabled);
    const totalConfiguredWeight = enabledProviders.reduce((sum, p) => sum + Number(p.weight || 0), 0);
    const unhealthyProviders = enabledProviders.filter(p => ['STALE', 'DOWN'].includes(p.healthStatus));
    const healthAlert = el('sentiment-health-alert');
    if (unhealthyProviders.length) {
        healthAlert.innerHTML = `<strong>Provider alert</strong><span>${unhealthyProviders.map(p => `${escapeHtml(p.displayName)} is ${escapeHtml(p.healthStatus)}: ${escapeHtml(p.healthMessage || 'No recent successful collection')}`).join('<br>')}</span><small>Stale and down providers are excluded from the evaluated sentiment score.</small>`;
        healthAlert.classList.remove('hidden');
    } else {
        healthAlert.classList.add('hidden');
        healthAlert.innerHTML = '';
    }

    el('sentiment-master-status').textContent = masterEnabled ? 'ENABLED' : 'DISABLED';
    el('sentiment-master-status').className = `badge ${masterEnabled ? 'buy' : 'reject'}`;
    el('sentiment-master-text').textContent = masterEnabled ? 'Enabled' : 'Disabled';
    el('sentiment-master-text').className = masterEnabled ? 'positive' : 'negative';
    el('sentiment-master-message').textContent = systemStatus.message || '—';
    el('sentiment-enabled-count').textContent = `${enabledProviders.length} / ${providerStatuses.length}`;
    el('sentiment-total-weight').textContent = totalConfiguredWeight.toFixed(3);

    el('sentiment-score').textContent = `${score >= 0 ? '+' : ''}${score.toFixed(3)}`;
    el('sentiment-score').className = score > 0 ? 'positive' : score < 0 ? 'negative' : '';
    el('sentiment-label').textContent = String(label).replaceAll('_', ' ');
    el('sentiment-label').className = `badge ${label.toLowerCase().includes('bullish') ? 'buy' : label.toLowerCase().includes('bearish') ? 'reject' : 'neutral'}`;
    el('sentiment-marker').style.left = `${(clamped + 1) * 50}%`;

    el('collect-sentiment-button').disabled = !masterEnabled || enabledProviders.length === 0;
    el('collect-sentiment-button').title = !masterEnabled
        ? 'Set SENTIMENT_ENABLED=true to collect sentiment'
        : enabledProviders.length === 0 ? 'Enable at least one provider' : '';

    el('sentiment-provider-body').innerHTML = providerStatuses.length ? providerStatuses.map(p => {
        const providerScore = Number(p.score || 0);
        const providerWeight = Number(p.weight || 0);
        const normalizedWeight = totalConfiguredWeight > 0 && p.enabled ? providerWeight / totalConfiguredWeight : 0;
        const evaluatedContribution = masterEnabled && p.enabled && p.contributing ? providerScore * normalizedWeight : 0;
        const keyBadge = p.apiKeyConfigured
            ? '<span class="badge buy">Ready</span>'
            : `<span class="badge reject" title="Set ${escapeHtml(p.apiKeyEnvironmentVariable || '')}">Missing</span>`;
        return `
        <tr class="${p.enabled ? '' : 'provider-disabled'}">
            <td><strong>${escapeHtml(p.displayName)}</strong><small class="provider-code">${escapeHtml(p.provider)}</small></td>
            <td><label class="switch"><input type="checkbox" ${p.enabled ? 'checked' : ''} onchange="updateSentimentProvider('${escapeHtml(p.provider)}', {enabled:this.checked})"><span></span></label></td>
            <td>${keyBadge}</td>
            <td><input class="provider-number" type="number" min="0" step="0.01" value="${providerWeight}" onchange="updateSentimentProvider('${escapeHtml(p.provider)}', {weight:Number(this.value)})"></td>
            <td class="${providerScore > 0 ? 'positive' : providerScore < 0 ? 'negative' : ''}"><strong>${providerScore >= 0 ? '+' : ''}${providerScore.toFixed(3)}</strong></td>
            <td class="${evaluatedContribution > 0 ? 'positive' : evaluatedContribution < 0 ? 'negative' : ''}">${evaluatedContribution >= 0 ? '+' : ''}${evaluatedContribution.toFixed(3)}</td>
            <td>${Number(p.confidence || 0).toFixed(3)}</td>
            <td>${p.sampleCount || 0}</td>
            <td><input class="provider-number interval-input" type="number" min="60" step="60" value="${Number(p.intervalSeconds || 300)}" onchange="updateSentimentProvider('${escapeHtml(p.provider)}', {collectionIntervalSeconds:Number(this.value)})"> s</td>
            <td>${dateTime(p.lastCollectionAt)}</td>
            <td title="${escapeHtml(p.healthMessage || '')}"><span class="badge ${p.healthStatus === 'HEALTHY' ? 'buy' : p.healthStatus === 'DEGRADED' ? 'neutral' : p.healthStatus === 'DISABLED' ? 'neutral' : 'reject'}">${escapeHtml(p.healthStatus || 'UNKNOWN')}</span><small class="provider-code">${p.hoursSinceSuccess >= 0 ? `${p.hoursSinceSuccess}h since success` : 'No success yet'}</small></td>
            <td title="${escapeHtml(p.message || '')}"><span class="badge ${p.status === 'SUCCESS' ? 'buy' : p.status === 'NEVER_RUN' ? 'neutral' : 'reject'}">${escapeHtml(p.status || 'UNKNOWN')}</span></td>
            <td><button type="button" class="small-button" onclick="collectSingleSentimentProvider('${escapeHtml(p.provider)}')" ${!masterEnabled || !p.enabled ? 'disabled' : ''}>Run</button></td>
        </tr>`;
    }).join('') : '<tr><td colspan="13" class="empty">No provider configuration found. Confirm Flyway V5 has run.</td></tr>';

    const recent = sentiment.recentSignals || [];
    el('sentiment-samples').textContent = masterEnabled
        ? `${sentiment.sampleCount || 0} samples in the active window. Provider scores are averaged first, then normalized weights produce the combined score.`
        : 'Sentiment is globally disabled. AnalysisService currently evaluates sentiment as 0.';
    el('sentiment-body').innerHTML = recent.length ? recent.map(item => `<tr><td>${dateTime(item.observedAt)}</td><td>${escapeHtml(item.source)}</td><td>${Number(item.score).toFixed(3)}</td><td>${Number(item.confidence).toFixed(3)}</td><td title="${escapeHtml(item.summary || '')}">${escapeHtml(item.summary || '—')}</td></tr>`).join('') : '<tr><td colspan="5" class="empty">No sentiment samples yet.</td></tr>';
}

async function updateSentimentProvider(provider, patch) {
    try {
        const response = await fetch(`/api/sentiment/providers/${encodeURIComponent(provider)}`, {
            method: 'PATCH', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(patch)
        });
        if (!response.ok) throw new Error(`Provider update returned ${response.status}`);
        await refreshDashboard();
    } catch (error) {
        el('sentiment-collect-feedback').textContent = error.message;
    }
}

async function collectSingleSentimentProvider(provider) {
    el('sentiment-collect-feedback').textContent = `Collecting ${provider}...`;
    try {
        const response = await fetch(`/api/sentiment/providers/${encodeURIComponent(provider)}/collect`, {method:'POST'});
        if (!response.ok) throw new Error(`Provider collection returned ${response.status}`);
        const result = await response.json();
        el('sentiment-collect-feedback').textContent = Object.entries(result).map(([k,v]) => `${k}: ${v}`).join(' | ');
        await refreshDashboard();
    } catch (error) {
        el('sentiment-collect-feedback').textContent = error.message;
    }
}

async function analyzeSentiment() {
    const text = el('sentiment-text').value.trim();
    const source = el('sentiment-source').value.trim();
    const symbol = el('symbol-select').value;
    if (!text || !source) {
        el('sentiment-feedback').textContent = 'Source and text are required.';
        return;
    }
    const button = el('analyze-sentiment-button');
    button.disabled = true;
    el('sentiment-feedback').textContent = 'Analyzing...';
    try {
        const response = await fetch('/api/sentiment/analyze', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ symbol, source, text })
        });
        if (!response.ok) throw new Error(`Sentiment API returned ${response.status}`);
        const saved = await response.json();
        el('sentiment-feedback').textContent = `Saved ${Number(saved.score).toFixed(3)} sentiment with ${Number(saved.confidence).toFixed(3)} confidence.`;
        el('sentiment-text').value = '';
        await refreshDashboard();
    } catch (error) {
        el('sentiment-feedback').textContent = error.message;
    } finally {
        button.disabled = false;
    }
}

async function collectSentimentProviders() {
    const button = el('collect-sentiment-button');
    button.disabled = true;
    el('sentiment-collect-feedback').textContent = 'Collecting enabled providers...';
    try {
        const response = await fetch('/api/sentiment/collect', { method: 'POST' });
        if (!response.ok) throw new Error(`Collection API returned ${response.status}`);
        const result = await response.json();
        el('sentiment-collect-feedback').textContent = Object.entries(result)
            .map(([provider, status]) => `${provider}: ${status}`)
            .join(' | ');
        await refreshDashboard();
    } catch (error) {
        el('sentiment-collect-feedback').textContent = error.message;
    } finally {
        button.disabled = false;
    }
}

function updateConnection(online) {
    // FIX-049: the sidebar connection-status widget was intentionally removed.
    // Keep the refresh hook as a no-op so existing dashboard health calls stay safe.
}

function displayInterval(value, fallback = 'Unavailable at creation') {
    const text = value == null ? '' : String(value).trim();
    return DASHBOARD_INTERVAL_LABELS[text.toLowerCase()] || text || fallback;
}

function escapeHtml(text) {
    return String(text).replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char]));
}


function setupCollapsibleSections() {
    document.querySelectorAll('[data-collapsible]').forEach(container => {
        const directToggle = Array.from(container.children)
            .flatMap(child => Array.from(child.querySelectorAll ? child.querySelectorAll('.collapse-toggle') : []))
            .find(button => button.closest('[data-collapsible]') === container);
        const toggle = directToggle || container.querySelector('.collapse-toggle');
        if (!toggle) return;

        const sync = () => {
            const collapsed = container.classList.contains('is-collapsed');
            toggle.setAttribute('aria-expanded', String(!collapsed));
            toggle.textContent = collapsed ? 'Expand' : 'Collapse';
        };

        toggle.addEventListener('click', event => {
            event.preventDefault();
            event.stopPropagation();
            container.classList.toggle('is-collapsed');
            sync();
        });
        sync();
    });
}

function setupSidebar() {
    const sidebar = el('dashboard-sidebar');
    const toggle = el('sidebar-toggle');
    if (!sidebar || !toggle) return;
    const stored = localStorage.getItem('cryptoSidebarCollapsed') === 'true';
    sidebar.classList.toggle('collapsed', stored);
    document.body.classList.toggle('sidebar-collapsed', stored);
    const sync = () => {
        const collapsed = sidebar.classList.contains('collapsed');
        toggle.textContent = collapsed ? '›' : '‹';
        toggle.setAttribute('aria-label', collapsed ? 'Expand navigation' : 'Collapse navigation');
        document.body.classList.toggle('sidebar-collapsed', collapsed);
        localStorage.setItem('cryptoSidebarCollapsed', String(collapsed));
    };
    toggle.addEventListener('click', () => {
        if (window.innerWidth <= 760) {
            sidebar.classList.toggle('mobile-open');
            toggle.textContent = sidebar.classList.contains('mobile-open') ? '×' : '☰';
            return;
        }
        sidebar.classList.toggle('collapsed');
        sync();
    });
    if (window.innerWidth <= 760) {
        sidebar.classList.remove('collapsed');
        document.body.classList.remove('sidebar-collapsed');
        toggle.textContent = '☰';
    } else {
        sync();
    }
}

const signalEvidencePeriod = el('signal-evidence-period');
const signalEvidenceMode = el('signal-evidence-mode');
const signalEvidenceRefresh = el('signal-evidence-refresh');
// Filters are intentionally applied on Load / the dedicated auto-refresh timer. This
// keeps the table predictable while the user is choosing multiple filter values.
if (signalEvidenceRefresh) signalEvidenceRefresh.addEventListener('click', () => refreshSignalEvidence(true));
if (signalEvidencePeriod) signalEvidencePeriod.addEventListener('change', () => refreshSignalEvidence(true));
if (signalEvidenceMode) signalEvidenceMode.addEventListener('change', () => refreshSignalEvidence(true));
configureSignalEvidenceRefreshTimer();

el('refresh-button').addEventListener('click', refreshDashboard);
el('analyze-sentiment-button').addEventListener('click', analyzeSentiment);
el('collect-sentiment-button').addEventListener('click', collectSentimentProviders);
const aiPeriodSelect = el('ai-period-select');
if (aiPeriodSelect) {
    const allowedPeriods = ['ALL_TIME','TODAY','LAST_24_HOURS','LAST_7_DAYS','LAST_30_DAYS'];
    aiPeriodSelect.value = allowedPeriods.includes(aiPerformancePeriod) ? aiPerformancePeriod : 'ALL_TIME';
    aiPerformancePeriod = aiPeriodSelect.value;
    aiPeriodSelect.addEventListener('change', () => {
        aiPerformancePeriod = aiPeriodSelect.value;
        localStorage.setItem('cryptoAiPerformancePeriod', aiPerformancePeriod);
        void refreshExecutionIntelligence();
    });
}

el('symbol-select').addEventListener('change', refreshDashboardForSelection);
el('interval-select').addEventListener('change', refreshDashboardForSelection);
setupCollapsibleSections();
setupSidebar();
(async () => {
    await loadSymbols();
    applyDashboardDeepLinkSelection();
    await refreshDashboard();
    if (debugMoveFocus) {
        window.requestAnimationFrame(() => {
            const marketSection = el('market');
            if (marketSection) marketSection.scrollIntoView({behavior: 'smooth', block: 'start'});
        });
    }
})();


async function openTradeReplay(positionId) {
    const dialog = el('trade-replay-dialog');
    const content = el('trade-replay-content');
    content.innerHTML = '<div class="empty">Loading trade lifecycle…</div>';
    dialog.showModal();
    try {
        const response = await fetch(`/api/paper-trades/${positionId}/replay`);
        if (!response.ok) throw new Error(`Replay request failed (${response.status})`);
        const replay = await response.json();
        renderTradeReplay(replay);
    } catch (error) {
        content.innerHTML = `<div class="error-panel">${escapeHtml(error.message || 'Unable to load trade replay.')}</div>`;
    }
}

function closeTradeReplay() {
    const dialog = el('trade-replay-dialog');
    if (dialog && dialog.open) dialog.close();
}

function renderTradeReplay(replay) {
    const p = replay.position || {};
    el('trade-replay-title').textContent = `${p.symbol || 'Trade'} #${p.id || ''} · Multi-timeframe inspector`;

    const pnl = Number(p.realizedPnl || 0);
    const pnlClass = pnl > 0 ? 'positive' : pnl < 0 ? 'negative' : '';
    const after = replay.afterExit;
    const allSignals = replay.timeline || [];
    const advice = replay.positionAdvice || [];
    const candles = replay.candles || [];

    const actionable = signal => {
        const decision = String(signal.decision || '').toUpperCase();
        return decision === 'BUY' || decision === 'STRONG_BUY' || decision === 'SELL' || decision === 'STRONG_SELL';
    };
    const actionableSignals = allSignals.filter(actionable);
    const hiddenSignals = allSignals.length - actionableSignals.length;
    const intervals = ['1m', '5m', '1h'];

    const intervalSummary = intervals.map(interval => {
        const rows = allSignals.filter(signal => String(signal.interval || '').toLowerCase() === interval);
        const latest = rows.length ? rows[rows.length - 1] : null;
        const firstBuy = rows.find(signal => String(signal.decision || '').toUpperCase() === 'BUY');
        const firstSell = rows.find(signal => ['SELL', 'STRONG_SELL'].includes(String(signal.decision || '').toUpperCase()));
        return `<article class="timeframe-card">
            <div class="timeframe-card-heading"><strong>${interval}</strong>${latest ? `<span class="badge ${String(latest.decision || 'neutral').toLowerCase()}">${escapeHtml(String(latest.decision || '—').replaceAll('_',' '))}</span>` : '<span class="badge neutral">NO DATA</span>'}</div>
            <div class="timeframe-metrics">
                <span>Latest score <b>${latest?.totalScore ?? '—'}/100</b></span>
                <span>Latest price <b>${latest ? money(latest.price) : '—'}</b></span>
                <span>First BUY <b>${firstBuy ? dateTime(firstBuy.generatedAt) : '—'}</b></span>
                <span>First SELL <b>${firstSell ? dateTime(firstSell.generatedAt) : '—'}</b></span>
            </div>
        </article>`;
    }).join('');

    const lifecycle = actionableSignals.map(s => `<tr>
        <td>${dateTime(s.generatedAt)}</td>
        <td><strong>${escapeHtml(s.interval || '—')}</strong></td>
        <td>${money(s.price)}</td>
        <td>${escapeHtml(String(s.originalDecision || '—').replaceAll('_',' '))}</td>
        <td><span class="badge ${String(s.decision || 'neutral').toLowerCase()}">${escapeHtml(String(s.decision || '—').replaceAll('_',' '))}</span></td>
        <td>${s.totalScore ?? '—'}/100</td>
        <td>${s.trendScore ?? '—'}/${s.volumeScore ?? '—'}/${s.momentumScore ?? '—'}</td>
        <td>${escapeHtml(String(s.confluenceStatus || '—').replaceAll('_',' '))}</td>
    </tr>`).join('');

    const adviceRows = advice.length ? advice.map(a => `<tr>
        <td>${dateTime(a.analyzedAt)}</td><td>${escapeHtml(a.interval || '—')}</td><td>${money(a.currentPrice)}</td>
        <td>${signedPercent(a.unrealizedPnlPercent)}</td><td>${a.exitScore}/25</td>
        <td><span class="badge ${String(a.recommendation || '').toLowerCase()}">${escapeHtml(a.recommendation || '—')}</span></td>
        <td>${escapeHtml(a.explanation || '')}</td>
    </tr>`).join('') : '<tr><td colspan="7" class="empty">No position-manager advice existed during this trade.</td></tr>';

    const candleHigh = candles.length ? Math.max(...candles.map(c => Number(c.high))) : null;
    const candleLow = candles.length ? Math.min(...candles.map(c => Number(c.low))) : null;

    const closeReason = String(p.closeReason || p.status || '—').replaceAll('_',' ');
    const lossExplanation = p.closeReason === 'STOP_LOSS'
        ? `Price reached the stored stop-loss before the take-profit target or an executable SELL. The wallet entered at ${money(p.entryPrice)} and closed at ${money(p.exitPrice)}.`
        : (p.exitReason || 'The position closed below its entry price.');

    el('trade-replay-content').innerHTML = `
        <div class="replay-summary-grid">
            <div><span>Entry</span><strong>${money(p.entryPrice)}</strong><small>${dateTime(p.openedAt)}</small></div>
            <div><span>Exit</span><strong>${money(p.exitPrice)}</strong><small>${dateTime(p.closedAt)}</small></div>
            <div><span>Result</span><strong class="${pnlClass}">${signedMoney(p.realizedPnl)}</strong><small class="${pnlClass}">${signedPercent(p.pnlPercent)}</small></div>
            <div><span>Closed by</span><strong>${escapeHtml(closeReason)}</strong><small>${escapeHtml(p.exitReason || '')}</small></div>
            <div><span>Stop loss</span><strong>${money(p.stopLoss)}</strong><small>Mechanical protection</small></div>
            <div><span>Take profit</span><strong>${money(p.takeProfit)}</strong><small>Configured target</small></div>
        </div>

        <section class="trade-inspector-main">
            <div>
                <p class="eyebrow">MULTI-TIMEFRAME VIEW</p>
                <h3>What 1m, 5m and 1h were saying</h3>
            </div>
            <div class="timeframe-card-grid">${intervalSummary}</div>
            <p class="inspector-note">The dashboard and the main timeline show actionable BUY/SELL signals only. ${hiddenSignals} NEUTRAL/WATCH signal${hiddenSignals === 1 ? '' : 's'} were hidden to keep the review clear.</p>
        </section>

        <section class="replay-verdict-panel">
            <strong>Why this trade closed</strong>
            <p>${escapeHtml(lossExplanation)}</p>
            <div class="replay-range"><span>Observed high <b>${candleHigh == null ? '—' : money(candleHigh)}</b></span><span>Observed low <b>${candleLow == null ? '—' : money(candleLow)}</b></span></div>
            ${after ? `<p><strong>After exit (${after.minutesObserved} min):</strong> high ${money(after.highestPrice)} (${signedPercent(after.highestMovePercent)}), low ${money(after.lowestPrice)} (${signedPercent(after.lowestMovePercent)}). ${escapeHtml(after.verdict || '')}</p>` : '<p>Post-exit candle evidence is not available yet.</p>'}
        </section>

        <details open><summary>Entry thesis</summary><p>${escapeHtml(p.entryReason || replay.entrySignal?.explanation || 'No stored entry explanation.')}</p></details>
        <details open><summary>Actionable decision timeline (${actionableSignals.length})</summary>
            <div class="table-wrap"><table><thead><tr><th>Time</th><th>Interval</th><th>Price</th><th>Original</th><th>Final</th><th>Score</th><th>T/V/M</th><th>Confluence</th></tr></thead><tbody>${lifecycle || '<tr><td colspan="8" class="empty">No actionable BUY or SELL signals were generated during this trade.</td></tr>'}</tbody></table></div>
        </details>
        <details><summary>Position Manager timeline (${advice.length})</summary>
            <div class="table-wrap"><table><thead><tr><th>Time</th><th>Interval</th><th>Price</th><th>P/L</th><th>Exit score</th><th>Advice</th><th>Reason</th></tr></thead><tbody>${adviceRows}</tbody></table></div>
        </details>`;
}


window.addEventListener('resize', syncDashboardHeaderOffset);
window.addEventListener('load', () => {
    syncDashboardHeaderOffset();
    // FIX-092C: Bind only the stable Bollinger/ATR presentation toggles.
    bindChartOverlayControls();
    const closeButton = el('execution-marker-close');
    if (closeButton) closeButton.addEventListener('click', () => el('execution-marker-dialog').close());
    const positionsKpi = el('active-positions-kpi');
    if (positionsKpi) positionsKpi.addEventListener('click', openActivePositionsModal);
    const positionsClose = el('active-positions-close');
    if (positionsClose) positionsClose.addEventListener('click', closeActivePositionsModal);
    document.querySelectorAll('[data-close-active-positions]').forEach(node => node.addEventListener('click', closeActivePositionsModal));
    document.addEventListener('keydown', event => { if (event.key === 'Escape') closeActivePositionsModal(); });
    document.querySelectorAll('[data-pipeline-filter]').forEach(node => node.addEventListener('click', () => {
        const center = el('positions');
        if (center) center.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }));
});
