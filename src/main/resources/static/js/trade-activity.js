(()=>{'use strict';const $=id=>document.getElementById(id),esc=v=>String(v??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const short=v=>String(v??'—').trim().toUpperCase().replace(/[^A-Z0-9]+/g,'_').replace(/^_+|_+$/g,'')||'—';
const time=v=>{if(!v)return '—';try{return new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Riyadh',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(new Date(v));}catch(e){return v;}};
async function loadSymbols(){try{const r=await fetch('/api/trade-activity/symbols',{cache:'no-store'});if(!r.ok)return;(await r.json()).forEach(s=>{const o=document.createElement('option');o.value=s;o.textContent=s;$('activity-symbol').appendChild(o);});}catch(e){}}

// FIX-058: COUPLE is an exclusive completed-trade mode. Keeping its controls separate
// avoids ambiguous combinations such as BUY + BLOCKED + LOST. Normal mode keeps the
// existing BUY/SELL + EXECUTED/BLOCKED contract; couple mode uses WIN/LOST only.
function syncMode(){
  const couple=$('activity-couple').checked;
  document.querySelectorAll('.activity-side').forEach(x=>{x.disabled=couple;if(couple)x.checked=false;});
  document.querySelectorAll('.activity-state').forEach(x=>{x.disabled=couple;if(couple)x.checked=false;});
  document.querySelectorAll('.activity-outcome').forEach(x=>x.disabled=!couple);
  if(couple && !document.querySelector('.activity-outcome:checked')) document.querySelector('.activity-outcome[value="LOST"]').checked=true;
  if(!couple){
    if(!document.querySelector('.activity-side:checked')) document.querySelector('.activity-side[value="BUY"]').checked=true;
    if(!document.querySelector('.activity-state:checked')) document.querySelector('.activity-state[value="EXECUTED"]').checked=true;
  }
}
function keepOneSelected(box,selector){if(document.querySelector(selector+':checked'))return;const peer=[...document.querySelectorAll(selector)].find(x=>x!==box&&!x.disabled);if(peer)peer.checked=true;else box.checked=true;}

async function search(){
  const couple=$('activity-couple').checked;
  const err=$('activity-error'); err.classList.add('hidden');
  const p=new URLSearchParams({symbol:$('activity-symbol').value,hours:$('activity-hours').value});
  if(couple){
    const outcomes=[...document.querySelectorAll('.activity-outcome:checked')].map(x=>x.value);
    if(!outcomes.length){err.textContent='Select WIN, LOST, or both for Couple mode.';err.classList.remove('hidden');return;}
    p.append('filter','COUPLE'); outcomes.forEach(v=>p.append('filter',v));
  } else {
    const sides=[...document.querySelectorAll('.activity-side:checked')].map(x=>x.value);
    const states=[...document.querySelectorAll('.activity-state:checked')].map(x=>x.value);
    if(!sides.length){err.textContent='Select BUY, SELL, or both.';err.classList.remove('hidden');return;}
    if(!states.length){err.textContent='Select EXECUTED, BLOCKED, or both.';err.classList.remove('hidden');return;}
    sides.forEach(v=>p.append('filter',v)); states.forEach(v=>p.append('filter',v));
  }
  $('activity-rows').innerHTML='<tr><td colspan="7" class="empty-cell">Loading…</td></tr>';$('activity-count').textContent='Loading…';
  try{const r=await fetch('/api/trade-activity?'+p,{cache:'no-store'});if(!r.ok)throw new Error('HTTP '+r.status);const rows=await r.json();$('activity-count').textContent=rows.length+' rows';$('activity-rows').innerHTML=rows.length?rows.map(x=>`<tr${x.pair_id?' data-pair-id="'+esc(x.pair_id)+'"':''}><td>${esc(time(x.event_time||x.eventTime))}</td><td><strong>${esc(x.symbol)}</strong></td><td>${esc(x.timeframe||'—')}</td><td><span class="activity-pill ${String(x.action||'').toLowerCase()}">${esc(x.action)}</span></td><td>${esc(x.status)}</td><td>${esc(short(x.source))}</td><td><code>${esc(short(x.reason))}</code></td></tr>`).join(''):'<tr><td colspan="7" class="empty-cell">No activity matches these filters.</td></tr>';}
  catch(e){$('activity-rows').innerHTML='<tr><td colspan="7" class="empty-cell">Could not load activity.</td></tr>';err.textContent='Trade Activity could not load: '+e.message;err.classList.remove('hidden');$('activity-count').textContent='Error';}
}
$('activity-search').addEventListener('click',search);
$('activity-couple').addEventListener('change',()=>{syncMode();search();});
document.querySelectorAll('.activity-side').forEach(box=>box.addEventListener('change',()=>keepOneSelected(box,'.activity-side')));
document.querySelectorAll('.activity-state').forEach(box=>box.addEventListener('change',()=>keepOneSelected(box,'.activity-state')));
document.querySelectorAll('.activity-outcome').forEach(box=>box.addEventListener('change',()=>keepOneSelected(box,'.activity-outcome')));
$('activity-symbol').addEventListener('change',search);
syncMode();loadSymbols();})();
