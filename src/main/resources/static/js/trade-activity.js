(()=>{'use strict';const $=id=>document.getElementById(id),esc=v=>String(v??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const short=v=>String(v??'—').trim().toUpperCase().replace(/[^A-Z0-9]+/g,'_').replace(/^_+|_+$/g,'')||'—';
const time=v=>{if(!v)return '—';try{return new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Riyadh',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(new Date(v));}catch(e){return v;}};
async function loadSymbols(){try{const r=await fetch('/api/trade-activity/symbols',{cache:'no-store'});if(!r.ok)return;(await r.json()).forEach(s=>{const o=document.createElement('option');o.value=s;o.textContent=s;$('activity-symbol').appendChild(o);});}catch(e){/* Symbol metadata is optional; activity data still remains on-demand. */}}
async function search(){
  // FIX-049: Trade Activity has two mandatory filter groups. At least one direction
  // (BUY/SELL) and at least one state (EXECUTED/BLOCKED) must always be selected.
  // The backend applies them as (direction) AND (state) AND (symbol).
  const sides=[...document.querySelectorAll('.activity-side:checked')].map(x=>x.value);
  const states=[...document.querySelectorAll('.activity-state:checked')].map(x=>x.value);
  const err=$('activity-error');
  err.classList.add('hidden');
  if(!sides.length){err.textContent='Select BUY, SELL, or both.';err.classList.remove('hidden');return;}
  if(!states.length){err.textContent='Select EXECUTED, BLOCKED, or both.';err.classList.remove('hidden');return;}
  const p=new URLSearchParams({symbol:$('activity-symbol').value,hours:$('activity-hours').value});
  sides.forEach(v=>p.append('filter',v));
  states.forEach(v=>p.append('filter',v));
  $('activity-rows').innerHTML='<tr><td colspan="7" class="empty-cell">Loading…</td></tr>';
  $('activity-count').textContent='Loading…';
  try{
    const r=await fetch('/api/trade-activity?'+p,{cache:'no-store'});
    if(!r.ok)throw new Error('HTTP '+r.status);
    const rows=await r.json();
    $('activity-count').textContent=rows.length+' rows';
    $('activity-rows').innerHTML=rows.length?rows.map(x=>`<tr><td>${esc(time(x.event_time||x.eventTime))}</td><td><strong>${esc(x.symbol)}</strong></td><td>${esc(x.timeframe||'—')}</td><td><span class="activity-pill ${String(x.action||'').toLowerCase()}">${esc(x.action)}</span></td><td>${esc(x.status)}</td><td>${esc(short(x.source))}</td><td><code>${esc(short(x.reason))}</code></td></tr>`).join(''):'<tr><td colspan="7" class="empty-cell">No activity matches these filters.</td></tr>';
  }catch(e){
    $('activity-rows').innerHTML='<tr><td colspan="7" class="empty-cell">Could not load activity.</td></tr>';
    err.textContent='Trade Activity could not load: '+e.message;
    err.classList.remove('hidden');
    $('activity-count').textContent='Error';
  }
}
$('activity-search').addEventListener('click',search);

// FIX-050: each filter group must always keep at least one option selected, but
// do not force the checkbox the operator just cleared back on. If the last
// selected option is unchecked, switch the group to its peer instead. This lets
// BUY <-> SELL and EXECUTED <-> BLOCKED be changed naturally while preserving the
// strict backend contract: (BUY or SELL) AND (EXECUTED or BLOCKED) AND symbol.
function keepOneSelected(box,selector){
  if(document.querySelector(selector+':checked')) return;
  const peer=[...document.querySelectorAll(selector)].find(x=>x!==box);
  if(peer) peer.checked=true;
  else box.checked=true;
}
document.querySelectorAll('.activity-side').forEach(box=>box.addEventListener('change',()=>keepOneSelected(box,'.activity-side')));
document.querySelectorAll('.activity-state').forEach(box=>box.addEventListener('change',()=>keepOneSelected(box,'.activity-state')));
loadSymbols();})();
