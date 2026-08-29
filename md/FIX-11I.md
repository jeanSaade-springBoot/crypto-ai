# FIX-11I — Trade Inspector OPEN/CLOSED + Symbol Filters

## Classification

Read-only Trade Inspector UI/query enhancement. No Production trading behavior, Replay behavior, execution rule, wallet mutation, position-management rule, threshold, sizing rule, or exit rule changes.

## Requirement

Trade Inspector must support:

- Trade status: **Closed trades** or **Open trades**.
- Symbol: **All symbols** or one persisted symbol.
- Filters can be combined, e.g. `EDUUSDT + OPEN` or `BNBUSDT + CLOSED`.
- Default remains **Closed trades + All symbols**, newest first, fixed page size **10**.

## Data authority

Closed trades continue to use the existing wallet SELL evidence and existing BUY-pairing logic from FIX-106/FIX-113.

Open trades are read from `wallet_managed_position` where `status = 'OPEN'`. Entry signal/wallet evidence is joined read-only when available. No SELL, realized P&L, post-exit price or exit-quality value is fabricated for an open position.

## UI behavior

Changing Trade status or Symbol resets pagination to page 1 and reloads server-side filtered rows. OPEN cards show persisted entry, stop loss, take profit, highest managed price, profit-lock state, quantity and holding time. Closed cards remain unchanged.

The existing View chart is available for OPEN positions. Its initial window is bounded around the entry so an old open position cannot trigger an unbounded candle query; the existing **Latest** chart control can jump to the newest candles. **View path** remains available only for completed BUY → SELL trades because an OPEN position has no terminal SELL lifecycle yet.

## Safety

All new repository/service paths are `readOnly` through the existing Trade Inspector service transaction. No Production or Replay decision path references these filters.
