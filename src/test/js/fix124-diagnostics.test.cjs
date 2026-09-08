const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const assert=require('node:assert/strict'),{test}=require('node:test');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/proven-analyzed-trades.js'),'utf8');
function node(){return {textContent:'',children:[],classList:{remove(){}},replaceChildren(){this.children=[]},appendChild(n){this.children.push(n)}};}
function render(result){
 const nodes=Object.fromEntries(['fix124-panel','fix124-summary','fix124-body'].map(id=>[id,node()]));
 const ctx=vm.createContext({document:{getElementById:id=>nodes[id],createElement:()=>node()}});
 const start=source.indexOf('function renderFix124(');
 vm.runInContext(source.slice(start,source.indexOf("document.addEventListener('click'",start)),ctx);
 ctx.renderFix124(result);return nodes;
}
test('failed fetch is visible',()=>assert.match(render({status:'rejected'})['fix124-summary'].textContent,/could not be loaded/));
test('empty history does not claim successful protection or Replay parity',()=>{
 const n=render({status:'fulfilled',value:{rows:[]}});
 assert.match(n['fix124-summary'].textContent,/not simulated Replay/);
 assert.match(n['fix124-summary'].textContent,/does not prove/);
});
test('outcomes and exact lineage render as text, never executable HTML',()=>{
 const n=render({status:'fulfilled',value:{rows:[{symbol:'UNIUSDT',candle_open_time:'2026-09-08T05:41:00Z',observed_at:'2026-09-08T05:41:05.434Z',price:'7.046',attempts:2,outcome:'FAILED',error_message:'<img onerror=alert(1)>'}]}});
 const cells=n['fix124-body'].children[0].children;
 assert.equal(cells[1].textContent,'2026-09-08T05:41:00Z');
 assert.equal(cells[4].textContent,'2');assert.equal(cells[5].textContent,'FAILED');
 assert.equal(cells[6].textContent,'<img onerror=alert(1)>');
 assert.equal(cells[6].innerHTML,undefined);
});
