const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const assert=require('node:assert/strict'),{test}=require('node:test');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/js/proven-analyzed-trades.js'),'utf8');
function node(){return {textContent:'',children:[],classList:{remove(){}},replaceChildren(){this.children=[]},appendChild(n){this.children.push(n)}};}
function render(result){
 const nodes=Object.fromEntries(['fix129-panel','fix129-summary','fix129-body'].map(id=>[id,node()]));
 const ctx=vm.createContext({document:{getElementById:id=>nodes[id],createElement:()=>node()}});
 const start=source.indexOf('function renderFix129(');
 vm.runInContext(source.slice(start,source.indexOf("document.addEventListener('click'",start)),ctx);
 ctx.renderFix129(result);return nodes;
}
test('timing fetch failure remains distinct from empty history',()=>assert.match(render({status:'rejected'})['fix129-summary'].textContent,/could not be loaded/));
test('empty timing history discloses sampling, nesting and persistence limitations',()=>{
 const text=render({status:'fulfilled',value:{rows:[]}})['fix129-summary'].textContent;
 for(const pattern of [/not Replay timings/,/at least 1 second/,/do not add/,/best effort/,/does not prove/]) assert.match(text,pattern);
});
test('measured timing and exact identity render safely without invented candle',()=>{
 const nodes=render({status:'fulfilled',value:{rows:[{symbol:'UNIUSDT',candle_open_time:null,block_start:'2026-09-10T00:00:00Z',stage:'BLOCK_FINALIZATION',elapsed_ms:1234,outcome:'RETURNED',thread_name:'<img onerror=alert(1)>'}]}});
 const cells=nodes['fix129-body'].children[0].children;
 assert.equal(cells[2].textContent,'');assert.equal(cells[3].textContent,'2026-09-10T00:00:00Z');
 assert.equal(cells[7].textContent,'1234');assert.equal(cells[9].textContent,'<img onerror=alert(1)>');assert.equal(cells[9].innerHTML,undefined);
});
