# FIX-026 — Recovery Transition Entry

## Scenario
ENAUSDT on 20 Aug 2026, 12:55–13:04 KSA (09:55–10:04 Binance/DB UTC). The system moved from STRONG_SELL 15 to WATCH 75 while the market was transitioning from selling into absorption and renewed aggressive buying.

## Production behavior added
A new **RECOVERY_TRANSITION_ENTRY** route permits only a 25% probe when all of the following are true:

- current 1m state is WATCH or NEUTRAL (normal BUY keeps priority);
- current score >= 72, confidence >= 68, trend >= 20, momentum >= 14;
- a bearish 1m state with score <= 35 occurred within the prior 10 minutes;
- fresh 5m and 1h context exist and are not bearish;
- FinalDecision, strategy, confluence, ATR-immediate, BTC, liquidity/order-book and derivatives gates all still allow entry;
- closed 1m candles show prior strong taker-buy absorption, a seller-dominant pullback/test, then three consecutive >=70% taker-buy recovery candles with rising closes and >=0.30% recovery.

The route does **not** change TradeSignal scoring, RANGE_MEAN_REVERSION thresholds, market-regime classification, or normal BUY authority. It is a small state-transition probe only.

## No future leakage
`RecoveryTransitionService` reads candles using `close_time <= signal.generated_at`. For signal #101305 at 13:04:51 KSA, the detector may use the completed 13:01, 13:02 and 13:03 KSA candles but not the still-open 13:04 candle and not the later 13:06–13:07 expansion.

## Production / Proven parity
`ShadowProductionReplayService` contains no recovery formula. Replay calls the same `ExecutionIntelligenceService.evaluateBuy(...)` as Production, which calls the same `RecoveryTransitionService`. Historical Replay therefore uses the same thresholds, hard gates, candle query and 25% sizing authority.

## Regression anchor
Suggested Proven run: **KSA 12:45–13:15**, equivalent DB/Binance **09:45–10:15 UTC** on 20 Aug 2026.

Expected at #101305: `RECOVERY_TRANSITION_ENTRY / ABSORPTION_RECOVERY_PROBE`, max 25% initial exposure, using only information available by that timestamp.
