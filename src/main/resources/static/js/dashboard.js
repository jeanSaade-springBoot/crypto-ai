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
let dashboardRefreshTimer;
const numberFormatter = new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 });
const moneyFormatter = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 8 });

const el = id => document.getElementById(id);
const value = v => v === null || v === undefined || v === '' ? '—' : numberFormatter.format(Number(v));
const money = v => v === null || v === undefined ? '—' : '$' + moneyFormatter.format(Number(v));
const dateTime = v => v ? new Date(v).toLocaleString() : '—';
const openSignalAnalysisIds = new Set();
let pinnedSignalId = localStorage.getItem('cryptoPinnedSignalId');

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
    const symbol = el('symbol-select').value;
    const interval = el('interval-select').value;
    el('refresh-button').disabled = true;
    try {
        const [response, providerResponse, sentimentStatusResponse, walletResponse] = await Promise.all([
            fetch(`/api/dashboard/overview?symbol=${encodeURIComponent(symbol)}&interval=${encodeURIComponent(interval)}`),
            fetch(`/api/sentiment/providers/${encodeURIComponent(symbol)}`),
            fetch('/api/sentiment/status'),
            fetch('/api/wallet')
        ]);
        if (!response.ok) throw new Error(`Dashboard API returned ${response.status}`);
        const data = await response.json();
        data.sentimentProviderStatuses = providerResponse.ok ? await providerResponse.json() : [];
        data.sentimentSystemStatus = sentimentStatusResponse.ok
            ? await sentimentStatusResponse.json()
            : { enabled: false, message: 'Could not read sentiment master status' };
        data.wallet = walletResponse.ok ? await walletResponse.json() : {};
        const intervalChanged = applyConfiguredDashboardIntervals(data.wallet.settings || {});
        if (intervalChanged) {
            window.setTimeout(refreshDashboard, 0);
            return;
        }
        updateConnection(true);
        el('error-banner').classList.add('hidden');
        render(data);
    } catch (error) {
        updateConnection(false);
        el('error-banner').textContent = error.message;
        el('error-banner').classList.remove('hidden');
    } finally {
        el('refresh-button').disabled = false;
    }
}

function render(data) {
    const s = data.summary;
    el('latest-price').textContent = money(s.latestPrice);
    const change = Number(s.priceChangePercent || 0);
    el('price-change').textContent = `${change >= 0 ? '+' : ''}${change.toFixed(3)}% from previous candle`;
    el('price-change').className = change >= 0 ? 'positive' : 'negative';
    el('candle-count').textContent = s.closedCandleCount;
    if (data.displayOnlyInterval) {
        el('history-progress').textContent = 'Derived from closed 1h candles';
        el('history-bar').style.width = s.closedCandleCount > 0 ? '100%' : '0%';
        el('latest-decision').textContent = 'DISPLAY ONLY';
        el('latest-score').textContent = 'Does not participate in BUY / SELL decisions';
    } else {
        el('history-progress').textContent = `${Math.min(s.closedCandleCount, s.minimumCandles)} / ${s.minimumCandles} required`;
        el('history-bar').style.width = `${Math.min(100, (s.closedCandleCount / s.minimumCandles) * 100)}%`;
        el('latest-decision').textContent = String(s.latestDecision).replace('_', ' ');
        el('latest-score').textContent = s.latestScore === null ? 'Analysis not ready' : `Score ${s.latestScore}`;
    }
    el('open-positions').textContent = s.openPositions;
    el('last-updated').textContent = `Updated ${dateTime(data.updatedAt)}`;
    el('market-subtitle').textContent = `${data.symbol} · ${displayInterval(data.interval)}${data.displayOnlyInterval ? ' · display only' : ''}`;
    renderPortfolio(data.wallet || {});
    renderTradePerformance((data.wallet || {}).tradePerformance || {});
    renderPipeline(data.pipeline);
    renderScoreDiagnostics(data.scoreDiagnostics || {});
    renderIndicators(data.indicator || {});
    renderSentiment(data.sentiment || {}, data.sentimentProviderStatuses || [], data.sentimentSystemStatus || {});
    renderSchedules(data.schedule || {});
    applyDashboardRefreshSchedule(data.schedule || {});
    renderCharts(data.candles || []);
    renderSignals(data.signals || [], data.displayOnlyInterval);
    renderOpenTrades(data.openPositions || []);
    renderTradeHistory(data.closedPositions || []);
}


function renderTradePerformance(performance) {
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

function renderPortfolio(wallet) {
    const pnl = Number(wallet.totalPnlUsdt || 0);
    const status = wallet.portfolioStatus || 'NOT STARTED';
    el('portfolio-status').textContent = status;
    el('portfolio-status').className = status === 'WINNING' ? 'positive' : status === 'LOSING' ? 'negative' : '';
    el('portfolio-return').textContent = `${Number(wallet.totalReturnPercent || 0).toFixed(2)}% since start`;
    el('portfolio-value').textContent = money(wallet.portfolioValueUsdt);
    el('portfolio-invested').textContent = `Net invested ${money(wallet.netInvestedUsdt)}`;
    el('portfolio-pnl').textContent = `${pnl >= 0 ? '+' : ''}${money(pnl)}`;
    el('portfolio-pnl').className = pnl >= 0 ? 'positive' : 'negative';
    const day = Number(wallet.change24hUsdt || 0);
    el('portfolio-change24h').textContent = `24h ${day >= 0 ? '+' : ''}${money(day)}`;
    el('portfolio-usdt').textContent = money(wallet.availableUsdt);
    const settings = wallet.settings || {};
    const daily = wallet.dailyTrading || {};
    el('portfolio-daily-budget').textContent = money(daily.dailyTradeBudgetUsdt);
    const maximumPositions = Number(daily.maximumNewPositions ?? settings.maximumDailyNewPositions ?? 0);
    el('portfolio-daily-buys').textContent = maximumPositions === 0
        ? `${Number(daily.executedBuys || 0)} / Unlimited new positions`
        : `${Number(daily.executedBuys || 0)} / ${maximumPositions} new positions`;
    const holdings = (wallet.assets || []).filter(a => Number(a.quantity || 0) > 0);
    el('portfolio-holdings').innerHTML = holdings.length ? holdings.map(a => {
        const assetPnl = Number(a.unrealizedPnlUsdt || 0);
        return `<div class="portfolio-asset"><strong>${escapeHtml(a.symbol)}</strong><span>${value(a.quantity)}</span><small class="${assetPnl >= 0 ? 'positive' : 'negative'}">${a.symbol === 'USDT' ? money(a.currentValueUsdt) : `${assetPnl >= 0 ? '+' : ''}${money(assetPnl)}`}</small></div>`;
    }).join('') : '<span class="empty">No wallet assets yet.</span>';
}


function renderScoreDiagnostics(diagnostics) {
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
    const groups = schedule.groups || [];
    el('schedule-groups').innerHTML = groups.length ? groups.map(group => `
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

function renderCharts(candles) {
    const candleSeries = candles.map(c => ({ x: new Date(c.time), y: [Number(c.open), Number(c.high), Number(c.low), Number(c.close)] }));
    const volumeSeries = candles.map(c => ({ x: new Date(c.time), y: Number(c.volume) }));
    const common = { chart: { background: 'transparent', foreColor: '#8da2b1', toolbar: { show: false }, animations: { enabled: false } }, theme: { mode: 'dark' }, grid: { borderColor: '#203342' }, xaxis: { type: 'datetime' }, noData: { text: 'Waiting for closed candles' } };
    if (!candleChart) {
        candleChart = new ApexCharts(el('candlestick-chart'), { ...common, chart: { ...common.chart, type: 'candlestick', height: 390 }, series: [{ name: 'Price', data: candleSeries }], yaxis: { tooltip: { enabled: true }, decimalsInFloat: 4 }, plotOptions: { candlestick: { colors: { upward: '#39d98a', downward: '#ff6b72' } } } });
        candleChart.render();
        volumeChart = new ApexCharts(el('volume-chart'), { ...common, chart: { ...common.chart, type: 'bar', height: 150 }, series: [{ name: 'Volume', data: volumeSeries }], dataLabels: { enabled: false }, yaxis: { labels: { formatter: v => Number(v).toLocaleString(undefined, { notation: 'compact' }) } } });
        volumeChart.render();
    } else {
        candleChart.updateSeries([{ name: 'Price', data: candleSeries }]);
        volumeChart.updateSeries([{ name: 'Volume', data: volumeSeries }]);
    }
}

function renderSignals(signals, displayOnlyInterval = false) {
    const body = el('signals-body');
    const actionableSignals = (signals || []).filter(signal => {
        const decision = String(signal.decision || '').toUpperCase();
        return decision === 'BUY' || decision === 'SELL' || decision === 'STRONG_SELL';
    });

    if (!actionableSignals.length) {
        body.innerHTML = displayOnlyInterval
            ? '<tr><td colspan="10" class="empty">4h and 1D are display-only market views. Trading signals remain generated only on 1m / 5m / 1h.</td></tr>'
            : '<tr><td colspan="10" class="empty">No actionable BUY or SELL signals for the selected symbol and interval. NEUTRAL and WATCH signals are hidden from this dashboard.</td></tr>';
        return;
    }

    signals = actionableSignals;
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
        return `
            <tr class="signal-row" data-detail-id="${detailId}">
                <td><strong class="signal-symbol">${escapeHtml(s.symbol || '—')}</strong></td>
                <td><span class="interval-chip">${escapeHtml(s.interval || '—')}</span></td>
                <td>${dateTime(s.generatedAt)}</td>
                <td><span class="badge ${String(s.decision).toLowerCase()}">${escapeHtml(String(s.decision).replaceAll('_', ' '))}</span></td>
                <td><strong>${s.totalScore}/100</strong><small class="raw-score">Raw ${s.rawScore}/${s.maximumAvailableScore}</small></td>
                <td>${money(s.latestPrice)}</td>
                <td>${money(s.stopLoss)}</td>
                <td>${money(s.takeProfit)}</td>
                <td><button type="button" class="signal-detail-button" data-signal-id="${signalId}" data-detail-id="${detailId}">${isOpen ? 'Hide analysis' : 'View analysis'}</button></td>
            </tr>
            <tr id="${detailId}" class="signal-detail-row ${isOpen ? '' : 'hidden'}">
                <td colspan="10">
                    <div class="analysis-view ${isPinned ? 'pinned' : ''}" data-signal-id="${signalId}">
                        <div class="analysis-toolbar">
                            <div><strong>${escapeHtml(s.symbol || '—')} · ${escapeHtml(s.interval || '—')}</strong><small>Readable AI analysis</small></div>
                            <div class="analysis-toolbar-actions">
                                <button type="button" class="analysis-pin-button ${isPinned ? 'active' : ''}" data-signal-id="${signalId}">${isPinned ? 'Unpin analysis' : 'Pin analysis'}</button>
                                <button type="button" class="analysis-close-button" data-signal-id="${signalId}" data-detail-id="${detailId}">Close</button>
                            </div>
                        </div>
                        ${scoreBreakdownHtml(s)}
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
            if (opening) openSignalAnalysisIds.add(signalId);
            else openSignalAnalysisIds.delete(signalId);
            button.textContent = opening ? 'Hide analysis' : 'View analysis';
        });
    });

    body.querySelectorAll('.analysis-close-button').forEach(button => {
        button.addEventListener('click', () => {
            const signalId = button.dataset.signalId;
            openSignalAnalysisIds.delete(signalId);
            if (pinnedSignalId === signalId) {
                pinnedSignalId = null;
                localStorage.removeItem('cryptoPinnedSignalId');
            }
            document.getElementById(button.dataset.detailId)?.classList.add('hidden');
            body.querySelector(`.signal-detail-button[data-signal-id="${CSS.escape(signalId)}"]`)?.replaceChildren(document.createTextNode('View analysis'));
        });
    });

    body.querySelectorAll('.analysis-pin-button').forEach(button => {
        button.addEventListener('click', () => {
            const signalId = button.dataset.signalId;
            pinnedSignalId = pinnedSignalId === signalId ? null : signalId;
            if (pinnedSignalId) {
                openSignalAnalysisIds.add(signalId);
                localStorage.setItem('cryptoPinnedSignalId', pinnedSignalId);
            } else {
                localStorage.removeItem('cryptoPinnedSignalId');
            }
            renderSignals(signals);
        });
    });
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
    const paperAction = !entryAllowed
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
                <h3>Analysis Pipeline</h3>
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
                <span class="pipeline-number">10</span><div><strong>Paper Trade</strong><small>${paperAction}</small><p>${entryAllowed ? 'PaperTradingService receives the final recommendation.' : 'No new position is permitted.'}</p></div>
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
                <div><strong>${escapeHtml(p.symbol || '—')}</strong><small>Paper trade #${p.id}</small></div>
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
                    <div><span>Trigger state</span><strong>${p.stopLossTriggered ? 'STOP HIT' : p.takeProfitTriggered ? 'TARGET HIT' : 'ACTIVE'}</strong></div>
                </div>
                <small>${escapeHtml(p.riskLogic || 'ATR risk logic unavailable for this older position.')}</small>
            </section>
            <div class="trade-recommendation"><strong>What this means:</strong> ${escapeHtml(p.recommendation || 'Waiting for the next signal.')}</div>
            <details><summary>Why the position was opened</summary><p>${escapeHtml(p.entryReason || 'No entry explanation stored.')}</p></details>
        </article>`;
    }).join('') : `<div class="empty trade-empty">
        No open paper trade for this symbol. A BUY or STRONG BUY signal will open one simulated trade; repeated BUY signals will not create duplicates.
    </div>`;
}

function renderTradeHistory(positions) {
    el('trade-history-body').innerHTML = positions.length ? positions.map(p => {
        const pnl = Number(p.realizedPnl || 0);
        const pnlClass = pnl > 0 ? 'positive' : pnl < 0 ? 'negative' : '';
        const predictionClass = p.predictionResult === 'SUCCESS' ? 'buy' : p.predictionResult === 'FAILED' ? 'reject' : 'neutral';
        return `<tr>
            <td><strong>${escapeHtml(p.symbol || '—')}</strong></td>
            <td class="trade-action-cell"><span class="badge buy">BUY ↑</span><span class="trade-action-arrow">→</span><span class="badge sell">SELL ↓</span></td>
            <td>${dateTime(p.openedAt)}</td>
            <td>${dateTime(p.closedAt)}</td>
            <td>${money(p.entryPrice)}<small>${escapeHtml(String(p.entryDecision || '—'))} ${p.entryScore ?? '—'}/100</small></td>
            <td>${money(p.exitPrice)}<small>${escapeHtml(String(p.exitDecision || p.closeReason || '—'))} ${p.exitScore ?? '—'}/100</small></td>
            <td>${value(p.quantity)}</td>
            <td class="${pnlClass}"><strong>${signedMoney(p.realizedPnl)}</strong><small>${signedPercent(p.pnlPercentage)}</small></td>
            <td title="${escapeHtml(p.exitReason || '')}">${escapeHtml(String(p.closeReason || p.status || '—').replaceAll('_',' '))}</td>
            <td><span class="badge ${predictionClass}">${escapeHtml(p.predictionResult || 'PENDING')}</span></td>
            <td><button type="button" class="replay-button" onclick="openTradeReplay(${p.id})">Inspect</button></td>
        </tr>`;
    }).join('') : '<tr><td colspan="11" class="empty">No completed paper trades yet. Closed trades will appear here with their final P&amp;L and result.</td></tr>';
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
    el('connection-dot').className = online ? 'online' : 'offline';
    el('connection-label').textContent = online ? 'API connected' : 'API unavailable';
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

el('refresh-button').addEventListener('click', refreshDashboard);
el('analyze-sentiment-button').addEventListener('click', analyzeSentiment);
el('collect-sentiment-button').addEventListener('click', collectSentimentProviders);
el('symbol-select').addEventListener('change', refreshDashboard);
el('interval-select').addEventListener('change', refreshDashboard);
setupCollapsibleSections();
(async () => { await loadSymbols(); await refreshDashboard(); })();


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
        return decision === 'BUY' || decision === 'SELL' || decision === 'STRONG_SELL';
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
