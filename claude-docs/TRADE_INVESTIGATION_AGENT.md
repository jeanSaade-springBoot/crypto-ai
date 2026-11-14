# Trade Investigation Procedure (Claude)

How Claude investigates one Crypto AI Trader trade. Follows the trade
investigation rules in `AGENTS.md`. Investigations are read-only: no change to
code, configuration, services, database records, Replay state or deployment
unless the user requests a fix.

## 1. Confirm scope

- Application, environment (local / test / production), DB host/schema.
- Symbol and one trade reference: `paper_position.id`, `wallet_trade.id`, or
  symbol + bounded entry window.
- Ingestion mode (`shared-market.mode`; OFF = FIX-153 local candles) and
  version/FIX level.
- Read the actual schema before choosing tables/columns. Never display
  secrets.

## 2. Evidence (bounded, read-only queries)

| Table | Establishes |
|---|---|
| `paper_position` | lifecycle, entry/exit, SL/TP, status, close reason |
| `wallet_trade` | executed qty/prices, `execution_key`, linked signal (fees are 0 for automatic trades) |
| `wallet_managed_position` | managed SL/TP, highest price, profit-lock / near-TP state |
| `trade_signal` | decision, strategy, scores, `entryAllowed`, `primaryBlockingStage`, `signalMode`, context JSON |
| `signal_processing_work` | claim, attempts, owner, status, expiry |
| `execution_opportunity` | entry route and opportunity state |
| `candle` / `technical_indicator` | inputs available at the time; check `closed`, provenance, gaps |
| `market_price_event` | intra-candle protection observations and status |
| `production_exit_audit` | actual close trigger |
| `position_analysis` | advisory exit recommendations |

Load large JSON fields only when needed.

## 3. Timeline (UTC stored -> show UTC and KSA, UTC+03:00)

`candle close -> signal saved -> work claimed -> entry committed -> price
observations -> exit trigger -> exit committed`. Measure each latency and mark
missing stages explicitly.

## 4. Entry assessment (information available at entry only)

Check data quality (210 candles, zero gaps), freshness, signal mode, final
decision chain, 5m/1h validation and vetoes, BTC context, consolidation, entry
route, FIX-122 stop-loss evidence exclusion, risk/reward, sizing, fill price
vs `latestPrice` (fill from price event <= 15s old). Report execution
correctness and entry quality separately.

## 5. Protection and exit

Use only accepted `LEGACY`/`APPLIED` price events; treat `PENDING`,
`NON_CANONICAL`, `PROTECTION_NOT_APPLIED` as gaps/exclusions. Never substitute
candle OHLC for missing price evidence. Apply priority TP -> SL -> hard floor
-> profit lock -> HTF/normal exit -> near-TP harvest. Identify the first
observation that met the exit threshold and the resulting committed trade.

## 6. P&L

Recompute from `wallet_trade`: quantities, weighted prices, gross P&L,
partial harvests, and fees (computed separately since automatic fees are
recorded as 0). Flag paper vs wallet vs UI discrepancies.

## 7. Cause classification

Strategy decision, data freshness/coverage (e.g. reconnect gap), analysis
delay, execution failure, protection failure, lock/pool contention,
accounting/UI discrepancy, or insufficient evidence. Use Replay only as a
controlled comparison with stored price events and only if the user agrees
(it creates Replay state).

## Report

1. What happened (identity, UTC/KSA timeline).
2. Why the system entered and exited.
3. Whether behavior followed the intended rules.
4. Confirmed defects and missing evidence.
5. Recommended correction and regression checks, if needed.

Record significant findings in `claude-docs/INVESTIGATION_LOG.md`.
