# Crypto AI Trader — Trading Rules (Claude)

Maintained by Claude. Describes trading behavior as implemented in the
working tree on 2026-10-09 (read-only review). Values are defaults from code
or `application.yml`; verify current configuration before relying on them.
Paper trading only.

## 1. Signal admission

- Only closed candles are analysed (1m, 5m, 1h for enabled coins).
- Data quality (`CandleDataQualityService`): at least 210 closed candles,
  `maximum-missing-candles: 0`, valid OHLCV. Any gap -> `DATA_QUALITY_BLOCKED`.
  Do not relax this to work around ingestion gaps; restore the missing candles.
- Freshness after close (`SharedEventPolicy` / `SignalProcessingFreshness`):
  1m 90s, 5m 2 min, 1h 5 min, 4h 10 min, 1d 30 min. Stale input produces a
  `HISTORICAL_ONLY` signal that never trades.
- Every analysis persists a `trade_signal`, including neutral/no-entry ones.
- `trading.scheduled-analysis-enabled: false` (legacy recovery scheduler off).

## 2. Scoring (`AnalysisService`)

Base categories: trend (max 25), momentum (15), volume (20), sentiment (15),
fundamentals (10). Unavailable sentiment/fundamentals are removed from the
maximum. Indicators: EMA20/50/200, SMA20, RSI14, MACD, Bollinger(20,2),
ATR14, volume SMA20, RVOL(20).

Regime (`MarketRegimeService` + `RegimeStateService`): UNKNOWN, BREAKOUT /
BREAKOUT_CANDIDATE, HIGH_VOLATILITY, STRONG/WEAK UP/DOWN, RANGE.

Strategy (`MarketStrategyService`):
- NO_TRADE if data invalid or regime UNKNOWN.
- DEFENSIVE if risky liquidity, crowded futures, sentiment coverage < 50%,
  higher-timeframe STRONG_CONFLICT, BTC STRONG_CONFLICT with influence >= 0.70,
  or low regime confidence.
- Otherwise TREND_FOLLOWING, RANGE_MEAN_REVERSION or BREAKOUT; score rescaled
  to 0-100 with per-profile thresholds (`DynamicStrategyProperties`).
- ATR `NO_ENTRY` downgrades BUY to WATCH.

## 3. Final decision chain (`FinalDecisionService`)

Ordered: STRATEGY_SCORE -> DATA_QUALITY -> ATR_RISK -> RANGE_ENTRY_LOCATION ->
MULTI_TIMEFRAME -> BTC_CONTEXT -> DERIVATIVES_POSITIONING -> ORDER_BOOK. Each
stage can VETO, DOWNGRADE, PASS or be UNAVAILABLE; `entryAllowed` is ANDed.
`primaryBlockingStage` = first blocker. Confidence weights: regime 25%, data
15%, timeframe 20%, BTC 15%, liquidity 12%, derivatives 8%, sentiment 10%;
capped at 49 when entry is blocked.

Multi-timeframe context: 1m uses 5m+1h; 5m uses 15m+1h; 1h uses 4h+1d.

BTC context (`BtcMarketContextService`): not applicable to BTCUSDT. Freshness
measured from BTC candle close to evaluation time (1m 120s, 5m 600s, 1h
7200s...); any freshness failure -> `STALE_CONTEXT`, entry not allowed. Fewer
than 120 samples -> LEARNING. Opposing direction with positive correlation ->
CONFLICT; |corr| >= 0.70 -> STRONG_CONFLICT (WATCH, veto if configured).

## 4. Entry (execution time)

- Only 1m signals can open a position.
- `TradeExecutionValidationService.validateBuy`: requires fresh 5m (<= 20 min)
  and 1h (<= 3 h) context; order: BUY_CONTINUATION, MISSING_5M_CONFIRMATION,
  MISSING_1H_CONTEXT, `5M_BEARISH_VETO`, `1H_BEARISH_VETO`, then profile
  (CONSERVATIVE/BALANCED/AGGRESSIVE). BALANCED allows a 25% position for 5m
  NEUTRAL + 1h bullish (confidence >= 72, entry quality >= 70, BTC not
  CONFLICT/STRONG_CONFLICT/STALE) and for 5m NEUTRAL + 1h WATCH.
- `OpportunityConsolidationService`: same vetoes, then >= 3 consecutive fresh
  1m BUYs with average score >= 75 and confidence >= 70, else `QUALITY_DECAY`.
- `ExecutionIntelligenceService.evaluateBuyInternal` routes (progressive,
  retracement, continuation, HTF transition, ATR authority, direct BUY,
  consolidation, pressure probe, recovery, accumulated evidence) each through
  entry-quality and anti-chase guards.
- `StopLossEvidenceService` (FIX-122): 1m candles closed before the last
  stop-loss are excluded as entry evidence.
- Max open positions and daily loss limit (realized P&L <= -balance x
  maxDailyLoss%) enforced.
- Fill price: latest canonical 1m `market_price_event` <= 15s old; no price ->
  no BUY. `revalidateAtExecutionPrice` rejects price <= stop or >= target and
  re-scores entry quality. `trade_signal.latestPrice` is never rewritten.

## 5. Sizing

- Paper position: risk = paperAccountBalance x riskPerTrade% x effective%;
  qty = risk / |price - SL|.
- Wallet (actual executed qty): `WalletExecutionSizingPolicy` — spend = daily
  budget x allocation% (total capped at 100%); daily budget = (USDT - reserve)
  / maxNewPositions. Rejects ALLOCATION_FULL, DAILY_POSITION_LIMIT,
  ZERO_BUDGET, INSUFFICIENT_USDT_ABOVE_RESERVE, INVALID_PRICE, ZERO_QUANTITY.
- The paper row adopts the wallet's executed quantity.
- `CryptoAccountConfigurationService` limits are not used in the automatic
  paper entry path.

## 6. Fees

Automatic wallet trades record `feeUsdt = 0` and net = gross. Fees are applied
only to manual wallet trades. Automated P&L is therefore gross of fees.

## 7. Live protection (every accepted 1m price, `LivePositionProtectionService`)

Priority:
1. Price >= TP: extend TP by +50% of entry->TP distance
   (`PositionContinuationPolicy`, TAKE_PROFIT_EXTENDED) or exit TAKE_PROFIT.
2. Price <= SL: STOP_LOSS (paper status STOPPED).
3. TP-extension rebase hard floor (entry x 1.0005): PROFIT_LOCK_HARD_EXIT.
4. Profit lock triggered: PROFIT_LOCK_HOLD / HTF_EXIT / BEARISH_CONFIRMATION /
   EXIT.
5. Normal exit: HTF_SELL_CONFIRMED / SELL_CONFIRMED.
6. Near-TP failure: partial harvest of 50% once (Binance minimum enforced),
   position stays open.

Profit lock (`ProfitLockPolicy`): activates at 70% progress to TP, initial
lock 40%, 10% steps, never below entry x 1.0005, only ratchets up.

Replay mechanical ordering: TP -> SL -> Profit Lock -> normal HTF exit.

## 8. Signal-driven handling of an open position

`PositionManagementService.analyze` is advisory except STOP_LOSS. A signal
price is usable only if the candle closed at/after the position open
(`PositionPriceAuthorityPolicy`). Order: TAKE_PROFIT -> profit lock ->
STOP_LOSS -> PROFIT_LOCK_HARD_EXIT -> SELL/STRONG_SELL (suppressed while
profit lock active; must pass `validateSell`: bearish 5m, no `1H_BULLISH_VETO`).
Progressive add on 1m is possible; SL/TP only move up. Signal-path closes use
`signal.latestPrice`; live closes use the tick price.

## Pending (not implemented): FIX-154

Historical bearish 1h may block a current 1m BUY only when fresh, quality-valid
and strong (`NOT_TRUSTED` / `SOFT_CONFLICT` / `HARD_BEARISH_CANDIDATE`); hard
veto needs fresh quality-valid bearish 5m. Legacy `5M_BEARISH_VETO` unchanged.
