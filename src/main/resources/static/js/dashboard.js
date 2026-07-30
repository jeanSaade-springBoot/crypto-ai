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

async function refreshDashboard() {
    const symbol = el('symbol-select').value;
    const interval = el('interval-select').value;
    el('refresh-button').disabled = true;
    try {
        const [response, providerResponse, sentimentStatusResponse] = await Promise.all([
            fetch(`/api/dashboard/overview?symbol=${encodeURIComponent(symbol)}&interval=${encodeURIComponent(interval)}`),
            fetch(`/api/sentiment/providers/${encodeURIComponent(symbol)}`),
            fetch('/api/sentiment/status')
        ]);
        if (!response.ok) throw new Error(`Dashboard API returned ${response.status}`);
        const data = await response.json();
        data.sentimentProviderStatuses = providerResponse.ok ? await providerResponse.json() : [];
        data.sentimentSystemStatus = sentimentStatusResponse.ok
            ? await sentimentStatusResponse.json()
            : { enabled: false, message: 'Could not read sentiment master status' };
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
    el('history-progress').textContent = `${Math.min(s.closedCandleCount, s.minimumCandles)} / ${s.minimumCandles} required`;
    el('history-bar').style.width = `${Math.min(100, (s.closedCandleCount / s.minimumCandles) * 100)}%`;
    el('latest-decision').textContent = String(s.latestDecision).replace('_', ' ');
    el('latest-score').textContent = s.latestScore === null ? 'Analysis not ready' : `Score ${s.latestScore}`;
    el('open-positions').textContent = s.openPositions;
    el('last-updated').textContent = `Updated ${dateTime(data.updatedAt)}`;
    el('market-subtitle').textContent = `${data.symbol} · ${data.interval}`;
    renderPipeline(data.pipeline);
    renderIndicators(data.indicator || {});
    renderSentiment(data.sentiment || {}, data.sentimentProviderStatuses || [], data.sentimentSystemStatus || {});
    renderSchedules(data.schedule || {});
    applyDashboardRefreshSchedule(data.schedule || {});
    renderCharts(data.candles || []);
    renderSignals(data.signals || []);
    renderPositions(data.positions || []);
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

function renderSignals(signals) {
    el('signals-body').innerHTML = signals.length ? signals.map(s => `<tr><td>${dateTime(s.generatedAt)}</td><td><span class="badge ${String(s.decision).toLowerCase()}">${escapeHtml(String(s.decision).replace('_', ' '))}</span></td><td>${s.totalScore}</td><td>${money(s.latestPrice)}</td><td>${money(s.stopLoss)}</td><td>${money(s.takeProfit)}</td><td title="${escapeHtml(s.explanation || '')}">${escapeHtml(s.explanation || '—')}</td></tr>`).join('') : '<tr><td colspan="7" class="empty">No trade signals yet. The row appears after AnalysisService saves a signal.</td></tr>';
}

function renderPositions(positions) {
    el('positions-body').innerHTML = positions.length ? positions.map(p => `<tr><td>${dateTime(p.openedAt)}</td><td>${escapeHtml(String(p.side))}</td><td><span class="badge ${String(p.status).toLowerCase()}">${escapeHtml(String(p.status))}</span></td><td>${value(p.quantity)}</td><td>${money(p.entryPrice)}</td><td>${money(p.stopLoss)}</td><td>${money(p.takeProfit)}</td><td>${money(p.realizedPnl)}</td></tr>`).join('') : '<tr><td colspan="8" class="empty">No paper positions yet. BUY or STRONG_BUY eligibility is required.</td></tr>';
}


function renderSentiment(sentiment, providerStatuses, systemStatus) {
    const score = Number(sentiment.weightedScore || 0);
    const clamped = Math.max(-1, Math.min(1, score));
    const label = sentiment.label || 'NEUTRAL';
    const masterEnabled = Boolean(systemStatus.enabled);
    const enabledProviders = providerStatuses.filter(p => p.enabled);
    const totalConfiguredWeight = enabledProviders.reduce((sum, p) => sum + Number(p.weight || 0), 0);

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
        const evaluatedContribution = masterEnabled && p.enabled ? providerScore * normalizedWeight : 0;
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
            <td title="${escapeHtml(p.message || '')}"><span class="badge ${p.status === 'SUCCESS' ? 'buy' : p.status === 'NEVER_RUN' ? 'neutral' : 'reject'}">${escapeHtml(p.status || 'UNKNOWN')}</span></td>
            <td><button type="button" class="small-button" onclick="collectSingleSentimentProvider('${escapeHtml(p.provider)}')" ${!masterEnabled || !p.enabled ? 'disabled' : ''}>Run</button></td>
        </tr>`;
    }).join('') : '<tr><td colspan="12" class="empty">No provider configuration found. Confirm Flyway V5 has run.</td></tr>';

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

function escapeHtml(text) {
    return String(text).replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char]));
}

el('refresh-button').addEventListener('click', refreshDashboard);
el('analyze-sentiment-button').addEventListener('click', analyzeSentiment);
el('collect-sentiment-button').addEventListener('click', collectSentimentProviders);
el('symbol-select').addEventListener('change', refreshDashboard);
el('interval-select').addEventListener('change', refreshDashboard);
(async () => { await loadSymbols(); await refreshDashboard(); })();
