// Run: node --test src/test/js/fix122-diagnostics.test.cjs
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const assert=require('node:assert/strict'),{test}=require('node:test');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/proven-analyzed-trades.js'),'utf8');
function render(result){
 const nodes=Object.fromEntries(['fix122-panel','fix122-summary','fix122-body'].map(id=>[id,{classList:{remove(){}},innerHTML:'',textContent:''}]));
 const ctx=vm.createContext({document:{getElementById:id=>nodes[id]},formatMoveTime:x=>String(x),escapeHtml:x=>String(x??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;')});
 vm.runInContext(source.slice(source.indexOf('function renderFix122(')),ctx);
 ctx.renderFix122(result);return nodes;
}
test('missing diagnostics never render as a passed rule',()=>{
 const n=render({status:'rejected'});assert.match(n['fix122-summary'].textContent,/unavailable/);assert.equal(n['fix122-body'].innerHTML,'');
});
test('old runs say not recorded; explicit OLD remains clearly distinguished',()=>{
 assert.match(render({status:'fulfilled',value:{enabled:null,rows:[]}})['fix122-summary'].textContent,/Not recorded/);
 assert.match(render({status:'fulfilled',value:{enabled:false,rows:[]}})['fix122-summary'].textContent,/Before FIX-122 comparison/);
});
test('blocked evaluations and origin exclusions are visible and escaped',()=>{
 const n=render({status:'fulfilled',value:{enabled:true,rows:[{stage:'EVALUATION',signal_id:619225,evaluated_at:'2026-09-07',payload:JSON.stringify({source:'PRODUCTION',status:'APPLIED_EXCLUSIONS',allowed:false,code:'BUILDING',stopExecutionId:1812,stopAt:'2026-09-07',excluded:[{purpose:'DEFERRED_ORIGIN',signalId:618956,reason:'<script>alert(1)</script>'}],explanation:'<unsafe>'})}]}});
 assert.match(n['fix122-body'].innerHTML,/Entry not qualified/);assert.match(n['fix122-body'].innerHTML,/618956/);
 assert.match(n['fix122-body'].innerHTML,/&lt;script&gt;/);assert.ok(!n['fix122-body'].innerHTML.includes('<script>'));
});
