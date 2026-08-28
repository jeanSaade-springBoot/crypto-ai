# FIX-094B — Catching Market View chart click wiring hardening

## Problem
The Catching Market View chart action could appear to do nothing. The row action relied on tbody event delegation and gave no persistent popup feedback when a chart request failed. Cached static assets could also leave older event wiring in the browser.

## Changes
- Every rendered `View chart` control is now `type="button"` and receives a direct click listener after each grid render/filter/refresh.
- A document-capture `[data-view]` fallback supports legacy/cached Catching Market markup.
- The modal opens synchronously before the backend request and shows `Loading caught move chart…`.
- Backend/chart errors remain visible inside the popup instead of immediately closing it.
- Catching Market chart JS and reused Trade Inspector/crosshair assets use FIX-094B cache-busting query versions.
- Empty candle results clear the loading placeholder and show the explicit no-history message.

## Scope
Read-only Catching Market UI only. No signal generation, Replay, FinalDecision, ExecutionIntelligence, position management, or wallet behavior changes.
