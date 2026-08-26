(()=>{'use strict';
const $=id=>document.getElementById(id),esc=v=>String(v??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const utcDate=v=>window.CryptoTime?.parseUtc(v)||(v?new Date(v):null);
// FIX-103: DB/Binance timestamps stay UTC; Trade Activity renders them explicitly in KSA.
const time=v=>{const d=utcDate(v);if(!d||Number.isNaN(d.getTime()))return '—';return new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Riyadh',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(d);};
const num=v=>v===null||v===undefined||v===''?null:Number(v);
const price=v=>{const n=num(v);return Number.isFinite(n)?n.toLocaleString(undefined,{maximumSignificantDigits:10}):'—';};
let analysisRows=[];

// FIX-103: Symbols are discovered from the read-only analysis endpoint so blocked/non-executed
// symbols remain selectable; this page does not depend on wallet history to populate the filter.
async function loadSymbols(){
  try{
    const r=await fetch('/api/trade-activity/symbols',{cache:'no-store'});if(!r.ok)return;
    const values=await r.json(),sel=$('activity-symbol'),existing=new Set([...sel.options].map(o=>String(o.value).toUpperCase()));
    (values||[]).forEach(value=>{const symbol=String(value||'').toUpperCase();if(!symbol||existing.has(symbol))return;const o=document.createElement('option');o.value=symbol;o.textContent=symbol;sel.appendChild(o);existing.add(symbol);});
  }catch(_){/* ALL remains usable even when symbol discovery is temporarily unavailable. */}
}
function analysisTypeLabel(row){return ({BLOCKED_BUY:'BLOCKED BUY',BLOCKED_SELL:'BLOCKED SELL',DONE:'BUY / SELL DONE',OPEN_BUY:'BUY OPEN'})[row.rowKind]||row.rowKind||'SIGNAL';}
function analysisDecisionText(row){return `${row.originalDecision||'—'} → ${row.decision||'—'}`;}
function analysisStateText(row){if(row.rowKind==='DONE')return row.executionStatus||'EXECUTED';if(row.rowKind==='OPEN_BUY')return 'OPEN';return row.finalEntryAllowed?'ALLOWED':'BLOCKED';}
function analysisReason(row){if(row.rowKind==='DONE')return row.realizedPnlPercent!=null?`P/L ${Number(row.realizedPnlPercent).toFixed(3)}%`:(row.executionReason||'Completed trade');if(row.rowKind==='OPEN_BUY')return row.allocatedPositionPercent!=null?`${row.allocatedPositionPercent}% allocated`:'Open position';return row.primaryBlockingStage||(!row.finalEntryAllowed?'FINAL DECISION':'—');}
function analysisClass(row){return row.rowKind==='DONE'?'good':row.rowKind==='OPEN_BUY'?'pending':row.finalEntryAllowed?'good':'early';}
function analysisRow(row,index){
  const raw=row.rawConfidence??row.confidence??'—',effective=row.effectiveConfidence??row.confidence??'—',decision=String(row.decision||'');
  return `<tr><td>${esc(time(row.eventTime||row.candleOpenTime||row.generatedAt))}</td><td><strong class="activity-row-symbol">${esc(String(row.symbol||'—').toUpperCase())}</strong><small>${row.signalId!=null?'#'+esc(row.signalId):''}</small></td><td><span class="analysis-quality ${analysisClass(row)}">${esc(analysisTypeLabel(row))}</span></td><td><span class="activity-pill ${decision.includes('SELL')?'sell':decision.includes('BUY')?'buy':''}">${esc(analysisDecisionText(row))}</span><small>${esc(row.interval||'—')}</small></td><td><strong>${esc(row.score??'—')}</strong> / ${esc(raw)}<small>effective ${esc(effective)}</small></td><td><span class="analysis-quality ${analysisClass(row)}">${esc(analysisStateText(row))}</span></td><td>${esc(row.regime||'—')}<small>${esc(row.strategy||'—')}</small></td><td>${esc(analysisReason(row))}</td><td><div class="activity-analysis-actions"><button type="button" class="activity-analyze-row" data-analysis-index="${index}">Analyze</button></div></td></tr>`;
}
function analysisField(label,value){return `<div class="activity-analysis-field"><small>${esc(label)}</small><strong>${esc(value??'—')}</strong></div>`;}
function prettyDecisionPath(value){if(value==null||value==='')return 'No persisted decision path.';try{const parsed=typeof value==='string'?JSON.parse(value):value;return JSON.stringify(parsed,null,2);}catch(_){return String(value);}}
function openAnalysis(index){
  const row=analysisRows[Number(index)];if(!row)return;const modal=$('activity-analysis-modal');
  $('activity-analysis-title').textContent=`${row.symbol||'Trade'}${row.signalId!=null?' #'+row.signalId:''} · ${analysisTypeLabel(row)}`;
  $('activity-analysis-subtitle').textContent=`${time(row.eventTime||row.candleOpenTime||row.generatedAt)} KSA · ${analysisDecisionText(row)} · ${analysisStateText(row)}`;
  const confidence=`raw ${row.rawConfidence??row.confidence??'—'} · effective ${row.effectiveConfidence??row.confidence??'—'}`;
  $('activity-analysis-content').innerHTML=`<div class="activity-analysis-detail-grid">${analysisField('Type',analysisTypeLabel(row))}${analysisField('Price',price(row.price))}${analysisField('Score',row.score)}${analysisField('Confidence',confidence)}${analysisField('State',analysisStateText(row))}${analysisField('Primary blocker',row.primaryBlockingStage||'—')}${analysisField('Trend / Volume / Momentum',`${row.trendScore??'—'} / ${row.volumeScore??'—'} / ${row.momentumScore??'—'}`)}${analysisField('Regime',`${row.regime||'—'} (${row.regimeConfidence??'—'})`)}${analysisField('Strategy',row.strategy)}${analysisField('ATR entry',`${row.atrEntryType||'—'} · immediate=${row.atrImmediateEntryAllowed==null?'—':row.atrImmediateEntryAllowed?'YES':'NO'}`)}${analysisField('Confluence',`${row.confluenceStatus||'—'} · ${row.confluenceHigherInterval||'—'}=${row.confluenceHigherDecision||'—'}`)}${analysisField('BTC context',row.btcContextStatus)}${analysisField('Liquidity',row.liquidityStatus)}${analysisField('Derivatives',row.derivativesStatus)}${analysisField('Stop / Take profit',`${price(row.stopLoss)} / ${price(row.takeProfit)}`)}${analysisField('Realized P/L',row.realizedPnlPercent!=null?`${row.realizedPnlPercent}%`:'—')}</div><div class="activity-analysis-explanations"><section><strong>Final decision / execution</strong><p>${esc(row.finalExplanation||row.executionReason||'—')}</p></section><section><strong>ATR</strong><p>${esc(row.atrExplanation||'—')}</p></section><section><strong>Multi-timeframe</strong><p>${esc(row.confluenceExplanation||'—')}</p></section><section><strong>BTC</strong><p>${esc(row.btcContextExplanation||'—')}</p></section><section><strong>Liquidity</strong><p>${esc(row.liquidityExplanation||'—')}</p></section><section><strong>Derivatives</strong><p>${esc(row.derivativesExplanation||'—')}</p></section></div><section class="activity-analysis-path"><strong>Persisted decision path</strong><pre>${esc(prettyDecisionPath(row.decisionPath))}</pre></section>`;
  modal.classList.remove('hidden');modal.setAttribute('aria-hidden','false');document.body.classList.add('activity-modal-open');
}
function closeAnalysis(){const modal=$('activity-analysis-modal');if(!modal)return;modal.classList.add('hidden');modal.setAttribute('aria-hidden','true');document.body.classList.remove('activity-modal-open');}

// FIX-103: This is the only Trade Activity data workflow. It reads the unified FIX-101/FIX-102
// persisted-evidence endpoint and never invokes chart, trading, Replay or wallet mutation logic.
async function loadTradeAnalysis(){
  const err=$('activity-error');err.classList.add('hidden');
  const p=new URLSearchParams({symbol:$('activity-symbol').value||'ALL',period:$('activity-period').value||'1d',type:$('activity-type').value||'BLOCKED_BUY',limit:'250'});
  $('activity-rows').innerHTML='<tr><td colspan="9" class="empty-cell">Loading trade analysis…</td></tr>';$('activity-count').textContent='Loading…';
  try{
    const r=await fetch(`/api/trade-inspector/signals?${p.toString()}`,{cache:'no-store'});if(!r.ok)throw new Error(`HTTP ${r.status}`);
    analysisRows=await r.json();$('activity-count').textContent=`${analysisRows.length} rows`;$('activity-rows').innerHTML=analysisRows.length?analysisRows.map(analysisRow).join(''):'<tr><td colspan="9" class="empty-cell">No rows match these filters.</td></tr>';
  }catch(e){$('activity-rows').innerHTML='<tr><td colspan="9" class="empty-cell">Trade analysis unavailable.</td></tr>';err.textContent=`Trade Activity could not load: ${e.message}`;err.classList.remove('hidden');$('activity-count').textContent='Error';}
}

$('activity-search').addEventListener('click',loadTradeAnalysis);
['activity-symbol','activity-period','activity-type'].forEach(id=>$(id).addEventListener('change',loadTradeAnalysis));
$('activity-rows').addEventListener('click',e=>{const analyze=e.target.closest('.activity-analyze-row');if(analyze)openAnalysis(analyze.dataset.analysisIndex);});
$('activity-analysis-close').addEventListener('click',closeAnalysis);
$('activity-analysis-modal').addEventListener('click',e=>{if(e.target?.dataset?.activityAnalysisClose==='1')closeAnalysis();});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!$('activity-analysis-modal').classList.contains('hidden'))closeAnalysis();});
loadSymbols().finally(loadTradeAnalysis);
})();
