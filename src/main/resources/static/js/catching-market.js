const messageBox=document.getElementById('message');
let savedSymbols=new Set(),catchChart=null,latestMoveRows=[],catchFocusId=null,catchFocusEvent=null,catchFocusSignal=null,catchCrosshairCleanup=null;
const esc=v=>String(v??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
async function api(url,opt={}){const r=await fetch(url,opt);if(!r.ok)throw new Error((await r.text())||`Request failed ${r.status}`);return r.status===204?null:r.json();}
function msg(t,e=false){messageBox.textContent=t;messageBox.classList.remove('hidden');messageBox.classList.toggle('error-banner',e);setTimeout(()=>messageBox.classList.add('hidden'),4000)}
function parseUtc(v){return window.CryptoTime?.parseUtc(v)||new Date(v)}
function utc(v){const d=parseUtc(v);return !d||Number.isNaN(d.getTime())?'—':d.toISOString().replace('T',' ').slice(0,16);}
function chartTime(v,includeDate=false){const d=new Date(Number(v));if(Number.isNaN(d.getTime()))return'—';return new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Riyadh',day:includeDate?'2-digit':undefined,month:includeDate?'2-digit':undefined,year:includeDate?'numeric':undefined,hour:'2-digit',minute:'2-digit',hour12:false}).format(d).replace(',','');}
function price(v){const n=Number(v);if(!Number.isFinite(n))return'—';const a=Math.abs(n),digits=a>=1000?2:a>=1?4:a>=.01?6:10;return n.toLocaleString(undefined,{maximumFractionDigits:digits});}
function num(v,d=4){const n=Number(v);return Number.isFinite(n)?n.toLocaleString(undefined,{maximumFractionDigits:d}):'—'}
function selected(){return [...document.querySelectorAll('#price-move-symbols input:checked')].map(x=>x.value)}
function intervalMs(v){return v==='5m'?300000:v==='1h'?3600000:v==='4h'?14400000:60000}
async function settings(){const s=await api('/api/administration/debug/price-moves/settings');document.getElementById('price-move-enabled').checked=!!s.enabled;savedSymbols=new Set(String(s.selectedSymbols||'').split(',').filter(Boolean));}
async function symbols(){const box=document.getElementById('price-move-symbols');const coins=await api('/api/administration/coins');box.innerHTML=coins.map(c=>`<label class="debug-symbol-check"><input type="checkbox" value="${esc(c.symbol)}" ${savedSymbols.has(c.symbol)?'checked':''}><span>${esc(c.symbol)}</span></label>`).join('');}
async function save(){const s=await api('/api/administration/debug/price-moves/settings',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({enabled:document.getElementById('price-move-enabled').checked,symbols:selected()})});savedSymbols=new Set(String(s.selectedSymbols||'').split(',').filter(Boolean));msg('Catching Market settings saved. Price catcher only; trading logic unchanged.');}

// FIX-093: the grid is intentionally price-catch focused. Trade/signal/blame diagnostics were removed
// from the table and remain available only as read-only markers inside Graph. HIGH is the default view.
function renderMoves(){
    const body=document.getElementById('price-move-body');
    const level=document.getElementById('price-move-level-filter')?.value||'HIGH';
    const rows=level==='ALL'?latestMoveRows:latestMoveRows.filter(x=>String(x.importanceLevel||'').toUpperCase()===level);
    body.innerHTML=rows.map(x=>`<tr><td><strong>${esc(x.symbol)}</strong></td><td>${x.direction==='UP'?'↑':'↓'} ${esc(x.direction)}</td><td>${esc(x.detectionWindow||'—')}</td><td><strong>${Number(x.changePercent)>=0?'+':''}${Number(x.changePercent).toFixed(3)}%</strong></td><td><span class="status-pill ${String(x.importanceLevel).toLowerCase()}">${esc(x.importanceLevel)}</span></td><td>${utc(x.startTime)}</td><td>${utc(x.endTime)}</td><td>${esc(x.outcomeStatus||'—')}</td><td><button type="button" class="secondary-button catch-view-chart-button" data-view="${x.id}">View chart</button></td></tr>`).join('')||`<tr><td colspan="9">No ${level==='ALL'?'':esc(level)+' '}completed catches for the selected symbols.</td></tr>`;

    // FIX-094B: bind each freshly rendered button directly. Catching Market replaces tbody HTML
    // whenever filters/refresh run; a per-button listener makes View chart independent from legacy
    // blame/review markup and from bubbling/delegation differences in cached browser pages.
    body.querySelectorAll('.catch-view-chart-button[data-view]').forEach(button=>{
        button.addEventListener('click',event=>{
            event.preventDefault();
            event.stopPropagation();
            viewGraph(button.dataset.view);
        });
    });
}
async function moves(){
    const body=document.getElementById('price-move-body');
    try{
        const syms=selected();
        const groups=syms.length?await Promise.all(syms.map(s=>api(`/api/administration/debug/price-moves?symbol=${encodeURIComponent(s)}`))):[];
        latestMoveRows=groups.flat().sort((a,b)=>new Date(b.endTime)-new Date(a.endTime));
        document.getElementById('price-move-new-count').textContent=latestMoveRows.length;
        document.getElementById('price-move-normal-count').textContent=latestMoveRows.filter(x=>x.importanceLevel==='NORMAL').length;
        document.getElementById('price-move-high-count').textContent=latestMoveRows.filter(x=>x.importanceLevel==='HIGH').length;
        document.getElementById('price-move-extreme-count').textContent=latestMoveRows.filter(x=>x.importanceLevel==='EXTREME').length;
        renderMoves();
    }catch(e){body.innerHTML=`<tr><td colspan="9">${esc(e.message)}</td></tr>`;msg(e.message,true)}
}

function catchAnnotations(data){
    // FIX-095: the blame popup must show exactly one annotation: the persisted best/blamed signal.
    // No other signals and no executed wallet trades are rendered in this diagnostic view.
    const s=data?.blamedSignal;
    if(!s)return {points:[]};
    const buy=d=>['BUY','STRONG_BUY'].includes(String(d||'').toUpperCase());
    const sell=d=>['SELL','STRONG_SELL'].includes(String(d||'').toUpperCase());
    const finalDecision=String(s.decision||'').toUpperCase();
    const originalDecision=String(s.originalDecision||'').toUpperCase();
    const effective=buy(originalDecision)||sell(originalDecision)?originalDecision:finalDecision;
    const isBuy=buy(effective);
    const isSell=sell(effective);
    const x=parseUtc(s.candleOpenTime||s.generatedAt)?.getTime();
    const y=Number(s.latestPrice);
    if((!isBuy&&!isSell)||!Number.isFinite(x)||!Number.isFinite(y))return {points:[]};
    const blocked=s.finalEntryAllowed===false;
    return {points:[{
        x,y,
        marker:{size:11,fillColor:isBuy?'#39d98a':'#ff6b72',strokeColor:'#ffffff',strokeWidth:3},
        label:{
            text:`BLAMED ${isBuy?'BUY':'SELL'}${blocked?' · BLOCKED':''}`,
            borderColor:isBuy?'#39d98a':'#ff6b72',
            style:{background:isBuy?'#16784d':'#a93e46',color:'#ffffff',fontSize:'11px',fontWeight:700}
        }
    }]};
}

function catchVisibleYRange(chart){
    const g=chart?.w?.globals;
    const mins=Array.from(g?.minYArr||[]).map(Number).filter(Number.isFinite);
    const maxs=Array.from(g?.maxYArr||[]).map(Number).filter(Number.isFinite);
    if(!mins.length||!maxs.length)return null;
    const min=Math.min(...mins),max=Math.max(...maxs);
    return max>min?{min,max}:null;
}

function bindCatchCrosshair(){
    if(typeof catchCrosshairCleanup==='function')catchCrosshairCleanup();
    const host=document.getElementById('catch-chart');
    catchCrosshairCleanup=window.CryptoChartCrosshair?.bind(host,catchChart,{valueFormatter:price,yRange:catchVisibleYRange,timeZone:'Asia/Riyadh'})||null;
}

function eventFocusRange(e,step,mode='fit'){
    const start=parseUtc(e.startTime)?.getTime();
    const end=parseUtc(e.endTime)?.getTime();
    const blockStart=parseUtc(e.blockStartTime||e.startTime)?.getTime();
    const blockEnd=parseUtc(e.blockEndTime||e.endTime)?.getTime();
    if(!Number.isFinite(start)||!Number.isFinite(end))return null;
    if(mode==='start')return{min:start-step*40,max:start+step*80};
    if(mode==='earliest'&&Number.isFinite(blockStart))return{min:blockStart,max:Math.min(blockEnd,blockStart+step*120)};
    if(mode==='latest'&&Number.isFinite(blockEnd))return{min:Math.max(blockStart,blockEnd-step*120),max:blockEnd};
    const pad=Math.max(step*15,(end-start)*.35);
    return{min:Math.max(blockStart,start-pad),max:Math.min(blockEnd,end+pad)};
}

async function loadCatchChart(){
    if(!catchFocusId)return;
    // FIX-095: backend chooses the blamed signal native interval; no popup timeframe can hide it.
    const data=await api(`/api/administration/debug/price-moves/${encodeURIComponent(catchFocusId)}/chart`);
    const e=data.event;catchFocusEvent=e;catchFocusSignal=data.blamedSignal||null;
    // FIX-097: an older/PENDING caught move may have no persisted bestSignalId. The backend now
    // reconstructs the deterministic best signal from immutable move-window signals. If no signal
    // truly exists, show an explicit explanation instead of a 400 error or an empty black chart.
    if(!catchFocusSignal){
        if(catchChart){catchChart.destroy();catchChart=null;}
        const host=document.getElementById('catch-chart');if(host)host.innerHTML='';
        const empty=document.getElementById('catch-chart-empty');
        if(empty){empty.textContent=data.blamedSignalMessage||'No persisted trade signal exists inside this caught move window, so there is no blamed signal to highlight.';empty.classList.remove('hidden');}
        document.getElementById('catch-chart-title').textContent=`${String(e?.symbol||'').toUpperCase()} · No blamed signal`;
        document.getElementById('catch-chart-context').textContent='No persisted signal exists inside the caught move window.';
        return;
    }
    // FIX-096: backend returns the actual candle interval used for this popup. It is normally the
    // blamed signal's native interval, with a truthful 1m fallback only when that historical native
    // candle series is absent. The signal's own interval remains visible in the context label.
    const interval=String(data.interval||catchFocusSignal?.interval||'1m');
    const signalInterval=String(data.signalInterval||catchFocusSignal?.interval||interval);
    if(catchFocusSignal)catchFocusSignal.__chartInterval=interval;
    const candles=(data.candles||[]).map(c=>({
        x:parseUtc(c.openTime)?.getTime(),
        y:[+c.openPrice,+c.highPrice,+c.lowPrice,+c.closePrice],
        meta:c
    })).filter(c=>Number.isFinite(c.x)&&c.y.every(Number.isFinite));
    const empty=document.getElementById('catch-chart-empty');
    if(!candles.length){
        if(catchChart){catchChart.destroy();catchChart=null;}
        const host=document.getElementById('catch-chart');if(host)host.innerHTML='';
        if(empty){empty.textContent=`No persisted candles are available around blamed signal #${catchFocusSignal?.id??'—'} (${signalInterval}). The chart was not rendered.`;empty.classList.remove('hidden');}
        return;
    }
    empty?.classList.add('hidden');
    const step=intervalMs(interval);
    const signalX=parseUtc(catchFocusSignal?.candleOpenTime||catchFocusSignal?.generatedAt)?.getTime();
    const signalY=Number(catchFocusSignal?.latestPrice);
    // FIX-096: never hand Apex an X window that lies outside the returned candle series. That can
    // create a valid chart instance with an entirely black viewport. Center on the blamed signal when
    // it falls inside the candle range; otherwise show the nearest real candle range explicitly.
    const firstX=candles[0].x,lastX=candles[candles.length-1].x;
    let focus;
    if(Number.isFinite(signalX)&&signalX>=firstX-step&&signalX<=lastX+step){
        focus={min:Math.max(firstX,signalX-step*45),max:Math.min(lastX,signalX+step*75)};
        if(focus.max-focus.min<step*8)focus={min:firstX,max:lastX};
    }else{
        focus={min:firstX,max:lastX};
    }
    const metaByTime=new Map(candles.map(c=>[c.x,c.meta]));
    const original=String(catchFocusSignal?.originalDecision||'').toUpperCase();
    const finalDecision=String(catchFocusSignal?.decision||'').toUpperCase();
    const side=['BUY','STRONG_BUY'].includes(original)?'BUY':['SELL','STRONG_SELL'].includes(original)?'SELL':finalDecision||'SIGNAL';
    const blocked=catchFocusSignal?.finalEntryAllowed===false;

    document.getElementById('catch-chart-title').textContent=`${String(e.symbol||'').toUpperCase()} · Blamed ${side} signal #${catchFocusSignal?.id??'—'}`;
    const intervalContext=data.fallbackIntervalUsed?`${signalInterval} signal · ${interval} candle fallback`:`${signalInterval}`;
    const resolutionNote=data.blamedSignalResolution==='MOVE_WINDOW_RECONSTRUCTED'?' · reconstructed from move-window signals':'';
    document.getElementById('catch-chart-context').textContent=`${intervalContext} · ${blocked?'BLOCKED · ':''}score ${catchFocusSignal?.totalScore??'—'} · confidence ${catchFocusSignal?.confidenceScore??'—'}${resolutionNote} · KSA chart context`;

    const tooltip=({seriesIndex,dataPointIndex,w})=>{
        const point=w?.config?.series?.[seriesIndex]?.data?.[dataPointIndex];
        const ts=Number(point?.x instanceof Date?point.x.getTime():point?.x);
        const c=metaByTime.get(ts);
        if(!c)return'';
        const volume=Number(c.volume),taker=Number(c.takerBuyBaseVolume);
        const buyPct=Number.isFinite(volume)&&volume>0&&Number.isFinite(taker)?taker/volume*100:NaN;
        return `<div class="inspector-candle-tooltip"><div class="tooltip-time">${esc(chartTime(ts,true))} KSA</div><div class="tooltip-grid"><span>Open</span><strong>${esc(price(c.openPrice))}</strong><span>High</span><strong>${esc(price(c.highPrice))}</strong><span>Low</span><strong>${esc(price(c.lowPrice))}</strong><span>Close</span><strong>${esc(price(c.closePrice))}</strong><span>Volume</span><strong>${esc(num(c.volume,8))}</strong><span>Taker buy</span><strong>${Number.isFinite(buyPct)?buyPct.toFixed(2)+'%':'—'}</strong><span>Trades</span><strong>${esc(num(c.numberOfTrades,0))}</strong></div></div>`;
    };

    const options={
        chart:{
            type:'candlestick',height:540,background:'transparent',foreColor:'#8da2b1',animations:{enabled:false},
            toolbar:{show:true,autoSelected:'zoom',tools:{download:false,selection:true,zoom:true,zoomin:true,zoomout:true,pan:true,reset:true}},
            zoom:{enabled:true,type:'x',autoScaleYaxis:true},
            events:{updated:()=>setTimeout(bindCatchCrosshair,0),zoomed:()=>setTimeout(bindCatchCrosshair,0),selection:()=>setTimeout(bindCatchCrosshair,0),scrolled:()=>setTimeout(bindCatchCrosshair,0)}
        },
        title:{text:`${String(e.symbol||'').toUpperCase()} · ${interval} · Blamed signal #${catchFocusSignal?.id??''}`,align:'left',style:{fontSize:'13px',fontWeight:600,color:'#dbe8ef'}},
        series:[{name:'Candles',data:candles.map(({x,y})=>({x,y}))}],
        dataLabels:{enabled:false},
        xaxis:{type:'datetime',min:focus.min,max:focus.max,tickAmount:10,labels:{datetimeUTC:false,hideOverlappingLabels:true,formatter:(value,timestamp)=>chartTime(timestamp??value)},axisTicks:{show:true},crosshairs:{show:true,position:'front',stroke:{width:1,dashArray:3}},tooltip:{enabled:false}},
        yaxis:{opposite:true,forceNiceScale:true,decimalsInFloat:8,labels:{formatter:value=>price(value)},tooltip:{enabled:false},crosshairs:{show:true,position:'front',stroke:{width:1,dashArray:3}}},
        grid:{borderColor:'#203342',xaxis:{lines:{show:false}},yaxis:{lines:{show:true}},padding:{left:6,right:10}},theme:{mode:'dark'},
        plotOptions:{candlestick:{colors:{upward:'#39d98a',downward:'#ff6b72'},wick:{useFillColor:true}}},
        annotations:catchAnnotations(data),tooltip:{shared:false,intersect:false,followCursor:true,custom:tooltip}
    };
    if(typeof catchCrosshairCleanup==='function'){catchCrosshairCleanup();catchCrosshairCleanup=null;}
    if(catchChart)catchChart.destroy();
    const host=document.getElementById('catch-chart');host.innerHTML='';
    catchChart=new ApexCharts(host,options);
    await catchChart.render();
    bindCatchCrosshair();
}

// FIX-094: View chart now opens the same fixed Trade Inspector modal shell instead of expanding an
// inline panel inside Catching Market. The parent frame remains visible/dimmed underneath.
async function viewGraph(id){
    if(id===undefined||id===null||String(id).trim()===''){
        msg('Caught move chart could not open: missing event id.',true);
        return;
    }
    catchFocusId=String(id).trim();
    const modal=document.getElementById('catch-chart-panel');
    const empty=document.getElementById('catch-chart-empty');
    const host=document.getElementById('catch-chart');

    // FIX-094B: open the popup synchronously before any HTTP work. Even if the backend chart request
    // fails, the operator must see that the click was received and must see the actual error in-place.
    modal?.classList.remove('hidden');
    modal?.setAttribute('aria-hidden','false');
    document.body.classList.add('inspector-modal-open');
    if(host)host.innerHTML='<div class="empty catch-chart-loading">Loading caught move chart…</div>';
    empty?.classList.add('hidden');
    try{
        await loadCatchChart();
    }catch(e){
        if(host)host.innerHTML='';
        if(empty){
            empty.textContent=`Caught move chart could not load: ${e.message}`;
            empty.classList.remove('hidden');
        }
        msg(`Caught move chart could not load: ${e.message}`,true);
    }
}
function closeCatchChart(){
    const modal=document.getElementById('catch-chart-panel');
    modal?.classList.add('hidden');modal?.setAttribute('aria-hidden','true');
    document.body.classList.remove('inspector-modal-open');
    if(typeof catchCrosshairCleanup==='function'){catchCrosshairCleanup();catchCrosshairCleanup=null;}
    if(catchChart){catchChart.destroy();catchChart=null;}
    const host=document.getElementById('catch-chart');if(host)host.innerHTML='';
    catchFocusId=null;catchFocusEvent=null;catchFocusSignal=null;
}
function navigateCatch(mode){
    if(!catchChart||!catchFocusSignal)return;
    const interval=String(catchFocusSignal.__chartInterval||catchFocusSignal.interval||'1m');
    const step=intervalMs(interval);
    const signalX=parseUtc(catchFocusSignal.candleOpenTime||catchFocusSignal.generatedAt)?.getTime();
    if(!Number.isFinite(signalX))return;
    let range;
    if(mode==='start'||mode==='fit')range={min:signalX-step*45,max:signalX+step*75};
    else if(mode==='earliest')range={min:signalX-step*120,max:signalX};
    else range={min:signalX,max:signalX+step*120};
    catchChart.updateOptions({xaxis:{min:range.min,max:range.max}},false,false).then(()=>setTimeout(bindCatchCrosshair,0));
}

document.getElementById('price-move-settings-form').addEventListener('submit',async e=>{e.preventDefault();try{await save();await moves()}catch(x){msg(x.message,true)}});
document.getElementById('price-move-refresh').addEventListener('click',moves);
document.getElementById('price-move-select-all').addEventListener('click',()=>document.querySelectorAll('#price-move-symbols input').forEach(x=>x.checked=true));
document.getElementById('price-move-clear-symbols').addEventListener('click',()=>document.querySelectorAll('#price-move-symbols input').forEach(x=>x.checked=false));
// FIX-094B: document-level capture is a defensive fallback for legacy/cached Catching Market markup
// that may still render a data-view button outside the current tbody. Current rows also have direct
// listeners installed by renderMoves(); the marker prevents the fallback from executing twice.
document.addEventListener('click',e=>{
    const v=e.target.closest?.('[data-view]');
    if(!v)return;
    if(v.classList?.contains('catch-view-chart-button'))return; // direct listener owns current markup
    e.preventDefault();
    viewGraph(v.dataset.view);
},true);
document.getElementById('price-move-level-filter').addEventListener('change',renderMoves);
document.getElementById('catch-chart-close').addEventListener('click',closeCatchChart);
document.querySelector('[data-catch-popup-close="1"]')?.addEventListener('click',closeCatchChart);
document.getElementById('catch-chart-fit').addEventListener('click',()=>navigateCatch('fit'));
document.getElementById('catch-chart-start').addEventListener('click',()=>navigateCatch('start'));
document.getElementById('catch-chart-earliest').addEventListener('click',()=>navigateCatch('earliest'));
document.getElementById('catch-chart-latest').addEventListener('click',()=>navigateCatch('latest'));
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!document.getElementById('catch-chart-panel')?.classList.contains('hidden'))closeCatchChart();});
(async()=>{try{await settings();await symbols();await moves()}catch(e){msg(e.message,true)}})();
