# UI / Proven review updates (FIX-015 to FIX-017)

## FIX-015 — Trade Inspector hover-price lifecycle
- Keeps the dedicated Binance-style right-axis hover price after interval changes and Apex toolbar actions.
- Cleans old chart listeners before interval-driven chart recreation.
- Rebinds after update / selection / zoom / pan / reset.
- Uses capture-phase pointer events and a current visible-Y fallback.
- Trading and candle retrieval logic are unchanged.

## FIX-016 — Proven trade leg archive
- Adds Flyway `V60__archive_proven_trade_legs.sql`.
- Proven trades now expose **Archive BUY** and **Archive SELL** independently.
- A new read-only table lists archived execution legs.
- The manual full-run Archive button was removed from Current Test rows.
- Automatic full-run archive before **Clear Data** remains unchanged for diagnostic safety.

## FIX-017 — Fix Registry replay guidance
- Existing **Entry time** and **Exit time** remain visible.
- Adds **Suggested Proven replay window** to every registry entry and to Copy all fixes output.
- FIX-014 explicitly recommends `2026-08-19 19:50-20:35 UTC` / `22:50-23:35 KSA`.
