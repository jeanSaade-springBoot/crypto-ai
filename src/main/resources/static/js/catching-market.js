const messageBox = document.getElementById('message');

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
}

async function api(url, options = {}) {
    const response = await fetch(url, options);
    if (response.status === 401) {
        window.location.assign('/login.html');
        throw new Error('Authentication session expired');
    }
    if (!response.ok) {
        let detail = '';
        try { detail = (await response.json())?.message || ''; } catch (_) { try { detail = await response.text(); } catch (_) {} }
        throw new Error(detail || `Request failed (${response.status})`);
    }
    if (response.status === 204) return null;
    return response.json();
}

function showAdminMessage(message, error = false) {
    if (!messageBox) return;
    messageBox.textContent = message;
    messageBox.classList.remove('hidden');
    messageBox.classList.toggle('success-banner', !error);
    messageBox.classList.toggle('error-banner', error);
    window.setTimeout(() => messageBox.classList.add('hidden'), 5000);
}

function initializeCatchingMarketSidebar() {
    const sidebar = document.getElementById('catching-market-sidebar');
    const toggle = document.getElementById('sidebar-toggle');
    if (!sidebar || !toggle) return;
    const storageKey = 'crypto-sidebar-collapsed';
    sidebar.classList.toggle('collapsed', window.localStorage.getItem(storageKey) === '1');
    document.body.classList.toggle('sidebar-collapsed', sidebar.classList.contains('collapsed'));
    const update = () => { const collapsed = sidebar.classList.contains('collapsed'); toggle.textContent = collapsed ? '›' : '‹'; toggle.setAttribute('aria-label', collapsed ? 'Expand navigation' : 'Collapse navigation'); };
    update();
    toggle.addEventListener('click', () => {
        sidebar.classList.toggle('collapsed');
        const collapsed = sidebar.classList.contains('collapsed');
        document.body.classList.toggle('sidebar-collapsed', collapsed);
        window.localStorage.setItem(storageKey, collapsed ? '1' : '0');
        update();
    });
}

initializeCatchingMarketSidebar();

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
    const d = window.CryptoTime.parseUtc(value);
    return d ? d.toLocaleString() : escapeHtml(value);
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
        showAdminMessage(`Could not load market symbols: ${error.message}`, true);
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
        const moves = moveGroups.flat().sort((a, b) => window.CryptoTime.parseUtc(b.endTime || 0) - window.CryptoTime.parseUtc(a.endTime || 0));
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
    if (showMessage) showAdminMessage('Catching Market settings saved. Trading logic was not changed.');
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
