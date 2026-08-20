# FIX-014 — XRP setup-timeframe wake-up

Scope: narrow initial-entry timing correction only.

- Keeps normal 1m BUY execution unchanged.
- Keeps 5m/1h non-executable as wallet authorities.
- Requires an existing unexecuted BUY opportunity.
- Requires a fresh 5m transition into BUY/STRONG_BUY (repeated 5m BUY does not retrigger).
- Requires latest 1m <= 2 minutes old, supportive/non-bearish, final-entry-allowed, and ATR-deferred.
- Requires fresh 1h WATCH/BUY authority.
- Reuses existing SETUP_TIMEFRAME_ATR, hard-risk gates, 5m ATR authority, and Entry Quality.
- Does not run when a position/allocation is already open.
- Production and ShadowProductionReplayService call the same wake-up policy.

XRP regression target:
- 1m #96944 WATCH @ ~1.0781, PULLBACK_ENTRY, ATR immediate=false.
- fresh 5m #96945 BUY 80/82, 1h WATCH.
- expected: opportunity is re-evaluated through SETUP_TIMEFRAME_ATR / SETUP_TIMEFRAME_WAKEUP rather than waiting for the later #97515 entry at 1.1279.

Validation status in this package:
- Fix Registry JS syntax checked with `node --check`.
- Focused unit tests added.
- Maven is not installed in the artifact container, so the full Maven/Jenkins build must be run after download/deployment.
