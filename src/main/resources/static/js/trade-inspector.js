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


// FIX-024 / FIX-027: Trade Inspector View Path is presentation-only. FIX-027
// deliberately simplifies the UI into one sequential ERD/state-machine story while
// preserving persisted production evidence. No score, state or trade is recalculated.
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
function compactNumber(value){
  const n=Number(value);if(!Number.isFinite(n))return '—';
  if(Math.abs(n)>=1_000_000)return `${(n/1_000_000).toFixed(n>=10_000_000?1:2)}M`;
  if(Math.abs(n)>=1_000)return `${(n/1_000).toFixed(n>=100_000?0:1)}K`;
  return `${Math.round(n*100)/100}`;
}
function phaseMetric(component,result,interpretation,kind=''){
  return `<div class="path-phase-metric ${esc(kind)}"><span>${esc(component)}</span><strong>${esc(result??'—')}</strong><small>${esc(interpretation||'')}</small></div>`;
}
function trendInterpretation(v){const n=Number(v);return !Number.isFinite(n)?'':n>=21?'Strong bullish structure':n>=18?'Supportive structure':n>=14?'Mixed structure':'Weak / bearish structure'}
function momentumInterpretation(v){const n=Number(v);return !Number.isFinite(n)?'':n>=14?'Very strong momentum':n>=11?'Supportive momentum':n>=7?'Mixed momentum':'Weak momentum'}
function pressureInterpretation(v){const n=Number(v);return !Number.isFinite(n)?'No closed-candle pressure data':n>=80?'Aggressive buyers dominant':n>=70?'Strong taker BUY pressure':n>=55?'Buyers slightly dominant':n>=45?'Balanced flow':'Sellers dominant'}
function volumeInterpretation(v){const n=Number(v);return !Number.isFinite(n)?'No closed-candle volume':n>=1_000_000?'Exceptional activity':n>=500_000?'High activity':n>=100_000?'Active':'Light activity'}
function scoreInterpretation(v){const n=Number(v);return !Number.isFinite(n)?'':n>=80?'Strong signal':n>=72?'Strong evidence / transition':n>=60?'Supportive evidence':'Weak evidence'}
function technicalInterpretation(s){
  const raw=Number(s.rawScore),max=Number(s.maximumAvailableScore);
  if(!Number.isFinite(raw)||!Number.isFinite(max)||max<=0)return 'Persisted base technical evidence';
  const pct=raw/max*100;return pct>=72?'Strong raw technical evidence':pct>=60?'Supportive raw technical evidence':'Weak raw technical evidence';
}
function atrInterpretation(s){
  if(s.atrImmediateEntryAllowed===false)return `WAIT / ${s.atrEntryType||'ATR block'}`;
  return `${s.atrEntryType||'ENTRY'} · ${s.atrRecommendedPositionPercent??'—'}% allowed`;
}
function vetoSummary(s){
  if(s.finalEntryAllowed===false)return `FINAL BLOCK · ${s.liquidityStatus||s.confluence||'decision veto'}`;
  if(s.liquidityEntryAllowed===false)return `ORDER BOOK VETO · ${s.liquidityStatus||'blocked'}`;
  if(s.atrImmediateEntryAllowed===false)return `ATR WAIT · ${s.atrEntryType||'wait'}`;
  if(s.liquidityStatus&&s.liquidityStatus!=='UNAVAILABLE'&&s.liquidityStatus!=='LEARNING')return `Order book ${s.liquidityStatus}`;
  return 'No hard veto';
}
// FIX-029: Translate persisted scores into one concise trader-readable conclusion.
// This is display-only: it never changes BUY/SELL logic. The wording intentionally uses
// the same evidence already shown in the node (decision, trend, momentum, volume, HTF,
// ATR and veto state) so Trade Inspector explains *why* a state is WATCH/BUY/STRONG_BUY
// without forcing the user to mentally decode every score.
function signalMeaning(s){
  if(!s||!s.id)return 'No persisted evidence is available for this phase.';
  const decision=String(s.decision||'').toUpperCase();
  const original=String(s.originalDecision||'').toUpperCase();
  const trend=Number(s.trend),volume=Number(s.volume),momentum=Number(s.momentum);
  const htf=String(s.pathFiveMinute?.decision||s.higherDecision||'').toUpperCase();
  const trendGood=Number.isFinite(trend)&&trend>=18;
  const trendStrong=Number.isFinite(trend)&&trend>=21;
  const momentumGood=Number.isFinite(momentum)&&momentum>=11;
  const volumeGood=Number.isFinite(volume)&&volume>=10;
  const volumeStrong=Number.isFinite(volume)&&volume>=14;
  const htfBullish=['BUY','STRONG_BUY'].includes(htf);
  const htfMixed=['WATCH','NEUTRAL',''].includes(htf);

  if(s.finalEntryAllowed===false){
    return `The technical setup may be ${['BUY','STRONG_BUY'].includes(original||decision)?'bullish':'interesting'}, but ${vetoSummary(s)} prevents a new entry.`;
  }
  if(s.atrImmediateEntryAllowed===false){
    return `The setup has evidence, but ATR classifies the current price as ${s.atrEntryType||'extended'}; wait for a better entry instead of chasing.`;
  }
  if(decision==='STRONG_BUY'){
    if(trendStrong&&volumeStrong&&momentumGood&&htfBullish)return 'Trend, momentum and participation align strongly, and the higher timeframe confirms the bullish move.';
    if(trendStrong&&volumeStrong&&momentumGood)return 'Trend, momentum and participation align strongly; higher-timeframe context is not bearish, so the setup qualifies as STRONG_BUY.';
    return 'The strategy-adjusted technical evidence is strong enough for STRONG_BUY, with no hard contextual veto.';
  }
  if(decision==='BUY'){
    if(trendGood&&momentumGood&&volumeGood&&htfBullish)return 'Bullish structure, momentum and participation are confirmed by the higher timeframe, supporting a BUY.';
    if(trendGood&&momentumGood&&volumeGood)return 'Technical evidence is bullish and participation is sufficient; higher-timeframe context does not invalidate the BUY.';
    return 'The combined technical and contextual evidence is sufficient for a BUY, although not all components are at strong-buy strength.';
  }
  if(decision==='WATCH'){
    if(trendGood&&momentumGood&&!volumeGood)return 'Direction and momentum look good, but participation/confirmation is not strong enough yet.';
    if(trendGood&&momentumGood&&volumeGood&&htfMixed)return 'Technical evidence is bullish, but higher-timeframe confirmation is still mixed, so the engine remains on WATCH.';
    if(trendGood&&momentumGood)return 'The bullish setup is developing, but one or more confirmation components are still incomplete.';
    return 'Some evidence is constructive, but the combined setup is not strong enough for a BUY yet.';
  }
  if(decision==='NEUTRAL'&&['SELL','STRONG_SELL'].includes(original))return 'Short-term evidence turned bearish, but contextual checks neutralized the raw SELL instead of confirming an exit/short signal.';
  if(decision==='NEUTRAL')return 'Evidence is mixed; neither buyers nor sellers have enough confirmed control for an actionable signal.';
  if(decision==='SELL'||decision==='STRONG_SELL')return 'Trend/momentum evidence has deteriorated enough for a bearish decision; position context determines whether this becomes an actual exit.';
  return 'The displayed state is the result of the persisted technical score plus strategy and contextual checks.';
}
function signalEvidenceHtml(s){
  if(!s||!s.id)return '<div class="path-phase-empty">No persisted signal snapshot</div>';
  const raw=`${s.rawScore??'—'}/${s.maximumAvailableScore??'—'}`;
  return `<div class="path-phase-evidence">
    <div class="path-phase-meaning"><span>What this means</span><strong>${esc(signalMeaning(s))}</strong></div>
    <div class="path-phase-metric path-phase-columns"><span>Component</span><strong>Result</strong><small>Interpretation</small></div>
    ${phaseMetric('Displayed total',`${s.score??'—'}/100`,scoreInterpretation(s.score),'primary')}
    ${phaseMetric('Base technical',raw,technicalInterpretation(s))}
    ${phaseMetric('Trend',`${s.trend??'—'}/25`,trendInterpretation(s.trend))}
    ${phaseMetric('Momentum',`${s.momentum??'—'}/15`,momentumInterpretation(s.momentum))}
    ${phaseMetric('BUY pressure',s.takerBuyPercent==null?'—':`${Number(s.takerBuyPercent).toFixed(2)}%`,pressureInterpretation(s.takerBuyPercent))}
    ${phaseMetric('Volume',compactNumber(s.candleVolume),volumeInterpretation(s.candleVolume))}
    ${phaseMetric('RSI',`${s.rsi??'—'}/7`,Number(s.rsi)>=6?'Bullish / healthy':'Not strongly bullish')}
    ${phaseMetric('MACD',`${s.macd??'—'}/8`,Number(s.macd)>=7?'Bullish / improving':'Weak or mixed')}
    ${s.actualExitTrigger?phaseMetric('REAL EXIT TRIGGER',s.actualExitTrigger,s.actualExitExplanation||'Production close trigger','primary'):''}
    ${s.exitSourceSignalRole?phaseMetric('Signal role',s.exitSourceSignalRole,s.exitSourceSignalRole==='SELL_TRIGGER'?'This persisted SELL/STRONG_SELL triggered the exit':'This signal is market context at exit; it did NOT itself trigger SELL'):''}
    ${s.positionRecommendation?phaseMetric('Position analysis',`${s.positionRecommendation}${s.positionAnalysisConfidence==null?'':` ${s.positionAnalysisConfidence}/100`}`,`Exit score ${s.positionExitScore??'—'} · advisory state at close`):''}
    ${phaseMetric('Decision',`${s.originalDecision&&s.originalDecision!==s.decision?`${s.originalDecision} → `:''}${s.decision||'—'}`,`${s.strategy||'—'} · ${s.regime||'—'}`)}
    ${phaseMetric('5m / 1h',s.pathFiveMinute||s.pathOneHour?`${s.pathFiveMinute?.decision||'—'} / ${s.pathOneHour?.decision||'—'}`:`${s.higherInterval||'HTF'} ${s.higherDecision||'—'}`,s.pathFiveMinute||s.pathOneHour?`Setup ${s.pathFiveMinute?.regime||'—'} · authority ${s.pathOneHour?.regime||'—'}`:`Confluence ${s.confluence||'—'}`)}
    ${phaseMetric('ATR',s.atrImmediateEntryAllowed===false?'WAIT':'ENTRY OK',atrInterpretation(s),s.atrImmediateEntryAllowed===false?'warn':'')}
    ${phaseMetric('Veto / blocker',vetoSummary(s),s.finalEntryAllowed===false?(s.finalExplanation||'Entry blocked'):'Context checks did not hard-block',s.finalEntryAllowed===false?'warn':'')}
  </div>`;
}
function phaseNode(name,time,subtitle,s,kind='normal',extra=''){
  return `<article class="trade-path-phase ${esc(kind)}">
    <div class="trade-path-phase-head"><div><span class="trade-path-phase-label">${esc(name)}</span><strong>${esc(subtitle||'')}</strong></div><time>${ksaDateTime(time)}</time></div>
    ${signalEvidenceHtml(s)}${extra||''}
  </article>`;
}
function decisionPathRows(raw){
  if(!raw)return '<div class="empty">No persisted final-decision path is available for this entry signal.</div>';
  try{
    const rows=typeof raw==='string'?JSON.parse(raw):raw;
    if(!Array.isArray(rows)||!rows.length)return '<div class="empty">No persisted final-decision checks.</div>';
    return `<div class="decision-check-list">${rows.map(r=>`<div class="decision-check ${String(r.type||'').toLowerCase()}"><span class="decision-check-seq">${esc(r.sequence??'')}</span><div><strong>${esc(r.source||r.type||'CHECK')} · ${esc(r.beforeDecision||'—')} → ${esc(r.afterDecision||'—')}</strong><p>${esc(r.reason||'')}</p></div><span class="decision-check-state">${r.entryAllowedAfter===false?'BLOCKED':'PASS'}</span></div>`).join('')}</div>`;
  }catch(_){return `<pre class="trade-path-json">${esc(raw)}</pre>`;}
}
function bestLifecycleSignal(signals,predicate){return (signals||[]).filter(predicate).sort((a,b)=>new Date(a.generatedAt)-new Date(b.generatedAt))[0]||null}
function latestBefore(signals,time,interval='1m'){
  if(!time)return null;const t=new Date(time).getTime();
  return (signals||[]).filter(s=>String(s.interval).toLowerCase()===interval&&new Date(s.generatedAt).getTime()<=t).sort((a,b)=>new Date(b.generatedAt)-new Date(a.generatedAt))[0]||null;
}
function buildSimpleTradePhases(data){
  const signals=[...(data.signalLifecycle||[])];
  const entryBase=data.entrySignal||data.oneMinute||{};
  const entry={...entryBase,pathFiveMinute:data.fiveMinute||{},pathOneHour:data.oneHour||{}};
  if(entry.id&&!signals.some(s=>s.id===entry.id))signals.push(entry);
  const wallets=[...(data.walletLifecycle||[])];
  const phases=[];
  const opp=data.opportunity||{};

  // FIX-027: recovery phases are evidence-backed labels, not new trading decisions. They
  // summarize the persisted signals/candles around the trade so the user can understand
  // the exact path in one glance. RECOVERY_PROBE is only shown when production actually
  // executed the FIX-026 recovery route.
  const bearish=bestLifecycleSignal(signals,s=>String(s.interval).toLowerCase()==='1m'&&['SELL','STRONG_SELL','NEUTRAL'].includes(String(s.originalDecision||s.decision||''))&&Number(s.score)<55);
  const stabilizing=bestLifecycleSignal(signals,s=>String(s.interval).toLowerCase()==='1m'&&new Date(s.generatedAt)<new Date(data.openedAt)&&Number(s.takerBuyPercent)>=55&&Number(s.score)<72);
  const recovering=bestLifecycleSignal(signals,s=>String(s.interval).toLowerCase()==='1m'&&new Date(s.generatedAt)<=new Date(data.openedAt)&&Number(s.score)>=68&&Number(s.trend)>=18&&Number(s.momentum)>=12);
  const entryWallet=wallets.find(w=>String(w.side).toUpperCase()==='BUY')||null;
  const recoveryProbe=entryWallet&&String(entryWallet.executionReason||'').toUpperCase()==='RECOVERY_TRANSITION_ENTRY';
  const confirm=bestLifecycleSignal(signals,s=>new Date(s.generatedAt)>new Date(data.openedAt)&&((String(s.interval).toLowerCase()==='5m'&&['BUY','STRONG_BUY'].includes(String(s.decision)))||(String(s.interval).toLowerCase()==='1m'&&['BUY','STRONG_BUY'].includes(String(s.decision))&&Number(s.score)>=75)));
  // FIX-027: capture the first real post-entry expansion candle even when no TradeSignal
  // was emitted on that exact minute. This is a diagnostic phase only. For recovery
  // probes, a bullish 1m candle with >=70% taker BUY and >=500K volume is displayed as
  // EXPANSION_CONFIRMED using the latest persisted signal as its technical context.
  const expansionCandle=(data.entryEvidenceCandles||[]).find(c=>new Date(c.closeTime)>new Date(data.openedAt)&&Number(c.takerBuyPercent)>=70&&Number(c.volume)>=500000&&Number(c.close)>=Number(c.open));
  const addWallet=wallets.find(w=>String(w.side).toUpperCase()==='BUY'&&entryWallet&&w.id!==entryWallet.id)||null;
  const sellWallet=wallets.filter(w=>String(w.side).toUpperCase()==='SELL').slice(-1)[0]||null;

  if(opp.startedAt)phases.push({name:'OPPORTUNITY',time:opp.startedAt,s:bearish||stabilizing||entry,kind:'context',subtitle:`Evidence ${opp.evidenceScore??'—'} · health ${opp.health??'—'}`});
  if(stabilizing)phases.push({name:'STABILIZING',time:stabilizing.generatedAt,s:stabilizing,kind:'transition',subtitle:'Selling pressure stops controlling price'});
  if(recovering)phases.push({name:'RECOVERING',time:recovering.generatedAt,s:recovering,kind:'transition',subtitle:'Technical + flow evidence improve'});
  if(recoveryProbe)phases.push({name:'RECOVERY_PROBE',time:entryWallet.executedAt,s:entry,kind:'buy',subtitle:`BUY ${price(entryWallet.price)} · ${entryWallet.executionReason}`});
  else phases.push({name:'ENTRY',time:data.openedAt,s:entry,kind:'buy',subtitle:`BUY ${price(data.entryPrice)} · ${data.entryExecutionReason||'BUY'}`});
  if(recoveryProbe&&expansionCandle){
    const context={...(latestBefore(signals,expansionCandle.closeTime)||entry),candleVolume:expansionCandle.volume,takerBuyPercent:expansionCandle.takerBuyPercent,candleClose:expansionCandle.close};
    phases.push({name:'EXPANSION_CONFIRMED',time:expansionCandle.closeTime,s:context,kind:'confirm',subtitle:`1m expansion · volume ${compactNumber(expansionCandle.volume)} · BUY pressure ${Number(expansionCandle.takerBuyPercent).toFixed(2)}%`});
  }else if(confirm)phases.push({name:'EXPANSION_CONFIRMED',time:confirm.generatedAt,s:confirm,kind:'confirm',subtitle:`${confirm.interval} ${confirm.decision} confirmation`});
  if(addWallet)phases.push({name:'NORMAL_POSITION',time:addWallet.executedAt,s:latestBefore(signals,addWallet.executedAt),kind:'position',subtitle:`Position add ${price(addWallet.price)} · ${addWallet.executionReason||'ADD'}`});
  else if(recoveryProbe&&(expansionCandle||confirm)){
    const normalTime=expansionCandle?.closeTime||confirm.generatedAt;
    const normalSignal=expansionCandle?{...(latestBefore(signals,normalTime)||entry),candleVolume:expansionCandle.volume,takerBuyPercent:expansionCandle.takerBuyPercent}:confirm;
    phases.push({name:'NORMAL_POSITION',time:normalTime,s:normalSignal,kind:'position',subtitle:'Recovery thesis graduated to normal position management'});
  }else if(confirm)phases.push({name:'NORMAL_POSITION',time:confirm.generatedAt,s:confirm,kind:'position',subtitle:'Confirmed position management'});

  const deterioration=bestLifecycleSignal(signals,s=>new Date(s.generatedAt)>new Date(data.openedAt)&&String(s.interval).toLowerCase()==='1m'&&(String(s.originalDecision||'').includes('SELL')||Number(s.score)<50));
  if(deterioration)phases.push({name:'DETERIORATING',time:deterioration.generatedAt,s:deterioration,kind:'warn',subtitle:'Short-term thesis weakened'});
  const exitBase=data.exitSignal?.id?data.exitSignal:(data.exitOneMinute||latestBefore(signals,data.closedAt));
  // FIX-028: the latest signal at an exit is context unless it is a persisted SELL/STRONG_SELL.
  // Show the production close trigger from production_exit_audit/paper_position first so
  // TAKE_PROFIT + WATCH can never be rendered as a misleading 'SELL signal'.
  const exitSignal={...(exitBase||{}),pathFiveMinute:data.exitFiveMinute||{},pathOneHour:data.exitOneHour||{},
    actualExitTrigger:data.actualExitTrigger||data.exitExecutionReason||'EXIT',
    actualExitExplanation:data.actualExitExplanation||'',
    exitSourceSignalRole:data.exitSourceSignalRole||'MARKET_CONTEXT_AT_EXIT',
    positionRecommendation:data.exitPositionAnalysis?.recommendation||data.exitAudit?.positionRecommendation||null,
    positionAnalysisConfidence:data.exitPositionAnalysis?.confidence??null,
    positionExitScore:data.exitPositionAnalysis?.exitScore??null};
  const contextText=exitBase?.id?` · context ${exitBase.decision||'—'} #${exitBase.id}`:'';
  phases.push({name:'EXIT',time:data.closedAt,s:exitSignal,kind:'sell',subtitle:`${data.actualExitTrigger||data.exitExecutionReason||'EXIT'} @ ${price(data.exitPrice)}${contextText} · ${pct(data.realizedPnlPercent)}`});
  return phases.sort((a,b)=>new Date(a.time)-new Date(b.time));
}
function pathProfitFactor(value){
  const n=Number(value);
  if(!Number.isFinite(n))return '—';
  return n>=999?'∞':n.toFixed(2);
}
function holdingEfficiencyMeaning(value){
  const n=Number(value||0);
  if(n>=85)return 'Excellent capture of the favorable move';
  if(n>=65)return 'Good capture; limited profit giveback';
  if(n>=40)return 'Moderate capture; meaningful move was left on the table';
  if(n>0)return 'Low capture; most favorable movement was given back';
  return 'No positive favorable move was realized';
}
function renderTradePath(data){
  $('inspected-trade-path-title').textContent=`${String(data.symbol||'').toUpperCase()} · full trade path`;
  $('inspected-trade-path-summary').innerHTML=`BUY <strong>${price(data.entryPrice)}</strong> · ${ksaDateTime(data.openedAt)} → SELL <strong>${price(data.exitPrice)}</strong> · ${ksaDateTime(data.closedAt)} · holding <strong>${secondsLabel(data.holdingSeconds)}</strong> · P&amp;L <strong class="${cls(data.realizedPnlPercent)}">${pct(data.realizedPnlPercent)}</strong>`;
  const phases=buildSimpleTradePhases(data);
  const nodes=phases.map((p,i)=>{
    const prev=i?phases[i-1]:null;const elapsed=prev?Math.max(0,(new Date(p.time)-new Date(prev.time))/1000):null;
    return `${i?`<div class="trade-path-arrow"><span>→</span><small>${secondsLabel(elapsed)}</small></div>`:''}${phaseNode(p.name,p.time,p.subtitle,p.s,p.kind)}`;
  }).join('');
  const entry=data.entrySignal||data.oneMinute||{};
  const rawChecks=decisionPathRows(data.decisionPath);
  const perf=data.performance||{};
  const recent=data.recentPerformance||{};
  const efficiency=Number(perf.holdingEfficiencyPercent||0);
  $('inspected-trade-path-content').innerHTML=`
    <section class="trade-path-one-look">
      <div class="trade-path-one-look-head"><div><h3>Sequential decision & position path</h3><p>One-look state machine · all timestamps KSA · persisted Production evidence</p></div><div class="trade-path-hold"><span>Holding time</span><strong>${secondsLabel(data.holdingSeconds)}</strong></div></div>
      <div class="trade-path-performance">
        <div><span>Holding time</span><strong>${secondsLabel(data.holdingSeconds)}</strong><small>BUY execution → real production exit</small></div>
        <div><span>Holding efficiency</span><strong>${Number.isFinite(efficiency)?efficiency.toFixed(1):'—'}%</strong><small>${esc(holdingEfficiencyMeaning(efficiency))} · MFE ${pct(perf.mfePercent)}</small></div>
        <div><span>Recent profit factor</span><strong>${pathProfitFactor(recent.profitFactor)}</strong><small>Latest ${esc(recent.windowTrades??0)} completed trades · WR ${pct(recent.winRate)}</small></div>
      </div>
      <div class="trade-path-phase-flow">${nodes}</div>
    </section>
    <details class="trade-path-details"><summary>Production exit audit</summary>${data.exitAudit&&Object.keys(data.exitAudit).length?`<pre class="trade-path-json">${esc(JSON.stringify(data.exitAudit,null,2))}</pre>`:'<div class="empty">No dedicated audit row exists for this legacy trade; paper_position fallback is used.</div>'}</details>
    <details class="trade-path-details"><summary>Raw entry decision checks</summary>${rawChecks}</details>`;
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
