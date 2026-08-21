# FIX-034 — Remove automatic executions from Wallet page

## Scope
UI cleanup only.

## Change
- Removed the `AUTOMATIC EXECUTIONS / Wallet trades` panel from the Wallet page.
- Removed the corresponding `trade-body` renderer from `wallet.js` so Wallet does not reference removed markup.
- Wallet trade persistence and backend responses remain unchanged.
- Trade history remains available through Trade Inspector, including venue filtering and full path diagnostics.

## Trading impact
None. BUY/SELL logic, wallet execution, Production, Replay, TP/SL and position management are unchanged.
