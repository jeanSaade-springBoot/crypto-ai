(()=>{'use strict';
// FIX-107: Trade Activity and Dashboard instantiate the exact same shared browser.
const browser=new window.SignalExecutionsBrowser({symbol:'activity-symbol',period:'activity-period',type:'activity-type',search:'activity-search',count:'activity-count',rows:'activity-rows',error:'activity-error',modal:'activity-analysis-modal',close:'activity-analysis-close',title:'activity-analysis-title',subtitle:'activity-analysis-subtitle',content:'activity-analysis-content'});
window.tradeActivitySignalBrowser=browser;
browser.init();
})();
