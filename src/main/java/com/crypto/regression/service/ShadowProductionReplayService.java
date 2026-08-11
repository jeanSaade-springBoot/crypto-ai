package com.crypto.regression.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.execution.domain.ExecutionOpportunity;
import com.crypto.execution.service.ExecutionIntelligenceService;
import com.crypto.execution.service.ExecutionReplayScope;
import com.crypto.position.service.PositionContinuationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShadowProductionReplayService {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal INITIAL_CAPITAL = BigDecimal.valueOf(10000);

    private final JdbcTemplate jdbcTemplate;
    private final ExecutionIntelligenceService executionIntelligenceService;
    private final ExecutionReplayScope replayScope;
    private final PositionContinuationPolicy continuationPolicy;

    public ReplayStats replay(long runId, String symbol, List<TradeSignal> generatedSignals) {
        List<TradeSignal> timeline = generatedSignals.stream()
                .filter(s -> s != null && s.getGeneratedAt() != null)
                .sorted(Comparator.comparing(TradeSignal::getGeneratedAt)
                        .thenComparingInt(s -> intervalOrder(s.getInterval())))
                .toList();

        TradeSignal latest1m = null;
        TradeSignal latest5m = null;
        TradeSignal latest1h = null;
        ShadowPosition open = null;
        BigDecimal cash = INITIAL_CAPITAL;
        BigDecimal realized = BigDecimal.ZERO;
        int trades = 0, wins = 0, losses = 0;

        try (ExecutionReplayScope.Scope ignored = replayScope.open(runId, timeline,
                opportunity -> persistProductionOpportunity(runId, opportunity))) {
        for (TradeSignal signal : timeline) {
            replayScope.reference(signal.getGeneratedAt());
            if ("1m".equals(signal.getInterval())) latest1m = signal;
            if ("5m".equals(signal.getInterval())) latest5m = signal;
            if ("1h".equals(signal.getInterval())) latest1h = signal;

            if (open != null) {
                ExitDecision exit = evaluateExit(runId, open, signal, latest1m, latest5m, latest1h);
                if (exit.exit()) {
                    BigDecimal proceeds = signal.getLatestPrice().multiply(open.quantity(), MC);
                    BigDecimal pnl = proceeds.subtract(open.cost(), MC);
                    BigDecimal pnlPct = percentage(open.entryPrice(), signal.getLatestPrice());
                    cash = cash.add(proceeds, MC);
                    realized = realized.add(pnl, MC);
                    trades++;
                    if (pnl.signum() > 0) wins++; else if (pnl.signum() < 0) losses++;
                    persistSell(runId, symbol, signal, open, exit, pnl, pnlPct);
                    closePosition(runId, open.positionId(), signal, exit, pnl, pnlPct);
                    open = null;
                } else {
                    if (exit.newTakeProfit() != null) open = open.withTakeProfit(exit.newTakeProfit());
                    open = updateProfitLock(open, signal);
                    persistManagement(runId, signal, open.profitLockActive() ? "PROFIT_LOCK_ACTIVE" : "POSITION_HOLD",
                            open.takeProfit(), open.takeProfit(), open, exit.explanation());
                }
            }

            if (!"1m".equals(signal.getInterval())) continue;
            ExecutionIntelligenceService.ExecutionDecision decision = executionIntelligenceService.evaluateBuy(signal);

            if (open == null && decision.allowed()) {
                int atrPercent = signal.getAtrRecommendedPositionPercent() <= 0 ? 100 : signal.getAtrRecommendedPositionPercent();
                int effectivePercent = Math.max(1, Math.min(100,
                        (int)Math.round(atrPercent * decision.positionPercent() / 100.0)));
                BigDecimal budget = INITIAL_CAPITAL.multiply(BigDecimal.valueOf(effectivePercent), MC)
                        .divide(BigDecimal.valueOf(100), MC).min(cash);
                if (budget.signum() > 0 && signal.getLatestPrice() != null && signal.getLatestPrice().signum() > 0) {
                    BigDecimal qty = budget.divide(signal.getLatestPrice(), 12, RoundingMode.HALF_UP);
                    long positionId = openPosition(runId, symbol, signal, qty, budget, effectivePercent);
                    persistBuy(runId, symbol, signal, qty, budget, effectivePercent, decision);
                    executionIntelligenceService.markExecuted(signal, decision);
                    cash = cash.subtract(budget, MC);
                    open = new ShadowPosition(positionId, signal.getGeneratedAt(), signal.getLatestPrice(), qty, budget,
                            signal.getStopLoss(), signal.getTakeProfit(), signal.getLatestPrice(), false, null,
                            signal.getTotalScore(), signal.getConfidenceScore(), signal.getTrendScore(), signal.getMomentumScore(), signal.getVolumeScore());
                }
            }
        }
        }

        BigDecimal finalWallet = cash;
        if (open != null && !timeline.isEmpty()) {
            BigDecimal mark = timeline.get(timeline.size() - 1).getLatestPrice();
            if (mark != null) finalWallet = finalWallet.add(mark.multiply(open.quantity(), MC), MC);
        }

        jdbcTemplate.update("""
                UPDATE analysis_test_run
                SET simulated_trade_count=?, simulated_win_count=?, simulated_loss_count=?,
                    simulated_realized_pnl=?, simulated_final_wallet=?
                WHERE id=?
                """, trades, wins, losses, realized, finalWallet, runId);
        return new ReplayStats(trades, wins, losses, realized, finalWallet);
    }

    private ExitDecision evaluateExit(long runId, ShadowPosition p, TradeSignal s, TradeSignal oneMinute, TradeSignal five, TradeSignal one) {
        BigDecimal price = s.getLatestPrice();
        if (price == null) return ExitDecision.hold();
        if (p.takeProfit() != null && price.compareTo(p.takeProfit()) >= 0) {
            PositionContinuationPolicy.Evaluation continuation = continuationPolicy.evaluate(
                    oneMinute != null ? oneMinute : s, five, one, p.entryTrend(), p.entryMomentum(), p.entryVolume());
            if (continuation.extendTarget()) {
                BigDecimal distance = p.takeProfit().subtract(p.entryPrice());
                BigDecimal newTarget = p.takeProfit().add(distance.multiply(BigDecimal.valueOf(0.50), MC), MC);
                persistManagement(runId, s, "TAKE_PROFIT_EXTENDED", p.takeProfit(), newTarget, p, continuation.explanation());
                jdbcTemplate.update("UPDATE wallet_position_test SET take_profit_usdt=? WHERE id=?", newTarget, p.positionId());
                return new ExitDecision(false, "EXTEND_TAKE_PROFIT", continuation.explanation(), newTarget);
            }
            persistManagement(runId, s, "TAKE_PROFIT_EXIT", p.takeProfit(), p.takeProfit(), p, continuation.explanation());
            return new ExitDecision(true, "TAKE_PROFIT", continuation.explanation());
        }
        ShadowPosition updated = profitLockState(p, price);
        BigDecimal minimumProfitableExit = p.entryPrice().multiply(BigDecimal.valueOf(1.0005));
        if (updated.profitLockActive() && updated.profitLockPrice() != null
                && price.compareTo(updated.profitLockPrice()) <= 0 && price.compareTo(minimumProfitableExit) >= 0) {
            persistManagement(runId, s, "PROFIT_LOCK_EXIT", p.takeProfit(), p.takeProfit(), updated,
                    "Price retraced to the protected profit level after a profitable advance.");
            return new ExitDecision(true, "PROFIT_LOCK", "Price retraced to the protected profit level after a profitable advance.");
        }
        if (p.stopLoss() != null && price.compareTo(p.stopLoss()) <= 0)
            return new ExitDecision(true, "STOP_LOSS", "Price reached the stored stop loss.");
        if ("1m".equals(s.getInterval()) && bearish(s.getDecision()) && five != null && bearish(five.getDecision())
                && (one == null || !bullish(one.getDecision())))
            return new ExitDecision(true, "SELL_CONFIRMED", "1m SELL confirmed by bearish 5m while 1h was not bullish.");
        return ExitDecision.hold();
    }

    private ShadowPosition updateProfitLock(ShadowPosition p, TradeSignal s) {
        if (s.getLatestPrice() == null) return p;
        ShadowPosition n = profitLockState(p, s.getLatestPrice());
        if (!equalsNullable(p.highest(), n.highest()) || p.profitLockActive() != n.profitLockActive()
                || !equalsNullable(p.profitLockPrice(), n.profitLockPrice())) {
            jdbcTemplate.update("UPDATE wallet_position_test SET highest_price_usdt=?, profit_lock_active=?, profit_lock_price_usdt=? WHERE id=?",
                    n.highest(), n.profitLockActive(), n.profitLockPrice(), p.positionId());
        }
        return n;
    }

    private ShadowPosition profitLockState(ShadowPosition p, BigDecimal price) {
        BigDecimal highest = p.highest() == null || price.compareTo(p.highest()) > 0 ? price : p.highest();
        if (p.takeProfit() == null || p.takeProfit().compareTo(p.entryPrice()) <= 0)
            return p.withLock(highest, p.profitLockActive(), p.profitLockPrice());
        BigDecimal distance = p.takeProfit().subtract(p.entryPrice());
        BigDecimal progress = highest.subtract(p.entryPrice()).multiply(BigDecimal.valueOf(100), MC)
                .divide(distance, 6, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
        int quality = (int)Math.round(p.entryScore() * .55 + p.entryConfidence() * .45);
        BigDecimal activation = quality >= 85 ? BigDecimal.valueOf(75) : quality >= 80 ? BigDecimal.valueOf(60)
                : quality >= 75 ? BigDecimal.valueOf(40) : quality >= 70 ? BigDecimal.valueOf(35) : BigDecimal.valueOf(30);
        BigDecimal initial = quality >= 85 ? BigDecimal.valueOf(45) : quality >= 80 ? BigDecimal.valueOf(35)
                : quality >= 75 ? BigDecimal.valueOf(20) : quality >= 70 ? BigDecimal.valueOf(15) : BigDecimal.valueOf(10);
        BigDecimal step = quality >= 75 ? BigDecimal.TEN : BigDecimal.valueOf(5);
        boolean active = p.profitLockActive();
        BigDecimal lock = p.profitLockPrice();
        if (progress.compareTo(activation) >= 0) {
            active = true;
            BigDecimal completed = progress.subtract(activation).max(BigDecimal.ZERO).divide(step, 0, RoundingMode.DOWN);
            BigDecimal lockedProgress = initial.add(completed.multiply(step));
            BigDecimal maximum = progress.subtract(step).max(initial);
            lockedProgress = lockedProgress.min(maximum).max(initial);
            BigDecimal candidate = p.entryPrice().add(distance.multiply(lockedProgress).divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP));
            candidate = candidate.max(p.entryPrice().multiply(BigDecimal.valueOf(1.0005)));
            if (lock == null || candidate.compareTo(lock) > 0) lock = candidate;
        }
        return p.withLock(highest, active, lock);
    }

    private void persistManagement(long runId, TradeSignal s, String code, BigDecimal oldTp, BigDecimal newTp, ShadowPosition p, String explanation) {
        jdbcTemplate.update("""
            INSERT INTO position_management_test
            (test_run_id,symbol,generated_at,action_code,current_price,old_take_profit,new_take_profit,highest_price,profit_lock_active,profit_lock_price,explanation)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """, runId, s.getSymbol(), Timestamp.from(s.getGeneratedAt()), code, s.getLatestPrice(), oldTp, newTp,
                p.highest(), p.profitLockActive(), p.profitLockPrice(), explanation);
    }

    private void persistProductionOpportunity(long runId, ExecutionOpportunity o) {
        TradeSignal s = o.getLatestSignal();
        if (s == null || s.getGeneratedAt() == null) return;
        jdbcTemplate.update("""
            INSERT INTO execution_opportunity_test
            (test_run_id, source_signal_id, symbol, generated_at, replay_stage, evidence_count, buy_count, watch_count,
             neutral_count, bearish_count, evidence_score, opportunity_health, recommended_position_percent,
             current_final_decision, current_original_decision, five_minute_decision, one_hour_decision,
             old_hard_bearish_reversal, corrected_hard_bearish_reversal, decision_code, decision_explanation)
            VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?)
            """, runId, s.getSymbol(), Timestamp.from(s.getGeneratedAt()), o.getStatus(), o.getEvidenceCount(),
                o.getBuyCount(), o.getWatchCount(), o.getNeutralCount(), o.getBearishCount(), o.getEvidenceScore(),
                o.getOpportunityHealth(), o.getRecommendedPositionPercent(), name(s.getDecision()),
                name(s.getOriginalDecision()), o.getFiveMinuteDecision(), o.getOneHourDecision(),
                o.getDecisionCode(), o.getDecisionExplanation());
    }

    private void persistBuy(long runId,String symbol,TradeSignal s,BigDecimal qty,BigDecimal budget,int pct,ExecutionIntelligenceService.ExecutionDecision d){
        jdbcTemplate.update("""
            INSERT INTO wallet_execution_test
            (test_run_id,symbol,side,execution_time,execution_price,quantity,notional_usdt,position_percent,signal_interval,signal_decision,execution_source,execution_code,execution_reason)
            VALUES (?,?,'BUY',?,?,?,?,?,?,?,?,?,?)""",
            runId,symbol,Timestamp.from(s.getGeneratedAt()),s.getLatestPrice(),qty,budget,pct,s.getInterval(),name(s.getDecision()),d.source(),d.code(),d.explanation());
    }

    private long openPosition(long runId,String symbol,TradeSignal s,BigDecimal qty,BigDecimal budget,int pct){
        org.springframework.jdbc.support.GeneratedKeyHolder kh=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(c->{var ps=c.prepareStatement("""
            INSERT INTO wallet_position_test
            (test_run_id,symbol,status,entry_time,entry_price,quantity,total_cost_usdt,position_percent,stop_loss_usdt,take_profit_usdt,highest_price_usdt)
            VALUES (?,?,'OPEN',?,?,?,?,?,?,?,?)""",java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,runId);ps.setString(2,symbol);ps.setTimestamp(3,Timestamp.from(s.getGeneratedAt()));ps.setBigDecimal(4,s.getLatestPrice());ps.setBigDecimal(5,qty);ps.setBigDecimal(6,budget);ps.setInt(7,pct);ps.setBigDecimal(8,s.getStopLoss());ps.setBigDecimal(9,s.getTakeProfit());ps.setBigDecimal(10,s.getLatestPrice());return ps;},kh);
        if (kh.getKey() == null) throw new IllegalStateException("Could not create shadow position");
        return kh.getKey().longValue();
    }

    private void persistSell(long runId,String symbol,TradeSignal s,ShadowPosition p,ExitDecision e,BigDecimal pnl,BigDecimal pnlPct){
        BigDecimal notional=s.getLatestPrice().multiply(p.quantity(),MC);
        jdbcTemplate.update("""
            INSERT INTO wallet_execution_test
            (test_run_id,symbol,side,execution_time,execution_price,quantity,notional_usdt,position_percent,signal_interval,signal_decision,execution_source,execution_code,execution_reason,realized_pnl_usdt,realized_pnl_percent)
            VALUES (?,?,'SELL',?,?,?,?,0,?,?,?,?,?,?,?)""",
            runId,symbol,Timestamp.from(s.getGeneratedAt()),s.getLatestPrice(),p.quantity(),notional,s.getInterval(),name(s.getDecision()),"POSITION_MANAGEMENT",e.reason(),e.explanation(),pnl,pnlPct);
    }

    private void closePosition(long runId,long id,TradeSignal s,ExitDecision e,BigDecimal pnl,BigDecimal pnlPct){
        jdbcTemplate.update("""
            UPDATE wallet_position_test SET status='CLOSED',exit_time=?,exit_price=?,exit_reason=?,exit_explanation=?,realized_pnl_usdt=?,realized_pnl_percent=? WHERE id=? AND test_run_id=?""",
                Timestamp.from(s.getGeneratedAt()),s.getLatestPrice(),e.reason(),e.explanation(),pnl,pnlPct,id,runId);
    }

    private BigDecimal percentage(BigDecimal entry,BigDecimal exit){return exit.subtract(entry).multiply(BigDecimal.valueOf(100)).divide(entry,8,RoundingMode.HALF_UP);}
    private boolean bullish(SignalDecision d){return d==SignalDecision.BUY||d==SignalDecision.STRONG_BUY;}
    private boolean bearish(SignalDecision d){return d==SignalDecision.SELL||d==SignalDecision.STRONG_SELL;}
    private String name(SignalDecision d){return d==null?null:d.name();}
    private int intervalOrder(String i){return "1h".equals(i)?0:"5m".equals(i)?1:2;}
    private boolean equalsNullable(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}

    public record ReplayStats(int trades,int wins,int losses,BigDecimal realizedPnl,BigDecimal finalWallet){}
    private record ExitDecision(boolean exit,String reason,String explanation,BigDecimal newTakeProfit){
        ExitDecision(boolean exit,String reason,String explanation){this(exit,reason,explanation,null);}
        static ExitDecision hold(){return new ExitDecision(false,"HOLD","Position remains open.",null);}
    }
    private record ShadowPosition(long positionId,Instant entryTime,BigDecimal entryPrice,BigDecimal quantity,BigDecimal cost,BigDecimal stopLoss,BigDecimal takeProfit,BigDecimal highest,boolean profitLockActive,BigDecimal profitLockPrice,int entryScore,int entryConfidence,int entryTrend,int entryMomentum,int entryVolume){
        ShadowPosition withLock(BigDecimal h,boolean a,BigDecimal l){return new ShadowPosition(positionId,entryTime,entryPrice,quantity,cost,stopLoss,takeProfit,h,a,l,entryScore,entryConfidence,entryTrend,entryMomentum,entryVolume);}
        ShadowPosition withTakeProfit(BigDecimal tp){return new ShadowPosition(positionId,entryTime,entryPrice,quantity,cost,stopLoss,tp,highest,profitLockActive,profitLockPrice,entryScore,entryConfidence,entryTrend,entryMomentum,entryVolume);}
    }
}
