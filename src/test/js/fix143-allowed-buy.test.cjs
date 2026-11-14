// FIX-143: Execute the shared browser renderer to verify visibility and honest outcome wording.
const fs=require('node:fs'), path=require('node:path'), vm=require('node:vm');
const assert=require('node:assert/strict'), {test}=require('node:test');
const resources=path.resolve(__dirname,'../../main/resources/static');
const ctx=vm.createContext({window:{},document:{getElementById:()=>null},URLSearchParams,Intl,Date});
vm.runInContext(fs.readFileSync(path.join(resources,'js/signal-executions-browser.js'),'utf8'),ctx);
const browser=new ctx.window.SignalExecutionsBrowser({});
test('allowed unexecuted row is visible with details and no invented execution cause',()=>{
 const html=browser.rowHtml({rowKind:'ALLOWED_BUY',signalId:77,symbol:'TAOUSDT',originalDecision:'BUY',decision:'BUY',finalEntryAllowed:true,eventTime:'2026-10-03T13:28:00Z',executionReason:'No executed wallet BUY linked to this signal; cause not established.'},0);
 assert.match(html,/ALLOWED BUY — NOT EXECUTED/);
 assert.match(html,/ALLOWED \/ NOT EXECUTED/);
 assert.match(html,/cause not established/);
 assert.match(html,/Analyze/);
 assert.match(html,/16:28:00/);
});
test('both shared grids expose the new selector without dropping existing selectors',()=>{
 for(const name of ['dashboard.html','trade-activity.html']) {
  const html=fs.readFileSync(path.join(resources,name),'utf8');
  for(const type of ['ALLOWED_BUY','BLOCKED_BUY','BLOCKED_SELL','DONE','OPEN_BUY']) assert.ok(html.includes(`value="${type}"`));
 }
});
test('existing blocked BUY retains blocked state',()=>{
 const html=browser.rowHtml({rowKind:'BLOCKED_BUY',decision:'WATCH',originalDecision:'BUY',finalEntryAllowed:false},0);
 assert.match(html,/BLOCKED BUY/);assert.doesNotMatch(html,/ALLOWED \/ NOT EXECUTED/);
});
