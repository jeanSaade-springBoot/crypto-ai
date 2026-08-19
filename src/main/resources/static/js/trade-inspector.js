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
    marker:{size:8,fillColor:isBuy?'#39d98a':'#ff6b72',strokeColor:'#071018',strokeWidth:2,radius:8},
    label:{text:side,borderColor:isBuy?'#39d98a':'#ff6b72',style:{background:isBuy?'#123d2d':'#47242a',color:'#fff',fontSize:'10px',fontWeight:700}}
  };
}

function inspectedIntervalMs(interval){
  return ({'1m':60_000,'5m':300_000,'1h':3_600_000,'4h':14_400_000})[interval]||60_000;
}

function chartPriceLabel(value){
  const n=Number(value);if(!Number.isFinite(n))return '';
  const abs=Math.abs(n),digits=abs>0&&abs<0.0001?12:abs<0.01?8:abs<1?6:4;
  return n.toFixed(digits).replace(/\.0+$/,'').replace(/(\.\d*?[1-9])0+$/,'$1');
}

function chartTimeLabel(value, seconds=false){
  const d=new Date(Number(value));
  if(Number.isNaN(d.getTime()))return '';
  return d.toLocaleString(undefined,{month:'short',day:'2-digit',hour:'2-digit',minute:'2-digit',second:seconds?'2-digit':undefined,hour12:false});
}

function chartNumber(value, digits=2){
  const n=Number(value);if(!Number.isFinite(n))return '—';
  return n.toLocaleString(undefined,{maximumFractionDigits:digits});
}

function inspectedFocusRange(t, interval, mode='trade'){
  const opened=window.CryptoTime.parseUtc(t.openedAt),closed=window.CryptoTime.parseUtc(t.closedAt);
  const step=inspectedIntervalMs(interval);
  if(!opened||Number.isNaN(opened.getTime()))return null;
  if(mode==='entry')return {min:opened.getTime()-60*step,max:opened.getTime()+90*step};
  const closeMs=closed&&!Number.isNaN(closed.getTime())?closed.getTime():opened.getTime();
  // Keep enough context on both sides while still showing individual candles clearly.
  const lifecycle=Math.max(step*30,closeMs-opened.getTime());
  const pad=Math.max(step*45,Math.min(lifecycle*.65,step*240));
  return {min:opened.getTime()-pad,max:closeMs+pad};
}

function inspectedChartAnnotations(t){
  const annotations={
    points:[inspectorPoint(t.openedAt,t.entryPrice,'BUY'),inspectorPoint(t.closedAt,t.exitPrice,'SELL')],
    yaxis:[]
  };
  const addLine=(value,text,borderColor)=>{
    const n=Number(value);if(!Number.isFinite(n))return;
    annotations.yaxis.push({y:n,borderColor,strokeDashArray:4,label:{borderColor,style:{background:'#0d1820',color:'#dbe8ef',fontSize:'9px'},text:`${text} ${chartPriceLabel(n)}`}});
  };
  addLine(t.entryPrice,'ENTRY','#39d98a');
  addLine(t.exitPrice,'EXIT','#ff6b72');
  addLine(t.stopLoss,'SL','#d98324');
  addLine(t.takeProfit,'TP','#57a8ff');
  return annotations;
}

const INSPECTOR_WINDOW_POINTS=1600;
const INSPECTOR_EDGE_POINTS=260;
let inspectedWindowTimer=null;
let inspectedWindowRequest=0;

function inspectedWindowBounds(min,max,step,fullStart=null,fullEnd=null){
  const visibleSpan=Math.max(step*40,Number(max)-Number(min));
  const buffer=Math.max(step*500,Math.min(step*INSPECTOR_WINDOW_POINTS,visibleSpan*1.4));
  let from=Number(min)-buffer,to=Number(max)+buffer;
  if(Number.isFinite(fullStart))from=Math.max(fullStart,from);
  if(Number.isFinite(fullEnd))to=Math.min(fullEnd,to);
  return {from,to};
}

async function fetchInspectedWindow(t,interval,fromMs,toMs){
  const params=new URLSearchParams({
    symbol:String(t.symbol||'').toUpperCase(),
    interval,
    from:new Date(fromMs).toISOString(),
    to:new Date(toMs).toISOString()
  });
  const r=await fetch(`/api/trade-inspector/chart?${params.toString()}`,{cache:'no-store'});
  if(!r.ok)throw new Error(`Chart HTTP ${r.status}`);
  return r.json();
}

function inspectedRows(data){
  const meta=new Map();
  const candles=(data.candles||[]).map(c=>{
    const when=window.CryptoTime.parseUtc(c.openTime);const ts=when?.getTime();
    meta.set(Number(ts),c);
    return {x:ts,y:[Number(c.openPrice),Number(c.highPrice),Number(c.lowPrice),Number(c.closePrice)]};
  }).filter(c=>Number.isFinite(c.x)&&c.y.every(Number.isFinite));
  return {candles,meta};
}

function inspectedMetaText(state){
  const loaded=Number(state.candles?.length||0).toLocaleString();
  const total=Number(state.totalPointCount||0).toLocaleString();
  return `${loaded} ${state.interval} candles in active window · ${total} total available · history ${chartTimeLabel(state.fullStart)} → ${chartTimeLabel(state.fullEnd)} · choose pan hand to move left/right; more candles load automatically`;
}

async function replaceInspectedWindow(min,max){
  const state=window.__inspectedChartState;
  if(!state||!inspectedTradeChart||state.loading)return;
  const bounds=inspectedWindowBounds(min,max,state.step,state.fullStart,state.fullEnd);
  if(bounds.from>=state.loadedStart&&bounds.to<=state.loadedEnd)return;
  const requestId=++inspectedWindowRequest;
  state.loading=true;
  $('inspected-trade-chart-meta').textContent='Loading nearby candles…';
  try{
    const data=await fetchInspectedWindow(state.trade,state.interval,bounds.from,bounds.to);
    if(requestId!==inspectedWindowRequest)return;
    const parsed=inspectedRows(data);
    if(!parsed.candles.length)return;
    state.candles=parsed.candles;
    state.candleMeta=parsed.meta;
    state.loadedStart=parsed.candles[0].x;
    state.loadedEnd=parsed.candles[parsed.candles.length-1].x;
    state.totalPointCount=Number(data.totalPointCount||state.totalPointCount||parsed.candles.length);
    state.fullStart=window.CryptoTime.parseUtc(data.firstOpenTime)?.getTime()??state.fullStart;
    state.fullEnd=window.CryptoTime.parseUtc(data.lastOpenTime)?.getTime()??state.fullEnd;
    state.suppressWindowCheck=true;
    await inspectedTradeChart.updateSeries([
      {name:'Candles',type:'candlestick',data:state.candles},
      {name:state.pathName,type:'line',data:state.path}
    ],false);
    inspectedTradeChart.zoomX(min,max);
    requestAnimationFrame(()=>{if(window.__inspectedChartState)window.__inspectedChartState.suppressWindowCheck=false;});
    $('inspected-trade-chart-meta').textContent=inspectedMetaText(state);
  }finally{
    if(window.__inspectedChartState)window.__inspectedChartState.loading=false;
  }
}

function scheduleInspectedWindow(min,max){
  const state=window.__inspectedChartState;
  if(!state||state.suppressWindowCheck||state.loading)return;
  state.visibleMin=Number(min);state.visibleMax=Number(max);
  const edge=state.step*INSPECTOR_EDGE_POINTS;
  const nearLeft=state.visibleMin-state.loadedStart<edge&&state.loadedStart>state.fullStart;
  const nearRight=state.loadedEnd-state.visibleMax<edge&&state.loadedEnd<state.fullEnd;
  if(!nearLeft&&!nearRight)return;
  clearTimeout(inspectedWindowTimer);
  inspectedWindowTimer=setTimeout(()=>replaceInspectedWindow(state.visibleMin,state.visibleMax).catch(e=>{
    $('inspector-error').textContent=`Trade chart could not load nearby candles: ${e.message}`;
    $('inspector-error').classList.remove('hidden');
  }),140);
}

async function loadInspectedTradeChart(){
  const t=inspectedTradeFocus;
  if(!t)return;
  const opened=window.CryptoTime.parseUtc(t.openedAt),closed=window.CryptoTime.parseUtc(t.closedAt);
  if(!opened||!closed||Number.isNaN(opened.getTime())||Number.isNaN(closed.getTime()))return;
  const interval=$('inspected-trade-interval')?.value||'1m';
  const step=inspectedIntervalMs(interval);
  const focus=inspectedFocusRange(t,interval,'trade')||{min:opened.getTime()-step*120,max:closed.getTime()+step*120};
  const initialBounds=inspectedWindowBounds(focus.min,focus.max,step);
  const data=await fetchInspectedWindow(t,interval,initialBounds.from,initialBounds.to);
  const parsed=inspectedRows(data);
  const candles=parsed.candles,candleMeta=parsed.meta;

  const empty=$('inspected-trade-chart-empty');
  if(!candles.length){
    empty?.classList.remove('hidden');
    $('inspected-trade-chart-meta').textContent='No closed candles available.';
    if(inspectedTradeChart){inspectedTradeChart.destroy();inspectedTradeChart=null;}
    return;
  }
  empty?.classList.add('hidden');

  const fullStart=window.CryptoTime.parseUtc(data.firstOpenTime)?.getTime()??candles[0].x;
  const fullEnd=window.CryptoTime.parseUtc(data.lastOpenTime)?.getTime()??candles[candles.length-1].x;
  const initialMin=Math.max(fullStart,focus.min),initialMax=Math.min(fullEnd,focus.max);
  const pnl=Number(t.realizedPnlPercent??0);
  const path=[{x:opened.getTime(),y:Number(t.entryPrice)},{x:closed.getTime(),y:Number(t.exitPrice)}];
  const pathName=`Trade Path · ${pnl>=0?'+':''}${pnl.toFixed(3)}%`;

  window.__inspectedChartState={
    fullStart,fullEnd,interval,step,candleMeta,candles,trade:t,path,pathName,
    loadedStart:candles[0].x,loadedEnd:candles[candles.length-1].x,
    totalPointCount:Number(data.totalPointCount||candles.length),
    visibleMin:initialMin,visibleMax:initialMax,loading:false,suppressWindowCheck:false
  };
  $('inspected-trade-chart-meta').textContent=inspectedMetaText(window.__inspectedChartState);

  const customTooltip=({seriesIndex,dataPointIndex,w})=>{
    const point=w?.config?.series?.[seriesIndex]?.data?.[dataPointIndex];
    const ts=Number(point?.x instanceof Date?point.x.getTime():point?.x);
    const c=window.__inspectedChartState?.candleMeta?.get(ts);
    if(c){
      const buyPct=Number(c.takerBuyPercent);
      return `<div class="inspector-candle-tooltip">
        <div class="tooltip-time">${esc(chartTimeLabel(ts,true))}</div>
        <div class="tooltip-grid">
          <span>Open</span><strong>${esc(chartPriceLabel(c.openPrice))}</strong>
          <span>High</span><strong>${esc(chartPriceLabel(c.highPrice))}</strong>
          <span>Low</span><strong>${esc(chartPriceLabel(c.lowPrice))}</strong>
          <span>Close</span><strong>${esc(chartPriceLabel(c.closePrice))}</strong>
          <span>Volume</span><strong>${esc(chartNumber(c.volume,8))}</strong>
          <span>Taker buy</span><strong>${Number.isFinite(buyPct)?buyPct.toFixed(2)+'%':'—'}</strong>
          <span>Trades</span><strong>${esc(chartNumber(c.numberOfTrades,0))}</strong>
        </div>
      </div>`;
    }
    if(seriesIndex===1&&point){
      return `<div class="inspector-candle-tooltip"><div class="tooltip-time">${esc(chartTimeLabel(ts,true))}</div><div class="tooltip-side">Trade lifecycle · ${pnl>=0?'+':''}${pnl.toFixed(3)}%</div></div>`;
    }
    return '';
  };

  const options={
    chart:{
      type:'line',height:540,background:'transparent',foreColor:'#8da2b1',animations:{enabled:false},
      toolbar:{show:true,autoSelected:'zoom',tools:{download:false,selection:true,zoom:true,zoomin:true,zoomout:true,pan:true,reset:true}},
      zoom:{enabled:true,type:'x',autoScaleYaxis:true},
      events:{
        zoomed:(_ctx,{xaxis})=>{if(xaxis)scheduleInspectedWindow(xaxis.min,xaxis.max);},
        scrolled:(_ctx,{xaxis})=>{if(xaxis)scheduleInspectedWindow(xaxis.min,xaxis.max);}
      }
    },
    title:{text:`${String(t.symbol||'').toUpperCase()} · ${interval} · Trade ${t.tradeHistoryId==null?'':`#${t.tradeHistoryId}`} · ${pnl>=0?'+':''}${pnl.toFixed(3)}%`,align:'left',style:{fontSize:'13px',fontWeight:600,color:'#dbe8ef'}},
    series:[{name:'Candles',type:'candlestick',data:candles},{name:pathName,type:'line',data:path}],
    stroke:{width:[1,2],curve:'straight',dashArray:[0,0]},markers:{size:[0,4]},dataLabels:{enabled:false},
    xaxis:{
      type:'datetime',min:initialMin,max:initialMax,tickAmount:10,
      labels:{datetimeUTC:false,hideOverlappingLabels:true,formatter:(value,timestamp)=>chartTimeLabel(timestamp??value)},
      axisTicks:{show:true},crosshairs:{show:true,position:'front',stroke:{width:1,dashArray:3}},
      tooltip:{enabled:true,formatter:value=>chartTimeLabel(value,true)}
    },
    yaxis:{
      forceNiceScale:true,decimalsInFloat:8,
      labels:{formatter:value=>chartPriceLabel(value)},
      // FIX-010: keep an explicit Binance-style hover price badge on the Y axis.
      // The badge follows the horizontal crosshair and uses the same precision as candle prices.
      tooltip:{enabled:true,offsetX:0},
      crosshairs:{show:true,position:'front',stroke:{width:1,dashArray:3}}
    },
    grid:{borderColor:'#203342',xaxis:{lines:{show:false}},padding:{left:6,right:10}},theme:{mode:'dark'},
    plotOptions:{candlestick:{colors:{upward:'#39d98a',downward:'#ff6b72'},wick:{useFillColor:true}}},
    annotations:inspectedChartAnnotations(t),
    tooltip:{shared:false,intersect:false,followCursor:true,custom:customTooltip}
  };

  if(inspectedTradeChart)inspectedTradeChart.destroy();
  inspectedTradeChart=new ApexCharts($('inspected-trade-chart'),options);
  await inspectedTradeChart.render();

  // FIX-009: do not hijack the mouse wheel. Vertical wheel/trackpad gestures must
  // scroll the Trade Inspector page normally. Use Apex toolbar zoom buttons for
  // chart zoom and explicitly choose the pan hand for left/right navigation.
  const host=$('inspected-trade-chart');
  if(host)host.onwheel=null;
}

async function showInspectedTradeChart(t){
  inspectedTradeFocus=t;
  $('inspected-trade-chart-panel')?.classList.remove('hidden');
  $('inspected-trade-chart-title').textContent=`${String(t.symbol||'').toUpperCase()} · inspected BUY → SELL`;
  await loadInspectedTradeChart();
  $('inspected-trade-chart-panel')?.scrollIntoView({behavior:'smooth',block:'start'});
}

async function inspectedNavigate(mode){
  if(!inspectedTradeChart||!inspectedTradeFocus||!window.__inspectedChartState)return;
  const state=window.__inspectedChartState;
  let min,max;
  if(mode==='earliest'){
    const span=Math.max(state.step*240,Number(state.visibleMax-state.visibleMin)||state.step*480);
    min=state.fullStart;max=Math.min(state.fullEnd,min+span);
  }else if(mode==='latest'){
    const span=Math.max(state.step*240,Number(state.visibleMax-state.visibleMin)||state.step*480);
    max=state.fullEnd;min=Math.max(state.fullStart,max-span);
  }else{
    const range=inspectedFocusRange(inspectedTradeFocus,state.interval,mode==='entry'?'entry':'trade');
    if(!range)return;
    min=Math.max(state.fullStart,range.min);max=Math.min(state.fullEnd,range.max);
  }
  await replaceInspectedWindow(min,max);
  state.visibleMin=min;state.visibleMax=max;
  inspectedTradeChart.zoomX(min,max);
}

document.addEventListener('click',event=>{
  const button=event.target.closest('button[data-inspect-chart]');if(!button)return;
  const trade=findTradeByKey(button.dataset.tradeId);if(!trade)return;
  showInspectedTradeChart(trade).catch(e=>{ $('inspector-error').textContent=`Trade chart could not load: ${e.message}`;$('inspector-error').classList.remove('hidden'); });
});
$('inspected-trade-interval')?.addEventListener('change',()=>loadInspectedTradeChart().catch(e=>{ $('inspector-error').textContent=`Trade chart could not load: ${e.message}`;$('inspector-error').classList.remove('hidden'); }));
$('inspected-trade-fit')?.addEventListener('click',()=>inspectedNavigate('trade').catch(()=>{}));
$('inspected-trade-entry')?.addEventListener('click',()=>inspectedNavigate('entry').catch(()=>{}));
$('inspected-trade-earliest')?.addEventListener('click',()=>inspectedNavigate('earliest').catch(()=>{}));
$('inspected-trade-latest')?.addEventListener('click',()=>inspectedNavigate('latest').catch(()=>{}));
