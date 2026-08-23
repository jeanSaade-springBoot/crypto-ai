# Change Log

## FIX-065 — Replay Investigation Queue / Batch Upload
- Added persistent `regression_investigation_case` queue.
- Added CSV batch upload with explicit KSA timestamps (`symbol,start_ksa,end_ksa`; optional case/wallet/expected/notes).
- Added Run per case and sequential Run Selected using the existing isolated replay runner and single-active-run backend lock.
- Saved last replay run/status per case so the same incident can be rerun after later fixes.
- Removed static BNB Rally dates/name and `BNBUSDT` fallbacks from the Replay UI.
- No Production trading, scoring, wallet, entry/exit, or Replay calculation logic changed.


## FIX-066 — Trade Activity symbol + visible TP extension
- Added a dedicated Symbol column to every Trade Activity row, including mixed-symbol/couple results.
- Trade Inspector now reads immutable `position_management_event` rows for the exact BUY→SELL lifecycle.
- `TAKE_PROFIT_EXTENDED` is rendered as a dedicated highlighted timeline phase with old TP, new TP, market price, reason and KSA time.
- Added clear FIX-066 source comments and Fix Registry documentation.
- Read-only UI/audit change only; no Production trading, Replay, scoring, execution, TP policy or exit logic changed.


## FIX-067 — Extended take-profit authoritative state synchronization
- Confirmed PEPE wallet #833 → SELL #847 / managed position #552 was an early TAKE_PROFIT caused by stale dual Production TP state.
- `wallet_managed_position.take_profit_usdt` was extended from `0.000004146897` to `0.000004165346` at 00:02:08 KSA, but `paper_position.take_profit` remained at `0.000004146897`.
- Signal #161661 arrived at `0.000004150`; PaperTradingService therefore matched the stale paper TP and closed, despite price still being below the approved extended target.
- LivePositionProtectionService now synchronizes the approved extended target into `paper_position` in the same transaction.
- PaperTradingService now treats the OPEN `wallet_managed_position` TP as authoritative and repairs stale `paper_position` TP before checking TAKE_PROFIT.
- Added literal PEPE #552 regression test.
- Replay behavior is unchanged because ShadowProductionReplayService already updates `wallet_position_test.take_profit_usdt` and carries the returned extended target forward before later exit evaluation.
- No strategy/category/score/continuation threshold/TP distance changed.
