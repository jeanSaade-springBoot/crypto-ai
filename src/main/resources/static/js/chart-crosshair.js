(() => {
  'use strict';

  const DEFAULT_TIME_ZONE = 'Asia/Riyadh';

  function adaptiveValue(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) return '—';
    const abs = Math.abs(n);
    const digits = abs >= 1000 ? 2 : abs >= 1 ? 4 : abs >= 0.01 ? 6 : 10;
    return n.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: digits });
  }

  function formatTime(value, timeZone = DEFAULT_TIME_ZONE) {
    const d = new Date(Number(value));
    if (Number.isNaN(d.getTime())) return '—';
    // FIX-070: every chart pointer uses one explicit KSA display format while timestamps stay UTC internally.
    return new Intl.DateTimeFormat('en-GB', {
      timeZone,
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    }).format(d).replace(',', '');
  }

  function liveYRange(chart) {
    const g = chart?.w?.globals;
    if (!g) return null;
    const mins = Array.from(g.minYArr || []).map(Number).filter(Number.isFinite);
    const maxs = Array.from(g.maxYArr || []).map(Number).filter(Number.isFinite);
    if (!mins.length || !maxs.length) return null;
    const min = Math.min(...mins);
    const max = Math.max(...maxs);
    return max > min ? { min, max } : null;
  }

  function bind(host, chart, options = {}) {
    if (!host || !chart) return () => {};
    if (typeof host.__cryptoChartCrosshairCleanup === 'function') host.__cryptoChartCrosshairCleanup();
    host.querySelectorAll('.crypto-chart-crosshair-v,.crypto-chart-crosshair-h,.crypto-chart-axis-label').forEach(node => node.remove());

    // FIX-070: one display-only crosshair implementation is shared by Dashboard, Trade Activity,
    // Trade Inspector and Proven Analysis. It never captures pointer events, so Apex zoom/pan stays intact.
    if (window.getComputedStyle(host).position === 'static') host.style.position = 'relative';
    const make = cls => {
      const node = document.createElement('div');
      node.className = cls;
      node.setAttribute('aria-hidden', 'true');
      host.appendChild(node);
      return node;
    };
    const ui = {
      vertical: make('crypto-chart-crosshair-v'),
      horizontal: make('crypto-chart-crosshair-h'),
      price: make('crypto-chart-axis-label crypto-chart-price-label'),
      time: make('crypto-chart-axis-label crypto-chart-time-label')
    };

    const hide = () => Object.values(ui).forEach(node => { node.style.display = 'none'; });
    const onMove = event => {
      const active = typeof options.chartProvider === 'function' ? options.chartProvider() : chart;
      const grid = host.querySelector('.apexcharts-grid') || host.querySelector('.apexcharts-inner');
      const g = active?.w?.globals;
      if (!grid || !g) return hide();
      const rect = grid.getBoundingClientRect();
      if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) return hide();

      const hostRect = host.getBoundingClientRect();
      const gx = rect.left - hostRect.left;
      const gy = rect.top - hostRect.top;
      const px = event.clientX - rect.left;
      const py = event.clientY - rect.top;
      const minX = Number(g.minX);
      const maxX = Number(g.maxX);
      const yRange = typeof options.yRange === 'function' ? options.yRange(active) : liveYRange(active);
      if (!Number.isFinite(minX) || !Number.isFinite(maxX) || maxX <= minX || !yRange) return hide();

      const xRatio = Math.max(0, Math.min(1, px / Math.max(1, rect.width)));
      const yRatio = Math.max(0, Math.min(1, py / Math.max(1, rect.height)));
      const timeValue = minX + xRatio * (maxX - minX);
      const yValue = yRange.max - yRatio * (yRange.max - yRange.min);

      ui.vertical.style.display = 'block';
      ui.vertical.style.left = `${gx + px}px`;
      ui.vertical.style.top = `${gy}px`;
      ui.vertical.style.height = `${rect.height}px`;
      ui.horizontal.style.display = 'block';
      ui.horizontal.style.left = `${gx}px`;
      ui.horizontal.style.top = `${gy + py}px`;
      ui.horizontal.style.width = `${rect.width}px`;

      const valueFormatter = options.valueFormatter || adaptiveValue;
      ui.price.textContent = valueFormatter(yValue);
      ui.price.style.display = 'block';
      ui.price.style.left = `${gx + rect.width + 5}px`;
      ui.price.style.top = `${gy + py}px`;

      const timeFormatter = options.timeFormatter || (v => formatTime(v, options.timeZone || DEFAULT_TIME_ZONE));
      ui.time.textContent = timeFormatter(timeValue);
      const labelLeft = Math.max(gx + 72, Math.min(gx + rect.width - 72, gx + px));
      ui.time.style.display = 'block';
      ui.time.style.left = `${labelLeft}px`;
      ui.time.style.top = `${gy + rect.height + 5}px`;
    };

    host.addEventListener('pointermove', onMove, true);
    host.addEventListener('pointerleave', hide, true);
    const cleanup = () => {
      host.removeEventListener('pointermove', onMove, true);
      host.removeEventListener('pointerleave', hide, true);
      Object.values(ui).forEach(node => node.remove());
      if (host.__cryptoChartCrosshairCleanup === cleanup) host.__cryptoChartCrosshairCleanup = null;
    };
    host.__cryptoChartCrosshairCleanup = cleanup;
    return cleanup;
  }

  window.CryptoChartCrosshair = { bind, formatTime, adaptiveValue, liveYRange };
})();
