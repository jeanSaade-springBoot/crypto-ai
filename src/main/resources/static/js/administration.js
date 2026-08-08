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
// DEBUG-ONLY Price Move Monitor
// This UI is intentionally isolated from every live trading decision path.
// -----------------------------------------------------------------------------
const priceMoveBody = document.getElementById('price-move-body');

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

async function loadPriceMoveSettings() {
    const settings = await api('/api/administration/debug/price-moves/settings');
    document.getElementById('price-move-enabled').checked = Boolean(settings.enabled);
    document.getElementById('price-move-threshold').value = Number(settings.minimumMovePercent ?? 0.30);
    document.getElementById('price-move-window').value = Number(settings.windowMinutes ?? 30);
    document.getElementById('price-move-retention').value = Number(settings.retentionDays ?? 7);
}

async function loadPriceMoves() {
    if (!priceMoveBody) return;
    try {
        const moves = await api('/api/administration/debug/price-moves');
        const newCount = moves.filter(move => move.reviewStatus === 'NEW').length;
        const upCount = moves.filter(move => move.direction === 'UP').length;
        const downCount = moves.filter(move => move.direction === 'DOWN').length;
        document.getElementById('price-move-new-count').textContent = newCount;
        document.getElementById('price-move-up-count').textContent = upCount;
        document.getElementById('price-move-down-count').textContent = downCount;

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
                    <td><span class="status-pill ${status}">${escapeHtml(move.reviewStatus)}</span></td>
                    <td class="price-move-actions">
                        <button type="button" class="secondary-button" data-move-id="${move.id}" data-review-status="REVIEWED">Reviewed</button>
                        <button type="button" class="secondary-button" data-move-id="${move.id}" data-review-status="IGNORED">Ignore</button>
                    </td>
                </tr>`;
        }).join('') || '<tr><td colspan="10">No qualifying price moves detected yet.</td></tr>';
    } catch (error) {
        priceMoveBody.innerHTML = `<tr><td colspan="10">${escapeHtml(error.message)}</td></tr>`;
        showAdminMessage(error.message, true);
    }
}

const priceMoveSettingsForm = document.getElementById('price-move-settings-form');
if (priceMoveSettingsForm) {
    priceMoveSettingsForm.addEventListener('submit', async event => {
        event.preventDefault();
        try {
            await api('/api/administration/debug/price-moves/settings', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    enabled: document.getElementById('price-move-enabled').checked,
                    minimumMovePercent: Number(document.getElementById('price-move-threshold').value),
                    windowMinutes: Number(document.getElementById('price-move-window').value),
                    retentionDays: Number(document.getElementById('price-move-retention').value)
                })
            });
            showAdminMessage('Debug Price Move Monitor settings saved. Trading logic was not changed.');
            await loadPriceMoves();
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
    priceMoveRefresh.addEventListener('click', loadPriceMoves);
}

Promise.all([loadPriceMoveSettings(), loadPriceMoves()]).catch(error => showAdminMessage(error.message, true));
