/* FIX-107: One read-only Signals & executions browser shared by Trade Activity and Dashboard.
 * Both pages use the same endpoint, filters, grid renderer, Analyze evidence modal and View-chart
 * deep-link rules. Filters never mutate Dashboard header selection or any trading state. */
(()=>{'use strict';
const esc=v=>String(v??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const utcDate=v=>window.CryptoTime?.parseUtc(v)||(v?new Date(v):null);
const ksa=v=>{const d=utcDate(v);if(!d||Number.isNaN(d.getTime()))return '—';return new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Riyadh',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(d);};
const num=v=>v===null||v===undefined||v===''?null:Number(v);
const price=v=>{const n=num(v);return Number.isFinite(n)?n.toLocaleString(undefined,{maximumSignificantDigits:10}):'—';};
const typeLabel=r=>({BLOCKED_BUY:'BLOCKED BUY',BLOCKED_SELL:'BLOCKED SELL',DONE:'BUY / SELL DONE',OPEN_BUY:'BUY OPEN'})[r.rowKind]||r.rowKind||'SIGNAL';
const decisionText=r=>`${r.originalDecision||'—'} → ${r.decision||'—'}`;
const stateText=r=>r.rowKind==='DONE'?(r.executionStatus||'EXECUTED'):r.rowKind==='OPEN_BUY'?'OPEN':r.finalEntryAllowed?'ALLOWED':'BLOCKED';
const reason=r=>r.rowKind==='DONE'?(r.realizedPnlPercent!=null?`P/L ${Number(r.realizedPnlPercent).toFixed(3)}%`:(r.executionReason||'Completed trade')):r.rowKind==='OPEN_BUY'?(r.allocatedPositionPercent!=null?`${r.allocatedPositionPercent}% allocated`:'Open position'):(r.primaryBlockingStage||(!r.finalEntryAllowed?'FINAL DECISION':'—'));
const quality=r=>r.rowKind==='DONE'?'good':r.rowKind==='OPEN_BUY'?'pending':r.finalEntryAllowed?'good':'early';
const field=(label,value)=>`<div class="activity-analysis-field"><small>${esc(label)}</small><strong>${esc(value??'—')}</strong></div>`;
const path=v=>{if(v==null||v==='')return 'No persisted decision path.';try{return JSON.stringify(typeof v==='string'?JSON.parse(v):v,null,2);}catch(_){return String(v);}};

function chartUrl(row){
  const symbol=String(row.symbol||'').toUpperCase(); if(!symbol)return '#';
  const interval=String(row.interval||'5m').toLowerCase();
  if(row.rowKind==='DONE'){
    const buy=utcDate(row.buyTime),sell=utcDate(row.sellTime||row.eventTime);
    const buyPrice=num(row.buyPrice),sellPrice=num(row.sellPrice??row.price);
    if(!buy||!sell||Number.isNaN(buy.getTime())||Number.isNaN(sell.getTime())||!Number.isFinite(buyPrice)||!Number.isFinite(sellPrice))return '#';
    const params=new URLSearchParams({symbol,interval,focusStart:new Date(buy.getTime()-30*60000).toISOString(),focusEnd:new Date(sell.getTime()+30*60000).toISOString(),focusDirection:Number(row.realizedPnlPercent||0)>=0?'UP':'DOWN',debugTrade:'1',debugTradeLabel:`Completed trade${row.sellTradeId?' #'+row.sellTradeId:''}`,debugEntryTime:buy.toISOString(),debugEntryPrice:String(buyPrice),debugExitTime:sell.toISOString(),debugExitPrice:String(sellPrice)});
    return `/dashboard?${params.toString()}#market`;
  }
  const at=utcDate(row.rowKind==='OPEN_BUY'?(row.eventTime||row.candleOpenTime||row.generatedAt):(row.candleOpenTime||row.eventTime||row.generatedAt)),p=num(row.price);
  if(!at||Number.isNaN(at.getTime())||!Number.isFinite(p))return '#';
  const raw=String(row.decision||row.originalDecision||'BUY').toUpperCase();
  const side=row.rowKind==='BLOCKED_SELL'||raw.includes('SELL')?'SELL':'BUY';
  const label=row.rowKind==='BLOCKED_BUY'?`Blocked BUY #${row.signalId||''}`:row.rowKind==='BLOCKED_SELL'?`Blocked SELL #${row.signalId||''}`:row.rowKind==='OPEN_BUY'?`Open BUY #${row.signalId||''}`:`${side} #${row.signalId||''}`;
  const params=new URLSearchParams({symbol,interval,focusStart:new Date(at.getTime()-30*60000).toISOString(),focusEnd:new Date(at.getTime()+30*60000).toISOString(),focusDirection:side==='SELL'?'DOWN':'UP',debugTrade:'1',debugTradeLabel:label,debugPointTime:at.toISOString(),debugPointPrice:String(p),debugPointSide:side});
  return `/dashboard?${params.toString()}#market`;
}

class Browser{
 constructor(c){this.c=c;this.rows=[];this.seq=0;this.abort=null;this.$=key=>document.getElementById(c[key]);}
 async init(initialSymbol){
   await this.loadSymbols();
   if(initialSymbol)this.setSymbol(initialSymbol,false);
   this.$('search')?.addEventListener('click',()=>this.load());
   this.$('rows')?.addEventListener('click',e=>{const b=e.target.closest('[data-signal-analysis-index]');if(b)this.open(Number(b.dataset.signalAnalysisIndex));});
   this.$('close')?.addEventListener('click',()=>this.close());
   this.$('modal')?.addEventListener('click',e=>{if(e.target?.dataset?.signalAnalysisClose==='1'||e.target?.dataset?.activityAnalysisClose==='1')this.close();});
   // FIX-107: no filter-change auto-refresh. The operator chooses filters, then presses Analyze.
   await this.load();
 }
 async loadSymbols(){try{const r=await fetch('/api/trade-inspector/signals/symbols',{cache:'no-store'});if(!r.ok)return;const vals=await r.json(),s=this.$('symbol');if(!s)return;const seen=new Set([...s.options].map(o=>String(o.value).toUpperCase()));(vals||[]).forEach(v=>{v=String(v||'').toUpperCase();if(!v||seen.has(v))return;const o=document.createElement('option');o.value=v;o.textContent=v;s.appendChild(o);seen.add(v);});}catch(_){}}
 setSymbol(symbol,load=false){const s=this.$('symbol');if(!s)return;const v=String(symbol||'').toUpperCase();if(v&&[...s.options].some(o=>o.value===v)){s.value=v;if(load)this.load();}}
 rowHtml(r,i){const raw=r.rawConfidence??r.confidence??'—',eff=r.effectiveConfidence??r.confidence??'—',d=String(r.decision||''),url=chartUrl(r),view=url==='#'?'<span class="activity-view-disabled">No chart</span>':`<a class="activity-analyze-row" href="${esc(url)}">View</a>`;return `<tr><td>${esc(ksa(r.eventTime||r.candleOpenTime||r.generatedAt))}</td><td><strong class="activity-row-symbol">${esc(String(r.symbol||'—').toUpperCase())}</strong><small>${r.signalId!=null?'#'+esc(r.signalId):''}</small></td><td><span class="analysis-quality ${quality(r)}">${esc(typeLabel(r))}</span></td><td><span class="activity-pill ${d.includes('SELL')?'sell':d.includes('BUY')?'buy':''}">${esc(decisionText(r))}</span><small>${esc(r.interval||'—')}</small></td><td><strong>${esc(r.score??'—')}</strong> / ${esc(raw)}<small>effective ${esc(eff)}</small></td><td><span class="analysis-quality ${quality(r)}">${esc(stateText(r))}</span></td><td>${esc(r.regime||'—')}<small>${esc(r.strategy||'—')}</small></td><td>${esc(reason(r))}</td><td><div class="activity-analysis-actions"><button type="button" class="activity-analyze-row" data-signal-analysis-index="${i}">Analyze</button>${view}</div></td></tr>`;}
 async load(){const err=this.$('error');err?.classList.add('hidden');const snap={symbol:this.$('symbol')?.value||'ALL',period:this.$('period')?.value||'1d',type:this.$('type')?.value||'BLOCKED_BUY'},id=++this.seq;if(this.abort)this.abort.abort();this.abort=new AbortController();const p=new URLSearchParams({...snap,limit:'250'});if(this.$('rows'))this.$('rows').innerHTML='<tr><td colspan="9" class="empty-cell">Loading trade analysis…</td></tr>';if(this.$('count'))this.$('count').textContent='Loading…';try{const r=await fetch(`/api/trade-inspector/signals?${p}`,{cache:'no-store',signal:this.abort.signal});if(!r.ok)throw new Error(`HTTP ${r.status}`);const rows=await r.json();if(id!==this.seq)return;this.rows=rows||[];if(this.$('count'))this.$('count').textContent=`${this.rows.length} rows`;if(this.$('rows'))this.$('rows').innerHTML=this.rows.length?this.rows.map((x,i)=>this.rowHtml(x,i)).join(''):'<tr><td colspan="9" class="empty-cell">No rows match these filters.</td></tr>';}catch(e){if(e?.name==='AbortError'||id!==this.seq)return;if(this.$('rows'))this.$('rows').innerHTML='<tr><td colspan="9" class="empty-cell">Trade analysis unavailable.</td></tr>';if(err){err.textContent=`Signals & executions could not load: ${e.message}`;err.classList.remove('hidden');}if(this.$('count'))this.$('count').textContent='Error';}}
 open(i){const r=this.rows[i];if(!r)return;this.$('title').textContent=`${r.symbol||'Trade'}${r.signalId!=null?' #'+r.signalId:''} · ${typeLabel(r)}`;this.$('subtitle').textContent=`${ksa(r.eventTime||r.candleOpenTime||r.generatedAt)} KSA · ${decisionText(r)} · ${stateText(r)}`;const conf=`raw ${r.rawConfidence??r.confidence??'—'} · effective ${r.effectiveConfidence??r.confidence??'—'}`;this.$('content').innerHTML=`<div class="activity-analysis-detail-grid">${field('Type',typeLabel(r))}${field('Price',price(r.price))}${field('Score',r.score)}${field('Confidence',conf)}${field('State',stateText(r))}${field('Primary blocker',r.primaryBlockingStage||'—')}${field('Trend / Volume / Momentum',`${r.trendScore??'—'} / ${r.volumeScore??'—'} / ${r.momentumScore??'—'}`)}${field('Regime',`${r.regime||'—'} (${r.regimeConfidence??'—'})`)}${field('Strategy',r.strategy)}${field('ATR entry',`${r.atrEntryType||'—'} · immediate=${r.atrImmediateEntryAllowed==null?'—':r.atrImmediateEntryAllowed?'YES':'NO'}`)}${field('Confluence',`${r.confluenceStatus||'—'} · ${r.confluenceHigherInterval||'—'}=${r.confluenceHigherDecision||'—'}`)}${field('BTC context',r.btcContextStatus)}${field('Liquidity',r.liquidityStatus)}${field('Derivatives',r.derivativesStatus)}${field('Stop / Take profit',`${price(r.stopLoss)} / ${price(r.takeProfit)}`)}${field('Realized P/L',r.realizedPnlPercent!=null?`${r.realizedPnlPercent}%`:'—')}</div><div class="activity-analysis-explanations"><section><strong>Final decision / execution</strong><p>${esc(r.finalExplanation||r.executionReason||'—')}</p></section><section><strong>ATR</strong><p>${esc(r.atrExplanation||'—')}</p></section><section><strong>Multi-timeframe</strong><p>${esc(r.confluenceExplanation||'—')}</p></section><section><strong>BTC</strong><p>${esc(r.btcContextExplanation||'—')}</p></section><section><strong>Liquidity</strong><p>${esc(r.liquidityExplanation||'—')}</p></section><section><strong>Derivatives</strong><p>${esc(r.derivativesExplanation||'—')}</p></section></div><section class="activity-analysis-path"><strong>Persisted decision path</strong><pre>${esc(path(r.decisionPath))}</pre></section>`;this.$('modal').classList.remove('hidden');this.$('modal').setAttribute('aria-hidden','false');document.body.classList.add('activity-modal-open');}
 close(){this.$('modal')?.classList.add('hidden');this.$('modal')?.setAttribute('aria-hidden','true');document.body.classList.remove('activity-modal-open');}
}
window.SignalExecutionsBrowser=Browser;
})();
