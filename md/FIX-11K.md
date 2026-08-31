# FIX-11K — Defensive Risk Reduction Replay Observation Harness

## Classification
Replay research only. Phase A observation only. Not authorized for Production execution.

## Purpose
Record when an already-open replay position shows the PEPE #810 pattern without changing the existing replay or Production SELL path:

- at least two consecutive **final** 1m `STRONG_SELL` observations (the lowest experiment-matrix streak; 2/3/4 remain analysis dimensions),
- fresh 5m `original_decision` is bearish,
- that 5m final `decision` is `NEUTRAL`,
- 5m `confluence_status` is `CONFLICT` or `STRONG_CONFLICT`,
- fresh 1h final decision is not `BUY`/`STRONG_BUY`,
- the replay-native position is currently profitable and has positive giveback from its replay-native peak.

No final peak-profit, giveback, streak, or reduction-size threshold is selected by this fix. Phase A persists raw candidate metrics so the agreed experiment matrix can be evaluated after the run.

## Isolation
The observer is invoked beside `ShadowProductionReplayService`'s existing Production-parity exit path. It does not mutate cash, quantity, replay positions, Production positions, wallet tables, `validateSell()`, `SELL_CONFIRMED`, signal decisions, MTF decisions, or BUY authority.

Observations are stored only in `defensive_risk_reduction_observation_test` and its replay archive table. A read-only endpoint exposes them per replay run.

## Searchable server logs
Search for these exact markers:

- `FIX11K_DEFENSIVE_OBSERVER_START`
- `FIX11K_DEFENSIVE_OBSERVER_REJECTED` — explains why a sustained 1m streak did not qualify
- `FIX11K_DEFENSIVE_OBSERVER_CANDIDATE`
- `FIX11K_DEFENSIVE_OBSERVER_SUMMARY`

Candidate logs include run id, position id, signal id/time, current/entry/peak prices, current/peak/giveback percentages, 1m streak, 5m raw/final/confluence state, 1h final state, and `action=OBSERVE_ONLY`.

## Phase A result endpoint

`GET /api/administration/regression-tests/runs/{id}/defensive-risk-observations`

Archived runs:

`GET /api/administration/regression-tests/archives/{id}/defensive-risk-observations`

## Deferred
Phase B quantity reduction is not implemented. `MultiTimeframeConfluenceService` regime behavior remains a separate investigation and is unchanged.
