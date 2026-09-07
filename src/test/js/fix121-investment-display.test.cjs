// FIX-121: display checks for exact decimal strings and unavailable historical values.
// Run from the project root: node --test src/test/js/fix121-investment-display.test.cjs
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const assert = require('node:assert/strict');
const {test} = require('node:test');
const root = path.resolve(__dirname, '../../main/resources/static/js');
const inspectorSource = fs.readFileSync(path.join(root, 'trade-inspector.js'), 'utf8');
const replaySource = fs.readFileSync(path.join(root, 'proven-analyzed-trades.js'), 'utf8');
const ctx = vm.createContext({window:{CryptoTime:{formatLocal:x=>String(x)}},document:{},URLSearchParams});
// Execute real pure render functions, excluding page boot/event handlers.
vm.runInContext(inspectorSource.slice(0, inspectorSource.indexOf('function shortText(')), ctx);
vm.runInContext('function inspectorTradeKey(t){return t.walletBuyTradeId;}', ctx);
vm.runInContext(replaySource.slice(0, replaySource.indexOf('async function api(')), ctx);
vm.runInContext(`function escapeHtml(v){return String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}`, ctx);
const priceStart=replaySource.indexOf('function formatMovePrice(');
vm.runInContext(replaySource.slice(priceStart,replaySource.indexOf('function formatMoveTime(',priceStart)),ctx);
const investment={entryQuantity:'136332841.976742454696',unitEntryPriceUsdt:'0.0000037',totalInvestedUsdt:'504.431515313946',buyCount:2,status:'AVAILABLE',explanation:'Cumulative BUYs before fees.'};

test('Inspector preserves every acquired-quantity decimal and labels weighted price',()=>{
 const html=ctx.investmentEntry({symbol:'PEPEUSDT',investment});
 assert.ok(html.includes('136,332,841.976742454696 PEPE'));
 assert.ok(html.includes('Unit Entry Price (weighted)'));
 assert.ok(html.includes('Total Invested (before fees)'));
});
test('Replay preserves decimal quantity without inserting commas in its fraction',()=>{
 const html=ctx.replayInvestmentCell({investment});
 assert.ok(html.includes('136,332,841.976742454696'));
 assert.ok(html.includes('2 BUY execution(s)'));
});
test('Unavailable Replay investment never appears as a zero price or zero investment',()=>{
 const html=ctx.replayInvestmentCell({investment:{status:'UNAVAILABLE',entryQuantity:null,unitEntryPriceUsdt:null,totalInvestedUsdt:null,explanation:'Missing history'}});
 assert.ok(html.includes('Unit Entry Price: —'));
 assert.ok(html.includes('Total Invested: —'));
 assert.ok(html.includes('Missing history'));
 assert.ok(!html.includes('0.0000'));
});
test('Unavailable Inspector history is escaped and does not fabricate zero investment',()=>{
 const html=ctx.investmentEntry({investment:{status:'UNAVAILABLE',explanation:'<missing>'}});
 assert.ok(html.includes('&lt;missing&gt;'));
 assert.ok(!html.includes('$0'));
});
test('Closed and open cards retain separate sold/remaining quantity labels',()=>{
 const trade={symbol:'PEPEUSDT',investment,quantity:'10',entryPrice:0.0000037,openedAt:'2026-09-07',closedAt:'2026-09-08',walletBuyTradeId:1,walletSellTradeId:2};
 assert.ok(ctx.tradeCard(trade).includes('Final SELL quantity'));
 assert.ok(ctx.openTradeCard(trade).includes('Remaining quantity'));
 assert.ok(ctx.tradeCard(trade).includes('Quantity acquired'));
 assert.ok(ctx.openTradeCard(trade).includes('Quantity acquired'));
});
