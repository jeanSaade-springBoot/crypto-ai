# Change Log

## FIX-065 — Replay Investigation Queue / Batch Upload
- Added persistent `regression_investigation_case` queue.
- Added CSV batch upload with explicit KSA timestamps (`symbol,start_ksa,end_ksa`; optional case/wallet/expected/notes).
- Added Run per case and sequential Run Selected using the existing isolated replay runner and single-active-run backend lock.
- Saved last replay run/status per case so the same incident can be rerun after later fixes.
- Removed static BNB Rally dates/name and `BNBUSDT` fallbacks from the Replay UI.
- No Production trading, scoring, wallet, entry/exit, or Replay calculation logic changed.
