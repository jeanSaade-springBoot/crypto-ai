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
    <button type="button" class="trade-chart-link trade-path-link" data-inspect-path="1" data-trade-id="${esc(t.tradeHistoryId??t.walletSellTradeId??'')}" title="View the persisted decision and state path for this trade"><span>⌁</span> View path</button>
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


async function replaceInspectedWindow(min,max){
  const state=window.__inspectedChartState;
  if(!state||!inspectedTradeChart||state.loading)return;
  const bounds=inspectedWindowBounds(min,max,state.step,state.fullStart,state.fullEnd);
  if(bounds.from>=state.loadedStart&&bounds.to<=state.loadedEnd)return;
  const requestId=++inspectedWindowRequest;
  state.loading=true;
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
        // FIX-015: toolbar/interval actions can recreate or mutate Apex internals.
        // Rebind the display-only Y-axis hover badge after every chart interaction
        // without changing candle, zoom, pan or trading behavior.
        updated:()=>scheduleInspectorYAxisHoverRefresh(),
        selection:(_ctx,{xaxis})=>{if(xaxis)scheduleInspectedWindow(xaxis.min,xaxis.max);scheduleInspectorYAxisHoverRefresh();},
        zoomed:(_ctx,{xaxis})=>{if(xaxis)scheduleInspectedWindow(xaxis.min,xaxis.max);scheduleInspectorYAxisHoverRefresh();},
        scrolled:(_ctx,{xaxis})=>{if(xaxis)scheduleInspectedWindow(xaxis.min,xaxis.max);scheduleInspectorYAxisHoverRefresh();},
        beforeResetZoom:()=>{scheduleInspectorYAxisHoverRefresh();}
      }
    },
    title:{text:`${String(t.symbol||'').toUpperCase()} · ${interval} · Trade ${t.tradeHistoryId==null?'':`#${t.tradeHistoryId}`} · ${pnl>=0?'+':''}${pnl.toFixed(3)}%`,align:'left',style:{fontSize:'13px',fontWeight:600,color:'#dbe8ef'}},
    series:[{name:'Candles',type:'candlestick',data:candles},{name:pathName,type:'line',data:path}],
    stroke:{width:[1,2],curve:'straight',dashArray:[0,0]},markers:{size:[0,4]},dataLabels:{enabled:false},
    xaxis:{
      type:'datetime',min:initialMin,max:initialMax,tickAmount:10,
      labels:{datetimeUTC:false,hideOverlappingLabels:true,formatter:(value,timestamp)=>chartTimeLabel(timestamp??value)},
      axisTicks:{show:true},crosshairs:{show:true,position:'front',stroke:{width:1,dashArray:3}},
      // FIX-019: X-axis date/time is rendered by the dedicated crosshair layer so it
      // survives interval changes and Apex toolbar mode changes consistently.
      tooltip:{enabled:false}
    },
    yaxis:{
      // FIX-010: Binance/TradingView-style price scale lives on the right side.
      opposite:true,
      forceNiceScale:true,decimalsInFloat:8,
      labels:{formatter:value=>chartPriceLabel(value)},
      // FIX-010: keep an explicit Binance-style hover price badge on the Y axis.
      // The badge follows the horizontal crosshair and uses the same precision as candle prices.
      // FIX-019: Y-axis price is rendered by the dedicated crosshair layer.
      tooltip:{enabled:false},
      crosshairs:{show:true,position:'front',stroke:{width:1,dashArray:3}}
    },
    grid:{borderColor:'#203342',xaxis:{lines:{show:false}},yaxis:{lines:{show:true}},padding:{left:6,right:10}},theme:{mode:'dark'},
    plotOptions:{candlestick:{colors:{upward:'#39d98a',downward:'#ff6b72'},wick:{useFillColor:true}}},
    annotations:inspectedChartAnnotations(t),
    tooltip:{shared:false,intersect:false,followCursor:true,custom:customTooltip}
  };

  const chartHost=$('inspected-trade-chart');
  if(chartHost&&typeof chartHost.__inspectorYAxisHoverCleanup==='function')chartHost.__inspectorYAxisHoverCleanup();
  if(inspectedTradeChart)inspectedTradeChart.destroy();
  inspectedTradeChart=new ApexCharts(chartHost,options);
  await inspectedTradeChart.render();

  // FIX-013: ApexCharts' built-in Y-axis tooltip is unreliable for a mixed
  // candlestick + lifecycle series. Render our own Binance-style price badge by
  // mapping the pointer's vertical position inside the actual plot grid to the
  // chart's current visible Y range. This is display-only and never captures
  // clicks, wheel events, pan/zoom gestures, or toolbar controls.
  const host=$('inspected-trade-chart');
  if(host){
    host.onwheel=null;
    installInspectorYAxisHoverPrice(host, inspectedTradeChart);
  }
}

function scheduleInspectorYAxisHoverRefresh(){
  window.clearTimeout(window.__inspectorHoverRefreshTimer);
  window.__inspectorHoverRefreshTimer=window.setTimeout(()=>{
    const host=$('inspected-trade-chart');
    if(host&&inspectedTradeChart)installInspectorYAxisHoverPrice(host,inspectedTradeChart);
  },0);
}

function inspectorVisibleYRange(chart){
  const globals=chart?.w?.globals;
  // Prefer Apex's live rendered scale. These arrays are refreshed after zoom/pan/reset.
  let min=Number(globals?.minYArr?.[0]);
  let max=Number(globals?.maxYArr?.[0]);
  if(Number.isFinite(min)&&Number.isFinite(max)&&max>min)return {min,max};

  // FIX-015 fallback: derive the range from candles that are actually visible after
  // interval changes or toolbar actions. This avoids holding a stale destroyed chart scale.
  const state=window.__inspectedChartState;
  const minX=Number(globals?.minX ?? state?.visibleMin);
  const maxX=Number(globals?.maxX ?? state?.visibleMax);
  const values=[];
  (state?.candles||[]).forEach(point=>{
    const x=Number(point?.x);
    if(Number.isFinite(minX)&&Number.isFinite(maxX)&&(x<minX||x>maxX))return;
    (Array.isArray(point?.y)?point.y:[point?.y]).forEach(v=>{const n=Number(v);if(Number.isFinite(n))values.push(n);});
  });
  if(!values.length)return null;
  min=Math.min(...values);max=Math.max(...values);
  if(max<=min)return null;
  const pad=(max-min)*0.04;
  return {min:min-pad,max:max+pad};
}

function installInspectorYAxisHoverPrice(host, chart){
  // FIX-019: Trade Inspector now shares the Proven/Test full X/Y crosshair behavior.
  // The custom layer is lifecycle-safe: interval changes destroy the old chart/listeners,
  // while zoom/pan/reset simply rebind against Apex's current visible scale. It is display-only
  // and never owns pointer events, so toolbar, wheel, page scrolling and chart gestures remain intact.
  if(typeof host.__inspectorYAxisHoverCleanup==='function')host.__inspectorYAxisHoverCleanup();
  host.querySelectorAll('.inspector-crosshair-v,.inspector-crosshair-h,.inspector-axis-hover-label,.inspector-y-hover-price').forEach(el=>el.remove());

  const make=(className)=>{const el=document.createElement('div');el.className=className;el.setAttribute('aria-hidden','true');host.appendChild(el);return el;};
  const ui={
    vertical:make('inspector-crosshair-v'),
    horizontal:make('inspector-crosshair-h'),
    price:make('inspector-axis-hover-label inspector-crosshair-price'),
    time:make('inspector-axis-hover-label inspector-crosshair-time')
  };
  host.__inspectorYAxisHoverChart=chart;

  const hide=()=>Object.values(ui).forEach(el=>{el.style.display='none';});
  const move=(event)=>{
    const activeChart=host.__inspectorYAxisHoverChart||inspectedTradeChart;
    const grid=host.querySelector('.apexcharts-grid')||host.querySelector('.apexcharts-inner');
    if(!grid||!activeChart?.w?.globals){hide();return;}
    const rect=grid.getBoundingClientRect();
    const hostRect=host.getBoundingClientRect();
    if(event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom){hide();return;}

    const globals=activeChart.w.globals;
    const yRange=inspectorVisibleYRange(activeChart);
    const minX=Number(globals.minX ?? window.__inspectedChartState?.visibleMin);
    const maxX=Number(globals.maxX ?? window.__inspectedChartState?.visibleMax);
    if(!yRange||!Number.isFinite(minX)||!Number.isFinite(maxX)||maxX<=minX){hide();return;}

    const gx=rect.left-hostRect.left,gy=rect.top-hostRect.top;
    const px=event.clientX-rect.left,py=event.clientY-rect.top;
    const xRatio=Math.min(1,Math.max(0,px/Math.max(1,rect.width)));
    const yRatio=Math.min(1,Math.max(0,py/Math.max(1,rect.height)));
    const timeValue=minX+xRatio*(maxX-minX);
    const priceValue=yRange.max-yRatio*(yRange.max-yRange.min);

    ui.vertical.style.display='block';
    ui.vertical.style.left=`${gx+px}px`;
    ui.vertical.style.top=`${gy}px`;
    ui.vertical.style.height=`${rect.height}px`;

    ui.horizontal.style.display='block';
    ui.horizontal.style.left=`${gx}px`;
    ui.horizontal.style.top=`${gy+py}px`;
    ui.horizontal.style.width=`${rect.width}px`;

    ui.price.textContent=chartPriceLabel(priceValue);
    ui.price.style.display='block';
    ui.price.style.left=`${gx+rect.width+4}px`;
    ui.price.style.top=`${gy+py}px`;

    ui.time.textContent=chartTimeLabel(timeValue,true);
    const labelLeft=Math.max(gx+72,Math.min(gx+rect.width-72,gx+px));
    ui.time.style.display='block';
    ui.time.style.left=`${labelLeft}px`;
    ui.time.style.top=`${gy+rect.height+5}px`;
  };

  host.addEventListener('pointermove',move,{passive:true,capture:true});
  host.addEventListener('pointerleave',hide,{passive:true,capture:true});
  host.__inspectorYAxisHoverCleanup=()=>{
    host.removeEventListener('pointermove',move,true);
    host.removeEventListener('pointerleave',hide,true);
    Object.values(ui).forEach(el=>el.remove());
    host.__inspectorYAxisHoverCleanup=null;
    host.__inspectorYAxisHoverChart=null;
  };
}

async function showInspectedTradeChart(t){
  inspectedTradeFocus=t;
  const modal=$('inspected-trade-chart-panel');
  modal?.classList.remove('hidden');
  modal?.setAttribute('aria-hidden','false');
  document.body.classList.add('inspector-modal-open');
  $('inspected-trade-chart-title').textContent=`${String(t.symbol||'').toUpperCase()} · inspected BUY → SELL`;
  await loadInspectedTradeChart();
}

function closeInspectedTradeChart(){
  const modal=$('inspected-trade-chart-panel');
  modal?.classList.add('hidden');
  modal?.setAttribute('aria-hidden','true');
  document.body.classList.remove('inspector-modal-open');
  const host=$('inspected-trade-chart');
  if(host&&typeof host.__inspectorYAxisHoverCleanup==='function')host.__inspectorYAxisHoverCleanup();
  if(inspectedTradeChart){inspectedTradeChart.destroy();inspectedTradeChart=null;}
  if(host)host.innerHTML='';
  window.__inspectedChartState=null;
  inspectedTradeFocus=null;
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
$('inspected-trade-close')?.addEventListener('click',closeInspectedTradeChart);
document.addEventListener('click',event=>{if(event.target.closest('[data-inspector-popup-close]'))closeInspectedTradeChart();});
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&inspectedTradeFocus)closeInspectedTradeChart();});
$('inspected-trade-fit')?.addEventListener('click',()=>inspectedNavigate('trade').catch(()=>{}));
$('inspected-trade-entry')?.addEventListener('click',()=>inspectedNavigate('entry').catch(()=>{}));
$('inspected-trade-earliest')?.addEventListener('click',()=>inspectedNavigate('earliest').catch(()=>{}));
$('inspected-trade-latest')?.addEventListener('click',()=>inspectedNavigate('latest').catch(()=>{}));


// FIX-024: Trade Inspector View Path. This is intentionally presentation-only:
// it renders persisted production signal/opportunity/wallet evidence returned by the
// read-only path endpoint and never recalculates trading rules in JavaScript.
function ksaDateTime(value){
  if(!value)return '—';
  const d=new Date(value);if(Number.isNaN(d.getTime()))return '—';
  return new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Riyadh',day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(d)+' KSA';
}
function secondsLabel(value){
  const total=Math.max(0,Math.round(Number(value||0)));
  const h=Math.floor(total/3600),m=Math.floor((total%3600)/60),sec=total%60;
  if(h)return `${h}h ${m}m ${sec}s`;
  if(m)return `${m}m ${sec}s`;
  return `${sec}s`;
}
function pathValue(value,suffix=''){return value===null||value===undefined||value===''?'—':`${esc(value)}${suffix}`}
function pathSignalCard(label,s){
  if(!s||!s.id)return `<article class="trade-path-node muted"><div class="trade-path-node-head"><strong>${esc(label)}</strong><span>Unavailable</span></div></article>`;
  return `<article class="trade-path-node">
    <div class="trade-path-node-head"><strong>${esc(label)} · ${esc(s.interval||'')}</strong><span>${ksaDateTime(s.generatedAt)}</span></div>
    <div class="trade-path-primary"><b>${esc(s.decision||'—')}</b><span>${pathValue(s.score)}/100 · confidence ${pathValue(s.confidence)}/100 · ${price(s.price)}</span></div>
    <div class="trade-path-stat-grid">
      <div><small>Trend</small><strong>${pathValue(s.trend)}</strong></div><div><small>Volume</small><strong>${pathValue(s.volume)}</strong></div><div><small>Momentum</small><strong>${pathValue(s.momentum)}</strong></div>
      <div><small>Regime</small><strong>${pathValue(s.regime)}</strong></div><div><small>Strategy</small><strong>${pathValue(s.strategy)}</strong></div><div><small>Confluence</small><strong>${pathValue(s.confluence)}</strong></div>
    </div>
  </article>`;
}
function contributor(label,status,body,kind=''){
  return `<article class="trade-contributor ${esc(kind)}"><div class="trade-contributor-head"><strong>${esc(label)}</strong><span>${esc(status||'—')}</span></div><div class="trade-contributor-body">${body}</div></article>`;
}
function decisionPathRows(raw){
  if(!raw)return '<div class="empty">No persisted final-decision path is available for this entry signal.</div>';
  try{
    const rows=typeof raw==='string'?JSON.parse(raw):raw;
    if(!Array.isArray(rows)||!rows.length)return '<div class="empty">No persisted final-decision checks.</div>';
    return `<div class="decision-check-list">${rows.map(r=>`<div class="decision-check ${String(r.type||'').toLowerCase()}"><span class="decision-check-seq">${esc(r.sequence??'')}</span><div><strong>${esc(r.source||r.type||'CHECK')} · ${esc(r.beforeDecision||'—')} → ${esc(r.afterDecision||'—')}</strong><p>${esc(r.reason||'')}</p></div><span class="decision-check-state">${r.entryAllowedAfter===false?'BLOCKED':'PASS'}</span></div>`).join('')}</div>`;
  }catch(_){return `<pre class="trade-path-json">${esc(raw)}</pre>`;}
}
function lifecycleSignalDetail(s){
  const pieces=[`${s.interval||'1m'} ${s.originalDecision&&s.originalDecision!==s.decision?`${s.originalDecision}→`:''}${s.decision||'—'}`,`${s.score??'—'}/100`,`conf ${s.confidence??'—'}`,price(s.price)];
  if(s.confluence)pieces.push(`MTF ${s.confluence}`);
  if(s.liquidityStatus&&s.liquidityStatus!=='UNAVAILABLE')pieces.push(`OB ${s.liquidityStatus}`);
  return pieces.join(' · ');
}
function renderTradePath(data){
  const one=data.oneMinute||{},five=data.fiveMinute||{},hour=data.oneHour||{},entry=data.entrySignal||one,opp=data.opportunity||{},mgmt=data.management||{};
  const exitOne=data.exitOneMinute||{},exitFive=data.exitFiveMinute||{},exitHour=data.exitOneHour||{};
  $('inspected-trade-path-title').textContent=`${String(data.symbol||'').toUpperCase()} · BUY → SELL decision path`;
  $('inspected-trade-path-summary').innerHTML=`Opened <strong>${ksaDateTime(data.openedAt)}</strong> at <strong>${price(data.entryPrice)}</strong> · closed <strong>${ksaDateTime(data.closedAt)}</strong> at <strong>${price(data.exitPrice)}</strong> · holding <strong>${secondsLabel(data.holdingSeconds)}</strong> · P&amp;L <strong class="${cls(data.realizedPnlPercent)}">${pct(data.realizedPnlPercent)}</strong>`;

  const opportunityAge=opp.startedAt&&data.openedAt?Math.max(0,(new Date(data.openedAt)-new Date(opp.startedAt))/1000):null;

  // FIX-025: build one chronological lifecycle from the persisted wallet executions
  // and signal states that occurred while the position was actually open. This is
  // intentionally diagnostic only; it does not infer or recalculate trading actions.
  const flow=[];
  if(opp.startedAt)flow.push({name:'Opportunity started',time:opp.startedAt,detail:`#${opp.id} · ${opp.status||'—'} · evidence ${opp.evidenceScore??'—'} · health ${opp.health??'—'}`,order:0});
  if(entry.generatedAt)flow.push({name:'Entry signal',time:entry.generatedAt,detail:lifecycleSignalDetail(entry),order:1});
  (data.walletLifecycle||[]).forEach(w=>{
    const isTerminal=String(w.side||'').toUpperCase()==='SELL';
    flow.push({name:isTerminal?'Wallet SELL':'Wallet BUY / add',time:w.executedAt,detail:`${w.executionReason||w.side||'EXECUTION'} · ${price(w.price)}${w.realizedPnlPercent==null?'':` · P&L ${pct(w.realizedPnlPercent)}`}`,order:isTerminal?90:10});
  });
  (data.signalLifecycle||[]).forEach(s=>{
    // Do not duplicate the exact entry signal; all later timing/setup/authority changes
    // remain visible so the user can follow confirmation, HOLD and deterioration.
    if(entry.id&&s.id===entry.id)return;
    flow.push({name:`${s.interval||'1m'} market state`,time:s.generatedAt,detail:lifecycleSignalDetail(s),order:30});
  });
  if(mgmt.profitLockActivatedAt)flow.push({name:'Profit lock activated',time:mgmt.profitLockActivatedAt,detail:`lock ${price(mgmt.profitLockPrice)} · progress ${pathValue(mgmt.profitLockProgressPercent,'%')}`,order:50});
  if(data.exitSignal?.generatedAt)flow.push({name:'Persisted exit signal',time:data.exitSignal.generatedAt,detail:lifecycleSignalDetail(data.exitSignal),order:80});
  // Mechanical STOP_LOSS/TAKE_PROFIT exits often have no linked TradeSignal. Keep the
  // latest 1m/5m/1h states at SELL visible before the terminal wallet event instead.
  [exitOne,exitFive,exitHour].filter(s=>s&&s.id).forEach(s=>flow.push({name:`Exit context · ${s.interval}`,time:s.generatedAt,detail:lifecycleSignalDetail(s),order:85}));
  if(!(data.walletLifecycle||[]).some(w=>String(w.side||'').toUpperCase()==='SELL'))flow.push({name:'Wallet SELL',time:data.closedAt,detail:`${data.exitExecutionReason||'SELL'} · ${price(data.exitPrice)} · P&L ${pct(data.realizedPnlPercent)}`,order:99});

  flow.sort((a,b)=>{const t=new Date(a.time)-new Date(b.time);return t||a.order-b.order;});
  let previous=null;
  const timeline=flow.map(item=>{const elapsed=previous?Math.max(0,(new Date(item.time)-new Date(previous.time))/1000):null;previous=item;return `<div class="trade-path-step"><div class="trade-path-dot"></div><div class="trade-path-step-body"><div><strong>${esc(item.name)}</strong><span>${ksaDateTime(item.time)}</span></div><p>${esc(item.detail)}</p>${elapsed===null?'':`<small>+${secondsLabel(elapsed)} from previous state</small>`}</div></div>`}).join('');

  const technicalBody=`<div class="trade-path-stat-grid compact"><div><small>EMA cross</small><strong>${pathValue(entry.emaCross)}</strong></div><div><small>EMA200 location</small><strong>${pathValue(entry.priceEma200)}</strong></div><div><small>EMA alignment</small><strong>${pathValue(entry.emaAlignment)}</strong></div><div><small>SMA20</small><strong>${pathValue(entry.sma20)}</strong></div><div><small>Trend direction</small><strong>${pathValue(entry.trendDirection)}</strong></div><div><small>Structure</small><strong>${pathValue(entry.trendStructure)}</strong></div><div><small>Strength</small><strong>${pathValue(entry.trendStrength)}</strong></div><div><small>Price location</small><strong>${pathValue(entry.trendPriceLocation)}</strong></div><div><small>RSI</small><strong>${pathValue(entry.rsi)}</strong></div><div><small>MACD</small><strong>${pathValue(entry.macd)}</strong></div><div><small>Bollinger</small><strong>${pathValue(entry.bollinger)}</strong></div><div><small>RVOL</small><strong>${pathValue(entry.relativeVolume)}</strong></div><div><small>Volume SMA20</small><strong>${pathValue(entry.volumeSma20)}</strong></div><div><small>Raw / max</small><strong>${pathValue(entry.rawScore)} / ${pathValue(entry.maximumAvailableScore)}</strong></div><div><small>Sentiment</small><strong>${entry.sentimentAvailable?pathValue(entry.sentiment):'EXCLUDED'}</strong></div><div><small>Fundamental</small><strong>${entry.fundamentalAvailable?pathValue(entry.fundamental):'EXCLUDED'}</strong></div></div>`;
  const orderBody=`<div class="trade-path-stat-grid compact"><div><small>Imbalance</small><strong>${pathValue(entry.orderBookImbalance)}</strong></div><div><small>Spread</small><strong>${pathValue(entry.spreadPercent,'%')}</strong></div><div><small>Bid depth</small><strong>${pathValue(entry.bidDepth)}</strong></div><div><small>Ask depth</small><strong>${pathValue(entry.askDepth)}</strong></div><div><small>Ask wall</small><strong>${price(entry.askWallPrice)}</strong></div><div><small>Ask wall size</small><strong>${pathValue(entry.askWallSize)}</strong></div><div><small>Bid wall</small><strong>${price(entry.bidWallPrice)}</strong></div><div><small>Bid wall size</small><strong>${pathValue(entry.bidWallSize)}</strong></div><div><small>Persistence</small><strong>${secondsLabel(entry.wallPersistenceSeconds)}</strong></div><div><small>Observations</small><strong>${pathValue(entry.orderBookObservations)}</strong></div><div><small>Window</small><strong>${secondsLabel(entry.orderBookWindowSeconds)}</strong></div><div><small>Influence</small><strong>${pathValue(entry.orderBookInfluence)}</strong></div><div><small>Target blocked</small><strong>${entry.targetBlocked?'YES':'NO'}</strong></div><div><small>Stop exposed</small><strong>${entry.stopExposed?'YES':'NO'}</strong></div></div><p>${esc(entry.liquidityExplanation||'')}</p>`;
  const btcBody=`<div class="trade-path-stat-grid compact"><div><small>BTC decision</small><strong>${pathValue(entry.btcDecision)}</strong></div><div><small>BTC trend</small><strong>${pathValue(entry.btcTrend)}</strong></div><div><small>Correlation</small><strong>${pathValue(entry.btcCorrelation)}</strong></div><div><small>Beta</small><strong>${pathValue(entry.btcBeta)}</strong></div><div><small>Influence</small><strong>${pathValue(entry.btcInfluence)}</strong></div><div><small>Samples</small><strong>${pathValue(entry.btcSampleSize)}</strong></div><div><small>Stable</small><strong>${entry.btcStable?'YES':'NO'}</strong></div><div><small>Higher TF</small><strong>${pathValue(entry.higherInterval)} ${pathValue(entry.higherDecision)}</strong></div></div><p>${esc(entry.btcExplanation||'')}</p>`;
  const derivBody=`<div class="trade-path-stat-grid compact"><div><small>Funding</small><strong>${pathValue(entry.fundingRate)}</strong></div><div><small>Funding percentile</small><strong>${pathValue(entry.fundingPercentile,'%')}</strong></div><div><small>Open interest</small><strong>${pathValue(entry.openInterest)}</strong></div><div><small>OI value</small><strong>${pathValue(entry.openInterestValue)}</strong></div><div><small>OI change</small><strong>${pathValue(entry.openInterestChangePercent,'%')}</strong></div><div><small>Price change</small><strong>${pathValue(entry.derivativesPriceChangePercent,'%')}</strong></div><div><small>Confidence adj.</small><strong>${pathValue(entry.derivativesConfidenceAdjustment)}</strong></div></div>`;
  const atrBody=`<div class="trade-path-stat-grid compact"><div><small>ATR</small><strong>${pathValue(entry.atr)}</strong></div><div><small>ATR %</small><strong>${pathValue(entry.atrPercent,'%')}</strong></div><div><small>Entry type</small><strong>${pathValue(entry.atrEntryType)}</strong></div><div><small>Immediate</small><strong>${entry.atrImmediateEntryAllowed?'YES':'NO'}</strong></div><div><small>Overextended</small><strong>${entry.atrOverextended?'YES':'NO'}</strong></div><div><small>R/R</small><strong>${pathValue(entry.riskReward)}</strong></div><div><small>Position</small><strong>${pathValue(entry.atrRecommendedPositionPercent,'%')}</strong></div><div><small>Stop / TP</small><strong>${price(entry.stopLoss)} / ${price(entry.takeProfit)}</strong></div></div>`;
  const oppBody=opp.id?`<div class="trade-path-stat-grid compact"><div><small>Age at BUY</small><strong>${opportunityAge==null?'—':secondsLabel(opportunityAge)}</strong></div><div><small>Evidence</small><strong>${pathValue(opp.evidenceScore)}</strong></div><div><small>BUY/WATCH</small><strong>${pathValue(opp.buyCount)} / ${pathValue(opp.watchCount)}</strong></div><div><small>Neutral/Bearish</small><strong>${pathValue(opp.neutralCount)} / ${pathValue(opp.bearishCount)}</strong></div><div><small>Health</small><strong>${pathValue(opp.health)}</strong></div><div><small>Health momentum</small><strong>${pathValue(opp.healthMomentum)}</strong></div><div><small>Evidence momentum</small><strong>${pathValue(opp.evidenceMomentum)}</strong></div><div><small>Execution source</small><strong>${pathValue(opp.executionSource)}</strong></div></div><p>${esc(opp.explanation||'')}</p>`:'<p>No linked execution-opportunity snapshot was found for the entry signal.</p>';

  $('inspected-trade-path-content').innerHTML=`
    <section class="trade-path-hero"><div><small>Holding time</small><strong>${secondsLabel(data.holdingSeconds)}</strong></div><div><small>Opportunity age</small><strong>${opportunityAge==null?'—':secondsLabel(opportunityAge)}</strong></div><div><small>Entry source</small><strong>${esc(data.entryExecutionReason||'—')}</strong></div><div><small>Exit source</small><strong>${esc(data.exitExecutionReason||'—')}</strong></div></section>
    <section class="trade-path-section"><div class="trade-path-section-head"><h3>Full BUY → SELL lifecycle</h3><span>Signals, adds and exit context · all times KSA (UTC+3)</span></div><div class="trade-path-timeline">${timeline}</div></section>
    <section class="trade-path-section"><div class="trade-path-section-head"><h3>1m / 5m / 1h state at entry</h3><span>Latest persisted state available at wallet execution</span></div><div class="trade-path-signal-grid">${pathSignalCard('Timing',one)}${pathSignalCard('Setup',five)}${pathSignalCard('Authority',hour)}</div></section>
    <section class="trade-path-section"><div class="trade-path-section-head"><h3>1m / 5m / 1h state at exit</h3><span>What the system saw immediately before the SELL</span></div><div class="trade-path-signal-grid">${pathSignalCard('Exit timing',exitOne)}${pathSignalCard('Exit setup',exitFive)}${pathSignalCard('Exit authority',exitHour)}</div></section>
    <section class="trade-path-section"><div class="trade-path-section-head"><h3>Entry decision contributors</h3><span>What strengthened, reduced or blocked the BUY</span></div><div class="trade-contributor-grid">${contributor('Technical statistics',`${entry.decision||'—'} ${entry.score??'—'}/100`,technicalBody)}${contributor('Opportunity',opp.status||'—',oppBody)}${contributor('ATR / volatility',entry.atrImmediateEntryAllowed?'PASS':'WAIT',atrBody,entry.atrImmediateEntryAllowed?'pass':'warn')}${contributor('BTC + MTF',`${entry.btcStatus||'—'} · ${entry.confluence||'—'}`,btcBody)}${contributor('Order book / liquidity',entry.liquidityStatus||'—',orderBody,entry.liquidityEntryAllowed?'pass':'warn')}${contributor('Derivatives',entry.derivativesStatus||'—',derivBody)}</div></section>
    <section class="trade-path-section"><div class="trade-path-section-head"><h3>Entry final decision checks</h3><span>Persisted ordered decision path</span></div>${decisionPathRows(data.decisionPath)}</section>`;
}
async function showInspectedTradePath(t){
  const modal=$('inspected-trade-path-panel');
  modal?.classList.remove('hidden');modal?.setAttribute('aria-hidden','false');document.body.classList.add('inspector-modal-open');
  $('inspected-trade-path-title').textContent=`${String(t.symbol||'').toUpperCase()} · decision path`;
  $('inspected-trade-path-summary').textContent='Loading persisted decision evidence…';
  $('inspected-trade-path-content').innerHTML='<div class="empty">Loading trade path…</div>';
  const q=new URLSearchParams({buyTradeId:String(t.walletBuyTradeId),sellTradeId:String(t.walletSellTradeId)});
  const r=await fetch(`/api/trade-inspector/path?${q.toString()}`,{cache:'no-store'});if(!r.ok)throw new Error(`HTTP ${r.status}`);
  renderTradePath(await r.json());
}
function closeInspectedTradePath(){
  const modal=$('inspected-trade-path-panel');modal?.classList.add('hidden');modal?.setAttribute('aria-hidden','true');
  if($('inspected-trade-chart-panel')?.classList.contains('hidden'))document.body.classList.remove('inspector-modal-open');
}
document.addEventListener('click',event=>{
  const button=event.target.closest('button[data-inspect-path]');if(!button)return;
  const trade=findTradeByKey(button.dataset.tradeId);if(!trade)return;
  showInspectedTradePath(trade).catch(e=>{$('inspector-error').textContent=`Trade path could not load: ${e.message}`;$('inspector-error').classList.remove('hidden');closeInspectedTradePath();});
});
$('inspected-trade-path-close')?.addEventListener('click',closeInspectedTradePath);
document.addEventListener('click',event=>{if(event.target.closest('[data-inspector-path-close]'))closeInspectedTradePath();});
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!$('inspected-trade-path-panel')?.classList.contains('hidden'))closeInspectedTradePath();});
