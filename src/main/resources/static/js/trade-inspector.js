const $ = id => document.getElementById(id);
let symbolsLoaded = false;
let inspectedTradeChart = null;
let inspectedTradeFocus = null;

function money(v){if(v===null||v===undefined)return '—';const n=Number(v);return `${n<0?'-':''}$${Math.abs(n).toLocaleString(undefined,{maximumFractionDigits:8})}`}
function price(v){if(v===null||v===undefined)return '—';const n=Number(v);return n>=1?`$${n.toLocaleString(undefined,{maximumFractionDigits:6})}`:`$${n.toLocaleString(undefined,{maximumFractionDigits:12})}`}
function pct(v){if(v===null||v===undefined)return '—';const n=Number(v);return `${n>=0?'+':''}${n.toFixed(3)}%`}
function date(v){return window.CryptoTime.formatLocal(v)}
function duration(mins){const m=Number(mins||0);if(m<60)return `${m}m`;const h=Math.floor(m/60),r=m%60;return `${h}h ${r}m`}
function cls(v){const n=Number(v||0);return n>0?'positive':n<0?'negative':''}
function qualityClass(q){return q==='GOOD_EXIT'?'good':q==='EARLY_EXIT'?'early':q==='PENDING'?'pending':'neutral'}
function qualityLabel(q){return (q||'NEUTRAL_EXIT').replaceAll('_',' ')}
function esc(v){return String(v??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]))}

function tradeChartUrl(t){
 const opened=window.CryptoTime.parseUtc(t.openedAt);
 const closed=window.CryptoTime.parseUtc(t.closedAt);
 if(Number.isNaN(opened.getTime())||Number.isNaN(closed.getTime())||closed<=opened)return '#';
 const pnl=Number(t.realizedPnlPercent??t.realizedPnl??0);
 const params=new URLSearchParams({
  symbol:String(t.symbol||'ETHUSDT').toUpperCase(),
  interval:'5m',
  focusStart:opened.toISOString(),
  focusEnd:closed.toISOString(),
  focusDirection:pnl>=0?'UP':'DOWN',
  debugTrade:'1',
  debugTradeLabel:t.tradeHistoryId==null?'Inspected trade':`Trade #${t.tradeHistoryId}`,
  debugEntryTime:opened.toISOString(),
  debugEntryPrice:String(t.entryPrice??''),
  debugExitTime:closed.toISOString(),
  debugExitPrice:String(t.exitPrice??'')
 });
 return `/dashboard?${params.toString()}#market`;
}

function renderSummary(s){
  $('summary-net').textContent=money(s.netPnl);$('summary-net').className=cls(s.netPnl);
  $('summary-count').textContent=`${s.trades} closed trades`;
  $('summary-win-rate').textContent=`${Number(s.winRate).toFixed(1)}%`;
  $('summary-record').textContent=`${s.wins}W / ${s.losses}L`;
  $('summary-average').textContent=money(s.averagePnl);$('summary-average').className=cls(s.averagePnl);
  $('summary-averages').textContent=`Avg win ${money(s.averageWin)} · Avg loss ${money(s.averageLoss)}`;
  $('summary-profit-factor').textContent=Number(s.profitFactor)>=999?'∞':Number(s.profitFactor).toFixed(2);
}

function renderSymbols(symbols){if(symbolsLoaded)return;const sel=$('symbol-filter');(symbols||[]).forEach(s=>{const o=document.createElement('option');o.value=s;o.textContent=s;sel.appendChild(o)});symbolsLoaded=true}

function tradeCard(t){
 const resultClass=cls(t.realizedPnl);
 const mfeClass=cls(t.maximumFavorablePercent),maeClass=cls(t.maximumAdversePercent);
 return `<article class="inspector-card">
  <div class="inspector-card-head">
   <div class="inspector-symbol"><strong>${esc(t.symbol)}</strong>${t.tradeHistoryId==null?'':`<span class="trade-reference">Trade #${esc(t.tradeHistoryId)}</span>`}<span class="badge buy">BUY ↑</span><span class="trade-action-arrow">→</span><span class="badge sell">SELL ↓</span><span class="quality ${qualityClass(t.exitQuality)}">${qualityLabel(t.exitQuality)}</span></div>
   <div class="inspector-head-actions">
    <button type="button" class="trade-chart-link" data-inspect-chart="1" data-trade-id="${esc(t.tradeHistoryId??t.walletSellTradeId??'')}" title="Inspect only this BUY/SELL on the dedicated chart"><span>↗</span> View chart</button>
    <div class="inspector-result"><strong class="${resultClass}">${money(t.realizedPnl)} · ${pct(t.realizedPnlPercent)}</strong><small>${duration(t.holdingMinutes)} holding time · ${esc(t.closeReason||t.status)}</small></div>
   </div>
  </div>
  <div class="inspector-card-body">
   <section class="inspector-block"><h3>Entry</h3><div class="inspector-kv">
    <div><span>Opened</span><strong>${date(t.openedAt)}</strong></div><div><span>Price</span><strong>${price(t.entryPrice)}</strong></div>
    <div><span>Trade Signal ID</span><strong>#${esc(t.entrySignalId??'—')}</strong></div><div><span>Wallet Trade ID</span><strong>#${esc(t.walletBuyTradeId??'—')}</strong></div>
    <div><span>Signal</span><strong>${esc(t.entryDecision||'BUY')} ${t.entryScore}/100</strong></div><div><span>Confidence</span><strong>${t.entryConfidence}/100</strong></div>
    <div><span>Interval</span><strong>${esc(t.entryInterval||'—')}</strong></div><div><span>Regime</span><strong>${esc(t.entryRegime||'—')}</strong></div><div><span>Strategy</span><strong>${esc(t.entryStrategy||'—')}</strong></div>
   </div></section>
   <section class="inspector-block"><h3>Trade Plan & Protection</h3><div class="inspector-kv">
    <div><span>Stop loss</span><strong class="negative">${price(t.stopLoss)}</strong></div><div><span>Take profit</span><strong class="positive">${price(t.takeProfit)}</strong></div>
    <div><span>Best price</span><strong>${price(t.maximumFavorablePrice)}</strong></div><div><span>Max favorable</span><strong class="${mfeClass}">${pct(t.maximumFavorablePercent)}</strong></div>
    <div><span>Worst price</span><strong>${price(t.maximumAdversePrice)}</strong></div><div><span>Max adverse</span><strong class="${maeClass}">${pct(t.maximumAdversePercent)}</strong></div>
    <div><span>Quantity</span><strong>${Number(t.quantity||0).toLocaleString(undefined,{maximumFractionDigits:8})}</strong></div>
   </div>
   ${t.profitLockActivated ? `<div class="inspector-profit-lock"><strong>PROFIT LOCK ACTIVATED</strong><span>Protected at ${price(t.profitLockPrice)}</span><small>Activated ${date(t.profitLockActivatedAt)} · best TP progress ${Number(t.profitLockProgressPercent||0).toFixed(1)}%</small></div>` : `<div class="inspector-profit-lock inactive"><strong>Profit Lock not activated</strong><small>The trade closed before the activation threshold was reached.</small></div>`}
   </section>
   <section class="inspector-block"><h3>Exit</h3><div class="inspector-kv">
    <div><span>Closed</span><strong>${date(t.closedAt)}</strong></div><div><span>Price</span><strong>${price(t.exitPrice)}</strong></div>
    <div><span>Trade Signal ID</span><strong>#${esc(t.exitSignalId??'—')}</strong></div><div><span>Wallet Trade ID</span><strong>#${esc(t.walletSellTradeId??'—')}</strong></div>
    <div><span>Closed by</span><strong>${esc(t.closeReason||'—')}</strong></div><div><span>Exit signal</span><strong>${esc(t.exitDecision||'—')}${t.exitScore==null?'':` ${t.exitScore}/100`}</strong></div>
    <div><span>Confidence</span><strong>${t.exitConfidence==null?'—':`${t.exitConfidence}/100`}</strong></div>
   </div></section>
   <section class="inspector-block"><h3>After Exit</h3>
    <div class="post-exit-prices"><div><small>+15m</small><strong>${price(t.priceAfter15m)}</strong></div><div><small>+30m</small><strong>${price(t.priceAfter30m)}</strong></div><div><small>+60m</small><strong>${price(t.priceAfter60m)}</strong></div></div>
    <span class="quality ${qualityClass(t.exitQuality)}">${qualityLabel(t.exitQuality)}</span><p class="quality-explanation">${esc(t.exitQualityExplanation)}</p>
   </section>
  </div>
 </article>`;
}

async function load(){
 $('inspector-error').classList.add('hidden');
 try{
  const symbol=encodeURIComponent($('symbol-filter').value||'ALL'),limit=encodeURIComponent($('limit-filter').value||20);
  const r=await fetch(`/api/trade-inspector?symbol=${symbol}&limit=${limit}`,{cache:'no-store'});if(!r.ok)throw new Error(`HTTP ${r.status}`);
  const d=await r.json();window.__inspectorTrades=d.trades||[];renderSummary(d.summary);renderSymbols(d.symbols);
  $('trade-cards').innerHTML=d.trades?.length?d.trades.map(tradeCard).join(''):'<div class="empty">No completed trades match this filter.</div>';
  $('inspector-updated').textContent=`Updated ${new Date().toLocaleTimeString()}`;
 }catch(e){$('inspector-error').textContent=`Trade Inspector could not load: ${e.message}`;$('inspector-error').classList.remove('hidden')}
}
$('refresh-inspector').addEventListener('click',load);$('symbol-filter').addEventListener('change',load);$('limit-filter').addEventListener('change',load);load();


function inspectorTradeKey(t){return String(t.tradeHistoryId??t.walletSellTradeId??'')}
function findTradeByKey(key){return (window.__inspectorTrades||[]).find(t=>inspectorTradeKey(t)===String(key))}

function inspectorPoint(time, value, side){
  const isBuy=side==='BUY';
  const d=window.CryptoTime.parseUtc(time);
  return {
    x:d?.getTime(), y:Number(value),
    marker:{size:7,fillColor:isBuy?'#39d98a':'#ff6b72',strokeColor:'#071018',strokeWidth:2,radius:7},
    label:{text:side,borderColor:isBuy?'#39d98a':'#ff6b72',style:{background:isBuy?'#123d2d':'#47242a',color:'#fff',fontSize:'10px',fontWeight:700}}
  };
}

async function loadInspectedTradeChart(){
  const t=inspectedTradeFocus;
  if(!t)return;
  const opened=window.CryptoTime.parseUtc(t.openedAt),closed=window.CryptoTime.parseUtc(t.closedAt);
  if(!opened||!closed||Number.isNaN(opened.getTime())||Number.isNaN(closed.getTime()))return;
  const interval=$('inspected-trade-interval')?.value||'5m';
  const from=new Date(opened.getTime()-7*60*60*1000),to=new Date(closed.getTime()+7*60*60*1000);
  const params=new URLSearchParams({symbol:String(t.symbol||'').toUpperCase(),interval,from:from.toISOString(),to:to.toISOString()});
  const r=await fetch(`/api/trade-inspector/chart?${params.toString()}`,{cache:'no-store'});
  if(!r.ok)throw new Error(`Chart HTTP ${r.status}`);
  const data=await r.json();
  const candles=(data.candles||[]).map(c=>({
    x:window.CryptoTime.parseUtc(c.openTime),
    y:[Number(c.openPrice),Number(c.highPrice),Number(c.lowPrice),Number(c.closePrice)]
  }));
  const empty=$('inspected-trade-chart-empty');
  if(!candles.length){empty?.classList.remove('hidden');if(inspectedTradeChart){inspectedTradeChart.destroy();inspectedTradeChart=null;}return;}
  empty?.classList.add('hidden');
  const pnl=Number(t.realizedPnlPercent??0);
  const path=[{x:opened.getTime(),y:Number(t.entryPrice)},{x:closed.getTime(),y:Number(t.exitPrice)}];
  const options={
    chart:{type:'line',height:420,background:'transparent',foreColor:'#8da2b1',toolbar:{show:true},animations:{enabled:false},zoom:{enabled:true}},
    title:{text:`${String(t.symbol||'').toUpperCase()} · ${interval} · Trade ${t.tradeHistoryId==null?'':`#${t.tradeHistoryId}`} · ${pnl>=0?'+':''}${pnl.toFixed(3)}%`,align:'left',style:{fontSize:'13px',fontWeight:600,color:'#dbe8ef'}},
    series:[{name:'Price',type:'candlestick',data:candles},{name:`Trade Path · ${pnl>=0?'+':''}${pnl.toFixed(3)}%`,type:'line',data:path}],
    stroke:{width:[1,3],curve:'straight'},markers:{size:[0,3]},dataLabels:{enabled:false},
    xaxis:{type:'datetime',labels:{datetimeUTC:false},tooltip:{enabled:true,formatter:value=>{const d=new Date(Number(value));return Number.isNaN(d.getTime())?'':d.toLocaleString();}}},
    yaxis:{tooltip:{enabled:true},decimalsInFloat:4},grid:{borderColor:'#203342'},theme:{mode:'dark'},
    plotOptions:{candlestick:{colors:{upward:'#39d98a',downward:'#ff6b72'}}},
    annotations:{points:[inspectorPoint(t.openedAt,t.entryPrice,'BUY'),inspectorPoint(t.closedAt,t.exitPrice,'SELL')]},
    tooltip:{shared:false}
  };
  if(inspectedTradeChart)inspectedTradeChart.destroy();
  inspectedTradeChart=new ApexCharts($('inspected-trade-chart'),options);
  await inspectedTradeChart.render();
}

async function showInspectedTradeChart(t){
  inspectedTradeFocus=t;
  $('inspected-trade-chart-panel')?.classList.remove('hidden');
  $('inspected-trade-chart-title').textContent=`${String(t.symbol||'').toUpperCase()} · inspected BUY → SELL`;
  await loadInspectedTradeChart();
  $('inspected-trade-chart-panel')?.scrollIntoView({behavior:'smooth',block:'start'});
}

document.addEventListener('click',event=>{
  const button=event.target.closest('button[data-inspect-chart]');if(!button)return;
  const trade=findTradeByKey(button.dataset.tradeId);if(!trade)return;
  showInspectedTradeChart(trade).catch(e=>{ $('inspector-error').textContent=`Trade chart could not load: ${e.message}`;$('inspector-error').classList.remove('hidden'); });
});
$('inspected-trade-interval')?.addEventListener('change',()=>loadInspectedTradeChart().catch(e=>{ $('inspector-error').textContent=`Trade chart could not load: ${e.message}`;$('inspector-error').classList.remove('hidden'); }));
