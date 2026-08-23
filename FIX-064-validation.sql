/*
 FIX-064 validation — raw closed-candle exhaustion vs winning controls.
 DB/Binance timestamps are UTC. KSA columns add +3 hours for review only.

 Failures under investigation:
   ENA wallet 703, PEPE wallet 756, SUI wallet 802
 Winning controls:
   BNB 776/788/790, PEPE 833, XLM 811/859, SHIB 814/869

 IMPORTANT: WEAK_UPTREND and ACCUMULATED_EVIDENCE are diagnostic columns only.
 They are NOT part of the FIX-064 rejection condition.
*/
WITH buys AS (
    SELECT
        wt.id AS wallet_id,
        wt.symbol,
        wt.signal_id,
        wt.execution_reason,
        wt.executed_at AS buy_time_utc,
        DATE_ADD(wt.executed_at, INTERVAL 3 HOUR) AS buy_time_ksa,
        wt.price_usdt AS execution_price,
        ts.candle_open_time,
        ts.generated_at AS signal_time_utc,
        ts.decision,
        ts.original_decision,
        ts.total_score,
        ts.confidence_score,
        ts.market_regime,
        ts.selected_strategy,
        ts.atr_at_signal,
        ts.latest_price AS signal_price
    FROM wallet_trade wt
    JOIN trade_signal ts ON ts.id = wt.signal_id
    WHERE wt.id IN (703,756,802,776,788,790,833,811,859,814,869)
      AND wt.side = 'BUY'
      AND wt.status = 'EXECUTED'
), raw AS (
    SELECT
        b.*,
        c.open_price,
        c.high_price,
        c.low_price,
        c.close_price,
        c.close_time,
        (
            SELECT pc.close_price
            FROM candle pc
            WHERE pc.symbol = b.symbol
              AND pc.interval_code = '1m'
              AND pc.closed = 1
              AND pc.open_time < c.open_time
            ORDER BY pc.open_time DESC
            LIMIT 1
        ) AS previous_close,
        (
            SELECT MAX(rc.high_price)
            FROM candle rc
            WHERE rc.symbol = b.symbol
              AND rc.interval_code = '1m'
              AND rc.closed = 1
              AND rc.close_time <= b.signal_time_utc
              AND rc.open_time >= c.open_time - INTERVAL 3 MINUTE
        ) AS recent_4_high
    FROM buys b
    LEFT JOIN candle c
      ON c.symbol = b.symbol
     AND c.interval_code = '1m'
     AND c.open_time = b.candle_open_time
     AND c.closed = 1
)
SELECT
    r.wallet_id,
    r.symbol,
    r.signal_id,
    r.execution_reason,
    r.buy_time_ksa,
    r.decision,
    r.total_score,
    r.confidence_score,
    r.market_regime,

    r.open_price,
    r.high_price,
    r.low_price,
    r.close_price,
    r.previous_close,
    r.recent_4_high,
    r.atr_at_signal,

    ROUND(
        CASE WHEN r.high_price > r.low_price
             THEN ((r.close_price - r.low_price) / (r.high_price - r.low_price)) * 100
             ELSE 50 END,
        2
    ) AS close_location_pct,

    ROUND(
        CASE WHEN r.atr_at_signal > 0
             THEN GREATEST(r.open_price - r.close_price, 0) / r.atr_at_signal
             ELSE NULL END,
        4
    ) AS bearish_body_atr,

    ROUND(
        CASE WHEN r.atr_at_signal > 0
             THEN GREATEST(r.high_price - r.close_price, 0) / r.atr_at_signal
             ELSE NULL END,
        4
    ) AS fade_from_candle_high_atr,

    ROUND(
        CASE WHEN r.atr_at_signal > 0
             THEN GREATEST(r.recent_4_high - r.close_price, 0) / r.atr_at_signal
             ELSE NULL END,
        4
    ) AS fade_from_recent_4_high_atr,

    ROUND(
        CASE WHEN r.atr_at_signal > 0
             THEN (r.execution_price - r.signal_price) / r.atr_at_signal
             ELSE NULL END,
        4
    ) AS execution_vs_signal_atr,

    CASE WHEN r.close_price < r.open_price THEN 1 ELSE 0 END AS red_candle,
    CASE WHEN r.previous_close IS NOT NULL AND r.close_price < r.previous_close THEN 1 ELSE 0 END AS close_below_previous,

    /* Mirrors FIX-064's two conservative invalidation signatures. */
    CASE
        WHEN r.atr_at_signal > 0
         AND r.close_price < r.open_price
         AND r.previous_close IS NOT NULL
         AND r.close_price < r.previous_close
         AND (GREATEST(r.open_price - r.close_price,0) / r.atr_at_signal) >= 0.25
         AND (CASE WHEN r.high_price > r.low_price
                   THEN (r.close_price-r.low_price)/(r.high_price-r.low_price)
                   ELSE 0.5 END) <= 0.45
        THEN 1 ELSE 0
    END AS fix064_bearish_rejection,

    CASE
        WHEN r.atr_at_signal > 0
         AND r.previous_close IS NOT NULL
         AND r.close_price < r.previous_close
         AND (GREATEST(r.recent_4_high-r.close_price,0) / r.atr_at_signal) >= 0.75
         AND (CASE WHEN r.high_price > r.low_price
                   THEN (r.close_price-r.low_price)/(r.high_price-r.low_price)
                   ELSE 0.5 END) <= 0.55
        THEN 1 ELSE 0
    END AS fix064_exhausted_pop,

    s.id AS sell_wallet_id,
    s.execution_reason AS sell_reason,
    s.price_usdt AS sell_price,
    ROUND(((s.price_usdt-r.execution_price)/r.execution_price)*100,4) AS raw_return_pct

FROM raw r
LEFT JOIN wallet_trade s
  ON s.id = (
      SELECT MIN(s2.id)
      FROM wallet_trade s2
      WHERE s2.symbol = r.symbol
        AND s2.side = 'SELL'
        AND s2.status = 'EXECUTED'
        AND s2.executed_at > r.buy_time_utc
  )
ORDER BY r.buy_time_utc;
