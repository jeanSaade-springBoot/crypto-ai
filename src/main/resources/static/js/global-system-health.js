/* FIX-071B: global, read-only System Health indicator. It never changes trading state. */
(() => {
    const HEALTH_URL = '/api/system-health/daily';
    const REFRESH_MS = 60000;

    function normalize(value) {
        const v = String(value || 'OK').toUpperCase();
        return ['CRITICAL', 'WARNING', 'OK'].includes(v) ? v : 'WARNING';
    }

    function healthLink() {
        return document.querySelector('.sidebar-nav a[href="/system-health"]');
    }

    function ensureBadge(link) {
        if (!link) return null;
        let badge = link.querySelector('.global-health-badge');
        if (!badge) {
            badge = document.createElement('span');
            badge.className = 'global-health-badge global-health-ok';
            badge.textContent = 'OK';
            link.appendChild(badge);
        }
        return badge;
    }

    function ensureBanner() {
        let banner = document.getElementById('global-system-health-banner');
        if (banner) return banner;
        banner = document.createElement('a');
        banner.id = 'global-system-health-banner';
        banner.href = '/system-health';
        banner.className = 'global-system-health-banner hidden';
        const main = document.querySelector('main');
        if (main) main.prepend(banner);
        else document.body.prepend(banner);
        return banner;
    }

    function topAlert(data, status) {
        const alerts = Array.isArray(data?.alerts) ? data.alerts : [];
        const matching = alerts.find(a => normalize(a.status) === status) || alerts[0];
        if (!matching) return status === 'CRITICAL' ? 'Critical production health check failed.' : 'Production health needs attention.';
        return matching.message || matching.title || 'Production health needs attention.';
    }

    function render(data) {
        const status = normalize(data?.status);
        const link = healthLink();
        const badge = ensureBadge(link);
        if (badge) {
            badge.className = `global-health-badge global-health-${status.toLowerCase()}`;
            badge.textContent = status;
            badge.title = `System Health: ${status}`;
        }
        if (link) link.classList.toggle('global-health-nav-critical', status === 'CRITICAL');

        const banner = ensureBanner();
        if (!banner) return;
        if (status === 'CRITICAL') {
            banner.className = 'global-system-health-banner critical';
            banner.innerHTML = `<strong>System Health CRITICAL</strong><span>${escapeText(topAlert(data, status))}</span><em>View health ›</em>`;
        } else {
            banner.className = 'global-system-health-banner hidden';
            banner.textContent = '';
        }
    }

    function escapeText(value) {
        return String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    }

    async function refresh() {
        try {
            const response = await fetch(HEALTH_URL, {headers: {'Accept': 'application/json'}, cache: 'no-store'});
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            render(await response.json());
        } catch (_) {
            // Endpoint failure is itself operationally important: make the menu visible without inventing a trading diagnosis.
            render({status: 'WARNING', alerts: [{status: 'WARNING', message: 'System Health status could not be refreshed.'}]});
        }
    }

    refresh();
    window.setInterval(refresh, REFRESH_MS);
})();
