package com.crypto.regression.service;

import com.crypto.config.TradingProperties;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShadowProductionReplayService {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final Duration EVIDENCE_WINDOW = Duration.ofMinutes(30);
    private static final int MIN_EVIDENCE_SCORE = 7;
    private static final BigDecimal INITIAL_CAPITAL = BigDecimal.valueOf(10000);

    private final JdbcTemplate jdbcTemplate;
    private final TradingProperties properties;

    public ReplayStats replay(long runId, String symbol, List<TradeSignal> generatedSignals) {
        List<TradeSignal> timeline = generatedSignals.stream()
                .filter(s -> s != null && s.getGeneratedAt() != null)
                .sorted(Comparator.comparing(TradeSignal::getGeneratedAt)
                        .thenComparingInt(s -> intervalOrder(s.getInterval())))
                .toList();

        List<TradeSignal> oneMinuteHistory = new ArrayList<>();
        TradeSignal latest5m = null;
        TradeSignal latest1h = null;
        ShadowPosition open = null;
        BigDecimal cash = INITIAL_CAPITAL;
        BigDecimal realized = BigDecimal.ZERO;
        int trades = 0, wins = 0, losses = 0;

        for (TradeSignal signal : timeline) {
            if ("5m".equals(signal.getInterval())) latest5m = signal;
            if ("1h".equals(signal.getInterval())) latest1h = signal;

            if (open != null) {
                ExitDecision exit = evaluateExit(open, signal, latest5m, latest1h);
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
                    open = updateProfitLock(open, signal);
                }
            }

            if (!"1m".equals(signal.getInterval())) continue;
            oneMinuteHistory.add(signal);
            Instant cutoff = signal.getGeneratedAt().minus(EVIDENCE_WINDOW);
            oneMinuteHistory.removeIf(s -> s.getGeneratedAt().isBefore(cutoff));

            Evidence evidence = evidence(oneMinuteHistory, latest5m, latest1h);
            EntryDecision decision = evaluateEntry(signal, latest5m, latest1h, evidence, open != null);
            persistOpportunity(runId, signal, latest5m, latest1h, evidence, decision);

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
                    cash = cash.subtract(budget, MC);
                    open = new ShadowPosition(positionId, signal.getGeneratedAt(), signal.getLatestPrice(), qty, budget,
                            signal.getStopLoss(), signal.getTakeProfit(), signal.getLatestPrice(), false, null,
                            signal.getTotalScore(), signal.getConfidenceScore());
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

    private EntryDecision evaluateEntry(TradeSignal s, TradeSignal five, TradeSignal one, Evidence e, boolean alreadyOpen) {
        if (alreadyOpen) return EntryDecision.no("POSITION_ALREADY_OPEN", "A shadow position is already open.", "MANAGED");
        if (s.getDecision() == SignalDecision.STRONG_SELL || bearish(decision(five)) || bearish(decision(one)))
            return EntryDecision.no("BEARISH_REVERSAL", "Final 1m/5m/1h bearish context invalidated the BUY opportunity.", "CANCELLED");
        if (!s.isStrategyEntryAllowed()) return EntryDecision.no("STRATEGY_ENTRY_BLOCKED", "Strategy entry gate blocked execution.", "BLOCKED");
        if (!s.isBtcContextEntryAllowed()) return EntryDecision.no("BTC_CONTEXT_BLOCKED", "BTC context blocked execution.", "BLOCKED");
        if (!s.isDerivativesEntryAllowed()) return EntryDecision.no("DERIVATIVES_BLOCKED", "Derivatives context blocked execution.", "BLOCKED");
        if (!s.isLiquidityEntryAllowed()) return EntryDecision.no("LIQUIDITY_BLOCKED", "Liquidity/order-book context blocked execution.", "BLOCKED");
        if (s.getLatestPrice() == null || s.getStopLoss() == null || s.getTakeProfit() == null)
            return EntryDecision.no("MISSING_RISK_PLAN", "Entry price/stop/target is incomplete.", "BLOCKED");

        boolean direct = bullish(s.getDecision()) && s.isFinalEntryAllowed() && s.isAtrImmediateEntryAllowed()
                && s.getTotalScore() >= properties.minimumBuyScore();
        if (direct) {
            int pct = balancedPositionPercent(five, one);
            if (pct > 0) return EntryDecision.yes("DIRECT_BUY", "DIRECT_SIGNAL", pct,
                    "Fresh 1m BUY passed final decision, ATR and higher-timeframe confirmation.");
        }

        if (!supportive(s)) return EntryDecision.no("NO_BULLISH_EVIDENCE", "Current 1m signal is not supportive.", "BUILDING");
        if (five == null || one == null) return EntryDecision.no("MISSING_CONTEXT", "Fresh 5m/1h context is unavailable.", "BUILDING");
        if (bearish(five.getDecision()) || bearish(one.getDecision()))
            return EntryDecision.no("HIGHER_TIMEFRAME_BEARISH", "5m or 1h final decision is bearish.", "CANCELLED");
        if (e.health() < 40) return EntryDecision.no("OPPORTUNITY_RECOVERING", "Opportunity health is below 40.", "RECOVERING");
        if (e.score() < MIN_EVIDENCE_SCORE)
            return EntryDecision.no("EVIDENCE_BUILDING", "Evidence score " + e.score() + "/" + MIN_EVIDENCE_SCORE + ".", "BUILDING");
        if (e.buys() == 0 && e.watches() < 5)
            return EntryDecision.no("WATCH_ONLY_BUILDING", "WATCH evidence has not persisted long enough.", "BUILDING");
        if (e.avgScore() < 65 || e.avgConfidence() < 65)
            return EntryDecision.no("EVIDENCE_QUALITY_LOW", "Average evidence quality is below 65.", "BUILDING");
        if (!s.isAtrImmediateEntryAllowed())
            return EntryDecision.no("ATR_ENTRY_BLOCKED", "ATR requested pullback/retracement before entry.", "BLOCKED");

        int pct = e.score() >= 11 ? 50 : 25;
        if (e.buys() >= 2) pct += 10;
        if (five.getDecision() == SignalDecision.WATCH) pct += 5;
        if (bullish(five.getDecision())) pct += 15;
        if (one.getDecision() == SignalDecision.WATCH) pct += 5;
        if (bullish(one.getDecision())) pct += 10;
        return EntryDecision.yes("ACCUMULATED_EVIDENCE", "OPPORTUNITY_CONFIRMED", Math.min(75, pct),
                "Accumulated fresh evidence confirmed the opportunity.");
    }

    private ExitDecision evaluateExit(ShadowPosition p, TradeSignal s, TradeSignal five, TradeSignal one) {
        BigDecimal price = s.getLatestPrice();
        if (price == null) return ExitDecision.hold();
        if (p.takeProfit() != null && price.compareTo(p.takeProfit()) >= 0)
            return new ExitDecision(true, "TAKE_PROFIT", "Price reached the stored take-profit target.");
        ShadowPosition updated = profitLockState(p, price);
        if (updated.profitLockActive() && updated.profitLockPrice() != null && price.compareTo(updated.profitLockPrice()) <= 0)
            return new ExitDecision(true, "PROFIT_LOCK", "Price retraced to the dynamic protected-profit level.");
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
            if (lock == null || candidate.compareTo(lock) > 0) lock = candidate;
        }
        return p.withLock(highest, active, lock);
    }

    private Evidence evidence(List<TradeSignal> history, TradeSignal five, TradeSignal one) {
        int buys=0,watches=0,neutrals=0,bearish=0,score=0,total=0,confidence=0;
        for (TradeSignal s : history) {
            SignalDecision d=s.getDecision();
            if (bullish(d)) { buys++; score+=3; }
            else if (d==SignalDecision.WATCH && s.getTotalScore()>=60 && s.getConfidenceScore()>=60) { watches++; score+=1; }
            else if (d==SignalDecision.SELL) { bearish++; score-=2; }
            else if (d==SignalDecision.STRONG_SELL) { bearish++; score-=4; }
            else neutrals++;
            total+=s.getTotalScore(); confidence+=s.getConfidenceScore();
        }
        int health=50;
        if (!history.isEmpty()) {
            TradeSignal last=history.get(history.size()-1);
            health += bullish(last.getDecision()) ? 15 : last.getDecision()==SignalDecision.WATCH ? 5
                    : last.getDecision()==SignalDecision.SELL ? -15 : last.getDecision()==SignalDecision.STRONG_SELL ? -30 : -2;
        }
        health += tfHealth(five,25,10,-25,-35);
        health += tfHealth(one,40,15,-40,-50);
        health=Math.max(0,Math.min(100,health));
        int n=Math.max(1,history.size());
        return new Evidence(history.size(),buys,watches,neutrals,bearish,score,health,total/n,confidence/n);
    }

    private int tfHealth(TradeSignal s,int buy,int watch,int sell,int strongSell) {
        if (s==null||s.getDecision()==null) return 0;
        return switch(s.getDecision()) { case BUY,STRONG_BUY -> buy; case WATCH -> watch; case SELL -> sell; case STRONG_SELL -> strongSell; default -> 0;};
    }

    private int balancedPositionPercent(TradeSignal five, TradeSignal one) {
        if (five==null||one==null) return 0;
        SignalDecision f=five.getDecision(), o=one.getDecision();
        if (bullish(f)&&bullish(o)) return 100;
        if (bullish(f)&&(o==SignalDecision.WATCH||o==SignalDecision.NEUTRAL)) return 75;
        if (f==SignalDecision.WATCH&&bullish(o)) return 75;
        if (f==SignalDecision.WATCH&&o==SignalDecision.WATCH) return 50;
        return 0;
    }

    private void persistOpportunity(long runId, TradeSignal s, TradeSignal five, TradeSignal one, Evidence e, EntryDecision d) {
        jdbcTemplate.update("""
            INSERT INTO execution_opportunity_test
            (test_run_id, source_signal_id, symbol, generated_at, replay_stage, evidence_count, buy_count, watch_count,
             neutral_count, bearish_count, evidence_score, opportunity_health, recommended_position_percent,
             current_final_decision, current_original_decision, five_minute_decision, one_hour_decision,
             old_hard_bearish_reversal, corrected_hard_bearish_reversal, decision_code, decision_explanation)
            VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?)
            """, runId,s.getSymbol(),Timestamp.from(s.getGeneratedAt()),d.stage(),e.count(),e.buys(),e.watches(),e.neutrals(),e.bearish(),
                e.score(),e.health(),d.positionPercent(),name(s.getDecision()),name(s.getOriginalDecision()),name(decision(five)),name(decision(one)),d.code(),d.explanation());
    }

    private void persistBuy(long runId,String symbol,TradeSignal s,BigDecimal qty,BigDecimal budget,int pct,EntryDecision d){
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
    private boolean supportive(TradeSignal s){return bullish(s.getDecision())||(s.getDecision()==SignalDecision.WATCH&&s.getTotalScore()>=60&&s.getConfidenceScore()>=60);}
    private boolean bullish(SignalDecision d){return d==SignalDecision.BUY||d==SignalDecision.STRONG_BUY;}
    private boolean bearish(SignalDecision d){return d==SignalDecision.SELL||d==SignalDecision.STRONG_SELL;}
    private SignalDecision decision(TradeSignal s){return s==null?null:s.getDecision();}
    private String name(SignalDecision d){return d==null?null:d.name();}
    private int intervalOrder(String i){return "1h".equals(i)?0:"5m".equals(i)?1:2;}
    private boolean equalsNullable(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}

    public record ReplayStats(int trades,int wins,int losses,BigDecimal realizedPnl,BigDecimal finalWallet){}
    private record Evidence(int count,int buys,int watches,int neutrals,int bearish,int score,int health,int avgScore,int avgConfidence){}
    private record EntryDecision(boolean allowed,String source,String code,String stage,int positionPercent,String explanation){
        static EntryDecision yes(String source,String code,int pct,String exp){return new EntryDecision(true,source,code,"CONFIRMED",pct,exp);}
        static EntryDecision no(String code,String exp,String stage){return new EntryDecision(false,"SHADOW_EXECUTION",code,stage,0,exp);}
    }
    private record ExitDecision(boolean exit,String reason,String explanation){static ExitDecision hold(){return new ExitDecision(false,"HOLD","Position remains open.");}}
    private record ShadowPosition(long positionId,Instant entryTime,BigDecimal entryPrice,BigDecimal quantity,BigDecimal cost,BigDecimal stopLoss,BigDecimal takeProfit,BigDecimal highest,boolean profitLockActive,BigDecimal profitLockPrice,int entryScore,int entryConfidence){
        ShadowPosition withLock(BigDecimal h,boolean a,BigDecimal l){return new ShadowPosition(positionId,entryTime,entryPrice,quantity,cost,stopLoss,takeProfit,h,a,l,entryScore,entryConfidence);}
    }
}
