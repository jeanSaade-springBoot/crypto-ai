/* FIX-132: read-only Production diagnostics, never a trading or Replay input. */
(() => {
  const el = id => document.getElementById(`fix132-${id}`);
  const now = new Date();
  el('to').value = now.toISOString().slice(0, 16);
  el('from').value = new Date(now.getTime() - 3600000).toISOString().slice(0, 16);
  document.getElementById('fix132-delivery').addEventListener('toggle', async event => {
    if (!event.target.open) return;
    try {
      const response = await fetch('/api/administration/shared-market/status');
      if (!response.ok) throw new Error(`Status unavailable (${response.status})`);
      const state = await response.json();
      el('connection').textContent = `Mode: ${state.mode}\n` +
        state.health.map(row => `${row.component}: ${row.status}${row.detail ? "; " + row.detail : ""}`).join('\n') + '\n' +
        state.states.map(row => `${row.symbol}: ${row.status}; approval ${row.cutover_source ?? "UNKNOWN"} / ${row.approval_reference ?? "not approved"}; last applied price age ${row.last_price_age_ms ?? 'unknown'} ms`).join('\n') + '\n' +
        state.pending.map(row => `${row.symbol}: ${row.event_count} ${row.status}/${row.analysis_status}; oldest row ${row.oldest_event}`).join('\n');
    } catch (error) { el('connection').textContent = error.message; }
  });
  el('load').addEventListener('click', async () => {
    try {
      el('status').textContent = 'Loading…';
      const query = new URLSearchParams({symbol: el('symbol').value.trim(), from: el('from').value + ':00Z', to: el('to').value + ':00Z'});
      const response = await fetch('/api/administration/shared-market/events?' + query);
      if (!response.ok) throw new Error(`Request failed (${response.status})`);
      const result = await response.json();
      el('rows').replaceChildren();
      for (const row of result.rows) {
        const tr = document.createElement('tr');
        for (const value of [row.symbol_sequence, row.candle_open_time, row.source, row.price_outcome || row.status,
          row.analysis_status, row.processing_signal_id, row.processing_status, row.processing_failure_stage, row.analysis_not_before, row.eligibility_reason, row.observer_status, row.last_error, row.protection_delay_ms, row.analysis_delay_ms]) {
          const td = document.createElement('td'); td.textContent = value ?? '—'; tr.appendChild(td);
        }
        el('rows').appendChild(tr);
      }
      el('status').textContent = result.truncated ? 'First 500 events; narrow the window to see the remaining events.' : `${result.rows.length} events. Empty results do not prove uninterrupted coverage.`;
    } catch (error) { el('status').textContent = error.message; }
  });
})();
