package com.crypto.shared;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.indicator.event.CandleClosedAnalysisWorker;
import com.crypto.indicator.event.CandleClosedEvent;
import com.crypto.position.service.LivePositionProtectionService;
import com.crypto.debug.monitor.service.PriceMoveMonitorService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** FIX-132 independent consumer. The immutable source is never claimed/updated.
 * Each phase fences on the local symbol row through COMMIT, then releases all
 * locks before the next phase. Unknown analysis outcomes are never auto-retried.
 * Durable rows are the backlog; executors never hold an unbounded event queue. */
@Component
public class SharedMarketConsumer {
    private static final Logger log=LoggerFactory.getLogger(SharedMarketConsumer.class);
    private final SharedMarketSource source;
    private final JdbcTemplate jdbc;
    private final CoinConfigurationService coins;
    private final LivePositionProtectionService protection;
    private final PriceMoveMonitorService observer;
    private final CandleClosedAnalysisWorker analysis;
    private final SharedAnalysisDispatcher analysisDispatch;
    private final TransactionTemplate transaction;
    private final ExecutorService prices=Executors.newFixedThreadPool(2);
    private final Set<String> activePrices=ConcurrentHashMap.newKeySet();
    private final String owner=UUID.randomUUID().toString();
    private final ScheduledExecutorService discoveryClock=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"fix132-discovery"));
    private final ScheduledExecutorService priceClock=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"fix132-price-dispatch"));
    private final ScheduledExecutorService analysisClock=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"fix132-analysis-dispatch"));
    private volatile boolean stopped;
    private int nextSymbol;
    private final ScheduledExecutorService historyClock=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"fix138-history-dispatch"));
    private final ScheduledExecutorService monitorClock=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"fix138-analysis-monitor"));
    private final Map<String,Long> discoveryLogTimes=new HashMap<>();
    private volatile String lastSourceStatus="";
    private volatile long lastSourceLog;
    private void sourceStatus(String status,String detail) {
        if(status.equals(lastSourceStatus))return;
        jdbc.update("INSERT INTO shared_market_health(component,status,detail) VALUES('SOURCE',?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),detail=VALUES(detail)",status,detail);
        lastSourceStatus=status;
    }
    public SharedMarketConsumer(SharedMarketSource source,JdbcTemplate jdbc,CoinConfigurationService coins,
            LivePositionProtectionService protection,PriceMoveMonitorService observer,CandleClosedAnalysisWorker analysis,
            @Qualifier("sharedLiveAnalysisExecutor") Executor executor,
            @Qualifier("sharedHistoricalAnalysisExecutor") Executor historicalExecutor,PlatformTransactionManager manager) {
        this.source=source;this.jdbc=jdbc;this.coins=coins;this.protection=protection;this.observer=observer;
        this.analysis=analysis;transaction=new TransactionTemplate(manager);
        analysisDispatch=new SharedAnalysisDispatcher(source,jdbc,coins,
            ()->intervals==null?List.of("1m"):intervals.getIntervals(),analysis,executor,historicalExecutor,manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @org.springframework.beans.factory.annotation.Value("${shared-market.activation-approved:false}")
    private boolean activationApproved;
    // FIX-140: disabling history is explicit; candles and saved decision context remain intact.
    @org.springframework.beans.factory.annotation.Value("${shared-market.analysis.historical-enabled:true}")
    private boolean historicalEnabled=true;
    // FIX-141 Phase 2 review correction: reconcileOwnerTimeoutReviews() no longer mutates
    // analysis_status - it only logs candidates for manual review (see
    // SharedAnalysisDispatcher#reconcileOwnerTimeoutReviews). This flag now gates whether
    // that diagnostic logging runs at all; off by default.
    @org.springframework.beans.factory.annotation.Value("${shared-market.analysis.owner-timeout-reconciliation-enabled:false}")
    private boolean ownerTimeoutReconciliationEnabled=false;
    @org.springframework.beans.factory.annotation.Value("${shared-market.analysis.owner-timeout-reconciliation-quiet-seconds:3600}")
    private long ownerTimeoutReconciliationQuietSeconds=3600;
    @org.springframework.beans.factory.annotation.Autowired
    private com.crypto.client.config.binance.BinanceMarketDataProperties intervals;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void start() {
        analysisDispatch.historicalEnabled(historicalEnabled);
        analysisDispatch.ownerTimeoutReconciliationEnabled(ownerTimeoutReconciliationEnabled);
        analysisDispatch.ownerTimeoutQuietSeconds(ownerTimeoutReconciliationQuietSeconds);
        validateCutover();
        if("OFF".equals(source.mode()))return;
        if(source.enabled())for(String symbol:coins.enabledSymbols()) {
            // Publish immutable cutover evidence only after every startup check passed.
            jdbc.update("INSERT IGNORE INTO shared_market_cutover_history(symbol,cutover_at,cutover_sequence) SELECT symbol,cutover_at,cutover_sequence FROM shared_market_consumer_state WHERE symbol=?",symbol);
        }
        // Independent clocks: slow source reads or CallerRuns analysis saturation
        // cannot monopolize Spring's default scheduler and stop price dispatch.
        discoveryClock.scheduleWithFixedDelay(this::discover,0,100,TimeUnit.MILLISECONDS);
        priceClock.scheduleWithFixedDelay(this::dispatchPrices,0,50,TimeUnit.MILLISECONDS);
        analysisClock.scheduleWithFixedDelay(this::dispatchAnalysis,0,100,TimeUnit.MILLISECONDS);
        if(historicalEnabled)historyClock.scheduleWithFixedDelay(this::dispatchHistoricalAnalysis,1000,1000,TimeUnit.MILLISECONDS);
        log.info("[FIX-140][HISTORY_CONFIGURATION] enabled={}, savedSignalsAndCandlesRetained=true",historicalEnabled);
        monitorClock.scheduleWithFixedDelay(analysisDispatch::monitor,30,30,TimeUnit.SECONDS);
        // FIX-141 Phase 2: runs independently of claim()'s REVIEW_BATCH_LIMIT check, so a lane
        // that crossed 16 unresolved reviews can still drain over successive runs.
        monitorClock.scheduleWithFixedDelay(analysisDispatch::reconcileAnalysisOnlyReviews,60,60,TimeUnit.SECONDS);
        // FIX-141 Phase 2 review correction: owner-timeout reconciliation is diagnostic-only
        // (logs candidates, performs no mutation) and off until the team enables it and sets a
        // quiet window (see SharedAnalysisDispatcher#ownerTimeoutReconciliationEnabled/Seconds);
        // scheduling it unconditionally here is safe since it no-ops while disabled, and even
        // enabled it cannot change analysis_status itself.
        monitorClock.scheduleWithFixedDelay(analysisDispatch::reconcileOwnerTimeoutReviews,300,300,TimeUnit.SECONDS);
        log.info("[FIX-138][ANALYSIS_CONFIGURATION] liveThreads=8, historyThreads=1, independentClocks=true, livePriority=true, historicalWalletExecution=false");
    }
    public void validateCutover() {
        log.info("[FIX-132][SOURCE_CONFIGURATION] mode={}, readPool=3, priceWorkers=2, pendingPerSymbol=200, publicationSource=collector",source.mode());
        if(!source.enabled())return;
        if(!activationApproved)throw new IllegalStateException("FIX-132 LIVE requires explicit measured cutover acceptance (activation-approved)");
        var unattributed=jdbc.queryForList("SELECT signal_id,status FROM signal_processing_work WHERE source_event_id IS NULL AND status NOT IN ('COMPLETED','EXPIRED','LOCK_RETRY_EXHAUSTED') ORDER BY signal_id LIMIT 25");
        if(!unattributed.isEmpty())throw new IllegalStateException("[FIX-132][UNATTRIBUTED_WORK] reconcile before LIVE: "+unattributed);
        var invalidOwners=jdbc.queryForList("""
            SELECT w.signal_id,w.status,w.source_event_id FROM signal_processing_work w
            LEFT JOIN shared_market_event_delivery d ON d.source_event_id=w.source_event_id
            WHERE w.source_event_id IS NOT NULL AND w.status NOT IN ('COMPLETED','EXPIRED','LOCK_RETRY_EXHAUSTED')
            AND (d.source_event_id IS NULL OR w.symbol<>d.symbol OR w.interval_code<>d.interval_code
                 OR w.candle_open_time<>d.candle_open_time OR d.closed=0
                 OR d.analysis_status NOT IN ('RUNNING','REVIEW_REQUIRED','EXECUTION_RETRY')) ORDER BY w.signal_id LIMIT 25
            """);
        if(!invalidOwners.isEmpty())throw new IllegalStateException("[FIX-132][PROCESSING_OWNER_INCONSISTENT] reconcile before LIVE: "+invalidOwners);
        for(String symbol:coins.enabledSymbols()) {
            var state=jdbc.queryForList("SELECT * FROM shared_market_consumer_state WHERE symbol=?",symbol);
            if(state.isEmpty() || !SharedCutoverPolicy.approved(state.getFirst()))
                throw new IllegalStateException("FIX-132 missing/unapproved/review-required cutover state for "+symbol);
            var boundary=state.getFirst();
            if(num(boundary,"cutover_sequence")<0 || num(boundary,"discovered_sequence")<num(boundary,"cutover_sequence"))
                throw new IllegalStateException("FIX-132 invalid sequence boundary for "+symbol);
            Integer approval=jdbc.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_approval WHERE symbol=? AND cutover_sequence=? AND cutover_at=? AND approved_by=? AND approved_at=? AND approval_reference=?",Integer.class,
                symbol,boundary.get("approved_sequence"),boundary.get("approved_cutover_at"),boundary.get("approved_by"),boundary.get("approved_at"),boundary.get("approval_reference"));
            if(approval==0)throw new IllegalStateException("FIX-132 missing approval audit for "+symbol);
            Long latest=source.feedReader().queryForObject("SELECT last_sequence FROM market_data_stream_cursor WHERE symbol=?",Long.class,symbol);
            if(latest==null || latest<num(state.getFirst(),"discovered_sequence"))throw new IllegalStateException("FIX-132 source sequence regressed for "+symbol);
            Set<String> required=new HashSet<>(intervals.getIntervals());required.add("1m");
            for(String interval:required) {
                Long count=source.reader().queryForObject("SELECT COUNT(*) FROM (SELECT id FROM candle WHERE symbol=? AND interval_code=? AND closed=1 LIMIT 300) c",Long.class,symbol,interval);
                if(count==null || count<300)throw new IllegalStateException("FIX-132 incomplete source history: "+symbol+" "+interval);
            }
        }
    }

    public void discover() {
        if(stopped || "OFF".equals(source.mode())) return;
        try {
            for(String symbol:coins.enabledSymbols()) {
                var state=jdbc.queryForList("SELECT * FROM shared_market_consumer_state WHERE symbol=?",symbol);
                if(state.isEmpty()) {
                    if(source.enabled()) { log.error("[FIX-132][REVIEW_REQUIRED] symbol={}; explicit cutover boundary missing",symbol); continue; }
                    var seq=source.feedReader().queryForList("SELECT last_sequence FROM market_data_stream_cursor WHERE symbol=?",symbol);
                    if(seq.isEmpty()) continue;
                    long n=((Number)seq.getFirst().get("last_sequence")).longValue();
                    jdbc.update("INSERT IGNORE INTO shared_market_consumer_state(symbol,cutover_sequence,cutover_at,discovered_sequence,status,cutover_source) VALUES(?,?,CURRENT_TIMESTAMP(6),?,'PENDING_CUTOVER','AUTO_OBSERVED')",symbol,n,n);
                    continue;
                }
                if(!SharedCutoverPolicy.admitted(state.getFirst(),source.enabled())) continue;
                int pending=jdbc.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE symbol=? AND phase<4",Integer.class,symbol);
                boolean discoveryHeartbeat=System.nanoTime()-discoveryLogTimes.getOrDefault(symbol,0L)>=TimeUnit.SECONDS.toNanos(30);
                if(discoveryHeartbeat)discoveryLogTimes.put(symbol,System.nanoTime());
                if(pending>=200) {
                    if(discoveryHeartbeat)log.warn("[FIX-132][CONSUMER_LAG] symbol={}, pending={}; discovery paused before analysis admission",symbol,pending);
                    continue;
                }
                long after=((Number)state.getFirst().get("discovered_sequence")).longValue();
                var rows=source.feedReader().queryForList("SELECT * FROM market_data_stream_event WHERE symbol=? AND symbol_sequence>? ORDER BY symbol_sequence LIMIT ?",symbol,after,Math.min(100,200-pending));
                log.debug("[FIX-137][DISCOVERY_BATCH] symbol={}, afterSequence={}, pending={}, fetched={}",
                    symbol,after,pending,rows.size());
                if(discoveryHeartbeat)log.info("[FIX-138][DISCOVERY_PROGRESS] symbol={}, afterSequence={}, pendingPricePhases={}, fetched={}, lastFetchedObservationUtc={}",
                    symbol,after,pending,rows.size(),rows.isEmpty()?null:time(rows.getLast(),"observed_at"));
                // Shared connection is returned before any local transaction starts.
                transaction.executeWithoutResult(tx->{
                    var locked=lockSymbol(symbol);
                    if(!SharedCutoverPolicy.admitted(locked,source.enabled()) || ((Number)locked.get("discovered_sequence")).longValue()!=after) return;
                    long next=after;
                    for(var e:rows) {
                        long sequence=num(e,"symbol_sequence");
                        if(sequence!=next+1) {
                            jdbc.update("UPDATE shared_market_consumer_state SET status='SEQUENCE_GAP' WHERE symbol=?",symbol);
                            log.error("[FIX-132][SEQUENCE_GAP] symbol={}, expected={}, found={}",symbol,next+1,sequence);break;
                        }
                        jdbc.update("""
                            INSERT INTO shared_market_event_delivery(source_event_id,symbol,symbol_sequence,interval_code,
                            candle_open_time,candle_close_time,closed,observed_at,received_at,source_created_at,price,source,classification)
                            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                            """,e.get("id"),symbol,sequence,e.get("interval_code"),e.get("candle_open_time"),e.get("candle_close_time"),
                            e.get("closed"),e.get("observed_at"),e.get("received_at"),e.get("created_at"),e.get("price"),e.get("source"),e.get("classification"));
                        next=sequence;
                    }
                    jdbc.update("UPDATE shared_market_consumer_state SET discovered_sequence=? WHERE symbol=?",next,symbol);
                });
            }
            sourceStatus("AVAILABLE",null);
        } catch(Exception e) {
            try { sourceStatus("SOURCE_UNAVAILABLE",shortError(e)); } catch(Exception diagnostic) { log.error("[FIX-132][HEALTH_RECORD_FAILED]",diagnostic); }
            long now=System.nanoTime();
            if(now-lastSourceLog>TimeUnit.SECONDS.toNanos(5)) {
                lastSourceLog=now;log.error("[FIX-132][SOURCE_UNAVAILABLE] discovery failed; checkpoint not silently advanced",e);
            }
        }
    }
    public void dispatchPrices() {
        if(stopped || "OFF".equals(source.mode())) return;
        try {
            // At most TWO submitted tasks, so newFixedThreadPool's internal queue cannot grow.
            List<String> symbols=coins.enabledSymbols();
            for(int checked=0;checked<symbols.size();checked++) {
                if(activePrices.size()>=2) break;
                String symbol=symbols.get(Math.floorMod(nextSymbol++,symbols.size()));
                if(!activePrices.add(symbol))continue;
                try { prices.execute(()->{try {
                    int count=jdbc.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE symbol=? AND phase<4",Integer.class,symbol);
                    for(int n=0;n<Math.min(8,count)&&!stopped;n++) processNext(symbol);
                } finally { activePrices.remove(symbol); }}); }
                catch(RuntimeException rejected) { activePrices.remove(symbol); throw rejected; }
            }
        } catch(Exception e) { log.error("[FIX-132][DISPATCH_FAILED]",e); }
    }
    private Map<String,Object> lockSymbol(String symbol) {
        return jdbc.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE",symbol);
    }
    private boolean beginProtection(String symbol,long id,String attempt) {
        return Boolean.TRUE.equals(transaction.execute(tx->{
            var state=lockSymbol(symbol);
            if(!SharedCutoverPolicy.admitted(state,source.enabled()))return false;
            var event=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",id);
            if(num(event,"phase")!=1 || !"1m".equals(event.get("interval_code")))return true;
            if(event.get("protection_started_at")==null) {
                // Durable START precedes business. A process crash cannot turn an
                // already-started wallet attempt into a fresh automatic retry.
                jdbc.update("UPDATE shared_market_event_delivery SET protection_started_at=CURRENT_TIMESTAMP(6),protection_owner=?,status='PROTECTION_RUNNING' WHERE source_event_id=?",attempt,id);
                return true;
            }
            if(attempt.equals(event.get("protection_owner")))return true;
            // Never steal/re-execute a started attempt. After 30s, fence it into
            // review; a worker already in its transaction holds this row through
            // commit, while one resuming later must observe REVIEW_REQUIRED.
            if(time(event,"protection_started_at").plusSeconds(30).isBefore(Instant.now())) {
                jdbc.update("UPDATE shared_market_event_delivery SET status='REVIEW_REQUIRED',last_error='Started protection outcome requires reconciliation; no crash retry' WHERE source_event_id=?",id);
                jdbc.update("UPDATE shared_market_consumer_state SET status='REVIEW_REQUIRED' WHERE symbol=?",symbol);
                log.error("[FIX-132][REVIEW_REQUIRED] symbol={}, event={}; previous protection attempt unconfirmed",symbol,id);
            }
            return false;
        }));
    }
    void processNext(String symbol) {
        Long id=null;
        try {
            var candidates=jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE symbol=? AND phase<4 ORDER BY symbol_sequence LIMIT 1",symbol);
            if(candidates.isEmpty())return;
            id=num(candidates.getFirst(),"source_event_id");
            log.debug("[FIX-137][DELIVERY_START] event={}, symbol={}",id,symbol);
            final long eventId=id;
            final String attemptToken=UUID.randomUUID().toString();
            // Small bounded turn: one event, four independently committed phases.
            for(int stage=0;stage<4;stage++) {
                final int expected=stage;
                if(source.enabled() && expected==1 && !beginProtection(symbol,eventId,attemptToken))return;
                int attempts=0;
                while(true) {
                try {
                transaction.executeWithoutResult(tx->{
                    var state=lockSymbol(symbol);
                    if(!SharedCutoverPolicy.admitted(state,source.enabled()))return;
                    var e=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",eventId);
                    if(num(e,"phase")!=expected || "REVIEW_REQUIRED".equals(e.get("status")))return;
                    if(source.enabled() && expected==1 && "1m".equals(e.get("interval_code"))
                            && !attemptToken.equals(e.get("protection_owner")))return;
                    if(!source.enabled()) {
                        jdbc.update("UPDATE shared_market_event_delivery SET phase=4,status='OBSERVED',analysis_status='OBSERVED' WHERE source_event_id=?",eventId);return;
                    }
                    boolean postCutover=num(e,"symbol_sequence")>num(state,"cutover_sequence")
                        && time(e,"observed_at")!=null && time(e,"observed_at").isAfter(time(state,"cutover_at"));
                    boolean canonical="1m".equals(e.get("interval_code"));
                    String reason = !canonical ? "NON_CANONICAL_INTERVAL"
                        : !postCutover ? "PRE_CUTOVER_OR_UNKNOWN_TIME"
                        : ((java.math.BigDecimal)e.get("price")).signum()<=0 ? "NON_POSITIVE_PRICE"
                        : SharedEventPolicy.priceReason(str(e,"source"),str(e,"classification"),time(e,"observed_at"),time(state,"last_observed_at"),Instant.now());
                    boolean eligible="ELIGIBLE".equals(reason);
                    jdbc.update("UPDATE shared_market_event_delivery SET owner_token=?,status='PROCESSING' WHERE source_event_id=?",owner,eventId);
                    if(expected==0 && canonical && e.get("observed_at")!=null) {
                        jdbc.update("""
                            INSERT IGNORE INTO market_price_event(symbol,observed_at,price,source,source_event_id,source_sequence,source_received_at,delivery_status)
                            VALUES(?,?,?,'COLLECTOR_KLINE_LIVE',?,?,?,'PENDING')
                            """,symbol,e.get("observed_at"),e.get("price"),eventId,e.get("symbol_sequence"),e.get("received_at"));
                    }
                    if(expected==1) {
                        jdbc.update("UPDATE shared_market_event_delivery SET eligibility_reason=? WHERE source_event_id=?",reason,eventId);
                    }
                    if(expected==1 && canonical) {
                        if(eligible) {
                            protection.onPrice(symbol,(java.math.BigDecimal)e.get("price"));
                            jdbc.update("UPDATE shared_market_consumer_state SET last_observed_at=?,last_price=? WHERE symbol=?",e.get("observed_at"),e.get("price"),symbol);
                        }
                        jdbc.update("UPDATE market_price_event SET delivery_status=? WHERE source_event_id=?",eligible?"APPLIED":"HISTORICAL_ONLY",eventId);
                        jdbc.update("UPDATE shared_market_event_delivery SET protection_completed_at=CURRENT_TIMESTAMP(6) WHERE source_event_id=?",eventId);
                        if(!eligible) log.debug("[FIX-132][PRICE_EXCLUDED] symbol={}, event={}, reason={}",symbol,eventId,reason);
                    }
                    if(expected==2 && canonical) {
                        String applied=jdbc.query("SELECT delivery_status FROM market_price_event WHERE source_event_id=?",r->r.next()?r.getString(1):"UNKNOWN",eventId);
                        if("APPLIED".equals(applied)) observer.onPrice(symbol,(java.math.BigDecimal)e.get("price"),Instant.now());
                        jdbc.update("UPDATE shared_market_event_delivery SET observer_status=? WHERE source_event_id=?","APPLIED".equals(applied)?"COMPLETED":"NOT_APPLICABLE",eventId);
                    }
                    if(expected==3) {
                        boolean live=postCutover && !time(e,"observed_at").isAfter(Instant.now())
                            && time(e,"candle_close_time").isAfter(time(state,"cutover_at"))
                            && "LIVE".equals(e.get("classification")) && "LIVE_WEBSOCKET".equals(e.get("source"));
                        jdbc.update("UPDATE shared_market_event_delivery SET status=?,analysis_status=? WHERE source_event_id=?",
                            live?"COMPLETED":"HISTORICAL_ONLY",truth(e.get("closed"))?"PENDING":"NOT_APPLICABLE",eventId);
                        if(!live && truth(e.get("closed"))) {
                            Instant afterGrace=time(e,"candle_close_time").plusSeconds(SharedEventPolicy.closeGraceSeconds(str(e,"interval_code"))).plusNanos(1_000_000);
                            jdbc.update("UPDATE shared_market_event_delivery SET analysis_status='HISTORICAL_DEFERRED',analysis_not_before=? WHERE source_event_id=?",Timestamp.from(afterGrace),eventId);
                            log.debug("[FIX-132][HISTORICAL_DEFERRED] event={}, until={}",eventId,afterGrace);
                        }
                    }
                    jdbc.update("UPDATE shared_market_event_delivery SET phase=? WHERE source_event_id=?",expected+1,eventId);
                });
                log.debug("[FIX-137][DELIVERY_STAGE_RETURNED] event={}, symbol={}, expectedPhase={}; transaction returned, may have been a guarded no-op",eventId,symbol,expected);
                break;
                } catch(com.crypto.infrastructure.transaction.InitialPositionLockDeadlock initial) {
                    // FIX-124's one safe retry is retained AFTER rollback. This marker
                    // is emitted only before position evaluation/wallet effects begin.
                    if(expected!=1 || ++attempts>=2)throw initial;
                    log.warn("[FIX-132][INITIAL_LOCK_RETRY] symbol={}, event={}",symbol,eventId);
                } catch(RuntimeException failure) {
                    if(expected!=2)throw failure;
                    // FIX-124 observer failures never prevented future protection.
                    // Record uncertainty without retrying diagnostic side effects.
                    transaction.executeWithoutResult(tx->{lockSymbol(symbol);
                        jdbc.update("UPDATE shared_market_event_delivery SET phase=3,observer_status='REVIEW_REQUIRED',last_error=? WHERE source_event_id=? AND phase=2",shortError(failure),eventId);
                    });
                    log.warn("[FIX-132][OBSERVER_REVIEW_REQUIRED] event={}; protection retained, no observer retry",eventId,failure);
                    break;
                }
                }
            }
        } catch(Exception failure) {
            if(id!=null) {
                final long failedId=id;
                // Unknown commits are reconciled manually. Never overwrite a committed
                // phase marker or replay a potentially committed wallet side effect.
                try { transaction.executeWithoutResult(tx->{lockSymbol(symbol);
                    jdbc.update("UPDATE shared_market_consumer_state SET status='REVIEW_REQUIRED' WHERE symbol=?",symbol);
                    jdbc.update("UPDATE shared_market_event_delivery SET status='REVIEW_REQUIRED',last_error=? WHERE source_event_id=?",shortError(failure),failedId);
                }); } catch(Exception recording) { log.error("[FIX-132][FAILURE_RECORD_FAILED] symbol={}",symbol,recording); }
            }
            log.error("[FIX-132][REVIEW_REQUIRED] symbol={}, event={}; no automatic wallet retry",symbol,id,failure);
        }
    }
    public void dispatchAnalysis() { analysisDispatch.live(); }
    public void dispatchHistoricalAnalysis() { analysisDispatch.historical(); }
    static long num(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    static String str(Map<String,Object> row,String key) { return String.valueOf(row.get(key)); }
    static Instant time(Map<String,Object> row,String key) { return row.get(key)==null?null:((Timestamp)row.get(key)).toInstant(); }
    static boolean truth(Object value) { return Boolean.TRUE.equals(value)||"1".equals(String.valueOf(value)); }
    static String shortError(Exception e) { String s=e.toString();return s.substring(0,Math.min(1000,s.length())); }
    @PreDestroy public void stop() {
        stopped=true;analysisDispatch.stop();historyClock.shutdown();monitorClock.shutdown();discoveryClock.shutdownNow();priceClock.shutdownNow();analysisClock.shutdown();prices.shutdown();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        try {
            boolean priceDone=prices.awaitTermination(30,TimeUnit.SECONDS);
            boolean analysisDone=analysisClock.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            boolean historyDone=historyClock.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            boolean monitorDone=monitorClock.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            if(!priceDone || !analysisDone || !historyDone || !monitorDone)log.error("[FIX-132][SHUTDOWN_REVIEW_REQUIRED] worker did not drain within 30 seconds; reconcile durable phases before restarting execution");
        } catch(InterruptedException e) {Thread.currentThread().interrupt();log.error("[FIX-132][SHUTDOWN_REVIEW_REQUIRED] drain interrupted",e);}
    }
}
