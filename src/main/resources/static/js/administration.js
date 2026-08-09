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
    document.getElementById('price-move-min-duration').value = Math.max(6, Number(settings.minimumDurationMinutes ?? 6));
    document.getElementById('price-move-retracement').value = Number(settings.retracementClosePercent ?? 30);
    document.getElementById('price-move-cooldown').value = Number(settings.cooldownMinutes ?? 10);
    document.getElementById('price-move-retention').value = Number(settings.retentionDays ?? 7);
}

async function loadPriceMoves() {
    if (!priceMoveBody) return;
    try {
        const moves = await api('/api/administration/debug/price-moves');
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
                    minimumDurationMinutes: Number(document.getElementById('price-move-min-duration').value),
                    retracementClosePercent: Number(document.getElementById('price-move-retracement').value),
                    cooldownMinutes: Number(document.getElementById('price-move-cooldown').value),
                    retentionDays: Number(document.getElementById('price-move-retention').value)
                })
            });
            showAdminMessage('Debug Market Move Tracker settings saved. Trading logic was not changed.');
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
    } catch (error) {
        body.innerHTML = `<tr><td colspan="7">${escapeHtml(error.message)}</td></tr>`;
    }
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
        document.getElementById('regression-corrections').textContent = r.decision_authority_corrections;
        document.getElementById('regression-old-reversals').textContent = r.old_hard_bearish_reversals;
        document.getElementById('regression-new-reversals').textContent = r.corrected_hard_bearish_reversals;
        document.getElementById('regression-notes').textContent = r.notes || '';
    } else {
        resultPanel.classList.add('hidden');
    }

    if (finished && includeTables) {
        const [signals, opportunities] = await Promise.all([
            api(`/api/administration/regression-tests/runs/${runId}/signals`),
            api(`/api/administration/regression-tests/runs/${runId}/opportunities`)
        ]);
        const detail = document.getElementById('regression-detail');
        detail.classList.remove('hidden');
        document.getElementById('regression-signals-body').innerHTML = signals.map(signal => {
            const replay = regressionBool(signal.replay_generated);
            const hasError = Boolean(signal.generation_error);
            const highlight = replay && ['BUY', 'STRONG_BUY'].includes(String(signal.final_decision || ''));
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
                </tr>`;
        }).join('') || '<tr><td colspan="8">No generated signals in this test window.</td></tr>';

        document.getElementById('regression-opportunities-body').innerHTML = opportunities
            .filter(row => regressionBool(row.old_hard_bearish_reversal) || regressionBool(row.corrected_hard_bearish_reversal))
            .map(row => {
                const oldHard = regressionBool(row.old_hard_bearish_reversal);
                const newHard = regressionBool(row.corrected_hard_bearish_reversal);
                return `
                    <tr${oldHard && !newHard ? ' class="regression-corrected"' : ''}>
                        <td>${formatMoveTime(row.generated_at)}</td>
                        <td>${escapeHtml(row.current_original_decision || '—')}</td>
                        <td>${escapeHtml(row.current_final_decision || '—')}</td>
                        <td>${escapeHtml(row.five_minute_decision || '—')}</td>
                        <td>${escapeHtml(row.one_hour_decision || '—')}</td>
                        <td>${oldHard ? 'YES' : 'NO'}</td>
                        <td>${newHard ? 'YES' : 'NO'}</td>
                        <td>${escapeHtml(row.decision_code || '—')}</td>
                    </tr>`;
            }).join('') || '<tr><td colspan="8">No hard bearish reversal rows in this window.</td></tr>';
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
                document.getElementById('regression-run').disabled = false;
            }
        } catch (error) {
            window.clearInterval(regressionPollTimer);
            regressionPollTimer = null;
            document.getElementById('regression-run').disabled = false;
            showAdminMessage(error.message, true);
        }
    }, 1200);
}

const regressionForm = document.getElementById('regression-test-form');
if (regressionForm) {
    regressionForm.addEventListener('submit', async event => {
        event.preventDefault();
        const button = document.getElementById('regression-run');
        button.disabled = true;
        document.getElementById('regression-detail').classList.add('hidden');
        document.getElementById('regression-result').classList.add('hidden');
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
            button.disabled = false;
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

const regressionRefresh = document.getElementById('regression-refresh');
if (regressionRefresh) regressionRefresh.addEventListener('click', loadRegressionRuns);
loadRegressionRuns();
