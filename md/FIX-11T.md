# FIX-11T — Production + Replay Near-TP Failure Protection

## Status

Implemented as a controlled Production behavior change and mirrored into Replay through one shared policy class.

## Objective

Protect part of an already-open profitable long position when price nearly reaches Take Profit but the TP attempt genuinely fails. This fix is **post-entry only**: it does not add any BUY rejection path, change initial entry sizing, reduce trade frequency, or modify existing signal qualification.

## Shared rule

`NearTpFailureProtectionPolicy` is the single business-rule authority used by both Production and Replay.

1. **INACTIVE → NEAR_TP_ARMED** when price reaches at least **90%** of the planned `entry → takeProfit` distance.
2. Track the best price after arming.
3. **NEAR_TP_ARMED → NEAR_TP_REJECTION_DETECTED** when price gives back at least **20%** of the planned TP distance from that best price.
4. The 20% rejection is **observation only**. It never sells by itself.
5. If price recovers back inside the same 20% rejection boundary, rejection is cleared and the bearish streak resets.
6. A partial harvest is eligible only when all of these are true together:
   - the rejection is still active;
   - the latest 1m evidence is fresh and at-or-before the evaluation time;
   - **two distinct consecutive fresh 1m signals** have `originalDecision = SELL/STRONG_SELL`;
   - the latest 5m evidence is fresh and at-or-before the evaluation time;
   - the 5m `originalDecision` is **not** `BUY/STRONG_BUY`.
7. Missing, future, or stale evidence always means **HOLD**. It can never confirm a sell.
8. On confirmation, calculate **one 50% partial harvest of CURRENT held quantity**.
9. Before executing, read the symbol's current Binance `NOTIONAL` / `MIN_NOTIONAL` minimum from `/api/v3/exchangeInfo`.
10. If the requested 50% harvest notional is **below Binance's minimum executable amount**, do nothing: no larger/full fallback sell is invented, the Near-TP state returns to rejection monitoring, the consumed bearish-confirmation counter is reset, and all existing position management continues normally.
11. If Binance minimum-order metadata is unavailable, fail safe in the same way and do not harvest.
12. Remaining quantity continues through all existing TP, SL, Profit Lock and normal signal-driven management.
13. A position can use Near-TP harvest only once in its lifetime.

## Freshness

- 1m maximum age: 2 minutes.
- 5m maximum age: 20 minutes, matching the existing Production execution-intelligence 5m freshness horizon.
- Future-dated signals are rejected as evidence.

## Existing exit priority preserved

Near-TP is evaluated only after the existing live protection path has found no higher-priority action for that evaluation cycle:

- existing Take Profit / continuation checkpoint;
- Stop Loss;
- Profit Lock handling;
- normal PositionExitPolicy exit;
- Near-TP Failure Protection last.

The fix does not delay or preempt any existing terminal exit.

## Production execution safety

`WalletAutoExecutionService.executeNearTpPartialHarvest(...)`:

- obtains the existing pessimistic lock on the open `wallet_managed_position`;
- uses a stable idempotency key: `POSITION:<positionId>:NEAR_TP_PARTIAL_HARVEST`;
- relies on the existing unique `wallet_trade.execution_key` constraint;
- performs wallet debit/credit, managed-position quantity/cost update, Near-TP state update and `wallet_trade` persistence in the same transaction;
- checks the requested 50% order value against Binance's current symbol `NOTIONAL` / `MIN_NOTIONAL` minimum before any wallet mutation;
- if below minimum (or the Binance minimum cannot be obtained), performs **no sell** and leaves normal management running;
- never promotes a below-minimum partial harvest into a larger or full exit;
- persists the **actual sold quantity** used by the wallet execution path;
- keeps the managed position OPEN;
- never reduces `allocated_position_percent`.

### Why allocation percent is deliberately not reduced

Progressive Position Building sizes future adds from cumulative `allocatedPositionPercent` (25 → 50 → 100), not from current coin quantity. Leaving this percentage unchanged after a partial harvest permanently reserves the harvested exposure gap inside later progressive targets. This prevents the harvested amount from simply being restored by the next confirmation/trend add without inventing a second sizing formula.

## Risk-geometry reset

Before a harvest has been used, Near-TP arming/rejection state is reset when:

- Take Profit is extended; or
- a progressive add changes average entry / risk geometry.

After a harvest has been used, the terminal Near-TP state and one-harvest cap are preserved.

## Closed-trade reporting boundary

The Production partial harvest is persisted as a real `wallet_trade` SELL because wallet balances and realized P/L must remain auditable. It is **not** a terminal position close. `WalletTradeRepository` therefore excludes `execution_reason = NEAR_TP_PARTIAL_HARVEST` from CLOSED-position/Trade-Inspector queries while `totalRealizedPnl()` continues to include the partial realization in wallet financial truth.

## `paper_position` synchronization

Production has both `wallet_managed_position` and `paper_position` state holders. After the partial harvest:

- `paper_position.quantity` is reduced to the remaining quantity;
- partial realized P/L is accumulated in `paper_position.realized_pnl`;
- later terminal closes add the remaining-leg P/L instead of overwriting already-realized partial P/L.

This prevents later signal-driven close reporting from using the pre-harvest quantity.

## Replay parity

`ShadowProductionReplayService` uses the same `NearTpFailureProtectionPolicy`.

- Exact-price Replay evaluates Near-TP from persisted Production `market_price_event` ordering.
- Legacy signal-price fallback uses the same shared policy only when the historical signal price is authoritative for that position.
- Partial harvest writes a `wallet_execution_test` SELL with code `NEAR_TP_PARTIAL_HARVEST`, reduces shadow quantity/cost, credits shadow cash, accumulates realized P/L, and keeps `wallet_position_test.status = OPEN`.
- Replay keeps `position_percent` unchanged for the same anti-rebuy behavior as Production.
- Replay uses the same `BinanceMinimumExecutionPolicy`; a 50% harvest below the symbol's Binance minimum is skipped exactly as in Production and normal Replay management continues.

## Persisted fields

Production `wallet_managed_position` and Replay `wallet_position_test` / archive carry:

- `near_tp_state`
- `near_tp_best_price`
- `near_tp_bearish_streak`
- `near_tp_last_1m_signal_id`
- `near_tp_harvest_used`
- `near_tp_harvested_quantity`

Flyway migration: `V81__near_tp_failure_protection.sql`.

## Server logs

Search for `FIX-11T` to review live and Replay activity. Key events include:

- `NEAR_TP_ARMED`
- `NEAR_TP_REJECTION_DETECTED`
- `NEAR_TP_RECOVERY`
- `HOLD_5M_BULLISH_SUPPORT`
- `HOLD_1M_BEARISH_NOT_PERSISTENT`
- `HOLD_MISSING_OR_STALE_EVIDENCE`
- `NEAR_TP_FAILURE_CONFIRMED`
- `NEAR_TP_PARTIAL_HARVESTED`
- `NEAR_TP_HARVEST_SKIPPED` (`BELOW_BINANCE_MINIMUM` / `BINANCE_MINIMUM_UNAVAILABLE`)

INFO logs are emitted for state/evidence changes and actual harvests; repetitive same-state holds are DEBUG to avoid flooding the server log on every market-price tick.

## Historical design evidence

The naive `90% arm + 20% giveback => sell 50%` rule was rejected because it would have partially harvested 7 of 20 successful TAKE_PROFIT controls. TAO #806 further proved that even a very deep giveback and weak 1m evidence can recover while strong 5m bullish support remains. SUI #889 showed the failure pattern this rule targets: persistent underlying 1m SELL evidence accompanied by weakening/lost higher-timeframe support.

## Regression protection

`NearTpFailureProtectionPolicyTest` covers:

- missing 5m evidence never confirming harvest;
- two distinct fresh bearish 1m signals plus lost 5m support confirming eligibility;
- strong 5m original BUY support blocking harvest;
- recovery inside the rejection boundary resetting bearish persistence.
- Binance minimum-notional guard: below minimum skips, exactly-at-minimum permits, unavailable exchange metadata fails safe.

No test result is claimed in this note until the project test suite is actually run.
## Build correction
- Added the missing `MathContext MC = MathContext.DECIMAL64` constant to `WalletAutoExecutionService`, which is required by the Near-TP partial-harvest `BigDecimal` calculations. This is a compile-only correction and does not change FIX-11T trading behavior.

