# FIX-027 — Trade Inspector one-look sequential state path

## Scope
Diagnostic/UI-only change inside **Trade Inspector → View Path**. No BUY, SELL, scoring, position-management, Production, Proven or Replay trading rule is changed.

## Problem
FIX-024/025 returned the right persisted evidence but displayed it in several separate sections. The path was harder to read than an ERD/state machine, and pre-entry recovery phases were not visible because lifecycle signals began at wallet BUY.

## Solution
View Path now renders one sequential state-machine flow with KSA timestamps and elapsed time between nodes. Every evidence-bearing node shows a compact **Component / Result / Interpretation** table containing:

- Displayed score
- Base technical raw / maximum score
- Trend
- Momentum
- Closed-candle BUY pressure
- Closed-candle volume
- RSI
- MACD
- Decision / regime / strategy
- 5m / 1h authority
- ATR entry action and position recommendation
- Hard veto / blocker when present

For recovery trades the diagnostic can show, when persisted evidence supports the phases:

`STABILIZING → RECOVERING → RECOVERY_PROBE → EXPANSION_CONFIRMED → NORMAL_POSITION → EXIT`

The read-only endpoint now includes a bounded 45-minute pre-entry signal window so STABILIZING/RECOVERING are based on real persisted pre-BUY signals. Each signal snapshot is enriched with its exact persisted candle using `symbol + interval + candle_open_time`, including volume, trade count and taker BUY percentage. No later candle is consulted.

## Safety / parity
- Diagnostic only; no trading method is called or mutated.
- Production and Replay behavior remain unchanged.
- Recovery phase names are UI summaries of persisted evidence, not new trading decisions.
- Raw ordered entry decision checks remain available under a collapsed section for deep debugging.
- All displayed times are KSA; stored Binance/DB timestamps remain unchanged.
