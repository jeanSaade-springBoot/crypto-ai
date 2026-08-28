# FIX-030 — Trade Path Performance Context

## Scope
Diagnostic/UI only. No production or replay trading behavior changes.

## What changed
Trade Inspector → View Path now displays a compact performance strip for every completed trade:

- **Holding time** — elapsed time from executed BUY to the real production exit.
- **Holding efficiency** — for profitable trades, realized P&L % divided by Maximum Favorable Excursion (MFE) %, capped at 100%. This makes profit capture/giveback visible without introducing a subjective expected holding duration.
- **Recent profit factor** — profit factor from the latest 20 completed wallet trades, shown as system context. Profit factor is deliberately not attributed to one individual trade.

## Example
If a trade reaches +2.00% MFE and exits +1.50%, holding efficiency is 75.0%.

## Safety
All values are read-only analytics. They are not inputs to scoring, Execution Intelligence, position management, TP/SL logic, or Replay.
