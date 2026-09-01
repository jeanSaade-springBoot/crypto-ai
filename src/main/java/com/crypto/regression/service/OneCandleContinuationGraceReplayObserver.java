package com.crypto.regression.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.market.service.MarketPriceEventService;
import com.crypto.position.service.PositionContinuationPolicy;
import com.crypto.position.service.PositionExitPolicy;
import com.crypto.position.service.ProfitLockPolicy;
import com.crypto.service.TradeExecutionValidationService;
import com.crypto.wallet.domain.WalletSettings;
import com.crypto.wallet.repository.WalletSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * FIX-11Q — Replay-only one-candle continuation grace experiment.
 *
 * The Production-parity Shadow position always remains authoritative. This observer starts a
 * separate counterfactual position only after the existing PositionContinuationPolicy has already
 * returned FAIL at a reached take-profit checkpoint. Existing continuation PASS paths therefore
 * remain untouched and continue to extend take profit exactly as before.
 *
 * The counterfactual holds through one specific NEUTRAL 1m candle without moving TP. The grace is
 * tied to that candle (not one live-price tick), may be consumed only once per position lifetime,
 * and normal shared continuation/exit policies resume after a new 1m candle arrives.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OneCandleContinuationGraceReplayObserver {
    private static final MathContext MC = MathContext.DECIMAL64;

    private final JdbcTemplate jdbcTemplate;
    private final PositionContinuationPolicy continuationPolicy;
    private final PositionExitPolicy exitPolicy;
    private final ProfitLockPolicy profitLockPolicy;
    private final TradeExecutionValidationService executionValidationService;
    private final WalletSettingsRepository walletSettingsRepository;

    private final Map<Long, Variant> activeByRun = new HashMap<>();
    private final Map<Long, java.util.Set<Long>> consumedByRun = new HashMap<>();

    public synchronized void startRun(long runId, String symbol) {
        activeByRun.remove(runId);
        consumedByRun.put(runId, new java.util.HashSet<>());
        log.info("FIX11Q_GRACE_START runId={} symbol={} mode=REPLAY_COUNTERFACTUAL productionMutation=false baselineReplayMutation=false", runId, symbol);
    }

    public synchronized void observeBaselineTakeProfitFailure(
            long runId, String symbol, long positionId, Instant observedAt, BigDecimal price,
            Instant entryTime, BigDecimal entryPrice, BigDecimal quantity, BigDecimal cost,
            BigDecimal stopLoss, BigDecimal takeProfit, BigDecimal highest,
            boolean profitLockActive, BigDecimal profitLockPrice,
            int entryScore, int entryConfidence, int entryTrend, int entryStructure,
            int entryMomentum, int entryVolume,
            TradeSignal current1m, TradeSignal fiveMinute, TradeSignal oneHour,
            String baselineExplanation) {

        if (activeByRun.containsKey(runId) || consumedByRun.getOrDefault(runId, java.util.Set.of()).contains(positionId)) return;
        if (!eligible(current1m, fiveMinute, oneHour, entryMomentum)) return;

        Instant candleIdentity = candleIdentity(current1m);
        if (candleIdentity == null) return;
        BigDecimal baselinePnl = price.multiply(quantity, MC).subtract(cost, MC);
        BigDecimal baselinePnlPct = percentage(entryPrice, price);
        BigDecimal safeHighest = highest == null ? price : highest.max(price);

        Variant v = new Variant(positionId, symbol, entryTime, entryPrice, quantity, cost, stopLoss,
                takeProfit, safeHighest, profitLockActive, profitLockPrice,
                entryScore, entryConfidence, entryTrend, entryStructure, entryMomentum, entryVolume,
                candleIdentity, current1m == null ? null : current1m.getId(), observedAt, price,
                baselinePnl, baselinePnlPct, price, price, current1m, fiveMinute, oneHour);
        activeByRun.put(runId, v);
        consumedByRun.computeIfAbsent(runId, k -> new java.util.HashSet<>()).add(positionId);

        jdbcTemplate.update("""
                INSERT INTO one_candle_continuation_grace_test
                (test_run_id,position_test_id,symbol,grace_at,grace_1m_signal_id,grace_candle_time,
                 price_at_grace,entry_price,take_profit_before_grace,take_profit_after_grace,
                 current_1m_decision,five_minute_decision,one_hour_decision,trend_score,trend_floor,
                 momentum_score,momentum_floor,baseline_action,baseline_exit_price,baseline_pnl_usdt,
                 baseline_pnl_percent,status,baseline_explanation)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, runId, positionId, symbol, Timestamp.from(observedAt), v.graceSignalId(), Timestamp.from(candleIdentity),
                price, entryPrice, takeProfit, takeProfit, name(current1m.getDecision()), name(fiveMinute.getDecision()),
                name(oneHour.getDecision()), current1m.getTrendScore(), trendFloor(entryTrend),
                current1m.getMomentumScore(), momentumFloor(entryMomentum), "TAKE_PROFIT_EXIT", price,
                baselinePnl, baselinePnlPct, "ACTIVE", baselineExplanation);

        log.info("FIX11Q_GRACE_ACTIVATED runId={} positionId={} symbol={} observedAt={} candle={} price={} tp={} current={} five={} one={} trend={}/{} momentum={}/{} action=HOLD_TARGET_UNCHANGED",
                runId, positionId, symbol, observedAt, candleIdentity, price, takeProfit,
                name(current1m.getDecision()), name(fiveMinute.getDecision()), name(oneHour.getDecision()),
                current1m.getTrendScore(), trendFloor(entryTrend), current1m.getMomentumScore(), momentumFloor(entryMomentum));
    }

    public synchronized void onSignal(long runId, TradeSignal signal, TradeSignal latest1m, TradeSignal latest5m, TradeSignal latest1h) {
        Variant v = activeByRun.get(runId);
        if (v == null || signal == null) return;
        if ("1m".equals(signal.getInterval()) && v.next1mDecision() == null && !sameGraceCandle(v, signal)) {
            v = v.withNext1m(name(signal.getDecision()));
        }
        if ("5m".equals(signal.getInterval()) && v.next5mDecision() == null && signal.getGeneratedAt() != null && signal.getGeneratedAt().isAfter(v.graceAt())) {
            v = v.withNext5m(name(signal.getDecision()));
        }
        if ("1h".equals(signal.getInterval()) && v.next1hDecision() == null && signal.getGeneratedAt() != null && signal.getGeneratedAt().isAfter(v.graceAt())) {
            v = v.withNext1h(name(signal.getDecision()));
        }
        activeByRun.put(runId, v.withContext(latest1m, latest5m, latest1h));
    }

    public synchronized void onPrice(long runId, String symbol, MarketPriceEventService.PriceEvent event,
                                     TradeSignal latest1m, TradeSignal latest5m, TradeSignal latest1h) {
        Variant v = activeByRun.get(runId);
        if (v == null || event == null || event.price() == null || !v.symbol().equals(symbol)) return;
        if (!event.observedAt().isAfter(v.graceAt())) return;

        BigDecimal price = event.price();
        v = v.withContext(latest1m, latest5m, latest1h).withPath(price);
        boolean sameGraceCandle = sameGraceCandle(v, latest1m);

        // Hard protection remains active even during grace; FIX-11Q suppresses only the baseline
        // TAKE_PROFIT exit caused by transient continuation failure.
        if (v.stopLoss() != null && price.compareTo(v.stopLoss()) <= 0) {
            close(runId, v, event.observedAt(), price, "STOP_LOSS", "Stop loss reached during/after grace."); return;
        }

        v = updateProfitLock(v, price);
        if (v.profitLockActive() && v.profitLockPrice() != null && price.compareTo(v.profitLockPrice()) <= 0) {
            BigDecimal hardFloor = v.entryPrice().multiply(BigDecimal.valueOf(1.0005), MC);
            if (price.compareTo(hardFloor) < 0) {
                close(runId, v, event.observedAt(), price, "PROFIT_LOCK_HARD_EXIT", "Profit-lock hard floor breached in FIX-11Q counterfactual."); return;
            }
            PositionExitPolicy.Evaluation lock = exitPolicy.evaluateProfitLockBreach(latest1m, latest5m, latest1h);
            if (lock.exit()) { close(runId, v, event.observedAt(), price, lock.code(), lock.explanation()); return; }
        }

        if (v.takeProfit() != null && price.compareTo(v.takeProfit()) >= 0) {
            if (sameGraceCandle) {
                activeByRun.put(runId, v); // hold entire candle; target intentionally unchanged
                return;
            }
            PositionContinuationPolicy.Evaluation continuation = continuationPolicy.evaluate(
                    latest1m, latest5m, latest1h, v.entryTrend(), v.entryStructure(), v.entryMomentum(), v.entryVolume(),
                    v.entryConfidence(), v.entryScore());
            if (continuation.extendTarget()) {
                BigDecimal distance = v.takeProfit().subtract(v.entryPrice());
                BigDecimal newTarget = v.takeProfit().add(distance.multiply(BigDecimal.valueOf(0.50), MC), MC);
                activeByRun.put(runId, v.withTakeProfit(newTarget));
                log.info("FIX11Q_VARIANT_TP_EXTENDED runId={} positionId={} oldTp={} newTp={} reason={}", runId, v.positionId(), v.takeProfit(), newTarget, continuation.explanation());
                return;
            }
            close(runId, v, event.observedAt(), price, "TAKE_PROFIT", continuation.explanation()); return;
        }

        if (!sameGraceCandle) {
            PositionExitPolicy.Evaluation normal = exitPolicy.evaluateNormalExit(latest1m, latest5m, latest1h);
            if (normal.exit()) { close(runId, v, event.observedAt(), price, normal.code(), normal.explanation()); return; }
            if (latest1m != null && "1m".equals(latest1m.getInterval()) && bearish(latest1m.getDecision())) {
                TradeExecutionValidationService.ValidationResult validated = executionValidationService.validateSell(latest1m);
                if (validated.allowed()) { close(runId, v, event.observedAt(), price, validated.code(), validated.explanation()); return; }
            }
        }
        activeByRun.put(runId, v);
    }

    public synchronized void finishRun(long runId, String symbol, Instant endTime) {
        Variant v = activeByRun.remove(runId);
        if (v != null) {
            BigDecimal pnl = v.lastPrice().multiply(v.quantity(), MC).subtract(v.cost(), MC);
            updateRow(runId, v, endTime, v.lastPrice(), "OPEN_AT_WINDOW_END", pnl, percentage(v.entryPrice(), v.lastPrice()));
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM one_candle_continuation_grace_test WHERE test_run_id=?", Integer.class, runId);
        log.info("FIX11Q_GRACE_SUMMARY runId={} symbol={} candidates={} mode=REPLAY_COUNTERFACTUAL", runId, symbol, count == null ? 0 : count);
        consumedByRun.remove(runId);
    }

    private boolean eligible(TradeSignal one, TradeSignal five, TradeSignal hour, int entryMomentum) {
        return one != null && five != null && hour != null
                && one.getDecision() == SignalDecision.NEUTRAL
                && bullish(hour.getDecision()) && !bearish(five.getDecision())
                && one.getMomentumScore() >= momentumFloor(entryMomentum);
    }

    private Variant updateProfitLock(Variant v, BigDecimal price) {
        WalletSettings s = walletSettingsRepository.findById(1L).orElseGet(() -> WalletSettings.builder()
                .id(1L).dynamicProfitLockEnabled(true).profitLockActivationPercent(BigDecimal.valueOf(70))
                .profitLockInitialPercent(BigDecimal.valueOf(40)).profitLockTrailStepPercent(BigDecimal.valueOf(10)).build());
        ProfitLockPolicy.State st = profitLockPolicy.evaluate(v.entryPrice(), v.takeProfit(), price, v.highest(),
                v.profitLockActive(), v.profitLockPrice(), s.isDynamicProfitLockEnabled(),
                nvl(s.getProfitLockActivationPercent(), BigDecimal.valueOf(70)),
                nvl(s.getProfitLockInitialPercent(), BigDecimal.valueOf(40)),
                nvl(s.getProfitLockTrailStepPercent(), BigDecimal.valueOf(10)));
        return v.withLock(st.highestPrice(), st.active(), st.lockPrice());
    }

    private void close(long runId, Variant v, Instant at, BigDecimal price, String reason, String explanation) {
        BigDecimal pnl = price.multiply(v.quantity(), MC).subtract(v.cost(), MC);
        updateRow(runId, v, at, price, reason, pnl, percentage(v.entryPrice(), price));
        activeByRun.remove(runId);
        log.info("FIX11Q_VARIANT_CLOSED runId={} positionId={} symbol={} at={} price={} reason={} baselinePnl={} variantPnl={} delta={}",
                runId, v.positionId(), v.symbol(), at, price, reason, v.baselinePnl(), pnl, pnl.subtract(v.baselinePnl(), MC));
    }

    private void updateRow(long runId, Variant v, Instant at, BigDecimal price, String reason, BigDecimal pnl, BigDecimal pnlPct) {
        String result = result(v);
        jdbcTemplate.update("""
                UPDATE one_candle_continuation_grace_test SET
                  next_1m_decision=?,next_5m_decision=?,next_1h_decision=?,variant_exit_time=?,variant_exit_price=?,
                  variant_exit_reason=?,variant_pnl_usdt=?,variant_pnl_percent=?,pnl_delta_usdt=?,
                  mfe_after_grace_percent=?,mae_after_grace_percent=?,grace_result=?,status=?
                WHERE test_run_id=? AND position_test_id=?
                """, v.next1mDecision(), v.next5mDecision(), v.next1hDecision(), Timestamp.from(at), price,
                reason, pnl, pnlPct, pnl.subtract(v.baselinePnl(), MC), percentage(v.priceAtGrace(), v.maxPrice()),
                percentage(v.priceAtGrace(), v.minPrice()), result, reason.equals("OPEN_AT_WINDOW_END") ? reason : "CLOSED",
                runId, v.positionId());
    }

    private String result(Variant v) {
        if ("BUY".equals(v.next1mDecision()) || "STRONG_BUY".equals(v.next1mDecision()) || "WATCH".equals(v.next1mDecision())) return "RECOVERED_SUPPORT";
        if ("NEUTRAL".equals(v.next1mDecision())) return "SECOND_NEUTRAL";
        if ("SELL".equals(v.next1mDecision()) || "STRONG_SELL".equals(v.next1mDecision())) return "BEARISH_AFTER_GRACE";
        return "OTHER";
    }

    private boolean sameGraceCandle(Variant v, TradeSignal one) {
        Instant id = candleIdentity(one);
        return id != null && id.equals(v.graceCandleTime());
    }
    private Instant candleIdentity(TradeSignal s) { return s == null ? null : (s.getCandleOpenTime() != null ? s.getCandleOpenTime() : s.getGeneratedAt()); }
    private int trendFloor(int entry) { return Math.max(12, entry - 3); }
    private int momentumFloor(int entry) { return Math.max(7, entry - 4); }
    private boolean bullish(SignalDecision d) { return d == SignalDecision.BUY || d == SignalDecision.STRONG_BUY; }
    private boolean bearish(SignalDecision d) { return d == SignalDecision.SELL || d == SignalDecision.STRONG_SELL; }
    private String name(SignalDecision d) { return d == null ? null : d.name(); }
    private BigDecimal nvl(BigDecimal v, BigDecimal fallback) { return v == null ? fallback : v; }
    private BigDecimal percentage(BigDecimal entry, BigDecimal price) {
        return price.subtract(entry, MC).multiply(BigDecimal.valueOf(100), MC).divide(entry, 8, RoundingMode.HALF_UP);
    }

    private record Variant(long positionId,String symbol,Instant entryTime,BigDecimal entryPrice,BigDecimal quantity,BigDecimal cost,
                           BigDecimal stopLoss,BigDecimal takeProfit,BigDecimal highest,boolean profitLockActive,BigDecimal profitLockPrice,
                           int entryScore,int entryConfidence,int entryTrend,int entryStructure,int entryMomentum,int entryVolume,
                           Instant graceCandleTime,Long graceSignalId,Instant graceAt,BigDecimal priceAtGrace,BigDecimal baselinePnl,BigDecimal baselinePnlPct,
                           BigDecimal maxPrice,BigDecimal minPrice,TradeSignal latest1m,TradeSignal latest5m,TradeSignal latest1h,
                           String next1mDecision,String next5mDecision,String next1hDecision,BigDecimal lastPrice) {
        Variant(long positionId,String symbol,Instant entryTime,BigDecimal entryPrice,BigDecimal quantity,BigDecimal cost,
                BigDecimal stopLoss,BigDecimal takeProfit,BigDecimal highest,boolean profitLockActive,BigDecimal profitLockPrice,
                int entryScore,int entryConfidence,int entryTrend,int entryStructure,int entryMomentum,int entryVolume,
                Instant graceCandleTime,Long graceSignalId,Instant graceAt,BigDecimal priceAtGrace,BigDecimal baselinePnl,BigDecimal baselinePnlPct,
                BigDecimal maxPrice,BigDecimal minPrice,TradeSignal latest1m,TradeSignal latest5m,TradeSignal latest1h) {
            this(positionId,symbol,entryTime,entryPrice,quantity,cost,stopLoss,takeProfit,highest,profitLockActive,profitLockPrice,
                    entryScore,entryConfidence,entryTrend,entryStructure,entryMomentum,entryVolume,graceCandleTime,graceSignalId,graceAt,priceAtGrace,
                    baselinePnl,baselinePnlPct,maxPrice,minPrice,latest1m,latest5m,latest1h,null,null,null,priceAtGrace);
        }
        Variant withContext(TradeSignal a,TradeSignal b,TradeSignal c){return new Variant(positionId,symbol,entryTime,entryPrice,quantity,cost,stopLoss,takeProfit,highest,profitLockActive,profitLockPrice,entryScore,entryConfidence,entryTrend,entryStructure,entryMomentum,entryVolume,graceCandleTime,graceSignalId,graceAt,priceAtGrace,baselinePnl,baselinePnlPct,maxPrice,minPrice,a,b,c,next1mDecision,next5mDecision,next1hDecision,lastPrice);}
        Variant withPath(BigDecimal p){return new Variant(positionId,symbol,entryTime,entryPrice,quantity,cost,stopLoss,takeProfit,highest==null?p:highest.max(p),profitLockActive,profitLockPrice,entryScore,entryConfidence,entryTrend,entryStructure,entryMomentum,entryVolume,graceCandleTime,graceSignalId,graceAt,priceAtGrace,baselinePnl,baselinePnlPct,maxPrice.max(p),minPrice.min(p),latest1m,latest5m,latest1h,next1mDecision,next5mDecision,next1hDecision,p);}
        Variant withTakeProfit(BigDecimal tp){return new Variant(positionId,symbol,entryTime,entryPrice,quantity,cost,stopLoss,tp,highest,profitLockActive,profitLockPrice,entryScore,entryConfidence,entryTrend,entryStructure,entryMomentum,entryVolume,graceCandleTime,graceSignalId,graceAt,priceAtGrace,baselinePnl,baselinePnlPct,maxPrice,minPrice,latest1m,latest5m,latest1h,next1mDecision,next5mDecision,next1hDecision,lastPrice);}
        Variant withLock(BigDecimal h,boolean a,BigDecimal l){return new Variant(positionId,symbol,entryTime,entryPrice,quantity,cost,stopLoss,takeProfit,h,a,l,entryScore,entryConfidence,entryTrend,entryStructure,entryMomentum,entryVolume,graceCandleTime,graceSignalId,graceAt,priceAtGrace,baselinePnl,baselinePnlPct,maxPrice,minPrice,latest1m,latest5m,latest1h,next1mDecision,next5mDecision,next1hDecision,lastPrice);}
        Variant withNext1m(String d){return new Variant(positionId,symbol,entryTime,entryPrice,quantity,cost,stopLoss,takeProfit,highest,profitLockActive,profitLockPrice,entryScore,entryConfidence,entryTrend,entryStructure,entryMomentum,entryVolume,graceCandleTime,graceSignalId,graceAt,priceAtGrace,baselinePnl,baselinePnlPct,maxPrice,minPrice,latest1m,latest5m,latest1h,d,next5mDecision,next1hDecision,lastPrice);}
        Variant withNext5m(String d){return new Variant(positionId,symbol,entryTime,entryPrice,quantity,cost,stopLoss,takeProfit,highest,profitLockActive,profitLockPrice,entryScore,entryConfidence,entryTrend,entryStructure,entryMomentum,entryVolume,graceCandleTime,graceSignalId,graceAt,priceAtGrace,baselinePnl,baselinePnlPct,maxPrice,minPrice,latest1m,latest5m,latest1h,next1mDecision,d,next1hDecision,lastPrice);}
        Variant withNext1h(String d){return new Variant(positionId,symbol,entryTime,entryPrice,quantity,cost,stopLoss,takeProfit,highest,profitLockActive,profitLockPrice,entryScore,entryConfidence,entryTrend,entryStructure,entryMomentum,entryVolume,graceCandleTime,graceSignalId,graceAt,priceAtGrace,baselinePnl,baselinePnlPct,maxPrice,minPrice,latest1m,latest5m,latest1h,next1mDecision,next5mDecision,d,lastPrice);}
    }
}
