# FIX-11S — Replay window all-trades chart with Production overlay

## Scope

Observability/UI only. No Production or Replay trading behavior is modified.

## Behavior

The Replay detail view now provides **View All Trades on Chart** for the complete persisted Replay run window. The chart displays every isolated `wallet_execution_test` BUY/SELL execution, including split entries and exits. It also reads real `wallet_trade` executions for the same symbol and exact run window and overlays them when present.

Replay and Production markers use different color families. Production markers can be hidden/re-shown with **Show Production** without changing or re-running any trade logic.

## Empty Production safety

Production executions are returned as an empty list when none exist. The chart continues to render Replay executions and historical candles normally. If both sources have no executions, the historical price chart still remains valid.

## Non-goals

No signal generation, opportunity lifecycle, execution intelligence, entry quality, allocation, stop loss, take profit, position management, wallet execution, or Production behavior is changed.
