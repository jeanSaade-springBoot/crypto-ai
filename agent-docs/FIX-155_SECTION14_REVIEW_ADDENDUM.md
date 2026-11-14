# FIX-155 Section 14 addendum — process rules before freeze

Date: 2026-10-10  
Status: REVIEW ADDITION (not hashed, not Production, not Replay implementation)  
Parent: `agent-docs/FIX-155-near-TP-profit-lock-replay-study-FINAL.pdf` (rev 1.1)  
This file is an addition. It does not rewrite the PDF.

These rules apply to Section 14 work only. Live profit-lock behavior is unchanged.

## Step 1 — Path-data audit (completeness, not existence)

For every past lock-exit that could be in the sample, measure coverage **across the whole window Candidate B would have waited**, not only “does a price row exist.”

Required per-trade output:

- Count of `market_price_event` observations **per minute** from the breach timestamp through the would-be window (N counted 1m candles + grace G, even though N and G are frozen only in Step 2 — the audit must still report holes at 1-minute resolution).
- Explicit holes: collector/candle outages (including 2026-10-09), reconnect gaps, missing minutes.
- Price kind of each source used: last-trade vs bid.

Hole policy is **not** chosen per trade in Step 3. Step 2 must freeze one rule for every gapped trade, for example:

- exclude the trade from the sample, or
- treat it as **A wins** (B ineligible / failed confirmation).

Nobody may pick exclude vs A-wins after seeing that trade’s P&L.

### Fill prices (same kind for A and B)

**PROJECT EVIDENCE:** live protection persists `market_price_event.price` from Binance kline field `c` (current kline last/close), not the bid (`BinanceKlineService` → `MarketPriceEventService`, source `BINANCE_KLINE_DIRECT_FIX153` / live-close).

Order-book snapshots exist under FIX-112C (`order_book_snapshot`). Historical spread is **not** available for the full paper history; persistence is later-period only. Step 1 must report the first usable snapshot time and coverage. Do not assume 5-second spacing without measuring it.

Whatever fill convention Step 2 freezes, **A and B must be filled at the same kind of price**. If Production A exits were last-trade prints, B must not be simulated on the bid (that would penalise B). If a spread is modeled, apply it **identically** to both sides.

### Fresh quality vs empty history

Candidate B requires fresh, `data_quality_valid = true` 1h (and 5m) evidence.

**PROJECT EVIDENCE:** `trade_signal.data_quality_valid` is nullable; historical rows stay `NULL`; they are not backfilled (`V107` / FIX-154). Pre-FIX-154 1h rows therefore **fail** “quality-valid” as stored.

Step 1 must count: lock-exits before vs after FIX-154 quality persistence went live.

Step 2 must freeze **one** history rule **before** the blinded count:

- **recompute** a timestamp-safe quality flag from contemporaneous candle evidence, with a written method, or
- **sample starts** only after FIX-154 quality writes exist in Production.

If this is left open, Step 3 can return near-zero eligible events for a bookkeeping reason, not because B is rare.

## Step 2 — Freeze everything (before any count)

In addition to N, G, fees, giveback, ATR/tolerance, momentum/structure, and statistical method, the frozen file must also fix:

| Item | Must freeze |
|---|---|
| Sample membership | Which exit codes count as a lock-exit (`PROFIT_LOCK_EXIT` and any listed variants); date range; hole/gap exclusion rule from Step 1 |
| Quality history | Recompute vs post-FIX-154 sample start |
| Decision rule | Minimum sample size; primary metric (fee-adjusted P&L per trade; mean or median); how large B’s advantage must be to win |
| SHIB `#89469` | **Out of the evaluation sample.** Report separately as the diagnostic fixture that inspired B. Including it lets the rule grade itself. |
| Fingerprint | Commit the freeze file, then **append** its SHA-256 to `codex-docs/CHANGELOG_AI.md` (and the matching Claude changelog if that process is in use). Addition only; never rewrite an older changelog entry. |

A small Step 3 count must not be allowed to change any of the above.

Do not hash until this freeze file has values in every TO BE FROZEN box **and** has been reviewed.

## Step 3 — Blinded eligibility count

The counting query must **not** return any price, high, or candle after each trade’s **exit** timestamp. Return only:

- eligibility flags (in/out, and why),
- whether the fee-adjusted emergency floor would bind at the breach.

If later prices are not in the result set, nobody can peek at “what happened next,” even by accident.

If the count is **below** the frozen minimum: conclusion is already fixed — **keep selling at the lock**, collect more history under the **same** frozen file. Do not widen 85%, quality, freshness, or hole rules to grow the sample.

## What this addendum does not do

- Does not fill numeric TO BE FROZEN values.
- Does not compute or record a SHA-256 yet.
- Does not change Production or start Replay implementation.
- Does not treat SHIB `#89469` as validation evidence.
