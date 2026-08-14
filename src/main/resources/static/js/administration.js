const coinBody = document.getElementById('coin-body');
const coinMessage = document.getElementById('message');

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
}

function showAdminMessage(message, error = false) {
    coinMessage.textContent = message;
    coinMessage.classList.remove('hidden');
    coinMessage.classList.toggle('success-banner', !error);
    coinMessage.classList.toggle('error-banner', error);
    window.setTimeout(() => coinMessage.classList.add('hidden'), 5000);
}

async function loadCoins() {
    try {
        const coins = await api('/api/administration/coins');
        coinBody.innerHTML = coins.map(coin => `
            <tr>
                <td><strong>${escapeHtml(coin.symbol)}</strong></td>
                <td>${coin.systemDefault ? 'Default' : 'User added'}</td>
                <td><span class="status-pill ${coin.enabled ? 'enabled' : 'disabled'}">${coin.enabled ? 'Enabled' : 'Disabled'}</span></td>
                <td class="coin-actions">
                    <button type="button" class="secondary-button" data-action="toggle" data-id="${coin.id}" data-enabled="${!coin.enabled}">${coin.enabled ? 'Disable' : 'Enable'}</button>
                    ${coin.removable ? `<button type="button" class="danger-button" data-action="remove" data-id="${coin.id}" data-symbol="${escapeHtml(coin.symbol)}">Remove</button>` : ''}
                </td>
            </tr>`).join('') || '<tr><td colspan="4">No coins configured</td></tr>';
    } catch (error) {
        coinBody.innerHTML = `<tr><td colspan="4">${escapeHtml(error.message)}</td></tr>`;
        showAdminMessage(error.message, true);
    }
}

document.getElementById('add-coin-form').addEventListener('submit', async event => {
    event.preventDefault();
    const input = document.getElementById('new-coin-symbol');
    try {
        await api('/api/administration/coins', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({symbol: input.value})
        });
        input.value = '';
        await loadCoins();
        showAdminMessage('Coin added. Live stream reload and historical bootstrap started automatically.');
    } catch (error) {
        showAdminMessage(error.message, true);
    }
});

coinBody.addEventListener('click', async event => {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    button.disabled = true;
    try {
        if (button.dataset.action === 'toggle') {
            await api(`/api/administration/coins/${button.dataset.id}/enabled`, {
                method: 'PUT', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({enabled: button.dataset.enabled === 'true'})
            });
            showAdminMessage(button.dataset.enabled === 'true' ? 'Coin enabled. Live stream reload and historical bootstrap started automatically.' : 'Coin disabled. Binance streams are updating automatically.');
        } else if (button.dataset.action === 'remove') {
            if (!window.confirm(`Remove ${button.dataset.symbol} from monitoring?`)) return;
            await api(`/api/administration/coins/${button.dataset.id}`, {method: 'DELETE'});
            showAdminMessage('Coin removed. Binance streams are updating automatically.');
        }
        await loadCoins();
    } catch (error) {
        showAdminMessage(error.message, true);
    } finally {
        button.disabled = false;
    }
});

document.getElementById('reload-streams').addEventListener('click', async event => {
    const button = event.currentTarget;
    button.disabled = true;
    try {
        const result = await api('/api/administration/coins/reload-streams', {method: 'POST'});
        showAdminMessage(`${result.message}: ${result.symbols.join(', ')}`);
    } catch (error) {
        showAdminMessage(error.message, true);
    } finally {
        button.disabled = false;
    }
});

loadCoins();

// -----------------------------------------------------------------------------
// DEBUG-ONLY Market Move Tracker
// This UI is intentionally isolated from every live trading decision path.
// -----------------------------------------------------------------------------
const priceMoveBody = document.getElementById('price-move-body');
let savedPriceMoveSymbols = new Set(['BNBUSDT']);

function formatMovePrice(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) return '—';
    if (Math.abs(n) >= 1000) return n.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 4});
    if (Math.abs(n) >= 1) return n.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 6});
    return n.toLocaleString(undefined, {minimumFractionDigits: 4, maximumFractionDigits: 10});
}

function formatMoveTime(value) {
    if (!value) return '—';
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? escapeHtml(value) : d.toLocaleString();
}

function formatDuration(seconds) {
    const total = Math.max(0, Number(seconds) || 0);
    if (total < 60) return `${Math.round(total)}s`;
    const minutes = Math.floor(total / 60);
    const secs = Math.round(total % 60);
    if (minutes < 60) return `${minutes}m ${secs}s`;
    const hours = Math.floor(minutes / 60);
    return `${hours}h ${minutes % 60}m`;
}

async function loadPriceMoveSymbols() {
    const container = document.getElementById('price-move-symbols');
    if (!container) return;
    const previouslySelected = new Set(selectedPriceMoveSymbols());
    try {
        const coins = await api('/api/administration/coins');
        const defaults = previouslySelected.size ? previouslySelected : savedPriceMoveSymbols;
        container.innerHTML = coins.map((coin, index) => {
            const checked = defaults.has(coin.symbol) || (!defaults.size && index === 0);
            return `
                <label class="debug-symbol-check">
                    <input type="checkbox" value="${escapeHtml(coin.symbol)}" ${checked ? 'checked' : ''}>
                    <span>${escapeHtml(coin.symbol)}${coin.enabled ? '' : ' · disabled'}</span>
                </label>`;
        }).join('') || '<span>No configured coins</span>';
    } catch (error) {
        container.innerHTML = '<label class="debug-symbol-check"><input type="checkbox" value="BNBUSDT" checked><span>BNBUSDT</span></label>';
        showAdminMessage(`Could not load debug symbols: ${error.message}`, true);
    }
}

function selectedPriceMoveSymbols() {
    return [...document.querySelectorAll('#price-move-symbols input[type="checkbox"]:checked')]
        .map(input => input.value)
        .filter(Boolean);
}

function renderActivePriceMoves(states) {
    const container = document.getElementById('price-move-active-list');
    if (!container) return;
    if (!states.length) {
        container.innerHTML = '<div class="debug-empty-state">Select at least one symbol to inspect its live tracker state.</div>';
        return;
    }
    container.innerHTML = states.map(active => {
        const change = Number(active.changePercent || 0);
        const changeText = active.tracking ? `${change >= 0 ? '+' : ''}${change.toFixed(3)}%` : '—';
        const start = active.startTime ? `${formatMoveTime(active.startTime)} · ${formatMovePrice(active.startPrice)}` : '—';
        const price = active.extremePrice != null
            ? `${formatMovePrice(active.extremePrice)}${active.lastPrice != null ? ` · live ${formatMovePrice(active.lastPrice)}` : ''}`
            : (active.lastPrice != null ? formatMovePrice(active.lastPrice) : '—');
        return `
            <article class="price-move-active-card">
                <div class="price-move-active-card-head">
                    <strong>${escapeHtml(active.symbol || '—')}</strong>
                    <span class="status-pill ${(active.importanceLevel || 'LOW').toLowerCase()}">${escapeHtml(active.importanceLevel || 'LOW')}</span>
                </div>
                <div class="price-move-active-metrics">
                    <div><span>State</span><strong>${escapeHtml(active.phase || 'WAITING')}</strong></div>
                    <div><span>Direction</span><strong>${escapeHtml(active.direction || '—')}</strong></div>
                    <div><span>Move</span><strong>${changeText}</strong></div>
                    <div><span>Duration</span><strong>${active.tracking ? formatDuration(active.durationSeconds) : '—'}</strong></div>
                    <div class="wide"><span>Start</span><strong>${start}</strong></div>
                    <div class="wide"><span>Current / extreme</span><strong>${price}</strong></div>
                </div>
            </article>`;
    }).join('');
}

async function loadActivePriceMove() {
    const symbols = selectedPriceMoveSymbols();
    if (!symbols.length) {
        renderActivePriceMoves([]);
        return;
    }
    try {
        const states = await Promise.all(symbols.map(symbol =>
            api(`/api/administration/debug/price-moves/active?symbol=${encodeURIComponent(symbol)}`)
        ));
        renderActivePriceMoves(states);
    } catch (error) {
        showAdminMessage(`Could not load live debug state: ${error.message}`, true);
    }
}

async function loadPriceMoveSettings() {
    const settings = await api('/api/administration/debug/price-moves/settings');
    document.getElementById('price-move-enabled').checked = Boolean(settings.enabled);
    document.getElementById('price-move-threshold').value = Number(settings.minimumMovePercent ?? 0.30);
    document.getElementById('price-move-min-duration').value = Math.max(6, Number(settings.minimumDurationMinutes ?? 6));
    document.getElementById('price-move-retracement').value = Number(settings.retracementClosePercent ?? 30);
    document.getElementById('price-move-cooldown').value = Number(settings.cooldownMinutes ?? 10);
    document.getElementById('price-move-retention').value = Number(settings.retentionDays ?? 7);
    savedPriceMoveSymbols = new Set(String(settings.selectedSymbols ?? 'BNBUSDT')
        .split(',').map(value => value.trim()).filter(Boolean));
}

async function loadPriceMoves() {
    if (!priceMoveBody) return;
    try {
        const symbols = selectedPriceMoveSymbols();
        const moveGroups = symbols.length
            ? await Promise.all(symbols.map(symbol => api(`/api/administration/debug/price-moves?symbol=${encodeURIComponent(symbol)}`)))
            : [];
        const moves = moveGroups.flat().sort((a, b) => new Date(b.endTime || 0) - new Date(a.endTime || 0));
        const newCount = moves.filter(move => move.reviewStatus === 'NEW').length;
        const mediumCount = moves.filter(move => move.importanceLevel === 'MEDIUM').length;
        const highCount = moves.filter(move => move.importanceLevel === 'HIGH').length;
        document.getElementById('price-move-new-count').textContent = newCount;
        document.getElementById('price-move-medium-count').textContent = mediumCount;
        document.getElementById('price-move-high-count').textContent = highCount;

        priceMoveBody.innerHTML = moves.map(move => {
            const directionClass = move.direction === 'UP' ? 'up' : 'down';
            const arrow = move.direction === 'UP' ? '↑' : '↓';
            const signedChange = Number(move.changePercent || 0);
            const changeText = `${signedChange >= 0 ? '+' : ''}${signedChange.toFixed(3)}%`;
            const status = String(move.reviewStatus || 'NEW').toLowerCase();
            return `
                <tr>
                    <td><strong>${escapeHtml(move.symbol)}</strong></td>
                    <td><span class="move-direction ${directionClass}">${arrow} ${escapeHtml(move.direction)}</span></td>
                    <td>${formatMoveTime(move.startTime)}</td>
                    <td>${formatMoveTime(move.endTime)}</td>
                    <td>${formatMovePrice(move.startPrice)}</td>
                    <td>${formatMovePrice(move.endPrice)}</td>
                    <td><span class="move-change ${directionClass}">${changeText}</span></td>
                    <td>${formatDuration(move.durationSeconds)}</td>
                    <td><span class="status-pill ${String(move.importanceLevel || 'MEDIUM').toLowerCase()}">${escapeHtml(move.importanceLevel || 'MEDIUM')}</span></td>
                    <td><span class="status-pill ${status}">${escapeHtml(move.reviewStatus)}</span></td>
                    <td class="price-move-actions">
                        <a class="secondary-button price-move-chart-link"
                           href="/dashboard?symbol=${encodeURIComponent(move.symbol)}&interval=5m&focusStart=${encodeURIComponent(move.startTime)}&focusEnd=${encodeURIComponent(move.endTime)}&focusDirection=${encodeURIComponent(move.direction || '')}&focusChange=${encodeURIComponent(move.changePercent ?? '')}#market">View 5m Chart</a>
                        <button type="button" class="secondary-button" data-move-id="${move.id}" data-review-status="REVIEWED">Reviewed</button>
                        <button type="button" class="secondary-button" data-move-id="${move.id}" data-review-status="IGNORED">Ignore</button>
                    </td>
                </tr>`;
        }).join('') || '<tr><td colspan="11">No MEDIUM/HIGH moves longer than 5 minutes detected yet.</td></tr>';
    } catch (error) {
        priceMoveBody.innerHTML = `<tr><td colspan="11">${escapeHtml(error.message)}</td></tr>`;
        showAdminMessage(error.message, true);
    }
}

async function savePriceMoveSettings(showMessage = true) {
    const settings = await api('/api/administration/debug/price-moves/settings', {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
            enabled: document.getElementById('price-move-enabled').checked,
            minimumMovePercent: Number(document.getElementById('price-move-threshold').value),
            minimumDurationMinutes: Number(document.getElementById('price-move-min-duration').value),
            retracementClosePercent: Number(document.getElementById('price-move-retracement').value),
            cooldownMinutes: Number(document.getElementById('price-move-cooldown').value),
            retentionDays: Number(document.getElementById('price-move-retention').value),
            symbols: selectedPriceMoveSymbols()
        })
    });
    savedPriceMoveSymbols = new Set(String(settings.selectedSymbols || '')
        .split(',').map(value => value.trim()).filter(Boolean));
    if (showMessage) showAdminMessage('Debug Market Move Tracker settings saved. Trading logic was not changed.');
    return settings;
}

const priceMoveSettingsForm = document.getElementById('price-move-settings-form');
if (priceMoveSettingsForm) {
    priceMoveSettingsForm.addEventListener('submit', async event => {
        event.preventDefault();
        try {
            await savePriceMoveSettings(true);
            await Promise.all([loadPriceMoves(), loadActivePriceMove()]);
        } catch (error) {
            showAdminMessage(error.message, true);
        }
    });
}

if (priceMoveBody) {
    priceMoveBody.addEventListener('click', async event => {
        const button = event.target.closest('button[data-move-id]');
        if (!button) return;
        button.disabled = true;
        try {
            await api(`/api/administration/debug/price-moves/${button.dataset.moveId}/review-status`, {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({status: button.dataset.reviewStatus})
            });
            await loadPriceMoves();
        } catch (error) {
            showAdminMessage(error.message, true);
        } finally {
            button.disabled = false;
        }
    });
}

const priceMoveRefresh = document.getElementById('price-move-refresh');
if (priceMoveRefresh) {
    priceMoveRefresh.addEventListener('click', async () => { await Promise.all([loadPriceMoves(), loadActivePriceMove()]); });
}

loadPriceMoveSettings().then(loadPriceMoveSymbols).then(() => Promise.all([loadPriceMoves(), loadActivePriceMove()])).catch(error => showAdminMessage(error.message, true));

const priceMoveSymbols = document.getElementById('price-move-symbols');
if (priceMoveSymbols) priceMoveSymbols.addEventListener('change', async event => {
    if (!event.target.matches('input[type="checkbox"]')) return;
    try { await savePriceMoveSettings(false); } catch (error) { showAdminMessage(error.message, true); }
    await Promise.all([loadPriceMoves(), loadActivePriceMove()]);
});

const priceMoveSelectAll = document.getElementById('price-move-select-all');
if (priceMoveSelectAll) priceMoveSelectAll.addEventListener('click', async () => {
    document.querySelectorAll('#price-move-symbols input[type="checkbox"]').forEach(input => { input.checked = true; });
    try { await savePriceMoveSettings(false); } catch (error) { showAdminMessage(error.message, true); }
    await Promise.all([loadPriceMoves(), loadActivePriceMove()]);
});

const priceMoveClearSymbols = document.getElementById('price-move-clear-symbols');
if (priceMoveClearSymbols) priceMoveClearSymbols.addEventListener('click', async () => {
    document.querySelectorAll('#price-move-symbols input[type="checkbox"]').forEach(input => { input.checked = false; });
    try { await savePriceMoveSettings(false); } catch (error) { showAdminMessage(error.message, true); }
    await Promise.all([loadPriceMoves(), loadActivePriceMove()]);
});

// -----------------------------------------------------------------------------
// AI REGRESSION TESTS
// Historical read-only replay. Test outputs are isolated from live trading data.
// -----------------------------------------------------------------------------
let regressionPollTimer = null;
let activeRegressionRunId = null;

function regressionUtcInstant(value) {
    if (!value) return null;
    const normalized = value.length === 16 ? `${value}:00Z` : `${value}Z`;
    return new Date(normalized).toISOString();
}

function regressionBool(value) {
    return value === true || value === 1 || value === '1';
}

function regressionUtcLocalValue(date) {
    const pad = value => String(value).padStart(2, '0');
    return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

async function loadRegressionSymbols() {
    const select = document.getElementById('regression-symbol');
    if (!select) return;
    const previous = select.value || 'BNBUSDT';
    try {
        const coins = await api('/api/administration/coins');
        select.innerHTML = coins.map(coin => `
            <option value="${escapeHtml(coin.symbol)}"${coin.symbol === previous ? ' selected' : ''}>
                ${escapeHtml(coin.symbol)}${coin.enabled ? '' : ' · disabled'}
            </option>`).join('') || '<option value="">No configured coins</option>';
        if (![...select.options].some(option => option.selected) && select.options.length) select.selectedIndex = 0;
    } catch (error) {
        select.innerHTML = '<option value="BNBUSDT">BNBUSDT</option>';
        showAdminMessage(`Could not load regression symbols: ${error.message}`, true);
    }
}

function setRegressionRunButtonRunning(running, run = null) {
    const button = document.getElementById('regression-run');
    if (!button) return;
    button.disabled = running;
    button.textContent = running
        ? `Test ${run?.id ? `#${run.id} ` : ''}Running…`
        : 'Run Regression Test';
}

function regressionStatusClass(status) {
    const s = String(status || '').toLowerCase();
    if (s === 'passed') return 'reviewed';
    if (s === 'failed' || s === 'error') return 'ignored';
    return 'new';
}

async function loadRegressionRuns() {
    const body = document.getElementById('regression-runs-body');
    if (!body) return;
    try {
        const runs = await api('/api/administration/regression-tests/runs');
        body.innerHTML = runs.map(run => `
            <tr>
                <td>#${run.id}</td>
                <td><strong>${escapeHtml(run.test_name)}</strong></td>
                <td>${escapeHtml(run.symbol)}</td>
                <td>${formatMoveTime(run.start_time)} → ${formatMoveTime(run.end_time)}</td>
                <td><span class="status-pill ${regressionStatusClass(run.status)}">${escapeHtml(run.status)}</span></td>
                <td>${Number(run.progress_percent || 0)}%</td>
                <td><button type="button" class="secondary-button" data-regression-run-id="${run.id}">View</button></td>
            </tr>
        `).join('') || '<tr><td colspan="7">No regression tests have been run yet.</td></tr>';
        const active = runs.find(run => ['PENDING', 'RUNNING'].includes(String(run.status)));
        setRegressionRunButtonRunning(Boolean(active), active);
        const resetButton = document.getElementById('regression-reset');
        if (resetButton) {
            resetButton.disabled = Boolean(active);
            resetButton.title = active ? `Test #${active.id} is still ${active.status}. Wait for it to finish before resetting.` : 'Clear all isolated regression/shadow test data';
        }
        return runs;
    } catch (error) {
        body.innerHTML = `<tr><td colspan="7">${escapeHtml(error.message)}</td></tr>`;
    }
}


function regressionPipelineState(value, kind = 'decision') {
    const normalized = String(value || '').toUpperCase();
    if (kind === 'decision') {
        if (['BUY', 'STRONG_BUY'].includes(normalized)) return 'pass';
        if (normalized === 'WATCH') return 'wait';
        if (['SELL', 'STRONG_SELL'].includes(normalized)) return 'fail';
        return 'neutral';
    }
    return normalized;
}

function regressionPipelineNode(label, value, state, detail = '', time = '') {
    return `
        <div class="pipeline-node ${state}">
            <span class="pipeline-node-label">${escapeHtml(label)}</span>
            <strong>${escapeHtml(value ?? '—')}</strong>
            ${time ? `<em class="pipeline-node-time">${escapeHtml(time)}</em>` : ''}
            ${detail ? `<small>${escapeHtml(detail)}</small>` : ''}
        </div>`;
}

function nearestOpportunityForCandidate(candidate, opportunities) {
    const candidateTime = new Date(candidate.generated_at).getTime();

    // Never attach an execution result that happened before the BUY candidate existed.
    // The previous absolute-distance lookup could associate a fresh BUY with an older
    // ATR_ENTRY_BLOCKED row, which made the visual pipeline misleading.
    const afterCandidate = opportunities
        .map(row => ({
            row,
            delta: new Date(row.generated_at).getTime() - candidateTime
        }))
        .filter(item => Number.isFinite(item.delta)
            && item.delta >= 0
            && item.delta <= 10 * 60 * 1000)
        .sort((a, b) => a.delta - b.delta);

    return afterCandidate[0]?.row || null;
}

function matchingTradeForCandidate(candidate, trades) {
    const candidateTime = new Date(candidate.generated_at).getTime();
    return trades
        .map(trade => ({trade, delta: new Date(trade.entry_time).getTime() - candidateTime}))
        .filter(item => Number.isFinite(item.delta) && item.delta >= 0 && item.delta <= 15 * 60 * 1000)
        .sort((a, b) => a.delta - b.delta)[0]?.trade || null;
}

function regressionTradeChartUrl(symbol, trade, index = 0) {
    if (!trade?.entry_time || !trade?.entry_price) return '#';
    const entry = new Date(trade.entry_time);
    const exit = trade.exit_time ? new Date(trade.exit_time) : new Date(entry.getTime() + 60 * 60 * 1000);
    const params = new URLSearchParams({
        symbol: String(symbol || 'BNBUSDT').toUpperCase(),
        interval: '5m',
        focusStart: entry.toISOString(),
        focusEnd: exit.toISOString(),
        focusDirection: 'UP',
        focusChange: trade.realized_pnl_percent == null ? '' : String(trade.realized_pnl_percent),
        debugTrade: '1',
        debugTradeLabel: `Replay #${index + 1}`,
        debugEntryTime: entry.toISOString(),
        debugEntryPrice: String(trade.entry_price)
    });
    if (trade.exit_time && trade.exit_price) {
        params.set('debugExitTime', new Date(trade.exit_time).toISOString());
        params.set('debugExitPrice', String(trade.exit_price));
    }
    return `/dashboard?${params.toString()}#market`;
}


function regressionSignalChartUrl(symbol, signal, index = 0) {
    if (!signal?.generated_at || signal?.latest_price == null) return '#';
    const at = new Date(signal.generated_at);
    if (Number.isNaN(at.getTime())) return '#';
    const start = new Date(at.getTime() - 20 * 60 * 1000);
    const end = new Date(at.getTime() + 40 * 60 * 1000);
    const decision = String(signal.final_decision || signal.execution_effective_decision || 'BUY').replaceAll('_', ' ');
    const params = new URLSearchParams({
        symbol: String(symbol || signal.symbol || 'BNBUSDT').toUpperCase(),
        interval: '5m',
        focusStart: start.toISOString(),
        focusEnd: end.toISOString(),
        focusDirection: 'UP',
        debugTrade: '1',
        debugTradeLabel: `Signal ${decision} #${index + 1}`,
        debugEntryTime: at.toISOString(),
        debugEntryPrice: String(signal.latest_price)
    });
    return `/dashboard?${params.toString()}#market`;
}

function regressionNearestSignal(signals, interval, atValue) {
    const at = new Date(atValue).getTime();
    if (!Number.isFinite(at)) return null;
    return [...(signals || [])]
        .filter(signal => String(signal.interval_code || '').toLowerCase() === String(interval).toLowerCase())
        .filter(signal => { const t = new Date(signal.generated_at).getTime(); return Number.isFinite(t) && t <= at; })
        .sort((a, b) => new Date(b.generated_at).getTime() - new Date(a.generated_at).getTime())[0] || null;
}

function regressionScoreDetail(signal) {
    if (!signal) return 'No score snapshot';
    return `Total ${signal.total_score ?? '—'} · C${signal.confidence_score ?? '—'} · T${signal.trend_score ?? '—'} · M${signal.momentum_score ?? '—'} · V${signal.volume_score ?? '—'}`;
}

function renderRegressionPipeline(signals, opportunities, trades, management = [], runSymbol = '') {
    const panel = document.getElementById('regression-pipeline');
    const body = document.getElementById('regression-pipeline-body');
    if (!panel || !body) return;

    const transitionTimes = new Set(opportunities
        .filter(row => ['HTF_TRANSITION_REDUCED_ENTRY', 'REDUCED_POSITION_ALLOWED', 'BREAKOUT_CONTINUATION_ENTRY']
            .includes(String(row.decision_code || '')))
        .map(row => new Date(row.generated_at).getTime()));
    const candidates = signals
        .filter(signal => {
            if (!regressionBool(signal.replay_generated)) return false;
            const finalDecision = String(signal.final_decision || '');
            const originalDecision = String(signal.original_decision || '');
            if (['BUY', 'STRONG_BUY'].includes(finalDecision) || ['BUY', 'STRONG_BUY'].includes(originalDecision)) return true;
            const generated = new Date(signal.generated_at).getTime();
            return finalDecision === 'WATCH' && [...transitionTimes].some(t => Math.abs(t - generated) <= 1000);
        })
        .slice(0, 20);

    panel.classList.remove('hidden');
    if (!candidates.length) {
        body.innerHTML = `
            <div class="pipeline-empty">
                <strong>No fresh BUY/STRONG_BUY signal was generated.</strong>
                <span>The pipeline stopped inside Analysis/FinalDecisionService before Execution Intelligence.</span>
            </div>`;
        return;
    }

    body.innerHTML = candidates.map((candidate, index) => {
        const opportunity = nearestOpportunityForCandidate(candidate, opportunities);
        const trade = matchingTradeForCandidate(candidate, trades);
        const contextAt = opportunity?.generated_at || candidate.generated_at;
        const oneMinuteSignal = regressionNearestSignal(signals, '1m', contextAt);
        const fiveMinuteSignal = regressionNearestSignal(signals, '5m', contextAt);
        const oneHourSignal = regressionNearestSignal(signals, '1h', contextAt);
        const oneMinute = opportunity?.current_final_decision || oneMinuteSignal?.final_decision || 'NO 1m CONTEXT';
        const fiveMinute = opportunity?.five_minute_decision || fiveMinuteSignal?.final_decision || (candidate.interval_code === '5m' ? candidate.final_decision : 'MISSING');
        const oneHour = opportunity?.one_hour_decision || oneHourSignal?.final_decision || (candidate.interval_code === '1h' ? candidate.final_decision : 'MISSING');
        const evidence = Number(opportunity?.evidence_score ?? 0);
        const health = Number(opportunity?.opportunity_health ?? 0);
        const stage = String(opportunity?.replay_stage || 'NOT_REACHED').toUpperCase();
        const code = opportunity?.decision_code || 'NO_EXECUTION_EVALUATION';
        const executionDetail = (() => {
            const labels = {
                ATR_ENTRY_BLOCKED: 'ATR timing gate',
                CHASE_ENTRY_BLOCKED: 'Late-entry protection',
                MISSING_CONTEXT: 'Waiting for context',
                EVIDENCE_BUILDING: 'Evidence building',
                NO_BULLISH_EVIDENCE: 'No fresh bullish trigger',
                OPPORTUNITY_RECOVERING: 'Opportunity recovering',
                BEARISH_REVERSAL: 'Bearish reversal',
                OPPORTUNITY_HEALTH_EXHAUSTED: 'Opportunity health exhausted',
                HTF_TRANSITION_REDUCED_ENTRY: '1h BUY + 5m/1m transition · reduced entry',
                TRANSITION_CHASE_BLOCKED: 'Transition valid · price quality too late',
                BTC_CONTEXT_BLOCKED: 'BTC context hard veto',
                BTC_CONFLICT_REDUCED_PROBE: 'Exceptional strength · BTC conflict reduced probe',
                EXCEPTIONAL_PROBE_PRICE_QUALITY_BLOCKED: 'Exceptional probe · entry price quality blocked',
                WATCH_ONLY_NEEDS_FRESH_CONFIRMATION: 'WATCH-only evidence needs fresh HTF confirmation'
            };
            return labels[String(code)] || String(code).replaceAll('_', ' ');
        })();

        let evidenceState = evidence >= 7 ? 'pass' : evidence >= 4 ? 'wait' : 'fail';
        let healthState = health >= 40 ? 'pass' : health > 0 ? 'fail' : 'neutral';
        let executionState = ['CONFIRMED', 'MANAGED'].includes(stage) || ['DIRECT_BUY', 'OPPORTUNITY_CONFIRMED'].includes(String(code))
            ? 'pass' : ['BUILDING', 'RECOVERING'].includes(stage) ? 'wait' : 'fail';
        let walletState = trade ? 'pass' : 'fail';
        let exitState = trade?.exit_time ? 'pass' : trade ? 'wait' : 'neutral';
        const entryMs = trade ? new Date(trade.entry_time).getTime() : NaN;
        const exitMs = trade?.exit_time ? new Date(trade.exit_time).getTime() : Number.POSITIVE_INFINITY;
        const managementEvents = trade ? management.filter(event => {
            const t = new Date(event.generated_at).getTime();
            return Number.isFinite(t) && t >= entryMs && t <= exitMs;
        }) : [];
        const extensions = managementEvents.filter(event => String(event.action_code) === 'TAKE_PROFIT_EXTENDED');
        const lastManagement = managementEvents.at(-1);
        const lastLock = [...managementEvents].reverse().find(event => regressionBool(event.profit_lock_active));

        const stopReason = trade
            ? (trade.exit_time ? `Trade completed: ${trade.exit_reason || 'SELL'}` : 'BUY reached shadow wallet; position stayed open')
            : `${opportunity?.decision_explanation || `Execution stopped at ${code}`} | Technical snapshot: ${regressionScoreDetail(candidate)}`;

        return `
            <article class="pipeline-candidate">
                <div class="pipeline-candidate-head">
                    <div>
                        <span class="pipeline-kicker">Candidate #${index + 1} · ${escapeHtml(candidate.interval_code || '—')}</span>
                        <strong>${formatMoveTime(candidate.generated_at)} · ${formatMovePrice(candidate.latest_price)}</strong>
                    </div>
                    <div class="pipeline-candidate-actions">
                        <span class="status-pill ${trade ? 'reviewed' : 'new'}">${trade ? 'SHADOW BUY' : 'NOT EXECUTED'}</span>
                        <a class="secondary-button pipeline-chart-button" href="${trade ? regressionTradeChartUrl(runSymbol, trade, index) : regressionSignalChartUrl(runSymbol, candidate, index)}">${trade ? 'View Buy/Sell Chart' : 'View Signal Chart'}</a>
                    </div>
                </div>
                <div class="pipeline-flow">
                    ${regressionPipelineNode('Fresh analysis', `${candidate.original_decision || '—'} → ${candidate.final_decision || '—'}`, regressionPipelineState(candidate.final_decision), regressionScoreDetail(candidate), formatMoveTime(candidate.generated_at))}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('1m trigger', oneMinute, regressionPipelineState(oneMinute), regressionScoreDetail(oneMinuteSignal || candidate), oneMinuteSignal ? formatMoveTime(oneMinuteSignal.generated_at) : formatMoveTime(candidate.generated_at))}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('5m context', fiveMinute, regressionPipelineState(fiveMinute), regressionScoreDetail(fiveMinuteSignal), fiveMinuteSignal ? formatMoveTime(fiveMinuteSignal.generated_at) : '—')}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('1h context', oneHour, regressionPipelineState(oneHour), regressionScoreDetail(oneHourSignal), oneHourSignal ? formatMoveTime(oneHourSignal.generated_at) : '—')}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('Evidence', `${evidence}/7`, evidenceState, `${opportunity?.buy_count ?? 0} BUY · ${opportunity?.watch_count ?? 0} WATCH · ${opportunity?.bearish_count ?? 0} bearish`, opportunity ? formatMoveTime(opportunity.generated_at) : '—')}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('Health', `${health}/100`, healthState, `Evidence ${evidence}/7 · minimum health 40`, opportunity ? formatMoveTime(opportunity.generated_at) : '—')}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('BUY blocker', opportunity && !['CONFIRMED', 'EXECUTED', 'MANAGED'].includes(stage) ? String(code).replaceAll('_',' ') : 'CLEAR', opportunity && !['CONFIRMED', 'EXECUTED', 'MANAGED'].includes(stage) ? 'fail' : 'pass', opportunity?.decision_explanation || 'No blocking gate at this evaluation.', opportunity ? formatMoveTime(opportunity.generated_at) : '—')}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('Execution', stage, executionState, executionDetail, opportunity ? formatMoveTime(opportunity.generated_at) : '—')}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('Shadow wallet', trade ? `BUY ${formatMovePrice(trade.entry_price)}` : 'NO BUY', walletState, trade ? 'Wallet position opened' : 'No wallet write', trade ? formatMoveTime(trade.entry_time) : '—')}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('Position mgmt', trade ? (extensions.length ? `HOLD · TP PUSHED ×${extensions.length}` : (trade.exit_time ? 'MANAGED' : 'HOLD')) : 'NOT REACHED', trade ? 'wait' : 'neutral', lastManagement?.explanation || 'Trend / momentum / volume continuation check', lastManagement ? formatMoveTime(lastManagement.generated_at) : (trade ? formatMoveTime(trade.entry_time) : '—'))}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('Profit lock', lastLock ? `ACTIVE ${formatMovePrice(lastLock.profit_lock_price)}` : (trade ? 'MONITORING' : 'NOT REACHED'), lastLock ? 'pass' : (trade ? 'wait' : 'neutral'), lastLock ? `High ${formatMovePrice(lastLock.highest_price)}` : 'Activates only after profitable progress', lastLock ? formatMoveTime(lastLock.generated_at) : (trade ? formatMoveTime(trade.entry_time) : '—'))}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('Exit', trade?.exit_time ? `SELL ${formatMovePrice(trade.exit_price)}` : (trade ? 'OPEN' : 'NOT REACHED'), exitState, trade?.exit_reason || '', trade?.exit_time ? formatMoveTime(trade.exit_time) : (trade ? 'OPEN' : '—'))}
                </div>
                <div class="pipeline-stop-reason"><strong>Diagnosis:</strong> ${escapeHtml(stopReason)}</div>
            </article>`;
    }).join('');
}

async function loadRegressionDetail(runId, includeTables = true) {
    const run = await api(`/api/administration/regression-tests/runs/${runId}`);
    activeRegressionRunId = runId;

    const active = document.getElementById('regression-active');
    active.classList.remove('hidden');
    document.getElementById('regression-active-title').textContent = `#${run.id} ${run.test_name} · ${run.symbol}`;
    document.getElementById('regression-active-status').textContent = run.status;
    document.getElementById('regression-active-status').className = `status-pill ${regressionStatusClass(run.status)}`;
    const progress = Math.max(0, Math.min(100, Number(run.progress_percent || 0)));
    document.getElementById('regression-progress-bar').style.width = `${progress}%`;
    document.getElementById('regression-progress-percent').textContent = `${progress}%`;
    document.getElementById('regression-current-step').textContent = run.error_message || run.current_step || '—';

    const failurePanel = document.getElementById('regression-failure');
    const hasFailure = ['FAILED', 'ERROR'].includes(String(run.status)) &&
        Boolean(run.error_message || run.failure_exception || run.failure_root_cause || run.failure_stack_trace);
    if (hasFailure) {
        failurePanel.classList.remove('hidden');
        document.getElementById('regression-failure-run').textContent = `#${run.id} · ${run.symbol}`;
        document.getElementById('regression-failure-step').textContent = run.failure_step || run.current_step || 'Unknown phase';
        document.getElementById('regression-failure-exception').textContent = run.failure_exception || 'Regression failure';
        document.getElementById('regression-failure-message').textContent = run.error_message || 'No concise error message recorded.';
        document.getElementById('regression-failure-root').textContent = `Root cause: ${run.failure_root_cause || run.error_message || 'Unknown'}`;
        document.getElementById('regression-failure-stack').textContent = run.failure_stack_trace || 'No stack trace recorded.';
    } else {
        failurePanel.classList.add('hidden');
        document.getElementById('regression-failure-stack').textContent = '';
    }

    const finished = ['PASSED', 'FAILED', 'ERROR'].includes(String(run.status));
    const resultPanel = document.getElementById('regression-result');
    if (run.result) {
        const r = run.result;
        resultPanel.classList.remove('hidden');
        document.getElementById('regression-1m').textContent = `${r.replayable_1m_events}/${r.candles_1m}`;
        document.getElementById('regression-1m-historical').textContent = `Historical signals: ${r.signals_1m_historical}`;
        document.getElementById('regression-5m').textContent = `${r.replayable_5m_events}/${r.candles_5m}`;
        document.getElementById('regression-5m-historical').textContent = `Historical signals: ${r.signals_5m_historical}`;
        document.getElementById('regression-1h').textContent = `${r.replayable_1h_events}/${r.candles_1h}`;
        document.getElementById('regression-1h-historical').textContent = `Historical signals: ${r.signals_1h_historical}`;
        const generatedBuys = Number(r.generated_buys_1m || 0) + Number(r.generated_buys_5m || 0) + Number(r.generated_buys_1h || 0);
        const generatedTotal = Number(r.generated_signals_1m || 0) + Number(r.generated_signals_5m || 0) + Number(r.generated_signals_1h || 0);
        document.getElementById('regression-generated-buys').textContent = generatedBuys;
        document.getElementById('regression-generated-total').textContent = `Fresh signals: ${generatedTotal}`;
        document.getElementById('regression-generation-errors').textContent = r.generated_signal_errors || 0;
        document.getElementById('regression-shadow-trades').textContent = r.simulated_trades || 0;
        document.getElementById('regression-shadow-pnl').textContent = `${Number(r.simulated_realized_pnl || 0).toFixed(4)} USDT`;
        document.getElementById('regression-shadow-wallet').textContent = `Final wallet ${Number(r.simulated_final_wallet || 0).toFixed(2)} USDT`;
        document.getElementById('regression-corrections').textContent = r.decision_authority_corrections;
        document.getElementById('regression-old-reversals').textContent = r.old_hard_bearish_reversals;
        document.getElementById('regression-new-reversals').textContent = r.corrected_hard_bearish_reversals;
        document.getElementById('regression-notes').textContent = r.notes || '';
    } else {
        resultPanel.classList.add('hidden');
    }

    if (finished && includeTables) {
        const [signals, opportunities, trades, management] = await Promise.all([
            api(`/api/administration/regression-tests/runs/${runId}/signals`),
            api(`/api/administration/regression-tests/runs/${runId}/opportunities`),
            api(`/api/administration/regression-tests/runs/${runId}/trades`),
            api(`/api/administration/regression-tests/runs/${runId}/position-management`)
        ]);
        const detail = document.getElementById('regression-detail');
        detail.classList.remove('hidden');
        const generatedBuySignals = signals.filter(signal =>
            ['BUY', 'STRONG_BUY'].includes(String(signal.final_decision || ''))
        );
        document.getElementById('regression-signals-body').innerHTML = generatedBuySignals.map(signal => {
            const replay = regressionBool(signal.replay_generated);
            const hasError = Boolean(signal.generation_error);
            const highlight = replay;
            return `
                <tr${highlight ? ' class="regression-corrected"' : ''}>
                    <td>${formatMoveTime(signal.generated_at)}</td>
                    <td>${escapeHtml(signal.interval_code)}</td>
                    <td>${formatMovePrice(signal.latest_price)}</td>
                    <td>${escapeHtml(signal.original_decision || '—')}</td>
                    <td>${escapeHtml(signal.final_decision || '—')}</td>
                    <td>${escapeHtml(signal.execution_effective_decision || '—')}</td>
                    <td>${replay ? 'FRESH' : 'REFERENCE'}</td>
                    <td>${hasError ? escapeHtml(signal.generation_error) : '—'}</td>
                    <td><a class="secondary-button regression-chart-link" href="${regressionSignalChartUrl(run.symbol, signal, 0)}">View Chart</a></td>
                </tr>`;
        }).join('') || '<tr><td colspan="9">No generated BUY / STRONG_BUY signals in this test window.</td></tr>';

        document.getElementById('regression-opportunities-body').innerHTML = opportunities.map(row => `
                    <tr${String(row.replay_stage) === 'CONFIRMED' ? ' class="regression-corrected"' : ''}>
                        <td>${formatMoveTime(row.generated_at)}</td>
                        <td>${escapeHtml(row.replay_stage || '—')}</td>
                        <td>${escapeHtml(row.current_final_decision || '—')}</td>
                        <td>${escapeHtml(row.five_minute_decision || '—')}</td>
                        <td>${escapeHtml(row.one_hour_decision || '—')}</td>
                        <td>${row.evidence_score ?? 0} (${row.buy_count ?? 0}B/${row.watch_count ?? 0}W)</td>
                        <td>${row.opportunity_health ?? 0}/100</td>
                        <td>${row.recommended_position_percent ?? 0}%</td>
                        <td>${escapeHtml(row.decision_code || '—')}</td>
                    </tr>`).join('') || '<tr><td colspan="9">No 1m execution-opportunity evaluations in this window.</td></tr>';

        renderRegressionPipeline(signals, opportunities, trades, management, run.symbol);

        const tradePanel = document.getElementById('regression-trades');
        tradePanel.classList.remove('hidden');
        document.getElementById('regression-trades-body').innerHTML = trades.map((trade, index) => `
            <tr>
                <td>${index + 1}</td>
                <td>${formatMoveTime(trade.entry_time)}</td>
                <td>${formatMovePrice(trade.entry_price)}</td>
                <td>${trade.exit_time ? formatMoveTime(trade.exit_time) : 'OPEN'}</td>
                <td>${trade.exit_price ? formatMovePrice(trade.exit_price) : '—'}</td>
                <td>${escapeHtml(trade.exit_reason || 'OPEN')}</td>
                <td>${trade.realized_pnl_usdt == null ? '—' : Number(trade.realized_pnl_usdt).toFixed(4)}</td>
                <td>${trade.realized_pnl_percent == null ? '—' : Number(trade.realized_pnl_percent).toFixed(3) + '%'}</td>
                <td><a class="secondary-button regression-chart-link" href="${regressionTradeChartUrl(run.symbol, trade, index)}">View Chart</a></td>
            </tr>`).join('') || '<tr><td colspan="9">NO BUY EXECUTED in this replay window.</td></tr>';
    }

    return finished;
}

function pollRegressionRun(runId) {
    if (regressionPollTimer) window.clearInterval(regressionPollTimer);
    regressionPollTimer = window.setInterval(async () => {
        try {
            const finished = await loadRegressionDetail(runId, false);
            await loadRegressionRuns();
            if (finished) {
                window.clearInterval(regressionPollTimer);
                regressionPollTimer = null;
                await loadRegressionDetail(runId, true);
                setRegressionRunButtonRunning(false);
            }
        } catch (error) {
            window.clearInterval(regressionPollTimer);
            regressionPollTimer = null;
            setRegressionRunButtonRunning(false);
            showAdminMessage(error.message, true);
        }
    }, 1200);
}

const regressionForm = document.getElementById('regression-test-form');
if (regressionForm) {
    regressionForm.addEventListener('submit', async event => {
        event.preventDefault();
        const button = document.getElementById('regression-run');
        setRegressionRunButtonRunning(true);
        document.getElementById('regression-detail').classList.add('hidden');
        document.getElementById('regression-result').classList.add('hidden');
        document.getElementById('regression-pipeline')?.classList.add('hidden');
        try {
            const created = await api('/api/administration/regression-tests/runs', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    testName: document.getElementById('regression-test-name').value,
                    symbol: document.getElementById('regression-symbol').value,
                    startTime: regressionUtcInstant(document.getElementById('regression-start').value),
                    endTime: regressionUtcInstant(document.getElementById('regression-end').value)
                })
            });
            showAdminMessage(`Regression test #${created.id} started safely in the background.`);
            await loadRegressionDetail(created.id, false);
            await loadRegressionRuns();
            pollRegressionRun(created.id);
        } catch (error) {
            setRegressionRunButtonRunning(false);
            showAdminMessage(error.message, true);
        }
    });
}

const regressionRunsBody = document.getElementById('regression-runs-body');
if (regressionRunsBody) {
    regressionRunsBody.addEventListener('click', async event => {
        const button = event.target.closest('button[data-regression-run-id]');
        if (!button) return;
        try {
            const finished = await loadRegressionDetail(button.dataset.regressionRunId, true);
            if (!finished) pollRegressionRun(button.dataset.regressionRunId);
        } catch (error) {
            showAdminMessage(error.message, true);
        }
    });
}

const regressionReset = document.getElementById('regression-reset');
if (regressionReset) regressionReset.addEventListener('click', async () => {
    const confirmed = window.confirm('Clear ALL regression/shadow test data? This does not touch live signals, opportunities, positions, wallet or trades.');
    if (!confirmed) return;
    regressionReset.disabled = true;
    try {
        const deleted = await api('/api/administration/regression-tests/runs', {method: 'DELETE'});
        if (regressionPollTimer) { window.clearInterval(regressionPollTimer); regressionPollTimer = null; }
        activeRegressionRunId = null;
        document.getElementById('regression-active').classList.add('hidden');
        document.getElementById('regression-result').classList.add('hidden');
        document.getElementById('regression-detail').classList.add('hidden');
        document.getElementById('regression-trades').classList.add('hidden');
        document.getElementById('regression-pipeline')?.classList.add('hidden');
        setRegressionRunButtonRunning(false);
        await loadRegressionRuns();
        showAdminMessage(`Test data reset. Runs ${deleted.runs || 0}, signals ${deleted.signals || 0}, opportunities ${deleted.opportunities || 0}, positions ${deleted.positions || 0}, executions ${deleted.executions || 0} removed.`);
    } catch (error) {
        showAdminMessage(error.message, true);
    } finally {
        regressionReset.disabled = false;
    }
});

const regressionRefresh = document.getElementById('regression-refresh');
if (regressionRefresh) regressionRefresh.addEventListener('click', async () => {
    const runs = await loadRegressionRuns();
    const active = runs?.find(run => ['PENDING', 'RUNNING'].includes(String(run.status)));
    if (active) {
        await loadRegressionDetail(active.id, false);
        pollRegressionRun(active.id);
    }
});

(async function initializeRegressionUi() {
    await loadRegressionSymbols();
    const runs = await loadRegressionRuns();
    const active = runs?.find(run => ['PENDING', 'RUNNING'].includes(String(run.status)));
    if (active) {
        await loadRegressionDetail(active.id, false);
        pollRegressionRun(active.id);
    }
})().catch(error => showAdminMessage(error.message, true));
