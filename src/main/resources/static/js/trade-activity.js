(()=>{'use strict';
const $=id=>document.getElementById(id),esc=v=>String(v??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const short=v=>String(v??'—').trim().toUpperCase().replace(/[^A-Z0-9]+/g,'_').replace(/^_+|_+$/g,'')||'—';
const utcDate=v=>window.CryptoTime?.parseUtc(v)||(v?new Date(v):null);
// FIX-059: DB/JDBC timestamps are UTC. Use the shared UTC parser before explicitly
// formatting Trade Activity in KSA so a zone-less MySQL timestamp is never mistaken for browser time.
const time=v=>{const d=utcDate(v);if(!d||Number.isNaN(d.getTime()))return '—';return new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Riyadh',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(d);};
const axisTime=v=>{const d=new Date(Number(v));return new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Riyadh',hour:'2-digit',minute:'2-digit',hour12:false}).format(d);};
const epoch=v=>{const d=utcDate(v);return d&&!Number.isNaN(d.getTime())?d.getTime():null;};
const num=v=>v===null||v===undefined||v===''?null:Number(v);
let activityChart=null;
let lastGraphData=null;
let currentGraphXRanges=[];

async function loadSymbols(){try{const r=await fetch('/api/trade-activity/symbols',{cache:'no-store'});if(!r.ok)return;(await r.json()).forEach(s=>{const o=document.createElement('option');o.value=s;o.textContent=s;$('activity-symbol').appendChild(o);});}catch(e){}}

// FIX-058: COUPLE is an exclusive completed-trade mode. Keeping its controls separate
// avoids ambiguous combinations such as BUY + BLOCKED + LOST. Normal mode keeps the
// existing BUY/SELL + EXECUTED/BLOCKED contract; couple mode uses WIN/LOST only.
function syncMode(){
  const couple=$('activity-couple').checked;
  document.querySelectorAll('.activity-side').forEach(x=>{x.disabled=couple;if(couple)x.checked=false;});
  document.querySelectorAll('.activity-state').forEach(x=>{x.disabled=couple;if(couple)x.checked=false;});
  document.querySelectorAll('.activity-outcome').forEach(x=>x.disabled=!couple);
  if(couple&&!document.querySelector('.activity-outcome:checked'))document.querySelector('.activity-outcome[value="LOST"]').checked=true;
  if(!couple){
    if(!document.querySelector('.activity-side:checked'))document.querySelector('.activity-side[value="BUY"]').checked=true;
    if(!document.querySelector('.activity-state:checked'))document.querySelector('.activity-state[value="EXECUTED"]').checked=true;
  }
}
function keepOneSelected(box,selector){if(document.querySelector(selector+':checked'))return;const peer=[...document.querySelectorAll(selector)].find(x=>x!==box&&!x.disabled);if(peer)peer.checked=true;else box.checked=true;}

async function search(){
  const couple=$('activity-couple').checked;
  const err=$('activity-error');err.classList.add('hidden');
  const p=new URLSearchParams({symbol:$('activity-symbol').value,hours:$('activity-hours').value});
  if(couple){
    const outcomes=[...document.querySelectorAll('.activity-outcome:checked')].map(x=>x.value);
    if(!outcomes.length){err.textContent='Select WIN, LOST, or both for Couple mode.';err.classList.remove('hidden');return;}
    p.append('filter','COUPLE');outcomes.forEach(v=>p.append('filter',v));
  }else{
    const sides=[...document.querySelectorAll('.activity-side:checked')].map(x=>x.value);
    const states=[...document.querySelectorAll('.activity-state:checked')].map(x=>x.value);
    if(!sides.length){err.textContent='Select BUY, SELL, or both.';err.classList.remove('hidden');return;}
    if(!states.length){err.textContent='Select EXECUTED, BLOCKED, or both.';err.classList.remove('hidden');return;}
    sides.forEach(v=>p.append('filter',v));states.forEach(v=>p.append('filter',v));
  }
  $('activity-rows').innerHTML='<tr><td colspan="8" class="empty-cell">Loading…</td></tr>';$('activity-count').textContent='Loading…';
  try{
    const r=await fetch('/api/trade-activity?'+p,{cache:'no-store'});if(!r.ok)throw new Error('HTTP '+r.status);
    const rows=await r.json();$('activity-count').textContent=rows.length+' rows';
    // FIX-060: SELL rows get a direct forensic drill-down. The button carries only persisted
    // identifiers/timestamps; it never invokes trading logic and never guesses a position from price.
    $('activity-rows').innerHTML=rows.length?rows.map((x,i)=>{const isSell=short(x.action)==='SELL';const graphButton=isSell?`<button type="button" class="activity-view-graph" data-row-index="${i}">View on graph</button>`:'—';return `<tr${x.pair_id?' data-pair-id="'+esc(x.pair_id)+'"':''}><td>${esc(time(x.event_time||x.eventTime))}</td><td><strong>${esc(x.symbol)}</strong></td><td>${esc(x.timeframe||'—')}</td><td><span class="activity-pill ${String(x.action||'').toLowerCase()}">${esc(x.action)}</span></td><td>${esc(x.status)}</td><td>${esc(short(x.source))}</td><td><code>${esc(short(x.reason))}</code></td><td>${graphButton}</td></tr>`;}).join(''):'<tr><td colspan="8" class="empty-cell">No activity matches these filters.</td></tr>';
    document.querySelectorAll('.activity-view-graph').forEach(btn=>btn.addEventListener('click',()=>viewSellOnGraph(rows[Number(btn.dataset.rowIndex)])));
    await loadGraph();
  }catch(e){
    $('activity-rows').innerHTML='<tr><td colspan="8" class="empty-cell">Could not load activity.</td></tr>';err.textContent='Trade Activity could not load: '+e.message;err.classList.remove('hidden');$('activity-count').textContent='Error';
  }
}

function clearGraph(message='Choose a specific symbol to display its activity graph.'){
  if(activityChart){activityChart.destroy();activityChart=null;}
  lastGraphData=null;currentGraphXRanges=[];
  $('activity-chart').innerHTML=`<div class="empty-cell">${esc(message)}</div>`;
  $('activity-graph-count').textContent='Select a symbol';
  $('activity-chart-detail').innerHTML='<strong>Marker details</strong><span>Click an analysis point or BUY/SELL trade marker.</span>';
  $('activity-couple-summary').innerHTML='';
}

function decisionGroup(v){const d=short(v);if(d.includes('BUY'))return 'BUY';if(d.includes('SELL'))return 'SELL';if(d==='WATCH')return 'WATCH';return 'NEUTRAL';}
function detailField(label,value){return `<div><small>${esc(label)}</small><span>${esc(value??'—')}</span></div>`;}
function showAnalysisDetail(a){
  $('activity-chart-detail').innerHTML=`<strong>Technical analysis #${esc(a.id)} · ${esc(a.interval_code)} · ${esc(short(a.decision))}</strong><div class="activity-detail-grid">
    ${detailField('Time (KSA)',time(a.generated_at))}${detailField('Price',a.latest_price)}${detailField('Score / confidence',`${a.total_score??'—'} / ${a.confidence_score??'—'}`)}${detailField('Original → final',`${short(a.original_decision)} → ${short(a.decision)}`)}
    ${detailField('Trend / volume / momentum',`${a.trend_score??'—'} / ${a.volume_score??'—'} / ${a.momentum_score??'—'}`)}${detailField('Regime',`${short(a.market_regime)} (${a.market_regime_confidence??'—'})`)}${detailField('Strategy',short(a.selected_strategy))}${detailField('Entry allowed',a.final_entry_allowed?'YES':'NO')}
    ${detailField('Confluence',`${short(a.confluence_status)} · ${a.confluence_higher_interval||'—'}=${short(a.confluence_higher_decision)}`)}${detailField('ATR entry',`${short(a.atr_entry_type)} · overextended=${a.atr_overextended?'YES':'NO'}`)}${detailField('BTC',short(a.btc_context_status))}${detailField('Liquidity',short(a.liquidity_status))}${detailField('Derivatives',short(a.derivatives_status))}${detailField('SL / TP',`${a.stop_loss??'—'} / ${a.take_profit??'—'}`)}
  </div><div class="activity-detail-explanation"><strong>Final decision:</strong> ${esc(a.final_decision_explanation||'—')}</div><div class="activity-detail-explanation"><strong>Confluence:</strong> ${esc(a.confluence_explanation||'—')}</div><div class="activity-detail-explanation"><strong>ATR:</strong> ${esc(a.atr_explanation||'—')}</div><div class="activity-detail-explanation"><strong>Liquidity:</strong> ${esc(a.liquidity_explanation||'—')}</div>`;
}
function showCoupleDetail(c,leg,relatedAnalyses=[]){
  const pnl=num(c.realized_pnl_usdt);
  const related=relatedAnalyses.length?`<div class="activity-detail-explanation"><strong>Related analyses on graph (${relatedAnalyses.length}):</strong> ${relatedAnalyses.map(a=>`${time(a.generated_at)} ${a.interval_code} ${short(a.decision)} ${a.total_score??'—'}/${a.confidence_score??'—'}`).join(' · ')}</div>`:'';
  $('activity-chart-detail').innerHTML=`<strong>Couple #${esc(c.pair_id)} · ${esc(c.outcome)} · selected ${esc(leg)}</strong><div class="activity-detail-grid">
    ${detailField('BUY time (KSA)',time(c.buy_time))}${detailField('BUY price',c.buy_price)}${detailField('Decision price',c.buy_decision_price)}${detailField('BUY reason',short(c.buy_reason))}
    ${detailField('SELL time (KSA)',time(c.sell_time))}${detailField('SELL price',c.sell_price)}${detailField('SELL reason',short(c.sell_reason))}${detailField('Realized P/L',pnl===null?'—':pnl.toFixed(6)+' USDT')}
    ${detailField('Entry TF',c.entry_timeframe||'—')}${detailField('Quantity',c.buy_quantity)}${detailField('Gross BUY',c.buy_gross)}${detailField('BUY signal',c.buy_signal_id||'—')}
  </div>${related}`;
}
function showCandleDetail(c){
  $('activity-chart-detail').innerHTML=`<strong>Real 1m candle · ${esc(time(c.open_time))}</strong><div class="activity-detail-grid">${detailField('Open',c.open_price)}${detailField('High',c.high_price)}${detailField('Low',c.low_price)}${detailField('Close',c.close_price)}${detailField('Volume',c.volume)}</div>`;
}

function renderCoupleSummary(couples){
  $('activity-couple-summary').innerHTML=couples.length?couples.map(c=>{const pnl=num(c.realized_pnl_usdt);const cls=c.outcome==='WIN'?'activity-outcome-win':'activity-outcome-lost';return `<div class="activity-couple-card" data-pair-id="${esc(c.pair_id)}"><strong><span>Couple #${esc(c.pair_id)}</span><span class="${cls}">${esc(c.outcome)}</span></strong><small>BUY ${esc(time(c.buy_time))} @ ${esc(c.buy_price)} · ${esc(short(c.buy_reason))}</small><small>SELL ${esc(time(c.sell_time))} @ ${esc(c.sell_price)} · ${esc(short(c.sell_reason))}</small><small>Realized P/L: ${esc(pnl===null?'—':pnl.toFixed(6)+' USDT')}</small></div>`;}).join(''):'<div class="empty-cell">No completed BUY→SELL couples in this time range.</div>';
}


// FIX-060: Focus a SELL row on the existing Trade Activity forensic graph. For a completed
// position, the persisted SELL trade id/pair id is matched to the graph lifecycle authority,
// then the chart zooms from setup context through exit. All persisted analyses remain visible.
async function viewSellOnGraph(row){
  if(!row||short(row.action)!=='SELL')return;
  const symbol=String(row.symbol||'').toUpperCase();
  if(!symbol)return;
  if($('activity-symbol').value!==symbol){$('activity-symbol').value=symbol;}
  await loadGraph();
  if(!activityChart||!lastGraphData)return;
  const tradeId=String(row.trade_id??row.tradeId??'');
  const pairId=String(row.pair_id??row.pairId??'');
  const eventMs=epoch(row.event_time||row.eventTime);
  const couples=lastGraphData.couples||[];
  let couple=couples.find(c=>tradeId&&String(c.sell_trade_id)===tradeId)
      ||couples.find(c=>pairId&&String(c.pair_id)===pairId)
      ||couples.find(c=>eventMs&&Math.abs((epoch(c.sell_time)||0)-eventMs)<=5000);
  if(couple){
    const buyMs=epoch(couple.buy_time),sellMs=epoch(couple.sell_time);
    const from=(buyMs||eventMs)-10*60000,to=(sellMs||eventMs)+5*60000;
    activityChart.zoomX(from,to);
    // Highlight the exact persisted SELL fill without hiding the candle/analysis path.
    await activityChart.updateOptions({annotations:{xaxis:currentGraphXRanges,points:[{x:sellMs,y:num(couple.sell_price),marker:{size:9,strokeWidth:3},label:{text:'SELL · '+short(couple.sell_reason),offsetY:-12}}]}},false,false);
    const related=(lastGraphData.analyses||[]).filter(a=>{const t=epoch(a.generated_at);return t&&t>=from&&t<=to;});
    showCoupleDetail(couple,'SELL',related);
  }else if(eventMs){
    activityChart.zoomX(eventMs-15*60000,eventMs+10*60000);
    const related=(lastGraphData.analyses||[]).filter(a=>{const t=epoch(a.generated_at);return t&&t>=eventMs-15*60000&&t<=eventMs+10*60000;});
    $('activity-chart-detail').innerHTML=`<strong>SELL · ${esc(symbol)} · ${esc(time(row.event_time||row.eventTime))}</strong><div class="activity-detail-grid">${detailField('Status',short(row.status))}${detailField('Reason',short(row.reason))}${detailField('Source',short(row.source))}${detailField('Related analyses',related.length)}</div><div class="activity-detail-explanation"><strong>Related analyses on graph:</strong> ${esc(related.map(a=>`${time(a.generated_at)} ${a.interval_code} ${short(a.decision)} ${a.total_score??'—'}/${a.confidence_score??'—'}`).join(' · ')||'None persisted in this focus window.')}</div>`;
  }
  $('activity-chart').scrollIntoView({behavior:'smooth',block:'center'});
}

async function loadGraph(){
  const symbol=$('activity-symbol').value;
  if(!symbol||symbol==='ALL'){clearGraph();return;}
  const hours=$('activity-hours').value;
  $('activity-graph-count').textContent='Loading…';
  $('activity-chart').innerHTML='<div class="empty-cell">Loading real activity path…</div>';
  try{
    const r=await fetch(`/api/trade-activity/graph?symbol=${encodeURIComponent(symbol)}&hours=${encodeURIComponent(hours)}`,{cache:'no-store'});
    if(!r.ok)throw new Error('HTTP '+r.status);
    const data=await r.json();
    lastGraphData=data;
    const candles=(data.candles||[]).map(c=>({x:epoch(c.open_time),y:[num(c.open_price),num(c.high_price),num(c.low_price),num(c.close_price)],meta:{kind:'candle',record:c}})).filter(p=>p.x&&p.y.every(Number.isFinite));
    const analyses=data.analyses||[],couples=data.couples||[];
    const grouped={BUY:[],SELL:[],WATCH:[],NEUTRAL:[]};
    analyses.forEach(a=>{const x=epoch(a.generated_at),y=num(a.latest_price);if(x&&Number.isFinite(y))grouped[decisionGroup(a.decision)].push({x,y,meta:{kind:'analysis',record:a}});});
    const coupleBuy=[],coupleSell=[],xRanges=[];
    couples.forEach(c=>{const bx=epoch(c.buy_time),sx=epoch(c.sell_time),bp=num(c.buy_price),sp=num(c.sell_price);if(bx&&Number.isFinite(bp))coupleBuy.push({x:bx,y:bp,meta:{kind:'couple',leg:'BUY',record:c}});if(sx&&Number.isFinite(sp))coupleSell.push({x:sx,y:sp,meta:{kind:'couple',leg:'SELL',record:c}});if(bx&&sx)xRanges.push({x:bx,x2:sx,fillColor:c.outcome==='WIN'?'#20a36a':'#d75a5a',opacity:.055,borderColor:'transparent'});});
    currentGraphXRanges=xRanges;
    if(activityChart){activityChart.destroy();activityChart=null;}
    $('activity-chart').innerHTML='';
    const series=[
      {name:'Real 1m candles',type:'candlestick',data:candles},
      {name:'Analysis BUY',type:'scatter',data:grouped.BUY},
      {name:'Analysis SELL',type:'scatter',data:grouped.SELL},
      {name:'Analysis WATCH',type:'scatter',data:grouped.WATCH},
      {name:'Analysis NEUTRAL',type:'scatter',data:grouped.NEUTRAL},
      {name:'Executed BUY',type:'scatter',data:coupleBuy},
      {name:'Executed SELL',type:'scatter',data:coupleSell}
    ];
    activityChart=new ApexCharts($('activity-chart'),{
      chart:{type:'candlestick',height:470,animations:{enabled:false},zoom:{enabled:true},toolbar:{show:true,tools:{download:false,selection:true,zoom:true,zoomin:true,zoomout:true,pan:true,reset:true}},events:{dataPointSelection:(event,ctx,opts)=>{const point=ctx?.w?.config?.series?.[opts.seriesIndex]?.data?.[opts.dataPointIndex];const meta=point?.meta;if(!meta)return;if(meta.kind==='analysis')showAnalysisDetail(meta.record);else if(meta.kind==='couple'){const bx=epoch(meta.record.buy_time),sx=epoch(meta.record.sell_time);const related=(lastGraphData?.analyses||[]).filter(a=>{const t=epoch(a.generated_at);return t&&bx&&sx&&t>=bx-5*60000&&t<=sx+60000;});showCoupleDetail(meta.record,meta.leg,related);}else if(meta.kind==='candle')showCandleDetail(meta.record);}}},
      series,
      colors:['#7f8c8d','#20a36a','#d75a5a','#d1a43b','#8090a0','#32d583','#ff6b72'],
      stroke:{width:[1,0,0,0,0,0,0]},markers:{size:[0,4,4,3,3,8,8],strokeWidth:1},
      plotOptions:{candlestick:{colors:{upward:'#39d98a',downward:'#ff6b72'},wick:{useFillColor:true}}},
      xaxis:{type:'datetime',labels:{formatter:(value,timestamp)=>axisTime(timestamp??value)},tooltip:{enabled:false}},
      yaxis:{opposite:true,tooltip:{enabled:true},labels:{formatter:v=>Number(v).toLocaleString(undefined,{maximumSignificantDigits:8})}},
      tooltip:{enabled:false},legend:{show:true,position:'top'},grid:{borderColor:'rgba(128,128,128,.12)'},
      annotations:{xaxis:xRanges},noData:{text:'No real 1m candle data in this window.'}
    });
    await activityChart.render();
    $('activity-graph-count').textContent=`${candles.length} candles · ${analyses.length} analyses · ${couples.length} couples`;
    renderCoupleSummary(couples);
    $('activity-chart-detail').innerHTML='<strong>Marker details</strong><span>Click any technical-analysis marker, executed BUY/SELL marker, or candle. Times are displayed in KSA; database timestamps remain UTC.</span>';
  }catch(e){clearGraph('Could not load Trade Activity graph: '+e.message);$('activity-graph-count').textContent='Error';}
}

$('activity-search').addEventListener('click',search);
$('activity-couple').addEventListener('change',()=>{syncMode();search();});
document.querySelectorAll('.activity-side').forEach(box=>box.addEventListener('change',()=>keepOneSelected(box,'.activity-side')));
document.querySelectorAll('.activity-state').forEach(box=>box.addEventListener('change',()=>keepOneSelected(box,'.activity-state')));
document.querySelectorAll('.activity-outcome').forEach(box=>box.addEventListener('change',()=>keepOneSelected(box,'.activity-outcome')));
$('activity-symbol').addEventListener('change',search);
$('activity-hours').addEventListener('change',search);
syncMode();loadSymbols();
})();
