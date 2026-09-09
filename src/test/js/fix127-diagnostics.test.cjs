const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const assert=require('node:assert/strict'),{test}=require('node:test');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/proven-analyzed-trades.js'),'utf8');
function node(){return {textContent:'',children:[],classList:{remove(){}},replaceChildren(){this.children=[]},appendChild(n){this.children.push(n)}};}
function render(result){
 const nodes=Object.fromEntries(['fix127-panel','fix127-summary','fix127-body'].map(id=>[id,node()]));
 const ctx=vm.createContext({document:{getElementById:id=>nodes[id],createElement:()=>node()}});
 const start=source.indexOf('function renderFix127(');
 vm.runInContext(source.slice(start,source.indexOf("document.addEventListener('click'",start)),ctx);
 ctx.renderFix127(result);return nodes;
}
test('processing fetch failure is visible',()=>assert.match(render({status:'rejected'})['fix127-summary'].textContent,/could not be loaded/));
test('empty processing history is not a Replay pass or successful trade',()=>{
 const text=render({status:'fulfilled',value:{rows:[]}})['fix127-summary'].textContent;
 assert.match(text,/not simulated Replay/);assert.match(text,/not necessarily a trade/);assert.match(text,/does not prove/);
});
test('processing review status, attempts, exact lineage and error render as text',()=>{
 const nodes=render({status:'fulfilled',value:{rows:[{signal_id:127,symbol:'PEPEUSDT',interval_code:'5m',candle_open_time:'2026-09-09T10:00:00Z',origin:'WORKER',status:'REVIEW_REQUIRED',attempts:2,error_message:'<img onerror=alert(1)>'}]}});
 const cells=nodes['fix127-body'].children[0].children;
 assert.equal(cells[3].textContent,'2026-09-09T10:00:00Z');assert.equal(cells[5].textContent,'REVIEW_REQUIRED');
 assert.equal(cells[6].textContent,'2');assert.equal(cells[10].textContent,'<img onerror=alert(1)>');assert.equal(cells[10].innerHTML,undefined);
});
test('truncation and saved-entry context are disclosed',()=>{
 const text=render({status:'fulfilled',value:{rows:[],truncated:true,precedingHour:true,startTime:'START',endTime:'END'}})['fix127-summary'].textContent;
 assert.match(text,/first 500/);assert.match(text,/one hour before/);assert.match(text,/START to END/);assert.match(text,/no inferred trade pairing/);
});
