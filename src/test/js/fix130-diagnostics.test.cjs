const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const assert=require('node:assert/strict'),{test}=require('node:test');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/proven-analyzed-trades.js'),'utf8');
function node(){return {textContent:'',children:[],classList:{remove(){}},replaceChildren(){this.children=[]},appendChild(n){this.children.push(n)}};}
function render(result){
 const nodes=Object.fromEntries(['fix130-panel','fix130-summary','fix130-body'].map(id=>[id,node()]));
 const ctx=vm.createContext({document:{getElementById:id=>nodes[id],createElement:()=>node()}});
 const start=source.indexOf('function renderFix130(');
 vm.runInContext(source.slice(start,source.indexOf("document.addEventListener('click'",start)),ctx);
 ctx.renderFix130(result);return nodes;
}
test('finalization failure is visible',()=>assert.match(render({status:'rejected'})['fix130-summary'].textContent,/could not be loaded/));
test('pending work and held global gate do not imply trading is paused',()=>{
 const text=render({status:'fulfilled',value:{rows:[],pendingCount:17,gate:{active_job_id:9}}})['fix130-summary'].textContent;
 assert.match(text,/Pending jobs: 17/);assert.match(text,/held job: 9/);assert.match(text,/does not halt live trading/);assert.match(text,/not a frozen rollover-time/);
});
test('job error is rendered as text and empty data is not success',()=>{
 const nodes=render({status:'fulfilled',value:{rows:[{id:7,status:'REVIEW_REQUIRED',last_error:'<script>alert(1)</script>'}]}});
 assert.equal(nodes['fix130-body'].children[0].children[10].textContent,'<script>alert(1)</script>');
 assert.equal(nodes['fix130-body'].children[0].children[10].innerHTML,undefined);
 assert.match(nodes['fix130-summary'].textContent,/does not prove/);
});
