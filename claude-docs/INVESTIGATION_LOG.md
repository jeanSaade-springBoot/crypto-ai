# Investigation Log — Claude

Findings and rejected approaches recorded by Claude. Earlier findings by Codex
(2026-10-09 candle coverage, signal staleness after reconnect, local repair of
42 rows, ETHUSDT paper trade 59) are in `codex-docs/INVESTIGATION_LOG.md`.

## 2026-10-09 — Read-only codebase review: observations

Static code review only; no database queries, logs or Replay runs.

1. **Reconnect close-boundary gap is not auto-repaired.**
   `CandleGapDiagnostics` logs `DB_CANDLE_GAP` with `repair=false`;
   `LocalClosedCandleDetector` only re-admits candles that already have a
   runtime close token. A row left at `closed=0` by a reconnect stays open, and
   `maximum-missing-candles: 0` then blocks analysis for that lane. Matches the
   Codex-recorded outage of 2026-10-09 and the late ETH entry (trade 59).
2. **Automatic trades record zero fees.** `WalletAutoExecutionService` writes
   `feeUsdt(ZERO)` for buy and all exit paths (lines ~173, 260, 349, 443, 540,
   653). Automated P&L is gross; investigations must compute fees separately.
3. **Two independent sizing models.** Paper position is risk-sized from
   `paperAccountBalance`; wallet execution is budget-sized. The paper row
   adopts the wallet quantity. Paper and managed position state are synced by
   specific fixes (FIX-067 TP, partial-harvest sync), not one model.
4. **Account risk limits unused in automatic entry.**
   `CryptoAccountConfigurationService` limits (max order, exposure, loss,
   slippage) are not read by the paper entry path.
5. **Single-threaded ingestion and protection.** Candle upserts, price events
   and `LivePositionProtectionService` run synchronously on the WebSocket
   socket thread; slow protection delays all later kline messages.
6. **`websocket.reconnect-delay-seconds` is display-only.** Reconnect backoff
   uses `health-check-seconds`; the property is only returned by
   `BinanceStatusController`.
7. **Wallet exit failure does not block paper close.** In
   `PaperTradingService.completeClose` a wallet exit exception is logged and
   the paper position still closes — a potential paper/wallet divergence to
   check in investigations.
8. **Fresh trader-only install blocked by V103** (collector schema
   dependency); known release blocker.
9. **FIX-153 work is uncommitted** in the working tree (including V99-V106,
   B105 and `com/crypto/local`).

Items 2-7 are observations, not confirmed defects; whether each is intended
has not been confirmed with the user.
