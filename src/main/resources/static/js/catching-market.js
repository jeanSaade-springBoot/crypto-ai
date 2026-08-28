const messageBox=document.getElementById('message');
let savedSymbols=new Set(),catchChart=null,catchPage=0,catchPageData=null,catchFocus=null,catchCrosshairCleanup=null;
const esc=v=>String(v??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
async function api(url,opt={}){const r=await fetch(url,{cache:'no-store',...opt});if(!r.ok)throw new Error((await r.text())||`Request failed ${r.status}`);return r.status===204?null:r.json();}
function msg(t,e=false){messageBox.textContent=t;messageBox.classList.remove('hidden');messageBox.classList.toggle('error-banner',e);setTimeout(()=>messageBox.classList.add('hidden'),4000)}
function parseUtc(v){return window.CryptoTime?.parseUtc(v)||new Date(v)}
function ksa(v){const d=parseUtc(v);return !d||Number.isNaN(d.getTime())?'—':new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Riyadh',day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit',hour12:false}).format(d).replace(',','');}
function chartTime(v){const d=new Date(Number(v));return Number.isNaN(d.getTime())?'—':new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Riyadh',day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(d).replace(',','');}
function price(v){const n=Number(v);if(!Number.isFinite(n))return'—';const a=Math.abs(n),digits=a>=1000?2:a>=1?4:a>=.01?6:10;return n.toLocaleString(undefined,{maximumFractionDigits:digits});}
function num(v,d=4){const n=Number(v);return Number.isFinite(n)?n.toLocaleString(undefined,{maximumFractionDigits:d}):'—'}
function selected(){return [...document.querySelectorAll('#price-move-symbols input:checked')].map(x=>x.value)}
function intervalMs(v){return v==='5m'?300000:v==='1h'?3600000:v==='4h'?14400000:60000}
function chartInterval(windowName){return windowName==='4h'?'4h':(windowName==='1h'||windowName==='2h')?'1h':'5m'}
async function settings(){const s=await api('/api/administration/debug/price-moves/settings');document.getElementById('price-move-enabled').checked=!!s.enabled;savedSymbols=new Set(String(s.selectedSymbols||'').split(',').filter(Boolean));}
async function symbols(){const box=document.getElementById('price-move-symbols');const coins=await api('/api/administration/coins');box.innerHTML=coins.map(c=>`<label class="debug-symbol-check"><input type="checkbox" value="${esc(c.symbol)}" ${savedSymbols.has(c.symbol)?'checked':''}><span>${esc(c.symbol)}</span></label>`).join('');}
async function save(){const s=await api('/api/administration/debug/price-moves/settings',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({enabled:document.getElementById('price-move-enabled').checked,symbols:selected()})});savedSymbols=new Set(String(s.selectedSymbols||'').split(',').filter(Boolean));msg('Catching Market settings saved. Price catcher only; trading logic unchanged.');}

/*
 * FIX-113: Catching Market is now a server-paged aggregate view. Rows are grouped by persisted
 * symbol + direction + catcher window and show count/average progress plus first/last market time.
 * This is reporting only: the underlying price catcher, Production and Replay are not changed.
 */
function renderMoves(){
 const body=document.getElementById('price-move-body'),rows=catchPageData?.rows||[];
 body.innerHTML=rows.map(x=>{const avg=Number(x.averageProgress),up=String(x.direction).toUpperCase()==='UP';return `<tr>
  <td><strong>${esc(x.symbol)}</strong></td><td><span class="catch-direction ${up?'up':'down'}">${up?'↑':'↓'} ${esc(x.direction)}</span></td>
  <td>${esc(x.detectionWindow||'—')}</td><td><strong>${Number(x.directionCount||0).toLocaleString()}</strong></td>
  <td><strong class="${avg>=0?'positive':'negative'}">${avg>=0?'+':''}${Number.isFinite(avg)?avg.toFixed(3):'—'}%</strong></td>
  <td>${ksa(x.startTime)}</td><td>${ksa(x.endTime)}</td>
  <td><button type="button" class="secondary-button catch-view-chart-button" data-view="${esc(x.startEventId)}" data-interval="${esc(chartInterval(x.detectionWindow))}">View chart</button></td>
 </tr>`}).join('')||'<tr><td colspan="8">No caught movements match this history window.</td></tr>';
 body.querySelectorAll('.catch-view-chart-button').forEach(b=>b.addEventListener('click',()=>viewGraph(b.dataset.view,b.dataset.interval)));
 const total=Number(catchPageData?.totalElements||0),pages=Number(catchPageData?.totalPages||0);
 document.getElementById('catch-page-info').textContent=pages?`Page ${catchPage+1} of ${pages} · ${total} groups`:`Page 0 of 0 · ${total} groups`;
 document.getElementById('catch-prev').disabled=catchPage<=0;
 document.getElementById('catch-next').disabled=!pages||catchPage>=pages-1;
 document.getElementById('price-move-new-count').textContent=total;
}
async function moves(){
 const body=document.getElementById('price-move-body');
 try{
  const syms=selected().join(','),level=document.getElementById('price-move-level-filter').value,hours=document.getElementById('price-move-history-filter').value;
  const q=new URLSearchParams({symbols:syms,level,hours,page:String(catchPage)});
  catchPageData=await api(`/api/administration/debug/price-moves/summary?${q}`);
  if(Number(catchPageData.totalPages||0)>0&&catchPage>=Number(catchPageData.totalPages)){catchPage=Math.max(0,Number(catchPageData.totalPages)-1);return moves();}
  catchPage=Number(catchPageData.page||0);renderMoves();
 }catch(e){body.innerHTML=`<tr><td colspan="8">${esc(e.message)}</td></tr>`;msg(e.message,true)}
}
function resetCatchPage(){catchPage=0;return moves();}

function bindCatchCrosshair(){if(typeof catchCrosshairCleanup==='function')catchCrosshairCleanup();catchCrosshairCleanup=window.CryptoChartCrosshair?.bind(document.getElementById('catch-chart'),catchChart,{valueFormatter:price,timeZone:'Asia/Riyadh'})||null;}

/* FIX-113: fast chart path loads only a bounded candle window and highlights START TIME only. */
async function loadCatchChart(){
 const data=await api(`/api/administration/debug/price-moves/${encodeURIComponent(catchFocus.id)}/start-chart?interval=${encodeURIComponent(catchFocus.interval)}`);
 const e=data.event,interval=String(data.interval||catchFocus.interval||'5m'),step=intervalMs(interval);
 const candles=(data.candles||[]).map(c=>({x:parseUtc(c.openTime)?.getTime(),y:[+c.openPrice,+c.highPrice,+c.lowPrice,+c.closePrice],meta:c})).filter(c=>Number.isFinite(c.x)&&c.y.every(Number.isFinite));
 const empty=document.getElementById('catch-chart-empty'),host=document.getElementById('catch-chart');
 if(!candles.length){if(catchChart){catchChart.destroy();catchChart=null;}host.innerHTML='';empty.textContent='No persisted candles are available around this catch start time.';empty.classList.remove('hidden');return;}
 empty.classList.add('hidden');
 const startX=parseUtc(data.startTime||e.startTime)?.getTime(),startY=Number(data.startPrice||e.startPrice),firstX=candles[0].x,lastX=candles[candles.length-1].x;
 catchFocus={...catchFocus,startX,firstX,lastX,interval};
 let min=Math.max(firstX,startX-step*30),max=Math.min(lastX,startX+step*50);if(max<=min){min=firstX;max=lastX;}
 const metaByTime=new Map(candles.map(c=>[c.x,c.meta]));
 document.getElementById('catch-chart-title').textContent=`${String(e.symbol||'').toUpperCase()} · Catch start`;
 document.getElementById('catch-chart-context').textContent=`${interval} · Start ${ksa(data.startTime||e.startTime)} KSA · start time only`;
 const vals=candles.flatMap(c=>c.y),lo=Math.min(...vals),hi=Math.max(...vals),pad=(hi-lo||Math.max(Math.abs(hi)*.002,1e-12))*.06;
 const options={chart:{type:'line',height:540,background:'transparent',foreColor:'#8da2b1',animations:{enabled:false},toolbar:{show:true,autoSelected:'zoom',tools:{download:false,selection:true,zoom:true,zoomin:true,zoomout:true,pan:true,reset:true}},zoom:{enabled:true,type:'x',autoScaleYaxis:true},events:{updated:()=>setTimeout(bindCatchCrosshair,0),zoomed:()=>setTimeout(bindCatchCrosshair,0)}},
  title:{text:`${String(e.symbol||'').toUpperCase()} · ${interval} · Start time`,align:'left',style:{fontSize:'13px',fontWeight:600,color:'#dbe8ef'}},
  series:[{name:'Candles',type:'candlestick',data:candles.map(({x,y})=>({x,y}))}],dataLabels:{enabled:false},stroke:{width:[1]},markers:{size:[0]},
  xaxis:{type:'datetime',min,max,tickAmount:10,labels:{datetimeUTC:false,formatter:(v,t)=>chartTime(t??v)},crosshairs:{show:true,position:'front',stroke:{width:1,dashArray:3}}},
  yaxis:{opposite:true,min:lo-pad,max:hi+pad,labels:{formatter:price},crosshairs:{show:true,position:'front',stroke:{width:1,dashArray:3}}},theme:{mode:'dark'},grid:{borderColor:'#203342'},plotOptions:{candlestick:{colors:{upward:'#39d98a',downward:'#ff6b72'},wick:{useFillColor:true}}},
  annotations:{points:Number.isFinite(startX)&&Number.isFinite(startY)?[{x:startX,y:startY,marker:{size:10,fillColor:'#f5c451',strokeColor:'#fff',strokeWidth:3},label:{text:'START',borderColor:'#f5c451',style:{background:'#8a6814',color:'#fff',fontSize:'11px',fontWeight:700}}}]:[]},
  tooltip:{shared:false,intersect:false,followCursor:true,custom:({seriesIndex,dataPointIndex,w})=>{const p=w?.config?.series?.[seriesIndex]?.data?.[dataPointIndex],c=metaByTime.get(Number(p?.x));return c?`<div class="inspector-candle-tooltip"><div class="tooltip-time">${esc(chartTime(p.x))} KSA</div><div class="tooltip-grid"><span>Open</span><strong>${esc(price(c.openPrice))}</strong><span>High</span><strong>${esc(price(c.highPrice))}</strong><span>Low</span><strong>${esc(price(c.lowPrice))}</strong><span>Close</span><strong>${esc(price(c.closePrice))}</strong><span>Volume</span><strong>${esc(num(c.volume,8))}</strong></div></div>`:''}}};
 if(typeof catchCrosshairCleanup==='function')catchCrosshairCleanup();if(catchChart)catchChart.destroy();host.innerHTML='';catchChart=new ApexCharts(host,options);await catchChart.render();bindCatchCrosshair();
}
async function viewGraph(id,interval){if(!id)return msg('Caught move chart could not open: missing start event id.',true);catchFocus={id:String(id),interval:interval||'5m'};const modal=document.getElementById('catch-chart-panel'),host=document.getElementById('catch-chart'),empty=document.getElementById('catch-chart-empty');modal.classList.remove('hidden');modal.setAttribute('aria-hidden','false');document.body.classList.add('inspector-modal-open');host.innerHTML='<div class="empty catch-chart-loading">Loading start-time chart…</div>';empty.classList.add('hidden');try{await loadCatchChart()}catch(e){host.innerHTML='';empty.textContent=`Caught move chart could not load: ${e.message}`;empty.classList.remove('hidden');}}
function closeCatchChart(){document.getElementById('catch-chart-panel').classList.add('hidden');document.body.classList.remove('inspector-modal-open');if(typeof catchCrosshairCleanup==='function')catchCrosshairCleanup();if(catchChart)catchChart.destroy();catchChart=null;catchFocus=null;}
function navigateCatch(mode){if(!catchChart||!catchFocus)return;const step=intervalMs(catchFocus.interval),x=catchFocus.startX;let min,max;if(mode==='earliest'){min=catchFocus.firstX;max=Math.min(catchFocus.lastX,min+step*80)}else if(mode==='latest'){max=catchFocus.lastX;min=Math.max(catchFocus.firstX,max-step*80)}else{min=Math.max(catchFocus.firstX,x-step*30);max=Math.min(catchFocus.lastX,x+step*50)}catchChart.updateOptions({xaxis:{min,max}},false,false);}

document.getElementById('price-move-settings-form').addEventListener('submit',async e=>{e.preventDefault();try{await save();await resetCatchPage()}catch(x){msg(x.message,true)}});
document.getElementById('price-move-refresh').addEventListener('click',resetCatchPage);
document.getElementById('price-move-select-all').addEventListener('click',()=>document.querySelectorAll('#price-move-symbols input').forEach(x=>x.checked=true));
document.getElementById('price-move-clear-symbols').addEventListener('click',()=>document.querySelectorAll('#price-move-symbols input').forEach(x=>x.checked=false));
document.getElementById('price-move-level-filter').addEventListener('change',resetCatchPage);
document.getElementById('price-move-history-filter').addEventListener('change',resetCatchPage);
document.getElementById('catch-prev').addEventListener('click',()=>{if(catchPage>0){catchPage--;moves()}});
document.getElementById('catch-next').addEventListener('click',()=>{catchPage++;moves()});
document.getElementById('catch-chart-close').addEventListener('click',closeCatchChart);document.querySelector('[data-catch-popup-close="1"]')?.addEventListener('click',closeCatchChart);
document.getElementById('catch-chart-fit').addEventListener('click',()=>navigateCatch('fit'));document.getElementById('catch-chart-start').addEventListener('click',()=>navigateCatch('start'));document.getElementById('catch-chart-earliest').addEventListener('click',()=>navigateCatch('earliest'));document.getElementById('catch-chart-latest').addEventListener('click',()=>navigateCatch('latest'));
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!document.getElementById('catch-chart-panel').classList.contains('hidden'))closeCatchChart()});
(async()=>{try{await settings();await symbols();await moves()}catch(e){msg(e.message,true)}})();
