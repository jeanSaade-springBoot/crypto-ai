package com.crypto.shared;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.indicator.event.CandleClosedAnalysisWorker;
import com.crypto.indicator.event.CandleClosedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.crypto.shared.SharedMarketConsumer.*;

/** FIX-138: bounded, fair per-lane discovery instead of a backlog-wide anti-join.
 * Live and historical work have separate clocks and capacity, but share both JVM
 * admission and durable RUNNING ownership. Historical tasks NEVER receive live
 * authority. Scoring, freshness thresholds and price-protection phases are unchanged.
 */
public final class SharedAnalysisDispatcher {
    private static final Logger log = LoggerFactory.getLogger(SharedAnalysisDispatcher.class);
    private record Lane(String symbol, String interval) {
        @Override public String toString() { return symbol + "|" + interval; }
    }
    private final SharedMarketSource source;
    private final JdbcTemplate jdbc;
    private final CoinConfigurationService coins;
    private final Supplier<List<String>> intervals;
    private final CandleClosedAnalysisWorker worker;
    private final Executor liveExecutor, historyExecutor;
    private final TransactionTemplate tx;
    private final Set<Lane> active = ConcurrentHashMap.newKeySet();
    private final Set<Lane> liveActive = ConcurrentHashMap.newKeySet();
    private final Set<Lane> historyActive = ConcurrentHashMap.newKeySet();
    private final Map<Lane,Long> lastBlockedLog = new ConcurrentHashMap<>();
    private int liveCursor, historyCursor;
    private long liveHeartbeat, historyHeartbeat;
    private volatile boolean stopped;

    public SharedAnalysisDispatcher(SharedMarketSource source, JdbcTemplate local,
            CoinConfigurationService coins, Supplier<List<String>> intervals,
            CandleClosedAnalysisWorker worker, Executor liveExecutor, Executor historyExecutor,
            PlatformTransactionManager manager) {
        this.source=source;this.coins=coins;this.intervals=intervals;this.worker=worker;
        this.liveExecutor=liveExecutor;this.historyExecutor=historyExecutor;
        // Independent settings: do not modify the application's shared JdbcTemplate.
        jdbc=new JdbcTemplate(Objects.requireNonNull(local.getDataSource()));
        jdbc.setQueryTimeout(3);
        tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(5);
    }

    // FIX-140: compatibility default; production disabling is an explicit operator setting.
    private volatile boolean historicalEnabled=true;
    public void historicalEnabled(boolean enabled) { historicalEnabled=enabled; }
    public void live() { dispatch(true); }
    public void historical() { dispatch(false); }
    public void stop() { stopped=true; }

    private List<Lane> lanes() {
        var result=new ArrayList<Lane>();
        // Preserve discovery of the existing standard delivery intervals even if
        // a legacy Binance configuration lists fewer locally subscribed periods.
        var periods=new LinkedHashSet<>(List.of("1m","5m","15m","1h","4h","1d"));
        periods.addAll(intervals.get());
        for(String symbol:coins.enabledSymbols())
            for(String interval:periods)result.add(new Lane(symbol,interval));
        return result;
    }

    private void dispatch(boolean live) {
        if(stopped || !source.enabled() || (!live && !historicalEnabled))return;
        long started=System.nanoTime();
        Set<Lane> poolActive=live?liveActive:historyActive;
        int capacity=live?8:1;
        if(poolActive.size()>=capacity)return;
        int checked=0, submitted=0;
        try {
            var lanes=lanes();
            // Bounded round-robin turns. A blocked lane cannot monopolize selection.
            for(int n=0;n<Math.min(16,lanes.size()) && !stopped;n++) {
                if(poolActive.size()>=capacity)break;
                int cursor=live?liveCursor++:historyCursor++;
                Lane lane=lanes.get(Math.floorMod(cursor,lanes.size()));
                checked++;
                if(!active.add(lane))continue;
                poolActive.add(lane);
                boolean handedOff=false;
                try {
                    var candidate=candidate(lane,live,Instant.now());
                    if(candidate==null)continue;
                    long id=num(candidate,"source_event_id");
                    String token=UUID.randomUUID().toString();
                    long claimStarted=System.nanoTime();
                    boolean claimed=Boolean.TRUE.equals(tx.execute(status->claim(lane,candidate,live,token)));
                    long claimMs=(System.nanoTime()-claimStarted)/1_000_000;
                    if(!claimed)continue;
                    long queued=System.nanoTime();
                    try {
                        (live?liveExecutor:historyExecutor).execute(()->run(lane,candidate,live,token,queued));
                        handedOff=true;submitted++;
                        log.info("[FIX-138][CLAIM] mode={}, event={}, lane={}, closeUtc={}, claimMs={}",
                            live?"LIVE":"HISTORY",id,lane,time(candidate,"candle_close_time"),claimMs);
                    } catch(RejectedExecutionException rejected) {
                        // AbortPolicy guarantees the task did not start. Roll back ONLY
                        // this known unsubmitted claim; uncertain execute failures go to review.
                        tx.executeWithoutResult(s->{lockSymbol(lane);
                            jdbc.update("UPDATE shared_market_event_delivery SET analysis_status=?,owner_token=NULL,analysis_started_at=NULL WHERE source_event_id=? AND owner_token=? AND analysis_status='RUNNING'",
                                str(candidate,"analysis_status"),id,token);
                        });
                        log.warn("[FIX-138][POOL_FULL] mode={}, event={}, lane={}; unsubmitted claim released",live?"LIVE":"HISTORY",id,lane);
                    } catch(RuntimeException uncertain) {
                        log.error("[FIX-138][SUBMISSION_UNCONFIRMED] event={}, lane={}; durable owner retained",id,lane,uncertain);
                    }
                } catch(Exception failure) {
                    log.error("[FIX-138][LANE_DISPATCH_FAILED] mode={}, lane={}",live?"LIVE":"HISTORY",lane,failure);
                } finally {
                    if(!handedOff) {poolActive.remove(lane);active.remove(lane);}
                }
            }
        } catch(Exception failure) { log.error("[FIX-138][DISPATCH_FAILED] mode={}",live?"LIVE":"HISTORY",failure); }
        finally {
            long elapsed=(System.nanoTime()-started)/1_000_000;
            long previous=live?liveHeartbeat:historyHeartbeat;
            if(started-previous>=TimeUnit.SECONDS.toNanos(30) || elapsed>=5000) {
                if(live)liveHeartbeat=started;else historyHeartbeat=started;
                log.info("[FIX-138][DISPATCH] mode={}, lanesChecked={}, submitted={}, active={}, elapsedMs={}, sourcePool={}",
                    live?"LIVE":"HISTORY",checked,submitted,poolActive.size(),elapsed,source.poolPressure());
            }
        }
    }

    private Map<String,Object> candidate(Lane lane,boolean live,Instant now) {
        long started=System.nanoTime();
        Map<String,Object> result=null;
        Instant cutoff=now.minusSeconds(SharedEventPolicy.closeGraceSeconds(lane.interval()));
        // FIX-140: attributed retry work is selected independently of candle grace,
        // so an expired retry can be terminalized instead of remaining stuck forever.
        if(live) {
            var retry=jdbc.queryForList("SELECT * FROM shared_market_event_delivery WHERE symbol=? AND interval_code=? AND phase=4 AND analysis_status='EXECUTION_RETRY' AND (analysis_not_before IS NULL OR analysis_not_before<=?) ORDER BY candle_close_time,symbol_sequence LIMIT 1",lane.symbol(),lane.interval(),Timestamp.from(now));
            if(!retry.isEmpty())return retry.getFirst();
        }
        // Single-status indexed ranges avoid IN-list sorting across the entire backlog.
        for(String status:live?List.of("PENDING"):List.of("PENDING","HISTORICAL_DEFERRED")) {
            String window=live
                ? " AND status='COMPLETED' AND candle_close_time>=? AND candle_close_time<=?"
                : " AND candle_close_time<?";
            var args=new ArrayList<Object>(List.of(lane.symbol(),lane.interval(),status,Timestamp.from(cutoff)));
            if(live)args.add(Timestamp.from(now));
            args.add(Timestamp.from(now));
            var rows=jdbc.queryForList("SELECT * FROM shared_market_event_delivery WHERE symbol=? AND interval_code=? AND phase=4 AND analysis_status=? AND closed=1"
                +window+" AND (analysis_not_before IS NULL OR analysis_not_before<?) ORDER BY candle_close_time,symbol_sequence LIMIT 1",args.toArray());
            if(!rows.isEmpty() && (result==null || before(rows.getFirst(),result)))result=rows.getFirst();
        }
        long ms=(System.nanoTime()-started)/1_000_000;
        if(ms>=1000)log.warn("[FIX-138][SLOW_CANDIDATE] mode={}, lane={}, elapsedMs={}",live?"LIVE":"HISTORY",lane,ms);
        return result;
    }
    private boolean before(Map<String,Object> a,Map<String,Object> b) {
        int order=time(a,"candle_close_time").compareTo(time(b,"candle_close_time"));
        return order<0 || (order==0 && num(a,"symbol_sequence")<num(b,"symbol_sequence"));
    }
    private Map<String,Object> lockSymbol(Lane lane) {
        return jdbc.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE",lane.symbol());
    }

    private boolean claim(Lane lane,Map<String,Object> selected,boolean live,String token) {
        if(stopped || (!live && !historicalEnabled) || !SharedCutoverPolicy.approved(lockSymbol(lane)))return false;
        long id=num(selected,"source_event_id");
        var e=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",id);
        Instant now=Instant.now();
        if(num(e,"phase")!=4 || !truth(e.get("closed"))
                || !Set.of("PENDING","HISTORICAL_DEFERRED","EXECUTION_RETRY").contains(str(e,"analysis_status")))return false;
        if(time(e,"analysis_not_before")!=null && !time(e,"analysis_not_before").isBefore(now))return false;
        boolean fresh=SharedEventPolicy.closeEligible(lane.interval(),time(e,"candle_close_time"),now);
        boolean retry="EXECUTION_RETRY".equals(str(e,"analysis_status"));
        if(retry && !live)return false;
        if(live && ((!fresh && !retry) || !"COMPLETED".equals(str(e,"status"))))return false;
        if(!live && !time(e,"candle_close_time").isBefore(now.minusSeconds(SharedEventPolicy.closeGraceSeconds(lane.interval()))))return false;

        // Both clocks/JVMs acquire symbol -> delivery locks in the same order.
        // Every RUNNING owner blocks, including an owner later than this old candle.
        var running=jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE analysis_status='RUNNING' AND symbol=? AND interval_code=? LIMIT 1 FOR UPDATE",lane.symbol(),lane.interval());
        if(!running.isEmpty()) {blocked(lane,"RUNNING_OWNER",num(running.getFirst(),"source_event_id"));return false;}
        // Bound reconciliation too. A completed analysis-only failure can be isolated,
        // but never re-executed automatically. Any work or wallet evidence retains review.
        var reviews=jdbc.queryForList("SELECT * FROM shared_market_event_delivery WHERE analysis_status='REVIEW_REQUIRED' AND symbol=? AND interval_code=? ORDER BY symbol_sequence LIMIT 17 FOR UPDATE",lane.symbol(),lane.interval());
        if(reviews.size()>16) {blocked(lane,"REVIEW_BATCH_LIMIT",num(reviews.getFirst(),"source_event_id"));return false;}
        for(var review:reviews) {
            if(!isolateAnalysisOnlyReview(review)) {blocked(lane,"UNRESOLVED_EXECUTION_REVIEW",num(review,"source_event_id"));return false;}
        }
        // A historical task may not claim a lane with ready fresh work. It does
        // not become live if the clock changes; false authority is passed explicitly.
        if(!live && candidate(lane,true,now)!=null)return false;
        return jdbc.update("UPDATE shared_market_event_delivery SET analysis_status='RUNNING',owner_token=?,analysis_started_at=CURRENT_TIMESTAMP(6),analysis_completed_at=NULL WHERE source_event_id=? AND analysis_status IN ('PENDING','HISTORICAL_DEFERRED','EXECUTION_RETRY')",token,id)==1;
    }

    private boolean isolateAnalysisOnlyReview(Map<String,Object> review) {
        long id=num(review,"source_event_id");
        // Completion marker is mandatory. Age alone NEVER proves a stopped worker.
        if(review.get("analysis_completed_at")==null || num(review,"phase")!=4)return false;
        var workByEvent=jdbc.queryForList("SELECT signal_id FROM signal_processing_work WHERE source_event_id=? LIMIT 1 FOR UPDATE",id);
        var workByCandle=jdbc.queryForList("SELECT signal_id FROM signal_processing_work WHERE symbol=? AND candle_open_time=? AND interval_code=? LIMIT 1 FOR UPDATE",
            review.get("symbol"),review.get("candle_open_time"),review.get("interval_code"));
        if(!workByEvent.isEmpty() || !workByCandle.isEmpty())return false;
        var signals=jdbc.queryForList("SELECT id FROM trade_signal WHERE symbol=? AND interval_code=? AND candle_open_time=? FOR UPDATE",
            review.get("symbol"),review.get("interval_code"),review.get("candle_open_time"));
        for(var signal:signals) {
            if(!jdbc.queryForList("SELECT signal_id FROM signal_processing_work WHERE signal_id=? FOR UPDATE",signal.get("id")).isEmpty())return false;
            if(!jdbc.queryForList("SELECT id FROM wallet_trade WHERE signal_id=? LIMIT 1 FOR UPDATE",signal.get("id")).isEmpty())return false;
        }
        jdbc.update("UPDATE shared_market_event_delivery SET analysis_status='ANALYSIS_ONLY_REVIEW' WHERE source_event_id=? AND analysis_status='REVIEW_REQUIRED'",id);
        // Original error, ownership, timestamps and signal remain intact. This is
        // an isolated analysis failure, NOT a claim that analysis succeeded.
        log.info("[FIX-138][ANALYSIS_REVIEW_ISOLATED] event={}; completed attempt, no processing work or wallet evidence; no retry; transaction pending commit",id);
        return true;
    }
    private void blocked(Lane lane,String reason,long event) {
        long now=System.nanoTime();
        Long previous=lastBlockedLog.get(lane);
        if(previous==null || now-previous>=TimeUnit.SECONDS.toNanos(30)) {
            lastBlockedLog.put(lane,now);
            log.warn("[FIX-138][LANE_BLOCKED] lane={}, reason={}, event={}",lane,reason,event);
        }
    }

    private void run(Lane lane,Map<String,Object> e,boolean live,String token,long queued) {
        long id=num(e,"source_event_id");
        String outcome="REVIEW_REQUIRED";
        log.info("[FIX-138][TASK_START] mode={}, event={}, lane={}, queueWaitMs={}, closeAgeMs={}",live?"LIVE":"HISTORY",id,lane,
            (System.nanoTime()-queued)/1_000_000,java.time.Duration.between(time(e,"candle_close_time"),Instant.now()).toMillis());
        try(var audit=CandleInputAudit.open(jdbc,tx.getTransactionManager(),(live?"LIVE:":"HISTORY:")+id)) {
            var state=jdbc.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=?",lane.symbol());
            var owned=jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE source_event_id=? AND owner_token=? AND analysis_status='RUNNING'",id,token);
            if(!SharedCutoverPolicy.approved(state) || owned.isEmpty()) {
                log.warn("[FIX-138][TASK_ABORTED] event={}, lane={}, reason=OWNERSHIP_OR_CUTOVER",id,lane);
            } else {
                outcome=worker.processShared(new CandleClosedEvent(lane.symbol(),lane.interval(),time(e,"candle_open_time")),time(e,"candle_close_time"),live,id);
            }
        } catch(Exception failure) {
            log.error("[FIX-138][TASK_FAILED] event={}, lane={}",id,lane,failure);
        } finally {
            String result=outcome;
            try {
                Boolean committed=tx.execute(s->{lockSymbol(lane);
                    int count=jdbc.update("UPDATE shared_market_event_delivery SET analysis_status=?,analysis_completed_at=CURRENT_TIMESTAMP(6) WHERE source_event_id=? AND owner_token=? AND analysis_status IN ('RUNNING','REVIEW_REQUIRED')",result,id,token);
                    // FIX-140: persisted work owns retry due-time. The delivery remains
                    // under the same scheduler and is never replayed by legacy recovery.
                    if(count==1 && "EXECUTION_RETRY".equals(result)) {
                        // FIX-140 review: equivalent single-row propagation on both MySQL
                        // and H2; missing/ambiguous work rolls back the delivery transition.
                        var retryWork=jdbc.queryForList("SELECT next_attempt_at FROM signal_processing_work WHERE source_event_id=? AND status='SYMBOL_LOCK_RETRY'",id);
                        if(retryWork.size()!=1 || retryWork.getFirst().get("next_attempt_at")==null)
                            throw new IllegalStateException("FIX-140 retry due-time evidence missing or ambiguous");
                        jdbc.update("UPDATE shared_market_event_delivery SET analysis_not_before=? WHERE source_event_id=? AND owner_token=?",retryWork.getFirst().get("next_attempt_at"),id,token);
                    }
                    if(count==1 && Set.of("COMPLETED","ALREADY_COMPLETED").contains(result))
                        jdbc.update("UPDATE shared_market_event_delivery SET analysis_status='COVERED_BY_LIVE',analysis_completed_at=CURRENT_TIMESTAMP(6) WHERE symbol=? AND interval_code=? AND candle_open_time=? AND analysis_status='HISTORICAL_DEFERRED'",lane.symbol(),lane.interval(),e.get("candle_open_time"));
                    return count==1;
                });
                log.info("[FIX-138][TASK_END] mode={}, event={}, lane={}, outcome={}, outcomeCommitted={}, elapsedMs={}",
                    live?"LIVE":"HISTORY",id,lane,result,committed,(System.nanoTime()-queued)/1_000_000);
            } catch(Exception failure) {
                log.error("[FIX-138][OUTCOME_COMMIT_UNCONFIRMED] event={}, lane={}, outcome={}; no replay",id,lane,result,failure);
            } finally {
                (live?liveActive:historyActive).remove(lane);active.remove(lane);
            }
        }
    }

    /** Monitoring only, outside the live clock. Bound the batch and retain owner.
     * An incomplete REVIEW remains blocking even if no work is visible yet. */
    public void monitor() {
        if(stopped || !source.enabled())return;
        try {
            var rows=jdbc.queryForList("SELECT source_event_id,symbol FROM shared_market_event_delivery WHERE analysis_status='RUNNING' AND analysis_started_at<? ORDER BY analysis_started_at LIMIT 16",Timestamp.from(Instant.now().minusSeconds(300)));
            for(var row:rows)tx.executeWithoutResult(s->{
                jdbc.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE",row.get("symbol"));
                int count=jdbc.update("UPDATE shared_market_event_delivery SET analysis_status='REVIEW_REQUIRED',last_error='FIX-138: completion unconfirmed after five minutes; owner retained, no automatic retry' WHERE source_event_id=? AND analysis_status='RUNNING' AND analysis_started_at<?",row.get("source_event_id"),Timestamp.from(Instant.now().minusSeconds(300)));
                if(count==1)log.warn("[FIX-138][OWNER_TIMEOUT] event={}; retained for review",row.get("source_event_id"));
            });
        } catch(Exception failure) {log.error("[FIX-138][OWNER_MONITOR_FAILED]",failure);}
    }
}
