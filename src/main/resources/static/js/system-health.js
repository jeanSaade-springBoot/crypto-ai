function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
}

async function api(url) {
    const response = await fetch(url, {headers: {'Accept': 'application/json'}, cache: 'no-store'});
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `Request failed (${response.status})`);
    }
    return response.json();
}

function setText(id, value) {
    const node = document.getElementById(id);
    if (node) node.textContent = value ?? '—';
}

function statusClass(status) {
    const value = String(status || 'OK').toUpperCase();
    if (value === 'CRITICAL') return 'critical';
    if (value === 'WARNING') return 'warning';
    if (value === 'LEARNING') return 'learning';
    return 'ok';
}

function statusPill(status) {
    const value = String(status || 'OK').toUpperCase();
    return `<span class="health-status-pill ${statusClass(value)}">${escapeHtml(value)}</span>`;
}

function fmtCount(value) {
    return Number(value || 0).toLocaleString();
}

function fmtBaseline(value) {
    return value == null ? '—' : Number(value).toFixed(1);
}

function fmtTime(value) {
    if (!value) return '—';
    if (window.CryptoTime?.formatLocal) return window.CryptoTime.formatLocal(value);
    return String(value);
}

function renderStaleness(targetId, rows) {
    const target = document.getElementById(targetId);
    if (!target) return;
    const sorted = Array.isArray(rows) ? rows : [];
    target.innerHTML = sorted.length ? sorted.map(row => `
        <tr class="health-row-${statusClass(row.status)}">
            <td>${statusPill(row.status)}</td>
            <td><strong>${escapeHtml(row.symbol)}</strong></td>
            <td>${escapeHtml(row.interval)}</td>
            <td>${escapeHtml(fmtTime(row.lastAt))}</td>
            <td>${row.minutesStale == null ? '—' : `${fmtCount(row.minutesStale)} min`}</td>
        </tr>`).join('') : '<tr><td colspan="5" class="empty">No rows returned.</td></tr>';
}

function renderAlerts(rows) {
    const target = document.getElementById('health-alerts');
    if (!target) return;
    const alerts = Array.isArray(rows) ? rows : [];
    setText('health-alert-count', `${alerts.length} active`);
    target.innerHTML = alerts.length ? alerts.map(row => `
        <article class="health-alert ${statusClass(row.status)}">
            ${statusPill(row.status)}
            <div><strong>${escapeHtml(row.title)}</strong><small>${escapeHtml(row.message)}</small></div>
        </article>`).join('') : '<div class="health-empty-ok"><strong>All monitored checks are OK</strong><small>No candle/signal staleness, execution imbalance or missing-context alert is active.</small></div>';
}

function renderTradeBaseline(rows) {
    const target = document.getElementById('health-trade-baseline');
    if (!target) return;
    target.innerHTML = (rows || []).map(row => `<tr><td><strong>${escapeHtml(row.side)}</strong></td><td>${fmtCount(row.todayCount)}</td><td>${fmtBaseline(row.baselineAvg)}</td><td>${statusPill(row.status)}</td></tr>`).join('');
}

function renderRoutes(rows) {
    const target = document.getElementById('health-entry-routes');
    if (!target) return;
    target.innerHTML = (rows || []).length ? rows.map(row => `<tr><td><strong>${escapeHtml(String(row.route || 'UNKNOWN').replaceAll('_',' '))}</strong></td><td>${fmtCount(row.todayCount)}</td><td>${fmtBaseline(row.baselineAvg)}</td><td>${statusPill(row.status)}</td></tr>`).join('') : '<tr><td colspan="4" class="empty">No BUY routes fired today.</td></tr>';
}

function renderStrategyRegimes(rows) {
    const target = document.getElementById('health-strategy-regime');
    if (!target) return;
    target.innerHTML = (rows || []).length ? rows.map(row => `<tr><td><strong>${escapeHtml(String(row.strategy || '').replaceAll('_',' '))}</strong></td><td>${escapeHtml(String(row.regime || '').replaceAll('_',' '))}</td><td>${fmtCount(row.todayCount)}</td><td>${fmtBaseline(row.baselineAvg)}</td><td>${statusPill(row.status)}</td></tr>`).join('') : '<tr><td colspan="5" class="empty">No strategy/regime signals today.</td></tr>';
}

function renderOpportunityOutcomes(rows) {
    const target = document.getElementById('health-opportunity-outcomes');
    if (!target) return;
    target.innerHTML = (rows || []).length ? rows.map(row => `<tr><td><strong>${escapeHtml(row.opportunityStatus)}</strong></td><td>${escapeHtml(String(row.decisionCode || 'NULL').replaceAll('_',' '))}</td><td>${fmtCount(row.todayCount)}</td><td>${fmtBaseline(row.baselineAvg)}</td></tr>`).join('') : '<tr><td colspan="4" class="empty">No opportunity activity today.</td></tr>';
}

function renderDailyHealth(data) {
    const summary = data?.summary || {};
    const candles = summary.candleCounts || {};
    const signals = summary.signalCounts || {};
    setText('health-candle-1m', fmtCount(candles['1m']));
    setText('health-candle-5m', fmtCount(candles['5m']));
    setText('health-candle-1h', fmtCount(candles['1h']));
    setText('health-signal-1m', fmtCount(signals['1m']));
    setText('health-signal-5m', fmtCount(signals['5m']));
    setText('health-signal-1h', fmtCount(signals['1h']));
    setText('health-buy-count', fmtCount(summary.buyCount));
    setText('health-sell-count', fmtCount(summary.sellCount));
    setText('health-open-positions', fmtCount(summary.openPositions));
    setText('health-balance-buy', fmtCount(summary.buyCount));
    setText('health-balance-sell', fmtCount(summary.sellCount));
    setText('health-balance-open', fmtCount(summary.openPositions));
    setText('health-balance-message', summary.buySellMessage || '—');
    setText('system-health-day', `${data.day || 'Today'} · Asia/Riyadh`);
    setText('overall-health-updated', `Updated ${fmtTime(data.generatedAt)}`);

    const overall = String(data.status || 'OK').toUpperCase();
    const overallCard = document.getElementById('overall-health-card');
    if (overallCard) overallCard.className = `health-overall-card ${statusClass(overall)}`;
    setText('overall-health-status', overall);
    const dot = document.getElementById('system-health-dot');
    if (dot) dot.className = overall === 'CRITICAL' ? 'offline' : overall === 'WARNING' ? '' : 'online';
    setText('system-health-sidebar-status', overall === 'OK' ? 'System Healthy' : `System ${overall}`);

    const balance = document.getElementById('health-balance-status');
    if (balance) {
        balance.className = `health-status-pill ${statusClass(summary.buySellStatus)}`;
        balance.textContent = summary.buySellStatus || 'OK';
    }
    const missing = document.getElementById('health-missing-context');
    if (missing) {
        missing.className = `health-status-pill ${statusClass(summary.missingContextStatus)}`;
        missing.textContent = `MISSING_CONTEXT ${fmtCount(summary.missingContextCount)} · ${summary.missingContextStatus || 'OK'}`;
    }

    renderAlerts(data.alerts);
    renderStaleness('health-signal-staleness', data.signalStaleness);
    renderStaleness('health-candle-staleness', data.candleStaleness);
    renderTradeBaseline(data.tradeBaseline);
    renderRoutes(data.entryRoutes);
    renderStrategyRegimes(data.strategyRegimes);
    renderOpportunityOutcomes(data.opportunityOutcomes);
}

async function loadSystemHealthScheduledJobs() {
    const target = document.getElementById('health-scheduled-jobs');
    if (!target) return;
    try {
        // FIX-114: Render only the eight requested recurring jobs. Enabled state/cadence come from
        // backend runtime configuration; Health remains read-only and cannot change scheduler state.
        const jobs = await api('/api/system-health/scheduled-jobs');
        target.innerHTML = Array.isArray(jobs) && jobs.length ? jobs.map(job => `
            <tr>
                <td><strong>${Number(job.number) || '—'}</strong></td>
                <td><code>${escapeHtml(job.name || '—')}</code></td>
                <td><strong>${escapeHtml(job.cadence || '—')}</strong></td>
                <td><span class="health-status-pill ${job.enabled ? 'ok' : 'warning'}">${job.enabled ? 'ENABLED' : 'DISABLED'}</span></td>
                <td>${escapeHtml(job.purpose || '—')}</td>
            </tr>`).join('') : '<tr><td colspan="5" class="empty">No scheduled jobs were returned.</td></tr>';
    } catch (error) {
        target.innerHTML = `<tr><td colspan="5" class="empty">${escapeHtml(error.message)}</td></tr>`;
    }
}

function initializeSystemHealthSidebar() {
    const sidebar = document.getElementById('system-health-sidebar');
    const toggle = document.getElementById('sidebar-toggle');
    if (!sidebar || !toggle) return;
    const storageKey = 'crypto-sidebar-collapsed';
    const stored = window.localStorage.getItem(storageKey) === '1';
    sidebar.classList.toggle('collapsed', stored);
    document.body.classList.toggle('sidebar-collapsed', stored);
    const updateToggle = () => {
        const collapsed = sidebar.classList.contains('collapsed');
        toggle.textContent = collapsed ? '›' : '‹';
        toggle.setAttribute('aria-label', collapsed ? 'Expand navigation' : 'Collapse navigation');
    };
    updateToggle();
    toggle.addEventListener('click', () => {
        if (window.matchMedia('(max-width: 760px)').matches) {
            sidebar.classList.toggle('mobile-open');
            toggle.textContent = sidebar.classList.contains('mobile-open') ? '×' : '☰';
            return;
        }
        sidebar.classList.toggle('collapsed');
        const collapsed = sidebar.classList.contains('collapsed');
        document.body.classList.toggle('sidebar-collapsed', collapsed);
        window.localStorage.setItem(storageKey, collapsed ? '1' : '0');
        updateToggle();
    });
}

async function refreshSystemHealth() {
    const button = document.getElementById('refresh-system-health');
    if (button) button.disabled = true;
    try {
        // FIX-071 loads daily production diagnostics and runtime cadence independently so a schedule error
        // cannot hide the candle/signal/execution health data that operators need first.
        const [health] = await Promise.all([api('/api/system-health/daily'), loadSystemHealthScheduledJobs()]);
        renderDailyHealth(health);
    } catch (error) {
        const target = document.getElementById('health-alerts');
        if (target) target.innerHTML = `<article class="health-alert critical"><span class="health-status-pill critical">CRITICAL</span><div><strong>Health endpoint failed</strong><small>${escapeHtml(error.message)}</small></div></article>`;
        setText('overall-health-status', 'UNAVAILABLE');
    } finally {
        if (button) button.disabled = false;
    }
}

initializeSystemHealthSidebar();
document.getElementById('refresh-system-health')?.addEventListener('click', refreshSystemHealth);
refreshSystemHealth();
