# FIX-029 — Trade Path human-readable decision meaning

## Scope

Diagnostic/UI only. No Production BUY/SELL logic, scoring thresholds, ATR logic, position management, wallet execution, or Replay behavior is changed.

## Scenario

PEPEUSDT signal #108246 at approximately 11:14 KSA was WATCH 66 with trend 19, volume 7, momentum 13, base technical 39/60, WEAK_UPTREND and higher-timeframe support. The View Path displayed these fields but did not state the practical conclusion the user needed: direction and momentum were supportive, but participation/confirmation was still insufficient.

Six minutes later #108276 became STRONG_BUY 86 with trend 23, volume 16, momentum 12 and base technical 51/60. The path should make the difference between these two states immediately understandable.

## Change

Each sequential View Path node now begins with a prominent **What this means** sentence derived from the persisted evidence already shown in the node.

Examples:

- WATCH + supportive trend/momentum + weak volume: **Direction and momentum look good, but participation/confirmation is not strong enough yet.**
- WATCH + good technical evidence + mixed HTF: technical evidence is bullish but higher-timeframe confirmation is still mixed.
- BUY: explains that combined technical/context evidence is sufficient for a BUY.
- STRONG_BUY: explains that trend, momentum and participation align strongly and whether HTF confirms.
- ATR WAIT: explains that the setup has evidence but current price is too extended and should not be chased.
- Blocked entry: explains the actual persisted veto/blocker.
- SELL -> NEUTRAL: explains that raw bearish evidence was neutralized by contextual checks.

## Safety

The interpretation function is browser-only and read-only. It receives the same persisted data already rendered in Trade Inspector and returns text. It cannot alter a TradeSignal, ExecutionOpportunity, WalletTrade, PaperPosition, PositionAnalysis, ProductionExitAudit or Replay record.

## Validation

- `node --check src/main/resources/static/js/trade-inspector.js`
- PEPE #108246 should show the WATCH participation/confirmation explanation.
- PEPE #108276 should show the STRONG_BUY aligned-evidence explanation.
- Blocked and ATR-wait path nodes should explain the blocker instead of implying an actionable entry.
