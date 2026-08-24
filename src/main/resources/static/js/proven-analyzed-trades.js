function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'\"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','\"':'&quot;'}[char]));
}
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
function showAdminMessage(message, error = false) {
    const node=document.getElementById('message'); if(!node) return;
    node.textContent=message; node.classList.remove('hidden');
    node.classList.toggle('success-banner',!error); node.classList.toggle('error-banner',error);
    window.setTimeout(()=>node.classList.add('hidden'),5000);
}
function formatMovePrice(value) {
    const n=Number(value); if(!Number.isFinite(n)) return '—';
    if(Math.abs(n)>=1000) return n.toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:4});
    if(Math.abs(n)>=1) return n.toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:6});
    return n.toLocaleString(undefined,{minimumFractionDigits:4,maximumFractionDigits:10});
}
function formatMoveTime(value) { if(!value) return '—'; const d=window.CryptoTime.parseUtc(value); return d?d.toLocaleString():escapeHtml(value); }
function initializeProvenTradesSidebar(){
 const sidebar=document.getElementById('proven-trades-sidebar'),toggle=document.getElementById('sidebar-toggle'); if(!sidebar||!toggle)return;
 const key='crypto-sidebar-collapsed', stored=localStorage.getItem(key)==='1'; sidebar.classList.toggle('collapsed',stored); document.body.classList.toggle('sidebar-collapsed',stored);
 const sync=()=>{const c=sidebar.classList.contains('collapsed');toggle.textContent=c?'›':'‹';}; sync();
 toggle.addEventListener('click',()=>{ if(matchMedia('(max-width:760px)').matches){sidebar.classList.toggle('mobile-open');toggle.textContent=sidebar.classList.contains('mobile-open')?'×':'☰';return;} sidebar.classList.toggle('collapsed');const c=sidebar.classList.contains('collapsed');document.body.classList.toggle('sidebar-collapsed',c);localStorage.setItem(key,c?'1':'0');sync();});
}
initializeProvenTradesSidebar();

// -----------------------------------------------------------------------------
// AI REGRESSION TESTS
// Historical read-only replay. Test outputs are isolated from live trading data.
// -----------------------------------------------------------------------------
let regressionPollTimer = null;
let activeRegressionRunId = null;

function regressionUtcInstant(value) {
    return window.CryptoTime.localInputToUtcIso(value);
}

function regressionBool(value) {
    return value === true || value === 1 || value === '1';
}

function regressionUtcLocalValue(date) {
    return window.CryptoTime.utcToLocalInput(date);
}

async function loadRegressionSymbols() {
    const select = document.getElementById('regression-symbol');
    if (!select) return;
    const previous = select.value;
    try {
        const coins = await api('/api/administration/coins');
        select.innerHTML = coins.map(coin => `
            <option value="${escapeHtml(coin.symbol)}"${coin.symbol === previous ? ' selected' : ''}>
                ${escapeHtml(coin.symbol)}${coin.enabled ? '' : ' · disabled'}
            </option>`).join('') || '<option value="">No configured coins</option>';
        if (![...select.options].some(option => option.selected) && select.options.length) select.selectedIndex = 0;
    } catch (error) {
        select.innerHTML = '<option value="">Configured coins unavailable</option>';
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
        // FIX-088: Recent Test Runs is view-only; Resume/recovery actions were removed.
        body.innerHTML = runs.map(run => `
            <tr>
                <td>#${run.id}</td>
                <td><strong>${escapeHtml(run.test_name)}</strong></td>
                <td>${escapeHtml(run.symbol)}</td>
                <td>${formatMoveTime(run.start_time)} → ${formatMoveTime(run.end_time)}</td>
                <td><span class="status-pill ${regressionStatusClass(run.status)}">${escapeHtml(run.status)}</span></td>
                <td>${Number(run.progress_percent || 0)}%</td>
                <td><label class="proven-success-check" title="Save every closed trade from this run in Proven trades"><input type="checkbox" data-proven-run-toggle="${run.id}" ${Number(run.closed_trade_count || 0) > 0 && Number(run.proven_trade_count || 0) === Number(run.closed_trade_count || 0) ? 'checked' : ''} ${Number(run.closed_trade_count || 0) === 0 ? 'disabled' : ''}></label></td>
                <td>
                    <button type="button" class="secondary-button" data-regression-run-id="${run.id}">View</button>
                    ${run.active_worker && ['PENDING','RUNNING'].includes(String(run.status))
                        ? `<button type="button" class="danger-button" data-regression-stop-id="${run.id}">Stop Test</button>` : ''}
                </td>
            </tr>
        `).join('') || '<tr><td colspan="8">No regression tests have been run yet.</td></tr>';
        const active = runs.find(run => Boolean(run.active_worker) || ['PENDING', 'RUNNING'].includes(String(run.status)));
        setRegressionRunButtonRunning(Boolean(active), active);
        const resetButton = document.getElementById('regression-reset');
        if (resetButton) {
            resetButton.disabled = runs.some(run => Boolean(run.active_worker));
            resetButton.title = runs.some(run => Boolean(run.active_worker)) ? 'A Replay/Test worker is still active. Stop it before Delete Data.' : 'Clear all isolated regression/shadow test data';
        }
        return runs;
    } catch (error) {
        body.innerHTML = `<tr><td colspan="8">${escapeHtml(error.message)}</td></tr>`;
    }
}


async function loadRegressionArchives() {
    const body = document.getElementById('regression-archives-body');
    if (!body) return [];
    try {
        const rows = await api('/api/administration/regression-tests/archives');
        body.innerHTML = rows.map(a => `<tr>
            <td>#${a.archive_batch_id}</td><td>#${a.source_test_run_id}</td><td><strong>${escapeHtml(a.test_name)}</strong></td>
            <td>${escapeHtml(a.symbol)}</td><td>${formatMoveTime(a.start_time)} → ${formatMoveTime(a.end_time)}</td>
            <td>${formatMoveTime(a.archived_at)}</td><td><button type="button" class="secondary-button" data-regression-archive-view="${a.archive_batch_id}">View</button></td>
        </tr>`).join('') || '<tr><td colspan="7">No archived test runs yet.</td></tr>';
        return rows;
    } catch (error) { body.innerHTML = `<tr><td colspan="8">${escapeHtml(error.message)}</td></tr>`; return []; }
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


let regressionPipelineCache = null;
let regressionDetailRequestToken = 0;

function regressionOpportunityStagePriority(stage) {
    const value = String(stage || '').toUpperCase();
    return ({EXECUTED: 6, CONFIRMED: 5, MANAGED: 4, BLOCKED: 3, BUILDING: 2, RECOVERING: 2, WEAKENING: 1, CANCELLED: 0})[value] ?? 0;
}

function regressionCandidateBadge(opportunity, trade) {
    const stage = String(opportunity?.replay_stage || '').toUpperCase();
    if (stage === 'EXECUTED' && trade) return {label: 'EXECUTED BUY', css: 'reviewed'};
    if (stage === 'CONFIRMED') return {label: 'BUY CONFIRMED', css: 'reviewed'};
    if (stage === 'BLOCKED') return {label: 'BUY BLOCKED', css: 'ignored'};
    if (stage === 'CANCELLED') return {label: 'CANCELLED', css: 'ignored'};
    if (['BUILDING', 'RECOVERING', 'WEAKENING'].includes(stage)) return {label: 'BUY CANDIDATE', css: 'new'};
    return {label: 'NOT EXECUTED', css: 'new'};
}

function regressionPipelineFilterMatch(item, filter) {
    if (!filter || filter === 'ALL') return true;
    const stage = String(item.opportunity?.replay_stage || '').toUpperCase();
    const code = String(item.opportunity?.decision_code || '').toUpperCase();
    const finalDecision = String(item.candidate?.final_decision || '').toUpperCase();
    const originalDecision = String(item.candidate?.original_decision || '').toUpperCase();
    const five = String(item.opportunity?.five_minute_decision || item.fiveMinuteSignal?.final_decision || '').toUpperCase();
    const one = String(item.opportunity?.one_hour_decision || item.oneHourSignal?.final_decision || '').toUpperCase();
    const bearish = value => ['SELL', 'STRONG_SELL'].includes(value);

    if (filter === 'EXECUTED') return stage === 'EXECUTED' && Boolean(item.trade);
    if (filter === 'BUY_CANDIDATES') return !item.trade && ['BUY', 'STRONG_BUY'].some(v => v === finalDecision || v === originalDecision);
    if (filter === 'BLOCKED_CONTEXT') {
        return ['MISSING_CONTEXT', 'WATCH_ONLY_NEEDS_FRESH_CONFIRMATION', 'BEARISH_REVERSAL'].includes(code)
            || ((stage === 'BLOCKED' || stage === 'CANCELLED') && (bearish(five) || bearish(one)));
    }
    if (filter === 'BLOCKED_ATR') return code === 'ATR_ENTRY_BLOCKED' || code === 'TRANSITION_CHASE_BLOCKED' || code === 'CHASE_ENTRY_BLOCKED';
    if (filter === 'BLOCKED_BTC') return code === 'BTC_CONTEXT_BLOCKED' || code === 'EXCEPTIONAL_PROBE_PRICE_QUALITY_BLOCKED';
    if (filter === 'BUILDING') return ['BUILDING', 'RECOVERING', 'WEAKENING'].includes(stage);
    if (filter === 'BEARISH_REVERSAL') return code === 'BEARISH_REVERSAL' || stage === 'CANCELLED';
    return true;
}

function nearestOpportunityForCandidate(candidate, opportunities) {
    const candidateTime = window.CryptoTime.parseUtc(candidate.generated_at)?.getTime();

    // Never attach an execution result that happened before the BUY candidate existed.
    // The previous absolute-distance lookup could associate a fresh BUY with an older
    // ATR_ENTRY_BLOCKED row, which made the visual pipeline misleading.
    const afterCandidate = opportunities
        .map(row => ({
            row,
            delta: window.CryptoTime.parseUtc(row.generated_at)?.getTime() - candidateTime
        }))
        .filter(item => Number.isFinite(item.delta)
            && item.delta >= 0
            && item.delta <= 10 * 60 * 1000)
        .sort((a, b) => a.delta - b.delta || regressionOpportunityStagePriority(b.row?.replay_stage) - regressionOpportunityStagePriority(a.row?.replay_stage));

    return afterCandidate[0]?.row || null;
}

function matchingTradeForCandidate(candidate, trades, opportunity) {
    // A candidate owns a wallet trade only when THIS evaluation actually executed it.
    // Do not attach a later trade to an earlier BUY candidate that was BLOCKED/BUILDING.
    if (String(opportunity?.replay_stage || '').toUpperCase() !== 'EXECUTED') return null;
    const executionTime = window.CryptoTime.parseUtc(opportunity?.generated_at || candidate.generated_at)?.getTime();
    return trades
        .map(trade => ({trade, delta: Math.abs(window.CryptoTime.parseUtc(trade.entry_time)?.getTime() - executionTime)}))
        .filter(item => Number.isFinite(item.delta) && item.delta <= 90 * 1000)
        .sort((a, b) => a.delta - b.delta)[0]?.trade || null;
}

function regressionTradeChartUrl(symbol, trade, index = 0) {
    if (!trade?.entry_time || !trade?.entry_price) return '#';
    const entry = window.CryptoTime.parseUtc(trade.entry_time);
    const exit = trade.exit_time ? window.CryptoTime.parseUtc(trade.exit_time) : new Date(entry.getTime() + 60 * 60 * 1000);
    const params = new URLSearchParams({
        symbol: String(symbol || '').toUpperCase(),
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
        params.set('debugExitTime', window.CryptoTime.parseUtc(trade.exit_time).toISOString());
        params.set('debugExitPrice', String(trade.exit_price));
    }
    return `/dashboard?${params.toString()}#market`;
}


function regressionSignalChartUrl(symbol, signal, index = 0) {
    if (!signal?.generated_at || signal?.latest_price == null) return '#';
    const at = window.CryptoTime.parseUtc(signal.generated_at);
    if (Number.isNaN(at.getTime())) return '#';
    const start = new Date(at.getTime() - 20 * 60 * 1000);
    const end = new Date(at.getTime() + 40 * 60 * 1000);
    const decision = String(signal.final_decision || signal.execution_effective_decision || 'BUY').replaceAll('_', ' ');
    const params = new URLSearchParams({
        symbol: String(symbol || signal.symbol || '').toUpperCase(),
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

function regressionRecentBearishAnchor(signals, atValue) {
    const at = window.CryptoTime.parseUtc(atValue)?.getTime();
    if (!Number.isFinite(at)) return null;
    const cutoff = at - 5 * 60 * 1000;
    return [...(signals || [])]
        .filter(signal => String(signal.interval_code || '').toLowerCase() === '1m')
        .filter(signal => {
            const t = window.CryptoTime.parseUtc(signal.generated_at)?.getTime();
            if (!Number.isFinite(t) || t >= at || t < cutoff) return false;
            const d = String(signal.final_decision || signal.original_decision || '');
            return ['SELL','STRONG_SELL'].includes(d) && Number(signal.total_score ?? 999) <= 40;
        })
        .sort((a, b) => window.CryptoTime.parseUtc(b.generated_at).getTime() - window.CryptoTime.parseUtc(a.generated_at).getTime())[0] || null;
}

function regressionPreviousSignal(signals, interval, atValue) {
    const at = window.CryptoTime.parseUtc(atValue)?.getTime();
    if (!Number.isFinite(at)) return null;
    return [...(signals || [])]
        .filter(signal => String(signal.interval_code || '').toLowerCase() === String(interval).toLowerCase())
        .filter(signal => { const t = window.CryptoTime.parseUtc(signal.generated_at)?.getTime(); return Number.isFinite(t) && t < at; })
        .sort((a, b) => window.CryptoTime.parseUtc(b.generated_at).getTime() - window.CryptoTime.parseUtc(a.generated_at).getTime())[0] || null;
}

function regressionNearestSignal(signals, interval, atValue) {
    const at = window.CryptoTime.parseUtc(atValue)?.getTime();
    if (!Number.isFinite(at)) return null;
    return [...(signals || [])]
        .filter(signal => String(signal.interval_code || '').toLowerCase() === String(interval).toLowerCase())
        .filter(signal => { const t = window.CryptoTime.parseUtc(signal.generated_at)?.getTime(); return Number.isFinite(t) && t <= at; })
        .sort((a, b) => window.CryptoTime.parseUtc(b.generated_at).getTime() - window.CryptoTime.parseUtc(a.generated_at).getTime())[0] || null;
}

function regressionScoreDetail(signal) {
    if (!signal) return 'No score snapshot';
    return `Total ${signal.total_score ?? '—'} · C${signal.confidence_score ?? '—'} · T${signal.trend_score ?? '—'} · M${signal.momentum_score ?? '—'} · V${signal.volume_score ?? '—'}`;
}

function regressionReversalProbeDetail(candidate, previous, five, one) {
    const score = Number(candidate?.total_score ?? 0);
    const trend = Number(candidate?.trend_score ?? 0);
    const momentum = Number(candidate?.momentum_score ?? 0);
    const volume = Number(candidate?.volume_score ?? 0);
    const priorScore = Number(previous?.total_score ?? NaN);
    const jump = Number.isFinite(priorScore) ? score - priorScore : NaN;
    const priorDecision = String(previous?.final_decision || previous?.original_decision || 'MISSING');
    const raw = String(candidate?.original_decision || '');
    const fiveDecision = String(five?.final_decision || 'MISSING');
    const oneDecision = String(one?.final_decision || 'MISSING');
    const bearish = value => ['SELL','STRONG_SELL'].includes(String(value));
    const checks = [
        [`Raw STRONG_BUY`, raw === 'STRONG_BUY'],
        [`Score ${score} ≥ 88`, score >= 88],
        [`Trend ${trend} ≥ 17`, trend >= 17],
        [`Momentum ${momentum} ≥ 13`, momentum >= 13],
        [`Volume ${volume} ≥ 15`, volume >= 15],
        [`Prior ${priorDecision}${Number.isFinite(priorScore) ? ` ${priorScore}` : ''} bearish`, bearish(priorDecision) && (!Number.isFinite(priorScore) || priorScore <= 40)],
        [`Score jump ${Number.isFinite(jump) ? (jump >= 0 ? '+' : '') + jump : '—'} ≥ +40`, Number.isFinite(jump) && jump >= 40],
        [`5m ${fiveDecision} not bearish`, !bearish(fiveDecision) && fiveDecision !== 'MISSING'],
        [`1h ${oneDecision} not bearish`, !bearish(oneDecision) && oneDecision !== 'MISSING']
    ];
    const passed = checks.every(([, ok]) => ok);
    return {
        passed,
        text: checks.map(([label, ok]) => `${ok ? '✓' : '✕'} ${label}`).join(' · ')
    };
}

function regressionScoreGrid(candidate, oneMinuteSignal, fiveMinuteSignal, oneHourSignal) {
    const row = (label, signal) => `<div><span>${escapeHtml(label)}</span><strong>${escapeHtml(regressionScoreDetail(signal))}</strong></div>`;
    return `<div class="pipeline-score-grid">
        ${row('Candidate', candidate)}
        ${row('1m', oneMinuteSignal || candidate)}
        ${row('5m', fiveMinuteSignal)}
        ${row('1h', oneHourSignal)}
    </div>`;
}

function renderRegressionPipeline(signals, opportunities, trades, management = [], runSymbol = '') {
    const panel = document.getElementById('regression-pipeline');
    const body = document.getElementById('regression-pipeline-body');
    if (!panel || !body) return;

    regressionPipelineCache = {signals, opportunities, trades, management, runSymbol};
    const transitionTimes = new Set(opportunities
        .filter(row => ['HTF_TRANSITION_REDUCED_ENTRY', 'REDUCED_POSITION_ALLOWED', 'BREAKOUT_CONTINUATION_ENTRY']
            .includes(String(row.decision_code || '')))
        .map(row => window.CryptoTime.parseUtc(row.generated_at)?.getTime()));
    const executionTimes = new Set(opportunities
        .filter(row => String(row.replay_stage || '').toUpperCase() === 'EXECUTED')
        .map(row => window.CryptoTime.parseUtc(row.generated_at)?.getTime()));
    const candidates = signals
        .filter(signal => {
            if (!regressionBool(signal.replay_generated)) return false;
            const finalDecision = String(signal.final_decision || '');
            const originalDecision = String(signal.original_decision || '');
            const generated = window.CryptoTime.parseUtc(signal.generated_at)?.getTime();
            if (['BUY', 'STRONG_BUY'].includes(finalDecision) || ['BUY', 'STRONG_BUY'].includes(originalDecision)) return true;
            if ([...executionTimes].some(t => Math.abs(t - generated) <= 1000)) return true;
            return finalDecision === 'WATCH' && [...transitionTimes].some(t => Math.abs(t - generated) <= 1000);
        });

    panel.classList.remove('hidden');
    if (!candidates.length) {
        body.innerHTML = `
            <div class="pipeline-empty">
                <strong>No fresh BUY/STRONG_BUY signal was generated.</strong>
                <span>The pipeline stopped inside Analysis/FinalDecisionService before Execution Intelligence.</span>
            </div>`;
        return;
    }

    const items = candidates.map((candidate, originalIndex) => {
        const opportunity = nearestOpportunityForCandidate(candidate, opportunities);
        const contextAt = opportunity?.generated_at || candidate.generated_at;
        const oneMinuteSignal = regressionNearestSignal(signals, '1m', contextAt);
        const fiveMinuteSignal = regressionNearestSignal(signals, '5m', contextAt);
        const oneHourSignal = regressionNearestSignal(signals, '1h', contextAt);
        const trade = matchingTradeForCandidate(candidate, trades, opportunity);
        return {candidate, opportunity, oneMinuteSignal, fiveMinuteSignal, oneHourSignal, trade, originalIndex};
    });
    const filter = String(document.getElementById('regression-pipeline-filter')?.value || 'ALL').toUpperCase();
    const filteredItems = items.filter(item => regressionPipelineFilterMatch(item, filter));
    const count = document.getElementById('regression-pipeline-filter-count');
    if (count) count.textContent = `Showing ${filteredItems.length} of ${items.length}`;
    if (!filteredItems.length) {
        body.innerHTML = `<div class="pipeline-empty"><strong>No pipeline rows match this filter.</strong><span>Choose another filter to continue the analysis.</span></div>`;
        return;
    }

    body.innerHTML = filteredItems.slice(0, 60).map((item, index) => {
        const {candidate, opportunity, oneMinuteSignal, fiveMinuteSignal, oneHourSignal, trade, originalIndex} = item;
        const previousOneMinuteSignal = regressionRecentBearishAnchor(signals, candidate.generated_at);
        const reversalProbe = regressionReversalProbeDetail(candidate, previousOneMinuteSignal, fiveMinuteSignal, oneHourSignal);
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
                ATR_RETRACEMENT_REACHED: 'ATR retracement reached · reduced reversal entry',
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
                BTC_CONFLICT_REDUCED_PROBE: 'High-conviction reversal · BTC conflict reduced probe',
                EXCEPTIONAL_PROBE_PRICE_QUALITY_BLOCKED: 'Exceptional probe · entry price quality blocked',
                WATCH_ONLY_NEEDS_FRESH_CONFIRMATION: 'WATCH-only evidence needs fresh HTF confirmation'
            };
            return labels[String(code)] || String(code).replaceAll('_', ' ');
        })();

        let evidenceState = evidence >= 7 ? 'pass' : evidence >= 4 ? 'wait' : 'fail';
        let healthState = health >= 40 ? 'pass' : health > 0 ? 'fail' : 'neutral';
        let executionState = ['CONFIRMED', 'EXECUTED', 'MANAGED'].includes(stage) || ['DIRECT_BUY', 'OPPORTUNITY_CONFIRMED'].includes(String(code))
            ? 'pass' : ['BUILDING', 'RECOVERING'].includes(stage) ? 'wait' : 'fail';
        let walletState = trade ? 'pass' : 'fail';
        let exitState = trade?.exit_time ? 'pass' : trade ? 'wait' : 'neutral';
        const entryMs = trade ? window.CryptoTime.parseUtc(trade.entry_time)?.getTime() : NaN;
        const exitMs = trade?.exit_time ? window.CryptoTime.parseUtc(trade.exit_time)?.getTime() : Number.POSITIVE_INFINITY;
        const managementEvents = trade ? management.filter(event => {
            const t = window.CryptoTime.parseUtc(event.generated_at)?.getTime();
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
                        <span class="pipeline-kicker">Candidate #${originalIndex + 1} · ${escapeHtml(candidate.interval_code || '—')}</span>
                        <strong>${formatMoveTime(candidate.generated_at)} · ${formatMovePrice(candidate.latest_price)}</strong>
                    </div>
                    <div class="pipeline-candidate-actions">
                        ${(() => { const badge = regressionCandidateBadge(opportunity, trade); return `<span class="status-pill ${badge.css}">${badge.label}</span>`; })()}
                        <a class="secondary-button pipeline-chart-button" href="${trade ? regressionTradeChartUrl(runSymbol, trade, originalIndex) : regressionSignalChartUrl(runSymbol, candidate, originalIndex)}">${trade ? 'View Executed Trade' : 'View Candidate Chart'}</a>
                    </div>
                </div>
                ${regressionScoreGrid(candidate, oneMinuteSignal, fiveMinuteSignal, oneHourSignal)}
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
                    ${regressionPipelineNode('Reversal probe test', reversalProbe.passed ? 'QUALIFIED' : 'NOT QUALIFIED', reversalProbe.passed ? 'pass' : 'wait', reversalProbe.text, formatMoveTime(candidate.generated_at))}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('Execution', stage, executionState, executionDetail, opportunity ? formatMoveTime(opportunity.generated_at) : '—')}
                    <span class="pipeline-arrow">→</span>
                    ${regressionPipelineNode('Shadow wallet', trade ? `EXECUTED ${formatMovePrice(trade.entry_price)}` : 'NO EXECUTION', walletState, trade ? 'This candidate opened the wallet position' : 'This candidate did not open a wallet position', trade ? formatMoveTime(trade.entry_time) : '—')}
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

async function loadRegressionDetail(runId, includeTables = true, archived = false) {
    // View and Archived View share the detail area. A request token prevents a slower
    // previous click from rendering after a newer selection and visually overlapping it.
    const requestToken = ++regressionDetailRequestToken;
    const pipelineSection = document.getElementById('regression-pipeline-section');
    const pipelinePanel = document.getElementById('regression-pipeline');
    const pipelineToggle = document.getElementById('regression-pipeline-toggle');
    pipelinePanel?.classList.add('hidden');
    pipelineSection?.classList.add('hidden');
    if (pipelineToggle) { pipelineToggle.textContent = 'Expand pipeline'; pipelineToggle.setAttribute('aria-expanded', 'false'); }
    document.getElementById('regression-trades')?.classList.add('hidden');
    const base = `/api/administration/regression-tests/runs/${runId}`;
    const run = await api(base);
    if (!archived) activeRegressionRunId = runId;

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

    // FIX-073: View must keep Shadow Trades visible for active/interrupted runs too;
    // partial isolated rows are useful evidence while progress is running or after a restart.
    if (includeTables) {
        const detailResults = await Promise.allSettled([
            api(`${base}/signals`),
            api(`${base}/opportunities`),
            api(`${base}/trades`),
            api(`${base}/position-management`)
        ]);
        if (requestToken !== regressionDetailRequestToken) return finished;
        const signals = detailResults[0].status === 'fulfilled' ? detailResults[0].value : [];
        const opportunities = detailResults[1].status === 'fulfilled' ? detailResults[1].value : [];
        const trades = detailResults[2].status === 'fulfilled' ? detailResults[2].value : [];
        const management = detailResults[3].status === 'fulfilled' ? detailResults[3].value : [];

        // Active test pipeline is always available for a completed run. A missing auxiliary
        // endpoint must not make the entire pipeline section disappear; render the available
        // trace and keep the panel collapsed until the user explicitly expands it.
        const pipelinePanel = document.getElementById('regression-pipeline');
        const pipelineSection = document.getElementById('regression-pipeline-section');
        const pipelineToggle = document.getElementById('regression-pipeline-toggle');
        renderRegressionPipeline(signals, opportunities, trades, management, run.symbol);
        pipelineSection?.classList.remove('hidden');
        pipelinePanel?.classList.add('hidden');
        if (pipelineToggle) { pipelineToggle.textContent = 'Expand pipeline'; pipelineToggle.setAttribute('aria-expanded', 'false'); }

        const tradePanel = document.getElementById('regression-trades');
        // FIX-074: Shadow Trades is a first-class run result directly under the replay result area.
        // Clear both CSS and native hidden state so View cannot leave it invisible after prior UI cleanup.
        if (!tradePanel) throw new Error('Shadow Trades panel is missing from the Proven/Test page.');
        tradePanel.classList.remove('hidden');
        tradePanel.hidden = false;
        const tradeTable = tradePanel.querySelector('table');
        const tradeHead = tradeTable?.querySelector('thead');
        if (tradeHead) {
            tradeHead.innerHTML = '<tr><th title="Add/remove from Proven trades">✓</th><th>#</th><th>Symbol</th><th>BUY time</th><th>BUY price</th><th>SELL time</th><th>SELL price</th><th>Exit reason</th><th>P/L USDT</th><th>P/L %</th><th>Chart</th></tr>';
        }
        const tradeNote = tradePanel.querySelector('.form-note');
        if (tradeNote) {
            tradeNote.textContent = 'Check a closed trade after your manual review. Checked trades are copied to the persistent Proven trades table; unchecking removes them.';
        }
        document.getElementById('regression-trades-body').innerHTML = trades.map((trade, index) => `
            <tr>
                <td><label class="proven-success-check" title="Add or remove this trade from Proven trades"><input type="checkbox" aria-label="Add or remove trade ${index + 1} from Proven trades" data-proven-trade-id="${trade.id}" data-proven-run-id="${run.id}" ${regressionBool(trade.proven_success) ? 'checked' : ''}></label></td>
                <td>${index + 1}</td>
                <td><strong>${escapeHtml(String(trade.symbol || run.symbol || '—').toUpperCase())}</strong></td>
                <td>${formatMoveTime(trade.entry_time)}</td>
                <td>${formatMovePrice(trade.entry_price)}</td>
                <td>${trade.exit_time ? formatMoveTime(trade.exit_time) : 'OPEN'}</td>
                <td>${trade.exit_price ? formatMovePrice(trade.exit_price) : '—'}</td>
                <td>${escapeHtml(trade.exit_reason || 'OPEN')}</td>
                <td>${trade.realized_pnl_usdt == null ? '—' : Number(trade.realized_pnl_usdt).toFixed(4)}</td>
                <td>${trade.realized_pnl_percent == null ? '—' : Number(trade.realized_pnl_percent).toFixed(3) + '%'}</td>
                <td><button type="button" class="secondary-button regression-chart-link" data-replay-chart="1" data-chart-symbol="${escapeHtml(run.symbol)}" data-chart-index="${index}" data-chart-entry-time="${escapeHtml(trade.entry_time || '')}" data-chart-entry-price="${escapeHtml(trade.entry_price ?? '')}" data-chart-exit-time="${escapeHtml(trade.exit_time || '')}" data-chart-exit-price="${escapeHtml(trade.exit_price ?? '')}">View Chart</button></td>
            </tr>`).join('') || '<tr><td colspan="11">NO BUY EXECUTED in this replay window.</td></tr>';
    }

    return finished;
}

async function loadRegressionArchiveDetail(archiveBatchId) {
    const base = `/api/administration/regression-tests/archives/${archiveBatchId}`;
    const [run, trades] = await Promise.all([api(base), api(`${base}/trades`)]);
    const panel = document.getElementById('regression-archive-detail');
    const body = document.getElementById('regression-archive-trades-body');
    if (!panel || !body) return;
    document.getElementById('regression-archive-title').textContent = `#${run.id} ${run.test_name} · ${run.symbol}`;
    document.getElementById('regression-archive-note').textContent = `Archived replay snapshot · ${formatMoveTime(run.start_time)} → ${formatMoveTime(run.end_time)} · read-only`;
    body.innerHTML = trades.map((trade, index) => `
        <tr>
            <td>${index + 1}</td>
            <td><strong>${escapeHtml(String(trade.symbol || run.symbol || '—').toUpperCase())}</strong></td>
            <td>${formatMoveTime(trade.entry_time)}</td>
            <td>${formatMovePrice(trade.entry_price)}</td>
            <td>${trade.exit_time ? formatMoveTime(trade.exit_time) : 'OPEN'}</td>
            <td>${trade.exit_price ? formatMovePrice(trade.exit_price) : '—'}</td>
            <td>${escapeHtml(trade.exit_reason || 'OPEN')}</td>
            <td>${trade.realized_pnl_usdt == null ? '—' : Number(trade.realized_pnl_usdt).toFixed(4)}</td>
            <td>${trade.realized_pnl_percent == null ? '—' : Number(trade.realized_pnl_percent).toFixed(3) + '%'}</td>
            <td><button type="button" class="secondary-button regression-chart-link" data-replay-chart="1" data-chart-symbol="${escapeHtml(run.symbol)}" data-chart-index="${index}" data-chart-entry-time="${escapeHtml(trade.entry_time || '')}" data-chart-entry-price="${escapeHtml(trade.entry_price ?? '')}" data-chart-exit-time="${escapeHtml(trade.exit_time || '')}" data-chart-exit-price="${escapeHtml(trade.exit_price ?? '')}">View Chart</button></td>
        </tr>`).join('') || '<tr><td colspan="10">NO BUY EXECUTED in this archived replay window.</td></tr>';
    panel.classList.remove('hidden');
    panel.scrollIntoView({behavior: 'smooth', block: 'start'});
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

// -----------------------------------------------------------------------------
// FIX-065 INVESTIGATION QUEUE
// Uploaded CSV cases are persisted server-side, then each Run action delegates to
// the existing isolated regression runner. Run Selected is intentionally sequential
// because the backend correctly allows only one PENDING/RUNNING replay at a time.
// -----------------------------------------------------------------------------
let investigationBatchRunning = false;

function investigationKsaToUtcIso(value) {
    const raw = String(value || '').trim().replace(' ', 'T');
    if (!raw) throw new Error('Missing KSA timestamp.');
    const normalized = raw.length === 16 ? `${raw}:00` : raw;
    const date = new Date(`${normalized}+03:00`);
    if (Number.isNaN(date.getTime())) throw new Error(`Invalid KSA timestamp: ${value}`);
    return date.toISOString();
}

function investigationKsaDisplay(value) {
    if (!value) return '—';
    const d = window.CryptoTime.parseUtc(value);
    if (!d) return String(value);
    return new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Riyadh',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(d);
}

function parseInvestigationCsv(text) {
    const lines = String(text || '').replace(/^\uFEFF/, '').split(/\r?\n/).filter(line => line.trim());
    if (lines.length < 2) throw new Error('CSV must contain a header and at least one case.');
    const split = line => {
        const out=[]; let cur=''; let quoted=false;
        for(let i=0;i<line.length;i++){ const c=line[i]; if(c==='"'){ if(quoted && line[i+1]==='"'){cur+='"';i++;} else quoted=!quoted; } else if(c===',' && !quoted){out.push(cur.trim());cur='';} else cur+=c; }
        out.push(cur.trim()); return out;
    };
    const headers = split(lines[0]).map(h => h.toLowerCase());
    const required = ['symbol','start_ksa','end_ksa'];
    required.forEach(h => { if(!headers.includes(h)) throw new Error(`Missing CSV column: ${h}`); });
    return lines.slice(1).map((line,index) => {
        const values=split(line); const row=Object.fromEntries(headers.map((h,i)=>[h,values[i] ?? '']));
        return {
            caseName: row.case_name || `${row.symbol} investigation ${index+1}`,
            symbol: String(row.symbol || '').trim().toUpperCase(),
            startTime: investigationKsaToUtcIso(row.start_ksa),
            endTime: investigationKsaToUtcIso(row.end_ksa),
            walletId: row.wallet_id ? Number(row.wallet_id) : null,
            expectedAction: row.expected_action || null,
            notes: row.notes || null
        };
    });
}

async function loadInvestigationCases() {
    const body=document.getElementById('investigation-cases-body'); if(!body) return [];
    try {
        const rows=await api('/api/administration/regression-tests/investigation-cases');
        body.innerHTML=rows.map(row=>`<tr>
            <td><input type="checkbox" data-investigation-select="${row.id}" aria-label="Select ${escapeHtml(row.case_name)}"></td>
            <td><strong>${escapeHtml(row.case_name)}</strong></td>
            <td>${escapeHtml(row.symbol)}</td>
            <td>${escapeHtml(investigationKsaDisplay(row.start_time))}</td>
            <td>${escapeHtml(investigationKsaDisplay(row.end_time))}</td>
            <td>${row.wallet_id ?? '—'}</td>
            <td>${escapeHtml(row.expected_action || '—')}</td>
            <td>${row.last_run_id ? `#${row.last_run_id} · ${escapeHtml(row.last_run_status || '—')}` : 'Never'}</td>
            <td>${escapeHtml(row.notes || '')}</td>
            <td><div class="investigation-row-actions"><button type="button" class="secondary-button" data-investigation-run="${row.id}">Run</button><button type="button" class="secondary-button" data-investigation-delete="${row.id}">Remove</button></div></td>
        </tr>`).join('') || '<tr><td colspan="10">No saved investigation cases yet.</td></tr>';
        return rows;
    } catch(error){body.innerHTML=`<tr><td colspan="10">${escapeHtml(error.message)}</td></tr>`;return [];}
}

async function waitForInvestigationRun(runId) {
    while (true) {
        const run=await api(`/api/administration/regression-tests/runs/${runId}`);
        if (['PASSED','FAILED','ERROR'].includes(String(run.status))) return run;
        await new Promise(resolve=>setTimeout(resolve,1200));
    }
}

async function runInvestigationCase(caseId, showResult=true) {
    const created=await api(`/api/administration/regression-tests/investigation-cases/${caseId}/run`,{method:'POST'});
    if(showResult){ await loadRegressionDetail(created.id,false); }
    const result=await waitForInvestigationRun(created.id);
    if(showResult){ await loadRegressionDetail(created.id,true); }
    await Promise.all([loadRegressionRuns(),loadInvestigationCases()]);
    return result;
}

const investigationFile=document.getElementById('investigation-file');
if(investigationFile) investigationFile.addEventListener('change',async()=>{
    const file=investigationFile.files?.[0]; if(!file)return;
    try{
        const cases=parseInvestigationCsv(await file.text());
        await api('/api/administration/regression-tests/investigation-cases/batch',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(cases)});
        showAdminMessage(`${cases.length} investigation case(s) uploaded.`); await loadInvestigationCases();
    }catch(error){showAdminMessage(error.message,true);} finally{investigationFile.value='';}
});

document.getElementById('investigation-template')?.addEventListener('click',()=>{
    const csv='case_name,symbol,start_ksa,end_ksa,wallet_id,expected_action,notes\nENA-703,ENAUSDT,2026-08-23 15:29:27,2026-08-23 15:31:18,703,WAIT,FIX-064 re-test\n';
    const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([csv],{type:'text/csv'}));a.download='replay-investigation-template.csv';a.click();URL.revokeObjectURL(a.href);
});

document.getElementById('investigation-select-all')?.addEventListener('click',()=>document.querySelectorAll('[data-investigation-select]').forEach(c=>c.checked=true));
document.getElementById('investigation-clear-selection')?.addEventListener('click',()=>document.querySelectorAll('[data-investigation-select]').forEach(c=>c.checked=false));

const investigationBody=document.getElementById('investigation-cases-body');
if(investigationBody) investigationBody.addEventListener('click',async event=>{
    const run=event.target.closest('[data-investigation-run]');
    const del=event.target.closest('[data-investigation-delete]');
    try{
        if(run){ if(investigationBatchRunning)return; investigationBatchRunning=true; run.disabled=true; await runInvestigationCase(run.dataset.investigationRun,true); showAdminMessage('Investigation replay completed.'); }
        if(del){ await api(`/api/administration/regression-tests/investigation-cases/${del.dataset.investigationDelete}`,{method:'DELETE'}); await loadInvestigationCases(); }
    }catch(error){showAdminMessage(error.message,true);} finally{investigationBatchRunning=false;if(run)run.disabled=false;}
});

document.getElementById('investigation-run-selected')?.addEventListener('click',async event=>{
    const ids=[...document.querySelectorAll('[data-investigation-select]:checked')].map(c=>c.dataset.investigationSelect);
    if(!ids.length){showAdminMessage('Select at least one investigation case.',true);return;}
    if(investigationBatchRunning)return; investigationBatchRunning=true; event.currentTarget.disabled=true;
    const status=document.getElementById('investigation-batch-status');
    try{
        for(let i=0;i<ids.length;i++){ status.textContent=`Running ${i+1}/${ids.length}…`; await runInvestigationCase(ids[i],i===ids.length-1); }
        status.textContent=`Completed ${ids.length}/${ids.length}`; showAdminMessage(`${ids.length} investigation replay(s) completed sequentially.`);
    }catch(error){status.textContent='Batch stopped';showAdminMessage(error.message,true);} finally{investigationBatchRunning=false;event.currentTarget.disabled=false;await loadInvestigationCases();}
});

// FIX-069: Replay names are deterministic and derived from the actual run input so
// a saved run can always be identified without manually maintaining a label.
function regressionGeneratedTestName(symbol, startLocal, endLocal) {
    const compact = value => String(value || '')
        .replace('T', '_')
        .replace(/:/g, '-')
        .replace(/\s+/g, '');
    return `${String(symbol || 'TEST').toUpperCase()}-${compact(startLocal)}-to-${compact(endLocal)}`.slice(0, 150);
}

const regressionForm = document.getElementById('regression-test-form');
if (regressionForm) {
    regressionForm.addEventListener('submit', async event => {
        event.preventDefault();
        const button = document.getElementById('regression-run');
        setRegressionRunButtonRunning(true);
        document.getElementById('regression-detail')?.classList.add('hidden');
        document.getElementById('regression-result').classList.add('hidden');
        document.getElementById('regression-pipeline')?.classList.add('hidden');
        document.getElementById('regression-pipeline-section')?.classList.add('hidden');
        try {
            const created = await api('/api/administration/regression-tests/runs', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    testName: regressionGeneratedTestName(
                        document.getElementById('regression-symbol').value,
                        document.getElementById('regression-start').value,
                        document.getElementById('regression-end').value),
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
        const stopButton = event.target.closest('button[data-regression-stop-id]');
        if (stopButton) {
            const runId = stopButton.dataset.regressionStopId;
            if (!window.confirm(`Stop Replay/Test #${runId}? Production will continue running.`)) return;
            stopButton.disabled = true;
            stopButton.textContent = 'Stopping…';
            try {
                const result = await api(`/api/administration/regression-tests/runs/${encodeURIComponent(runId)}/stop`, {method: 'POST'});
                showAdminMessage(result.message || `Stop requested for test #${runId}.`);
                // FIX-090: poll actual worker ownership. Delete Data stays disabled until
                // active_worker=false, even if the database status has already become ERROR.
                const waitForStop = window.setInterval(async () => {
                    try {
                        const runs = await loadRegressionRuns();
                        const row = runs?.find(r => String(r.id) === String(runId));
                        if (!row || !row.active_worker) {
                            window.clearInterval(waitForStop);
                            if (row) await loadRegressionDetail(runId, false);
                            showAdminMessage(`Test #${runId} is stopped. You can now Delete Data.`);
                        }
                    } catch (_) { /* normal refresh/polling will retry */ }
                }, 1000);
            } catch (error) {
                stopButton.disabled = false;
                stopButton.textContent = 'Stop Test';
                showAdminMessage(error.message, true);
            }
            return;
        }

        const button = event.target.closest('button[data-regression-run-id]');
        if (!button) return;
        try {
            const finished = await loadRegressionDetail(button.dataset.regressionRunId, true);
            document.getElementById('regression-active')?.scrollIntoView({behavior: 'smooth', block: 'start'});
            if (!finished) pollRegressionRun(button.dataset.regressionRunId);
        } catch (error) {
            showAdminMessage(error.message, true);
        }
    });
}

// FIX-069: A completed run can be promoted to Proven with one checkbox. This is
// only a review/persistence shortcut: it reuses the existing per-trade Proven API and
// never changes replay or production trading decisions.
if (regressionRunsBody) regressionRunsBody.addEventListener('change', async event => {
    const cb = event.target.closest('input[data-proven-run-toggle]');
    if (!cb) return;
    cb.disabled = true;
    try {
        const runId = cb.dataset.provenRunToggle;
        const trades = await api(`/api/administration/regression-tests/runs/${encodeURIComponent(runId)}/trades`);
        const closed = (trades || []).filter(t => t.exit_time);
        for (const trade of closed) {
            const url = `/api/administration/regression-tests/proven-trades/${encodeURIComponent(runId)}/${encodeURIComponent(trade.id)}`;
            await api(url, {method: cb.checked ? 'POST' : 'DELETE'});
        }
        await Promise.all([loadRegressionRuns(), loadProvenTradesGraph()]);
        showAdminMessage(cb.checked ? `Run #${runId} saved to Proven trades.` : `Run #${runId} removed from Proven trades.`);
    } catch (error) {
        cb.checked = !cb.checked;
        showAdminMessage(error.message, true);
    } finally {
        cb.disabled = false;
    }
});

const regressionArchivesBody = document.getElementById('regression-archives-body');
if (regressionArchivesBody) regressionArchivesBody.addEventListener('click', async event => {
    const button = event.target.closest('button[data-regression-archive-view]');
    if (!button) return;
    try { await loadRegressionArchiveDetail(button.dataset.regressionArchiveView); }
    catch (error) { showAdminMessage(error.message, true); }
});

const regressionReset = document.getElementById('regression-reset');
if (regressionReset) regressionReset.addEventListener('click', async () => {
    const confirmed = window.confirm('Permanently delete ALL Replay/Test data and Recent Test Runs, including replay archives? Proven trades and production data will be kept.');
    if (!confirmed) return;
    regressionReset.disabled = true;
    try {
        const deleted = await api('/api/administration/regression-tests/runs', {method: 'DELETE'});
        if (regressionPollTimer) { window.clearInterval(regressionPollTimer); regressionPollTimer = null; }
        activeRegressionRunId = null;
        document.getElementById('regression-active').classList.add('hidden');
        document.getElementById('regression-result').classList.add('hidden');
        document.getElementById('regression-detail')?.classList.add('hidden');
        document.getElementById('regression-trades').classList.add('hidden');
        document.getElementById('regression-pipeline')?.classList.add('hidden');
        setRegressionRunButtonRunning(false);
        await loadRegressionRuns();
        showAdminMessage(deleted.message || 'All Replay/Test data deleted and validated. Proven trades were preserved.');
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

const regressionPipelineToggle = document.getElementById('regression-pipeline-toggle');
if (regressionPipelineToggle) regressionPipelineToggle.addEventListener('click', () => {
    const panel = document.getElementById('regression-pipeline');
    if (!panel) return;
    const opening = panel.classList.contains('hidden');
    panel.classList.toggle('hidden', !opening);
    regressionPipelineToggle.textContent = opening ? 'Collapse pipeline' : 'Expand pipeline';
    regressionPipelineToggle.setAttribute('aria-expanded', opening ? 'true' : 'false');
});

const regressionPipelineFilter = document.getElementById('regression-pipeline-filter');
if (regressionPipelineFilter) regressionPipelineFilter.addEventListener('change', () => {
    if (!regressionPipelineCache) return;
    const {signals, opportunities, trades, management, runSymbol} = regressionPipelineCache;
    renderRegressionPipeline(signals, opportunities, trades, management, runSymbol);
});


// FIX-018: Current-test, archived and Proven "View Chart" actions open a trade-focused
// modal. The existing combined Proven chart remains untouched; this avoids reusing one
// Apex DOM instance for two different review jobs and keeps the parent trade row visible.
let provenPopupChart = null;
let provenPopupTrade = null;
let provenPopupCrosshairCleanup = null;

function normalizePopupTrade(raw = {}) {
    const entry = raw.entry_time ? window.CryptoTime.parseUtc(raw.entry_time) : null;
    if (!entry || Number.isNaN(entry.getTime()) || raw.entry_price == null) return null;
    const exit = raw.exit_time ? window.CryptoTime.parseUtc(raw.exit_time) : null;
    return {
        ...raw,
        symbol: String(raw.symbol || '').toUpperCase(),
        entry_time: entry.toISOString(),
        entry_price: Number(raw.entry_price),
        exit_time: exit && !Number.isNaN(exit.getTime()) ? exit.toISOString() : null,
        exit_price: raw.exit_price == null ? null : Number(raw.exit_price)
    };
}

function popupTradeFromButton(button) {
    return normalizePopupTrade({
        symbol: button.dataset.chartSymbol,
        entry_time: button.dataset.chartEntryTime,
        entry_price: button.dataset.chartEntryPrice,
        exit_time: button.dataset.chartExitTime || null,
        exit_price: button.dataset.chartExitPrice || null,
        _label: `Trade #${Number(button.dataset.chartIndex || 0) + 1}`
    });
}

function openProvenTradePopup(trade) {
    const normalized = normalizePopupTrade(trade);
    if (!normalized) throw new Error('Trade chart requires a valid BUY time and price.');
    provenPopupTrade = normalized;
    const modal = document.getElementById('proven-trade-chart-modal');
    modal?.classList.remove('hidden');
    modal?.setAttribute('aria-hidden', 'false');
    document.body.classList.add('proven-modal-open');
    document.getElementById('proven-popup-title').textContent = `${normalized.symbol} · ${normalized._label || 'Trade review'}`;
    const buy = `${formatMoveTime(normalized.entry_time)} @ ${formatMovePrice(normalized.entry_price)}`;
    const sell = normalized.exit_time && normalized.exit_price != null
        ? `${formatMoveTime(normalized.exit_time)} @ ${formatMovePrice(normalized.exit_price)}`
        : 'OPEN';
    document.getElementById('proven-popup-meta').textContent = `BUY ${buy} · SELL ${sell}`;
    return renderProvenTradePopup();
}

function closeProvenTradePopup() {
    const modal = document.getElementById('proven-trade-chart-modal');
    modal?.classList.add('hidden');
    modal?.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('proven-modal-open');
    provenPopupCrosshairCleanup?.();
    provenPopupCrosshairCleanup = null;
    if (provenPopupChart) { provenPopupChart.destroy(); provenPopupChart = null; }
    const host = document.getElementById('proven-popup-chart');
    if (host) host.innerHTML = '';
    provenPopupTrade = null;
}

function adaptivePopupPrice(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) return '—';
    const abs = Math.abs(n);
    const digits = abs >= 1000 ? 2 : abs >= 1 ? 4 : abs >= .01 ? 6 : 10;
    return n.toLocaleString(undefined, {minimumFractionDigits: 0, maximumFractionDigits: digits});
}

function ensurePopupCrosshairElements(host) {
    const make = (cls) => { const el=document.createElement('div'); el.className=cls; host.appendChild(el); return el; };
    return {
        vertical: make('proven-popup-crosshair-v'),
        horizontal: make('proven-popup-crosshair-h'),
        price: make('proven-popup-axis-label proven-popup-price-label'),
        time: make('proven-popup-axis-label proven-popup-time-label')
    };
}

function bindPopupCrosshair(chart, host) {
    provenPopupCrosshairCleanup?.();
    host.querySelectorAll('.proven-popup-crosshair-v,.proven-popup-crosshair-h,.proven-popup-axis-label').forEach(el => el.remove());
    const ui = ensurePopupCrosshairElements(host);
    const hide = () => Object.values(ui).forEach(el => { el.style.display='none'; });
    const onMove = event => {
        const grid = host.querySelector('.apexcharts-grid');
        if (!grid || !chart?.w?.globals) return hide();
        const rect = grid.getBoundingClientRect();
        const x = event.clientX, y = event.clientY;
        if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) return hide();

        const hostRect = host.getBoundingClientRect();
        const gx = rect.left - hostRect.left, gy = rect.top - hostRect.top;
        const px = x - rect.left, py = y - rect.top;
        const g = chart.w.globals;
        const minX = Number(g.minX), maxX = Number(g.maxX);
        const minY = Number(g.minYArr?.[0]), maxY = Number(g.maxYArr?.[0]);
        if (![minX,maxX,minY,maxY].every(Number.isFinite) || maxX <= minX || maxY <= minY) return hide();
        const timeValue = minX + (px / rect.width) * (maxX - minX);
        const priceValue = maxY - (py / rect.height) * (maxY - minY);

        ui.vertical.style.display='block'; ui.vertical.style.left=`${gx+px}px`; ui.vertical.style.top=`${gy}px`; ui.vertical.style.height=`${rect.height}px`;
        ui.horizontal.style.display='block'; ui.horizontal.style.left=`${gx}px`; ui.horizontal.style.top=`${gy+py}px`; ui.horizontal.style.width=`${rect.width}px`;
        ui.price.textContent = adaptivePopupPrice(priceValue);
        ui.price.style.display='block'; ui.price.style.left=`${gx+rect.width+4}px`; ui.price.style.top=`${gy+py}px`;
        const dt = new Date(timeValue);
        ui.time.textContent = Number.isNaN(dt.getTime()) ? '' : dt.toLocaleString();
        const labelLeft = Math.max(gx + 65, Math.min(gx + rect.width - 65, gx + px));
        ui.time.style.display='block'; ui.time.style.left=`${labelLeft}px`; ui.time.style.top=`${gy+rect.height+5}px`;
    };
    host.addEventListener('pointermove', onMove, true);
    host.addEventListener('pointerleave', hide, true);
    provenPopupCrosshairCleanup = () => {
        host.removeEventListener('pointermove', onMove, true);
        host.removeEventListener('pointerleave', hide, true);
        Object.values(ui).forEach(el => el.remove());
    };
}

async function renderProvenTradePopup() {
    if (!provenPopupTrade) return;
    const trade = provenPopupTrade;
    const interval = document.getElementById('proven-popup-interval')?.value || '5m';
    const entry = window.CryptoTime.parseUtc(trade.entry_time);
    const exit = trade.exit_time ? window.CryptoTime.parseUtc(trade.exit_time) : null;
    const from = new Date(entry.getTime() - 7 * 60 * 60 * 1000);
    const tradeEnd = exit && !Number.isNaN(exit.getTime()) ? exit : entry;
    const to = new Date(tradeEnd.getTime() + 7 * 60 * 60 * 1000);
    const data = await api(`/api/administration/regression-tests/trade-chart?symbol=${encodeURIComponent(trade.symbol)}&interval=${encodeURIComponent(interval)}&from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`);
    const candles = (data.candles || []).map(c => ({
        x: window.CryptoTime.parseUtc(c.open_time),
        y: [Number(c.open_price), Number(c.high_price), Number(c.low_price), Number(c.close_price)]
    }));
    const empty = document.getElementById('proven-popup-chart-empty');
    empty?.classList.toggle('hidden', candles.length > 0);
    const points=[];
    if (trade.entry_time && trade.entry_price != null) points.push(provenPoint(trade.entry_time, trade.entry_price, 'BUY', 0));
    if (trade.exit_time && trade.exit_price != null) points.push(provenPoint(trade.exit_time, trade.exit_price, 'SELL', 0));
    const path=[];
    if (trade.exit_time && trade.exit_price != null) {
        path.push({
            name:'Trade Path', type:'line',
            data:[{x:entry.getTime(),y:Number(trade.entry_price)},{x:window.CryptoTime.parseUtc(trade.exit_time).getTime(),y:Number(trade.exit_price)}]
        });
    }
    const options={
        chart:{type:'line',height:520,background:'transparent',foreColor:'#8da2b1',toolbar:{show:true},animations:{enabled:false},zoom:{enabled:true,autoScaleYaxis:true}},
        title:{text:`${trade.symbol} · ${interval} · 7h before/after trade`,align:'left',style:{fontSize:'13px',fontWeight:600,color:'#dbe8ef'}},
        series:[{name:'Price',type:'candlestick',data:candles},...path],
        stroke:{width:[1,...path.map(()=>3)],curve:'straight'}, markers:{size:[0,...path.map(()=>3)]}, dataLabels:{enabled:false},
        xaxis:{type:'datetime',crosshairs:{show:true,stroke:{width:1,dashArray:0}},labels:{datetimeUTC:false},tooltip:{enabled:false}},
        yaxis:{tooltip:{enabled:false},decimalsInFloat:4},
        grid:{borderColor:'#203342'},theme:{mode:'dark'},plotOptions:{candlestick:{colors:{upward:'#39d98a',downward:'#ff6b72'}}},
        annotations:{points},tooltip:{shared:false}
    };
    const host=document.getElementById('proven-popup-chart');
    provenPopupCrosshairCleanup?.();
    if (provenPopupChart) provenPopupChart.destroy();
    host.innerHTML='';
    provenPopupChart=new ApexCharts(host,options);
    await provenPopupChart.render();
    // FIX-070: Proven popup uses the same KSA X/Y pointer overlay as every other market/trade chart.
    provenPopupCrosshairCleanup = window.CryptoChartCrosshair?.bind(host, provenPopupChart, { valueFormatter: adaptivePopupPrice }) || null;
    bindProvenDotTitles([trade]);
}

async function showRegressionTradeChart(button) {
    const trade = popupTradeFromButton(button);
    if (!trade) return;
    await openProvenTradePopup(trade);
}

document.addEventListener('click', event => {
    const button=event.target.closest('button[data-replay-chart]');
    if (button) showRegressionTradeChart(button).catch(error => showAdminMessage(error.message,true));
    if (event.target.closest('[data-proven-popup-close]') || event.target.closest('#proven-popup-close')) closeProvenTradePopup();
});
document.addEventListener('keydown', event => { if (event.key === 'Escape' && provenPopupTrade) closeProvenTradePopup(); });
document.getElementById('proven-popup-interval')?.addEventListener('change', () => renderProvenTradePopup().catch(error => showAdminMessage(error.message,true)));

let provenTradesChart = null;
let provenTradeFocus = null;
function renderProvenTradesGrid(all) {
    const body = document.getElementById('proven-saved-trades-body');
    const count = document.getElementById('proven-trades-count');
    const rows = all || [];
    if (count) count.textContent = `${rows.length} trade${rows.length === 1 ? '' : 's'}`;
    if (!body) return;
    body.innerHTML = rows.length ? rows.map((trade, index) => `
        <tr>
            <td>${index + 1}</td>
            <td>${escapeHtml(String(trade.symbol || '—').toUpperCase())}</td>
            <td>${formatMoveTime(trade.entry_time)}</td>
            <td>${formatMovePrice(trade.entry_price)}</td>
            <td>${trade.exit_time ? formatMoveTime(trade.exit_time) : 'OPEN'}</td>
            <td>${trade.exit_price == null ? '—' : formatMovePrice(trade.exit_price)}</td>
            <td>${trade.realized_pnl_percent == null ? '—' : Number(trade.realized_pnl_percent).toFixed(3) + '%'}</td>
            <td>${formatMoveTime(trade.marked_at)}</td>
            <td><button type="button" class="secondary-button regression-chart-link" data-proven-view-index="${index}">View</button></td>
        </tr>`).join('') : '<tr><td colspan="9">No proven trades yet.</td></tr>';
}

async function loadArchivedProvenTradeLegs() {
    const body = document.getElementById('proven-archived-legs-body');
    if (!body) return;
    try {
        const rows = await api('/api/administration/regression-tests/proven-trades/archived-legs');
        body.innerHTML = (rows || []).map((leg, index) => `
            <tr>
                <td>${index + 1}</td>
                <td><strong>${escapeHtml(String(leg.symbol || '—').toUpperCase())}</strong></td>
                <td><span class="status-pill ${String(leg.side || '').toUpperCase()==='BUY' ? 'reviewed' : 'ignored'}">${escapeHtml(leg.side || '—')}</span></td>
                <td>${formatMoveTime(leg.execution_time)}</td>
                <td>${formatMovePrice(leg.execution_price)}</td>
                <td>${escapeHtml(leg.exit_reason || '—')}</td>
                <td>${leg.realized_pnl_percent == null ? '—' : Number(leg.realized_pnl_percent).toFixed(3) + '%'}</td>
                <td>${formatMoveTime(leg.archived_at)}</td>
            </tr>`).join('') || '<tr><td colspan="8">No archived trade legs yet.</td></tr>';
    } catch (error) {
        body.innerHTML = `<tr><td colspan="8">${escapeHtml(error.message)}</td></tr>`;
    }
}

async function loadProvenTradesGraph(preferredSymbol = null) {
    const all = await api('/api/administration/regression-tests/proven-trades');
    renderProvenTradesGrid(all);
    await loadArchivedProvenTradeLegs();
    // FIX-072B: The redundant upper combined Proven chart was intentionally removed.
    // Keep loading/rendering the persistent Proven trades grid without doing chart work.
    if (!document.getElementById('proven-trades-chart')) return;
    const selector = document.getElementById('proven-chart-symbol');
    const symbols = [...new Set((all || []).map(t => String(t.symbol || '').toUpperCase()).filter(Boolean))];
    const focusSymbol = String(provenTradeFocus?.symbol || '').toUpperCase();
    if (focusSymbol && !symbols.includes(focusSymbol)) symbols.push(focusSymbol);
    if (selector) {
        const current = preferredSymbol || focusSymbol || selector.value || symbols[0] || '';
        selector.innerHTML = symbols.length ? symbols.map(s => `<option value="${escapeHtml(s)}" ${s===current?'selected':''}>${escapeHtml(s)}</option>`).join('') : '<option value="">No proven trades yet</option>';
        if (symbols.includes(current)) selector.value = current;
    }
    const symbol = selector?.value || symbols[0] || '';
    const interval = document.getElementById('proven-chart-interval')?.value || '5m';
    const empty = document.getElementById('proven-trades-empty');
    if (!symbol) {
        empty?.classList.remove('hidden');
        if (provenTradesChart) { provenTradesChart.destroy(); provenTradesChart = null; }
        return;
    }
    empty?.classList.add('hidden');

    let data;
    let chartTrades;
    const single = provenTradeFocus?._singleTradeView && focusSymbol === symbol;
    if (single) {
        const entry = window.CryptoTime.parseUtc(provenTradeFocus.entry_time);
        const exit = provenTradeFocus.exit_time ? window.CryptoTime.parseUtc(provenTradeFocus.exit_time) : null;
        // Analysis context: exactly 7 hours before the actual BUY and 7 hours after the actual SELL.
        const from = new Date(entry.getTime() - 7 * 60 * 60 * 1000);
        const tradeEnd = exit && !Number.isNaN(exit.getTime()) ? exit : entry;
        const to = new Date(tradeEnd.getTime() + 7 * 60 * 60 * 1000);
        data = await api(`/api/administration/regression-tests/trade-chart?symbol=${encodeURIComponent(symbol)}&interval=${encodeURIComponent(interval)}&from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`);
        chartTrades = [provenTradeFocus];
    } else {
        data = await api(`/api/administration/regression-tests/proven-trades/chart?symbol=${encodeURIComponent(symbol)}&interval=${encodeURIComponent(interval)}`);
        chartTrades = data.trades || [];
    }

    const candles = (data.candles || []).map(c => ({
        x: window.CryptoTime.parseUtc(c.open_time),
        y: [Number(c.open_price), Number(c.high_price), Number(c.low_price), Number(c.close_price)]
    }));
    const points = [];
    const tradePaths = [];
    (chartTrades || []).forEach((t, i) => {
        if (t.entry_time && t.entry_price != null) points.push(provenPoint(t.entry_time, t.entry_price, 'BUY', i));
        if (t.exit_time && t.exit_price != null) points.push(provenPoint(t.exit_time, t.exit_price, 'SELL', i));
        const entryTime = t.entry_time ? window.CryptoTime.parseUtc(t.entry_time) : null;
        const exitTime = t.exit_time ? window.CryptoTime.parseUtc(t.exit_time) : null;
        if (entryTime && exitTime && !Number.isNaN(entryTime.getTime()) && !Number.isNaN(exitTime.getTime()) && t.entry_price != null && t.exit_price != null) {
            const pct = Number(t.realized_pnl_percent);
            tradePaths.push({
                name:`${t._label || `Trade #${i+1}`} Path${Number.isFinite(pct) ? ` · ${pct >= 0 ? '+' : ''}${pct.toFixed(3)}%` : ''}`,
                type:'line',
                data:[{x:entryTime.getTime(),y:Number(t.entry_price)},{x:exitTime.getTime(),y:Number(t.exit_price)}]
            });
        }
    });
    const options = {
        chart: { type:'line', height:420, background:'transparent', foreColor:'#8da2b1', toolbar:{show:true}, animations:{enabled:false} },
        title: { text: `${symbol} · ${interval}${single ? ' · 7h before/after trade' : ''}`, align:'left', style:{fontSize:'13px',fontWeight:600,color:'#dbe8ef'} },
        series: [{name:'Price', type:'candlestick', data:candles}, ...tradePaths],
        stroke:{width:[1,...tradePaths.map(()=>3)],curve:'straight'},
        markers:{size:[0,...tradePaths.map(()=>3)]},
        dataLabels:{enabled:false},
        xaxis: { type:'datetime', labels:{datetimeUTC:false}, tooltip:{enabled:false}},
        yaxis: { tooltip:{enabled:false}, decimalsInFloat:4 },
        grid:{borderColor:'#203342'}, theme:{mode:'dark'},
        plotOptions:{candlestick:{colors:{upward:'#39d98a',downward:'#ff6b72'}}},
        annotations:{points}, tooltip:{shared:false}
    };
    const host=document.getElementById('proven-trades-chart');
    if (provenTradesChart) provenTradesChart.destroy();
    provenTradesChart = new ApexCharts(host, options);
    await provenTradesChart.render();
    // FIX-070: Combined Proven chart uses the unified pointer time/price overlay too.
    window.CryptoChartCrosshair?.bind(host, provenTradesChart, { valueFormatter: adaptivePopupPrice });
    bindProvenDotTitles(chartTrades || []);
}
function provenPoint(time, price, side, index) {
    const isBuy=side==='BUY';
    return {x:window.CryptoTime.parseUtc(time)?.getTime(), y:Number(price), marker:{size:6,fillColor:isBuy?'#39d98a':'#ff6b72',strokeColor:'#071018',strokeWidth:2,radius:6}, label:{text:'',borderColor:'transparent',style:{background:'transparent',color:'transparent',fontSize:'1px'},cssClass:`proven-trade-dot proven-${side.toLowerCase()} proven-trade-${index}-${side.toLowerCase()}`}};
}
function bindProvenDotTitles(trades) {
    (trades||[]).forEach((t,i)=>['BUY','SELL'].forEach(side=>{
        const time=side==='BUY'?t.entry_time:t.exit_time, price=side==='BUY'?t.entry_price:t.exit_price;
        if(!time||price==null)return;
        const label=document.querySelector(`.proven-trade-${i}-${side.toLowerCase()}`);
        const marker=label?.parentElement?.querySelector('circle') || label;
        if(marker){ const d=window.CryptoTime.parseUtc(time); marker.setAttribute('title',`${side} ${formatMovePrice(price)} · ${d?d.toLocaleString():time}`); }
    }));
}
const provenTradesBody=document.getElementById('regression-trades-body');
if (provenTradesBody) provenTradesBody.addEventListener('change', async event => {
    const cb=event.target.closest('input[data-proven-trade-id]'); if(!cb)return;
    cb.disabled=true;
    try {
        const url=`/api/administration/regression-tests/proven-trades/${encodeURIComponent(cb.dataset.provenRunId)}/${encodeURIComponent(cb.dataset.provenTradeId)}`;
        await api(url,{method:cb.checked?'POST':'DELETE'});
        await loadProvenTradesGraph();
        showAdminMessage(cb.checked?'Trade added to Proven trades.':'Trade removed from Proven trades.');
    } catch(error){ cb.checked=!cb.checked; showAdminMessage(error.message,true); }
    finally{cb.disabled=false;}
});
document.getElementById('proven-saved-trades-body')?.addEventListener('click', async event => {
    const archiveButton = event.target.closest('button[data-proven-archive-leg]');
    if (archiveButton) {
        archiveButton.disabled = true;
        try {
            const side = String(archiveButton.dataset.provenArchiveLeg || '').toUpperCase();
            await api(`/api/administration/regression-tests/proven-trades/${encodeURIComponent(archiveButton.dataset.provenId)}/archive-leg/${encodeURIComponent(side)}`, {method:'POST'});
            showAdminMessage(`${side} leg archived independently.`);
            await loadProvenTradesGraph();
        } catch (error) {
            archiveButton.disabled = false;
            showAdminMessage(error.message, true);
        }
        return;
    }

    const button = event.target.closest('button[data-proven-view-index]');
    if (!button) return;
    try {
        const all = await api('/api/administration/regression-tests/proven-trades');
        const trade = all?.[Number(button.dataset.provenViewIndex)];
        if (!trade) throw new Error('Proven trade not found.');
        // FIX-018: Proven trade review uses the same focused modal as Current Test/Archive.
        // The persistent combined graph is not mutated just to inspect one row.
        await openProvenTradePopup({...trade, _label:`Proven #${Number(button.dataset.provenViewIndex) + 1}`});
    } catch (error) {
        showAdminMessage(error.message, true);
    }
});
document.getElementById('proven-chart-symbol')?.addEventListener('change',()=>{
    provenTradeFocus = null;
    loadProvenTradesGraph().catch(e=>showAdminMessage(e.message,true));
});
document.getElementById('proven-chart-interval')?.addEventListener('change',()=>loadProvenTradesGraph().catch(e=>showAdminMessage(e.message,true)));

(async function initializeRegressionUi() {
    await loadRegressionSymbols();
    await loadProvenTradesGraph();
    await loadInvestigationCases();
    const runs = await loadRegressionRuns();
    const active = runs?.find(run => ['PENDING', 'RUNNING'].includes(String(run.status)));
    if (active) {
        await loadRegressionDetail(active.id, false);
        pollRegressionRun(active.id);
    }
})().catch(error => showAdminMessage(error.message, true));

