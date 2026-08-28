# FIX-028 — Production exit audit truth + View Path real trigger

## Scope
Diagnostic/audit correctness only. No BUY, SELL, stop-loss, take-profit, continuation, profit-lock, sizing, scoring, HTF, order-book, ATR, Production execution condition, or Replay trading behavior is changed.

## Proven scenario
BTCUSDT latest trade investigated on 2026-08-21 KSA:

- BUY: 03:03:47 KSA at 73,156.13 from signal #105616.
- Position closed: 03:15:09 KSA at 73,393.85.
- `paper_position #145`: `TAKE_PROFIT`, exit reason "Price reached the configured take-profit target."
- `trade_signal #105688`: `WATCH`, not SELL.
- `position_analysis #1234`: `HOLD`, confidence 85, exit score 0.
- Legacy `wallet_trade #342`: incorrectly stored `SIGNAL_SELL` and said signal #105688 was a SELL decision.

The position engine was correct; the wallet audit wording was misleading because `PaperTradingService.completeClose(...)` reused the latest signal as an execution carrier and always called `executeSell(signal)`, whose metadata was hard-coded to `SIGNAL_SELL`.

## Changes
1. Added Flyway V61 `production_exit_audit` table. It stores the real terminal trigger separately from the latest market-context signal and Position Analysis recommendation.
2. Added `ProductionExitAuditService` and immutable audit entity/repository.
3. `PaperTradingService` now preserves the real close reason when handing an already-decided TP/SL/other terminal close to the wallet. Execution price, quantity, balances, idempotency key and exit condition are unchanged.
4. `WalletAutoExecutionService.executeSignalLinkedExit(...)` performs the same liquidation as the old signal-linked path but writes the actual reason (`TAKE_PROFIT`, `STOP_LOSS`, etc.) instead of blindly writing `SIGNAL_SELL`.
5. `LivePositionProtectionService` also records live-price exits in `production_exit_audit`; this does not change any protection policy.
6. Trade Inspector / View Path prefers the new audit row, then `paper_position`, then legacy wallet metadata. Old trades therefore become understandable without rewriting historical wallet rows.
7. The EXIT node explicitly distinguishes:
   - **REAL EXIT TRIGGER** — e.g. TAKE_PROFIT.
   - **Signal role** — `SELL_TRIGGER` only for a genuine persisted SELL/STRONG_SELL; otherwise `MARKET_CONTEXT_AT_EXIT`.

## Expected BTC display
`TAKE_PROFIT @ 73,393.85 · context WATCH #105688`

The evidence panel should state that WATCH #105688 was market context at exit and did **not** itself trigger a SELL.

## Regression
`ProductionExitAuditServiceTest` reproduces the BTC evidence: close trigger TAKE_PROFIT + source signal WATCH + Position Analysis HOLD. It verifies the audit preserves all three facts independently.

## Replay parity
No trading behavior changed, so Shadow Production Replay is intentionally untouched. View Path and the new audit table are read-only/diagnostic with respect to trading decisions.
