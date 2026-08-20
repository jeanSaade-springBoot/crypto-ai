# FIX-020–FIX-023 Trading Corrections

## Time convention used by these regressions
Binance/database market timestamps are UTC. KSA time is database/Binance time + 3 hours.

## FIX-020 — Completed position evidence boundary
- Scenario: ENAUSDT, 2026-08-20.
- Good scout: DB 18:19 / KSA 21:19 at 0.1069.
- Good TP: DB 18:43 / KSA 21:43 at 0.1082.
- Bad stale re-entry: DB 18:44 / KSA 21:44 at 0.1082.
- Rule: every terminal position close consumes the active BUILDING/WEAKENING/BLOCKED/CONFIRMED opportunity. Pre-exit evidence cannot finance a new position.
- Production and ShadowProductionReplayService call the same ExecutionIntelligenceService lifecycle method.

## FIX-021 — Accumulated evidence HTF authority parity
- Scenarios: BICOUSDT #102491 and ETHUSDT #103638.
- Rule: accumulated evidence is memory, not independent execution authority.
- The current 5m/1h context must pass the same configured CONSERVATIVE/BALANCED/AGGRESSIVE profile as a normal BUY.
- Insufficient authority keeps the opportunity BUILDING with ACCUMULATED_AUTHORITY_WAIT.
- Accumulated position sizing is capped by the profile's granted position percentage.

## FIX-022 — Ultra-close shrinking ask-wall exception
- Scenario: ETHUSDT around DB 15:11 / KSA 18:11 on 2026-08-20.
- Historical wall: 2294.04, distance 0.097%, strength 90/100, size change -16.6%.
- Rule: TARGET_BLOCKED remains negative liquidity evidence, but a wall <=0.10% away and already shrinking >=10% is not an automatic hard veto.
- Stable/growing/non-shrinking strong walls retain the existing hard veto.

## FIX-023 — Fresh 5m confirmation wake-up
- Scenario: ETHUSDT #102889/#102890 around DB 15:24 / KSA 18:24.
- Rule: a fresh 5m non-BUY -> BUY transition may wake an existing unexecuted opportunity only when latest 1m is <=2 minutes old, supportive, final-entry-allowed, ATR-immediate, and free of hard risk vetoes.
- The configured HTF profile must pass.
- 5m never owns wallet execution price; latest 1m owns price/SL/TP.
- Initial allocation is capped at 25% before Entry Quality.
- Repeated 5m BUYs and already-open positions cannot use this path.

## Suggested Proven replay windows
- ENA FIX-020: DB 2026-08-20 18:10–19:05 / KSA 21:10–22:05.
- BICO FIX-021: DB 2026-08-20 13:30–14:25 / KSA 16:30–17:25.
- ETH FIX-021 later-entry case: DB 17:20–18:10 / KSA 20:20–21:10.
- ETH FIX-022/FIX-023 early-entry case: DB 14:55–15:35 / KSA 17:55–18:35.
