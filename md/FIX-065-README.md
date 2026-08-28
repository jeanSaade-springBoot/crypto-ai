# FIX-065 — Replay Investigation Queue / Batch Upload

This build is based on the uploaded `src (2).zip`.

## What changed
- Added a persistent Investigation Queue to Proven Analyzed Trades / Safe Replay.
- CSV upload supports multiple exact replay cases.
- CSV timestamps are KSA (Asia/Riyadh); UI converts them to UTC before persistence/replay.
- Run one case or select multiple cases and Run Selected sequentially.
- Each saved case retains the last replay run ID/status.
- Existing single replay form remains available.
- Removed stale static BNB/BNBUSDT defaults from replay UI and chart URL fallbacks.
- Included `replay-investigation-current-batch.csv` with current incidents and winning controls.

## Safety
No Production trading/scoring/wallet/position behavior changed. Saved cases delegate to the existing isolated regression runner and its single-active-run backend protection.

## Deploy note
Flyway migration V68 creates `regression_investigation_case`.
