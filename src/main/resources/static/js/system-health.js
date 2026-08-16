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

async function loadSystemHealthRuntimeConfiguration() {
    const target = document.getElementById('admin-schedule-groups');
    if (!target) return;
    try {
        const schedule = await api('/api/dashboard/runtime-configuration');
        const groups = schedule?.groups || [];
        target.innerHTML = groups.length ? groups.map(group => `
            <article class="schedule-group">
                <h3>${escapeHtml(group.name || 'Schedule')}</h3>
                <div class="schedule-entry-list">
                    ${(group.entries || []).map(item => `
                        <div class="schedule-entry">
                            <div class="schedule-entry-heading"><strong>${escapeHtml(item.name || '—')}</strong><span class="badge ${item.enabled ? 'buy' : 'reject'}">${item.enabled ? 'ENABLED' : 'DISABLED'}</span></div>
                            <span class="schedule-cadence">${escapeHtml(item.cadence || '—')}</span>
                            <small>${escapeHtml(item.detail || '')}</small>
                            ${item.delayMs == null ? '' : `<code>${Number(item.delayMs).toLocaleString()} ms</code>`}
                        </div>`).join('')}
                </div>
            </article>`).join('') : '<div class="empty">No runtime schedule configuration was returned.</div>';
    } catch (error) {
        target.innerHTML = `<div class="empty">${escapeHtml(error.message)}</div>`;
    }
}

async function loadSystemHealthAiOperations() {
    const period = document.getElementById('admin-ai-period')?.value || 'ALL_TIME';
    const updated = document.getElementById('admin-ai-updated');
    try {
        const [summary, opportunities] = await Promise.all([
            api(`/api/execution-intelligence/summary?period=${encodeURIComponent(period)}`),
            api('/api/execution-intelligence/opportunities/active')
        ]);
        const rows = Array.isArray(opportunities) ? opportunities : [];
        const building = rows.filter(o => String(o.status || '').toUpperCase() === 'BUILDING').length;
        const recovering = rows.filter(o => String(o.status || '').toUpperCase() === 'WEAKENING' && Number(o.healthMomentum || 0) > 0).length;
        const weakening = rows.filter(o => String(o.status || '').toUpperCase() === 'WEAKENING' && Number(o.healthMomentum || 0) <= 0).length;
        const ready = rows.filter(o => String(o.status || '').toUpperCase() === 'CONFIRMED').length;
        const values = {
            'admin-pipeline-coins-scanned': summary.coinsScanned || 0,
            'admin-pipeline-opportunities-found': summary.opportunitiesFound || 0,
            'admin-pipeline-building': summary.buildingNow ?? building,
            'admin-pipeline-weakening': summary.weakeningNow ?? weakening,
            'admin-pipeline-recovering': summary.recoveringNow ?? recovering,
            'admin-pipeline-ready': summary.readyNow ?? ready,
            'admin-pipeline-blocked-rejected': summary.blockedRejected || 0,
            'admin-pipeline-executed': summary.executed || 0,
            'admin-pipeline-managed': summary.activePositions || 0,
            'admin-pipeline-closed': summary.closedTrades || 0
        };
        Object.entries(values).forEach(([id, value]) => {
            const node = document.getElementById(id);
            if (node) node.textContent = value;
        });
        if (updated) updated.textContent = summary.updatedAt ? window.CryptoTime.formatLocal(summary.updatedAt) : 'Live';
    } catch (error) {
        if (updated) updated.textContent = error.message;
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
        await Promise.all([loadSystemHealthAiOperations(), loadSystemHealthRuntimeConfiguration()]);
    } finally {
        if (button) button.disabled = false;
    }
}

initializeSystemHealthSidebar();
document.getElementById('admin-ai-period')?.addEventListener('change', loadSystemHealthAiOperations);
document.getElementById('refresh-system-health')?.addEventListener('click', refreshSystemHealth);
refreshSystemHealth();
