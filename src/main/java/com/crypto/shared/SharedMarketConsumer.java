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
    private final Executor analysisExecutor;
    private final TransactionTemplate transaction;
    private final ExecutorService prices=Executors.newFixedThreadPool(2);
    private final Set<String> activePrices=ConcurrentHashMap.newKeySet(), activeAnalysis=ConcurrentHashMap.newKeySet();
    private final String owner=UUID.randomUUID().toString();
    private final ScheduledExecutorService discoveryClock=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"fix132-discovery"));
    private final ScheduledExecutorService priceClock=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"fix132-price-dispatch"));
    private final ScheduledExecutorService analysisClock=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"fix132-analysis-dispatch"));
    private volatile boolean stopped;
    private int nextSymbol;
    private volatile String lastSourceStatus="";
    private volatile long lastSourceLog;
    private void sourceStatus(String status,String detail) {
        if(status.equals(lastSourceStatus))return;
        jdbc.update("INSERT INTO shared_market_health(component,status,detail) VALUES('SOURCE',?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),detail=VALUES(detail)",status,detail);
        lastSourceStatus=status;
    }
    public SharedMarketConsumer(SharedMarketSource source,JdbcTemplate jdbc,CoinConfigurationService coins,
            LivePositionProtectionService protection,PriceMoveMonitorService observer,CandleClosedAnalysisWorker analysis,
            @Qualifier("candleAnalysisExecutor") Executor executor,PlatformTransactionManager manager) {
        this.source=source;this.jdbc=jdbc;this.coins=coins;this.protection=protection;this.observer=observer;
        this.analysis=analysis;analysisExecutor=executor;transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @org.springframework.beans.factory.annotation.Value("${shared-market.activation-approved:false}")
    private boolean activationApproved;
    @org.springframework.beans.factory.annotation.Autowired
    private com.crypto.client.config.binance.BinanceMarketDataProperties intervals;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void start() {
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
    }
    public void validateCutover() {
        log.info("[FIX-132][SOURCE_CONFIGURATION] mode={}, readPool=3, priceWorkers=2, pendingPerSymbol=200, publicationSource=collector",source.mode());
        if(!source.enabled())return;
        if(!activationApproved)throw new IllegalStateException("FIX-132 LIVE requires explicit measured cutover acceptance (activation-approved)");
        var unattributed=jdbc.queryForList("SELECT signal_id,status FROM signal_processing_work WHERE source_event_id IS NULL AND status NOT IN ('COMPLETED','EXPIRED') ORDER BY signal_id LIMIT 25");
        if(!unattributed.isEmpty())throw new IllegalStateException("[FIX-132][UNATTRIBUTED_WORK] reconcile before LIVE: "+unattributed);
        var invalidOwners=jdbc.queryForList("""
            SELECT w.signal_id,w.status,w.source_event_id FROM signal_processing_work w
            LEFT JOIN shared_market_event_delivery d ON d.source_event_id=w.source_event_id
            WHERE w.source_event_id IS NOT NULL AND w.status NOT IN ('COMPLETED','EXPIRED')
            AND (d.source_event_id IS NULL OR w.symbol<>d.symbol OR w.interval_code<>d.interval_code
                 OR w.candle_open_time<>d.candle_open_time OR d.closed=0
                 OR d.analysis_status NOT IN ('RUNNING','REVIEW_REQUIRED')) ORDER BY w.signal_id LIMIT 25
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
                if(pending>=200) { log.warn("[FIX-132][CONSUMER_LAG] symbol={}, pending={}; discovery paused",symbol,pending); continue; }
                long after=((Number)state.getFirst().get("discovered_sequence")).longValue();
                var rows=source.feedReader().queryForList("SELECT * FROM market_data_stream_event WHERE symbol=? AND symbol_sequence>? ORDER BY symbol_sequence LIMIT ?",symbol,after,Math.min(100,200-pending));
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
    public void dispatchAnalysis() {
        if(stopped || !source.enabled())return;
        try {
            // Monitoring threshold only: retain the SAME owner; never reassign or
            // retry a started analysis. A still-running owner may report completion.
            jdbc.update("UPDATE shared_market_event_delivery SET analysis_status='REVIEW_REQUIRED',last_error='Analysis completion unconfirmed after five minutes; no automatic retry' WHERE analysis_status='RUNNING' AND analysis_started_at<TIMESTAMPADD(MINUTE,-5,CURRENT_TIMESTAMP(6))");
            var rows=jdbc.queryForList("""
                SELECT d.* FROM shared_market_event_delivery d
                WHERE d.analysis_status IN ('PENDING','HISTORICAL_DEFERRED') AND d.phase=4
                AND (d.analysis_not_before IS NULL OR d.analysis_not_before<CURRENT_TIMESTAMP(6))
                AND EXISTS(SELECT 1 FROM shared_market_consumer_state s WHERE s.symbol=d.symbol AND s.status='READY')
                AND NOT EXISTS(SELECT 1 FROM shared_market_event_delivery p WHERE p.symbol=d.symbol AND p.interval_code=d.interval_code
                AND p.symbol_sequence<d.symbol_sequence AND p.analysis_status IN ('PENDING','RUNNING','REVIEW_REQUIRED','HISTORICAL_DEFERRED')
                AND NOT(p.analysis_status='HISTORICAL_DEFERRED' AND p.candle_open_time=d.candle_open_time AND d.status='COMPLETED'))
                ORDER BY d.source_event_id LIMIT 16
                """);
            for(var e:rows) {
                String lane=str(e,"symbol")+"|"+str(e,"interval_code");
                if(activeAnalysis.size()>=16 || !activeAnalysis.add(lane)) continue;
                // Claim is durable BEFORE enqueue. A crash after this point requires
                // review, never an automatic second call into the trading worker.
                long id=num(e,"source_event_id");
                int claimed;
                try { claimed=transaction.execute(tx->{
                    // Same lock order as price phases. Do not claim queued analysis after
                    // protection has already quarantined this symbol.
                    var state=lockSymbol(str(e,"symbol"));
                    if(!SharedCutoverPolicy.admitted(state,source.enabled()))return 0;
                    // FIX-132: recheck with CURRENT locking reads, not a repeatable-read
                    // discovery snapshot. The symbol row serializes admissions across JVMs.
                    var candidate=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",id);
                    if(!Set.of("PENDING","HISTORICAL_DEFERRED").contains(str(candidate,"analysis_status"))
                        || num(candidate,"phase")!=4)return 0;
                    var eligible=jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE source_event_id=? AND (analysis_not_before IS NULL OR analysis_not_before<CURRENT_TIMESTAMP(6)) FOR UPDATE",id);
                    if(eligible.isEmpty())return 0;
                    var blockers=jdbc.queryForList("""
                        SELECT source_event_id,analysis_status,candle_open_time,symbol_sequence FROM shared_market_event_delivery
                        WHERE symbol=? AND interval_code=? AND source_event_id<>?
                        AND analysis_status IN ('PENDING','RUNNING','REVIEW_REQUIRED','HISTORICAL_DEFERRED') FOR UPDATE
                        """,str(e,"symbol"),str(e,"interval_code"),id);
                    boolean blocked=blockers.stream().anyMatch(p -> {
                        String status=str(p,"analysis_status");
                        // Even a higher sequence already RUNNING blocks an older deferred row.
                        if(Set.of("RUNNING","REVIEW_REQUIRED").contains(status))return true;
                        if(num(p,"symbol_sequence")>=num(candidate,"symbol_sequence"))return false;
                        return !("HISTORICAL_DEFERRED".equals(status)
                            && Objects.equals(p.get("candle_open_time"),candidate.get("candle_open_time"))
                            && "COMPLETED".equals(candidate.get("status")));
                    });
                    if(blocked)return 0;
                    return jdbc.update("UPDATE shared_market_event_delivery SET analysis_status='RUNNING',owner_token=?,analysis_started_at=CURRENT_TIMESTAMP(6) WHERE source_event_id=? AND analysis_status IN ('PENDING','HISTORICAL_DEFERRED')",owner,id);
                });
                } catch(RuntimeException claimFailure) {
                    // A failed claim must not strand this lane in the local admission set.
                    activeAnalysis.remove(lane);
                    throw claimFailure;
                }
                if(claimed==0) { activeAnalysis.remove(lane);continue; }
                try { analysisExecutor.execute(()->{
                    String outcome="REVIEW_REQUIRED";
                    try(var audit=CandleInputAudit.open(jdbc,transaction.getTransactionManager(),"LIVE:"+id)) {
                        // A symbol may enter review while this task waits in the executor.
                        // This pre-start check is not a claim to solve FIX-125 wallet races.
                        var current=jdbc.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=?",str(e,"symbol"));
                        if(!SharedCutoverPolicy.approved(current))return;
                        boolean live="COMPLETED".equals(e.get("status"));
                        outcome=analysis.processShared(new CandleClosedEvent(str(e,"symbol"),str(e,"interval_code"),time(e,"candle_open_time")),time(e,"candle_close_time"),live,id);
                    } catch(Exception ex) { log.error("[FIX-132][ANALYSIS_FAILED] event={}",id,ex); }
                    finally {
                        final String completedOutcome=outcome;
                        try { transaction.executeWithoutResult(tx->{
                            lockSymbol(str(e,"symbol"));
                            int updated=jdbc.update("UPDATE shared_market_event_delivery SET analysis_status=?,analysis_completed_at=CURRENT_TIMESTAMP(6) WHERE source_event_id=? AND owner_token=? AND analysis_status IN ('RUNNING','REVIEW_REQUIRED')",completedOutcome,id,owner);
                            if(updated==1 && ("COMPLETED".equals(completedOutcome) || "ALREADY_COMPLETED".equals(completedOutcome)))
                                jdbc.update("UPDATE shared_market_event_delivery SET analysis_status='COVERED_BY_LIVE',analysis_completed_at=CURRENT_TIMESTAMP(6) WHERE symbol=? AND interval_code=? AND candle_open_time=? AND analysis_status='HISTORICAL_DEFERRED'",str(e,"symbol"),str(e,"interval_code"),e.get("candle_open_time"));
                        });
                            log.info("[FIX-132][ANALYSIS_COMPLETED] event={}, outcome={}",id,outcome);
                        } finally { activeAnalysis.remove(lane); }
                    }
                }); } catch(RuntimeException rejected) {
                    activeAnalysis.remove(lane);
                    jdbc.update("UPDATE shared_market_event_delivery SET analysis_status='REVIEW_REQUIRED',last_error=? WHERE source_event_id=?",shortError(rejected),id);
                }
            }
        } catch(Exception e) { log.error("[FIX-132][ANALYSIS_DISPATCH_FAILED]",e); }
    }
    static long num(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    static String str(Map<String,Object> row,String key) { return String.valueOf(row.get(key)); }
    static Instant time(Map<String,Object> row,String key) { return row.get(key)==null?null:((Timestamp)row.get(key)).toInstant(); }
    static boolean truth(Object value) { return Boolean.TRUE.equals(value)||"1".equals(String.valueOf(value)); }
    static String shortError(Exception e) { String s=e.toString();return s.substring(0,Math.min(1000,s.length())); }
    @PreDestroy public void stop() {
        stopped=true;discoveryClock.shutdownNow();priceClock.shutdownNow();analysisClock.shutdown();prices.shutdown();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        try {
            boolean priceDone=prices.awaitTermination(30,TimeUnit.SECONDS);
            boolean analysisDone=analysisClock.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            if(!priceDone || !analysisDone)log.error("[FIX-132][SHUTDOWN_REVIEW_REQUIRED] worker did not drain within 30 seconds; reconcile durable phases before restarting execution");
        } catch(InterruptedException e) {Thread.currentThread().interrupt();log.error("[FIX-132][SHUTDOWN_REVIEW_REQUIRED] drain interrupted",e);}
    }
}
