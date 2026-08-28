# FIX-111 — Dashboard hover stability + Signal View filter persistence

- Rebinds the shared chart crosshair after Apex redraw/zoom/pan and derives Y hover range from visible candles.
- Signal View deep links preserve the independent Signals Symbol/Period/Type filters and restore them before grid loading.
- Dashboard header symbol remains independent after manual Signals selection.
- Read-only UI change only; no Production, Replay, wallet, signal-generation or execution behavior changed.
