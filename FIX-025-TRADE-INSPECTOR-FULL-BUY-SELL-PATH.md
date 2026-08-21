# FIX-025 — Trade Inspector full BUY → SELL path

## Scope
Diagnostic/UI only. No trading or replay behavior changes.

## Problem
FIX-024 showed the entry decision and holding duration, but the timeline did not expose the persisted state changes between the wallet BUY and wallet SELL. This made the visual path appear to stop before the SELL lifecycle.

## Change
- Read every executed wallet event for the selected symbol between the selected BUY and SELL, including confirmation/scale-in BUYs.
- Read persisted trade signals in the same holding window and show meaningful 1m states plus all 5m/1h context states.
- Resolve the latest 1m/5m/1h state at the SELL timestamp independently from `wallet_trade.signal_id`, so mechanical TP/SL exits are explained even when the SELL has no signal id.
- Render one chronological timeline from opportunity start → entry → adds/context changes → profit lock (when present) → exit context → wallet SELL.
- Show separate 1m/5m/1h cards for entry and exit and preserve exact holding time.

## Safety
The endpoint remains `@Transactional(readOnly = true)`. It only reads persisted production records and does not call scoring, execution, replay, wallet mutation or position-management decision logic.
