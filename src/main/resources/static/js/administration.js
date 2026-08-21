async function api(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) {
        let message = 'Request failed';
        try { const json = await response.json(); message = json.message || json.error || message; }
        catch (_) { const text = await response.text(); if (text) message = text; }
        throw new Error(message);
    }
    return response.status === 204 ? null : response.json();
}

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

function initializeAdministrationSidebar() {
    const sidebar = document.getElementById('administration-sidebar');
    const toggle = document.getElementById('sidebar-toggle');
    if (!sidebar || !toggle) return;
    const storageKey = 'crypto-sidebar-collapsed';
    const stored = window.localStorage.getItem(storageKey) === '1';
    sidebar.classList.toggle('collapsed', stored);
    document.body.classList.toggle('sidebar-collapsed', stored);
    const updateToggle = () => { const collapsed = sidebar.classList.contains('collapsed'); toggle.textContent = collapsed ? '›' : '‹'; toggle.setAttribute('aria-label', collapsed ? 'Expand navigation' : 'Collapse navigation'); };
    updateToggle();
    toggle.addEventListener('click', () => {
        if (window.matchMedia('(max-width: 760px)').matches) { sidebar.classList.toggle('mobile-open'); toggle.textContent = sidebar.classList.contains('mobile-open') ? '×' : '☰'; return; }
        sidebar.classList.toggle('collapsed'); const collapsed = sidebar.classList.contains('collapsed'); document.body.classList.toggle('sidebar-collapsed', collapsed); window.localStorage.setItem(storageKey, collapsed ? '1' : '0'); updateToggle();
    });
}

initializeAdministrationSidebar();

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

const adminRefresh = document.getElementById('refresh');
if (adminRefresh) adminRefresh.addEventListener('click', loadCoins);
loadCoins();
