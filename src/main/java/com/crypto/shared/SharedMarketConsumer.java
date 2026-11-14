package com.crypto.shared;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.indicator.event.CandleClosedAnalysisWorker;
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
    // FIX-146: operational fences are local to symbols; configuration errors remain fatal; failed readiness probes fail closed.
    private final SharedTradingReadiness tradingReadiness=new SharedTradingReadiness();
    private final ScheduledExecutorService readinessClock=Executors.newSingleThreadScheduledExecutor(
        task->new Thread(task,"fix146-readiness"));
    private final JdbcTemplate readinessJdbc;
    private final TransactionTemplate readinessTransaction;
    private final SharedAutomaticActivation automaticActivation;
    // FIX-148: deployment policy authorizes evidence-based automatic FIRST cutover.
    @org.springframework.beans.factory.annotation.Value("${shared-market.automatic-first-cutover-enabled:true}")
    private boolean automaticFirstCutoverEnabled;
    private int readinessCursor;
    private volatile List<String> readinessSymbols=List.of();
    // FIX-142 flags remain off until migration, legacy-owner inventory and MySQL acceptance pass.
    @org.springframework.beans.factory.annotation.Value("${shared-market.fix142.enabled:false}")
    private boolean fix142Enabled;
    @org.springframework.beans.factory.annotation.Value("${shared-market.fix142.acknowledgment-enabled:false}")
    private boolean acknowledgmentEnabled;
    // FIX-150: mandatory in Spring, null only in constructor-only tests. Recovery
    // owns a separate transaction; invoke only outside a held symbol lock.
    @org.springframework.beans.factory.annotation.Autowired
    private Fix150ProtectionRecovery protectionRecovery;
    @org.springframework.beans.factory.annotation.Value("${shared-market.analysis.historical-threads:1}")
    private int historicalThreads=1;
    @org.springframework.beans.factory.annotation.Value("${shared-market.fix142.admission-ceiling:32}")
    private int admissionCeiling=32;
    @org.springframework.beans.factory.annotation.Value("${shared-market.fix142.fresh-reserve:4}")
    private int freshReserve=4;
    // Only a known pre-business deferral may reuse this process-local attempt token.
    private final Map<Long,String> deferredProtection=new ConcurrentHashMap<>();
    private final Fix142DeliveryProtocol protocol;
    private final ScheduledExecutorService protectionClock=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"fix142-protection"));
    private final ScheduledExecutorService freshClock=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"fix142-fresh-admission"));
    private final Set<String> activeProtection=ConcurrentHashMap.newKeySet();
    private final ExecutorService protectionPool=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new SynchronousQueue<>(),r->new Thread(r,"fix142-protection-worker"),new ThreadPoolExecutor.AbortPolicy());
    private int nextProtectionSymbol;

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
        readinessJdbc=new JdbcTemplate(Objects.requireNonNull(jdbc.getDataSource()));
        readinessJdbc.setQueryTimeout(2);
        readinessTransaction=new TransactionTemplate(manager);
        readinessTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        readinessTransaction.setTimeout(2);
        automaticActivation=new SharedAutomaticActivation(source,readinessJdbc,manager);
        this.analysis=analysis;protocol=new Fix142DeliveryProtocol(jdbc);transaction=new TransactionTemplate(manager);
        analysisDispatch=new SharedAnalysisDispatcher(source,jdbc,coins,
            ()->intervals==null?List.of("1m"):intervals.getIntervals(),analysis,executor,historicalExecutor,manager);
        analysisDispatch.admissionGate(this::symbolAllowed);
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
        if(fix142Enabled && !source.enabled())throw new IllegalStateException("FIX-142 live scheduling requires LIVE mode");
        if(admissionCeiling<2 || freshReserve<1 || freshReserve>=admissionCeiling)
            throw new IllegalArgumentException("FIX-142 invalid admission ceiling/reserve");
        protocol.ceiling=admissionCeiling;protocol.reserve=freshReserve;
        analysisDispatch.historicalCapacity(historicalThreads);
        analysisDispatch.historicalEnabled(historicalEnabled);
        analysisDispatch.ownerTimeoutReconciliationEnabled(ownerTimeoutReconciliationEnabled);
        analysisDispatch.ownerTimeoutQuietSeconds(ownerTimeoutReconciliationQuietSeconds);
        // FIX-146: do not throw ApplicationReadyEvent for recoverable symbol state.
        // Every dispatch surface uses this gate; diagnostics and operator APIs stay up.
        initializeTradingReadiness();
        analysisDispatch.acknowledgmentEnabled(acknowledgmentEnabled);
        analysisDispatch.freshPriority(fix142Enabled);
        if("OFF".equals(source.mode()))return;
        if(fix142Enabled) {
            freshClock.scheduleWithFixedDelay(this::discoverFresh,0,100,TimeUnit.MILLISECONDS);
            protectionClock.scheduleWithFixedDelay(this::dispatchProtectionTicks,0,50,TimeUnit.MILLISECONDS);
        }
        monitorClock.scheduleWithFixedDelay(analysisDispatch::reconcileCompletedWorkAcknowledgments,60,60,TimeUnit.SECONDS);
        if("OFF".equals(source.mode()))return;
        if(source.enabled())readinessClock.scheduleWithFixedDelay(this::refreshTradingReadiness,10,10,TimeUnit.SECONDS);
        // Independent clocks: slow source reads or CallerRuns analysis saturation
        // cannot monopolize Spring's default scheduler and stop price dispatch.
        discoveryClock.scheduleWithFixedDelay(this::discover,0,100,TimeUnit.MILLISECONDS);
        priceClock.scheduleWithFixedDelay(this::dispatchPrices,0,50,TimeUnit.MILLISECONDS);
        analysisClock.scheduleWithFixedDelay(this::dispatchAnalysis,0,100,TimeUnit.MILLISECONDS);
        if(historicalEnabled)historyClock.scheduleWithFixedDelay(this::dispatchHistoricalAnalysis,1000,1000,TimeUnit.MILLISECONDS);
        log.info("[FIX-140][HISTORY_CONFIGURATION] enabled={}, savedSignalsAndCandlesRetained=true",historicalEnabled);
        monitorClock.scheduleWithFixedDelay(analysisDispatch::monitor,30,30,TimeUnit.SECONDS);
        // FIX-142 review correction: old protection attempts age out of the recent-price
        // selector, so fencing/alerting must not depend on another claim selecting them.
        monitorClock.scheduleWithFixedDelay(this::monitorProtectionAttempts,5,5,TimeUnit.SECONDS);
        // FIX-141 Phase 2: runs independently of claim()'s REVIEW_BATCH_LIMIT check, so a lane
        // that crossed 16 unresolved reviews can still drain over successive runs.
        monitorClock.scheduleWithFixedDelay(analysisDispatch::reconcileAnalysisOnlyReviews,60,60,TimeUnit.SECONDS);
        // FIX-141 Phase 2 review correction: owner-timeout reconciliation is diagnostic-only
        // (logs candidates, performs no mutation) and off until the team enables it and sets a
        // quiet window (see SharedAnalysisDispatcher#ownerTimeoutReconciliationEnabled/Seconds);
        // scheduling it unconditionally here is safe since it no-ops while disabled, and even
        // enabled it cannot change analysis_status itself.
        monitorClock.scheduleWithFixedDelay(analysisDispatch::reconcileOwnerTimeoutReviews,300,300,TimeUnit.SECONDS);
        log.info("[FIX-138][ANALYSIS_CONFIGURATION] liveThreads=8, historyThreads={}, independentClocks=true, livePriority=true, historicalWalletExecution=false",historicalThreads);
    }
    public void validateCutover() {
        log.info("[FIX-132][SOURCE_CONFIGURATION] mode={}, readPool=3, priceWorkers=2, pendingPerSymbol=200, publicationSource=collector",source.mode());
        if(!source.enabled())return;
        if(!activationApproved)throw new IllegalStateException("FIX-132 LIVE requires explicit measured cutover acceptance (activation-approved)");
        var unattributed=jdbc.queryForList(OperatorWorkCancellation.UNATTRIBUTED_BLOCKERS);
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
        for(String symbol:coins.enabledSymbols())validateSymbolCutover(symbol);
    }
    // Shared by strict acceptance checks and per-symbol operational readiness.
    private Map<String,Object> validateSymbolCutover(String symbol) {
            var state=readinessJdbc.queryForList("SELECT * FROM shared_market_consumer_state WHERE symbol=?",symbol);
            if(state.isEmpty() || !SharedCutoverPolicy.approved(state.getFirst()))
                throw new IllegalStateException("FIX-132 missing/unapproved/review-required cutover state for "+symbol);
            var boundary=state.getFirst();
            if(num(boundary,"cutover_sequence")<0 || num(boundary,"discovered_sequence")<num(boundary,"cutover_sequence"))
                throw new IllegalStateException("FIX-132 invalid sequence boundary for "+symbol);
            Integer approval=readinessJdbc.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_approval WHERE symbol=? AND cutover_sequence=? AND cutover_at=? AND approved_by=? AND approved_at=? AND approval_reference=?",Integer.class,
                symbol,boundary.get("approved_sequence"),boundary.get("approved_cutover_at"),boundary.get("approved_by"),boundary.get("approved_at"),boundary.get("approval_reference"));
            if(approval==0)throw new IllegalStateException("FIX-132 missing approval audit for "+symbol);
            Long latest=source.feedReader().queryForObject("SELECT last_sequence FROM market_data_stream_cursor WHERE symbol=?",Long.class,symbol);
            if(latest==null || latest<num(state.getFirst(),"discovered_sequence"))throw new IllegalStateException("FIX-132 source sequence regressed for "+symbol);
            Set<String> required=new HashSet<>(intervals.getIntervals());required.add("1m");
            for(String interval:required) {
                Long count=source.reader().queryForObject("SELECT COUNT(*) FROM (SELECT id FROM candle WHERE symbol=? AND interval_code=? AND closed=1 LIMIT 300) c",Long.class,symbol,interval);
                if(count==null || count<300)throw new IllegalStateException("FIX-132 incomplete source history: "+symbol+" "+interval);
            }
            return boundary;
    }

    private boolean symbolAllowed(String symbol) {
        return !source.enabled() || (readinessSymbols.contains(symbol) || !tradingReadiness.managed())
            && tradingReadiness.allowed(symbol);
    }
    public Map<String,Object> tradingReadiness() {
        var snapshot=new LinkedHashMap<String,Object>(tradingReadiness.snapshot());
        snapshot.put("configuredSymbols",readinessSymbols);
        snapshot.put("automaticFirstCutoverEnabled",automaticFirstCutoverEnabled);
        snapshot.put("waitingCutoverDoesNotAutoApprove",!automaticFirstCutoverEnabled);
        return snapshot;
    }
    void initializeTradingReadiness() {
        if(!source.enabled())return;
        tradingReadiness.begin();
        refreshTradingReadiness();
        log.warn("[FIX-146][STARTUP_READINESS] {}; process stays available, validated first cutovers may activate automatically, uncertain symbols receive no new shared attempts",tradingReadiness.snapshot());
    }
    private static final String SYMBOL_LEGACY_BLOCKERS=OperatorWorkCancellation.UNATTRIBUTED_BLOCKERS
        .replace("WHERE w.source_event_id IS NULL","WHERE w.symbol=? AND w.source_event_id IS NULL");
    private static final String UNSCOPED_BLOCKERS=OperatorWorkCancellation.UNATTRIBUTED_BLOCKERS
        .replace("WHERE w.source_event_id IS NULL","WHERE (w.symbol IS NULL OR TRIM(w.symbol)='') AND w.source_event_id IS NULL");
    // Dedicated clock and round-robin budget: one slow probe cannot monopolize trading
    // clocks or permanently strand later symbols. JDBC timeouts are not total deadlines.
    synchronized void refreshTradingReadiness() {
        if(stopped || !source.enabled())return;
        List<String> symbols;
        try {symbols=List.copyOf(coins.enabledSymbols());}
        catch(RuntimeException failure) {
            for(String symbol:readinessSymbols)recordReadiness(symbol,SharedTradingReadiness.Reason.CHECK_FAILED);
            log.error("[FIX-146][READINESS_INVENTORY_FAILED]",failure);return;
        }
        readinessSymbols=symbols;tradingReadiness.retainSymbols(symbols);
        boolean unscoped;
        try {unscoped=!readinessJdbc.queryForList(UNSCOPED_BLOCKERS).isEmpty()
                || !readinessJdbc.queryForList("SELECT signal_id FROM signal_processing_work WHERE source_event_id IS NOT NULL AND (symbol IS NULL OR TRIM(symbol)='') AND status NOT IN ('COMPLETED','EXPIRED','LOCK_RETRY_EXHAUSTED') LIMIT 1").isEmpty();}
        catch(RuntimeException failure) {
            for(String symbol:symbols)recordReadiness(symbol,SharedTradingReadiness.Reason.CHECK_FAILED);
            log.error("[FIX-146][READINESS_SCOPE_CHECK_FAILED]",failure);return;
        }
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        for(int checked=0;checked<symbols.size();checked++) {
            if(checked>0 && System.nanoTime()>=deadline)break;
            String symbol=symbols.get(Math.floorMod(readinessCursor++,symbols.size()));
            if(unscoped)recordReadiness(symbol,SharedTradingReadiness.Reason.UNSCOPED_WORK);
            else if(!activationApproved)recordReadiness(symbol,SharedTradingReadiness.Reason.ACTIVATION_REQUIRED);
            else {
                try {
                    if(!readinessJdbc.queryForList(SYMBOL_LEGACY_BLOCKERS,symbol).isEmpty()) {
                        recordReadiness(symbol,SharedTradingReadiness.Reason.LEGACY_WORK_REVIEW);continue;
                    }
                    // Inconsistent cross-symbol ownership blocks both affected symbols.
                    var owners=readinessJdbc.queryForList("""
                        SELECT w.signal_id FROM signal_processing_work w
                        LEFT JOIN shared_market_event_delivery d ON d.source_event_id=w.source_event_id
                        WHERE (w.symbol=? OR d.symbol=?) AND w.source_event_id IS NOT NULL
                          AND w.status NOT IN ('COMPLETED','EXPIRED','LOCK_RETRY_EXHAUSTED')
                          AND (w.status='OPERATOR_CANCELLED' OR d.source_event_id IS NULL OR w.symbol IS NULL
                            OR w.interval_code IS NULL OR w.candle_open_time IS NULL OR d.symbol IS NULL
                            OR d.interval_code IS NULL OR d.candle_open_time IS NULL OR d.closed IS NULL
                            OR d.analysis_status IS NULL OR w.symbol<>d.symbol
                            OR w.interval_code<>d.interval_code OR w.candle_open_time<>d.candle_open_time
                            OR d.closed=0 OR d.analysis_status NOT IN ('RUNNING','REVIEW_REQUIRED','EXECUTION_RETRY'))
                        LIMIT 1
                        """,symbol,symbol);
                    if(!owners.isEmpty()) {recordReadiness(symbol,SharedTradingReadiness.Reason.OWNER_INCONSISTENT);continue;}
                    if(automaticFirstCutoverEnabled)automaticActivation.activateFirst(symbol,intervals.getIntervals());
                    var validated=validateSymbolCutover(symbol);
                    if(!fix142Enabled && !readinessJdbc.queryForList("SELECT symbol FROM shared_market_consumer_state WHERE symbol=? AND active_delivery_state<>'NONE'",symbol).isEmpty()) {
                        recordReadiness(symbol,SharedTradingReadiness.Reason.OWNERSHIP_MODE_MISMATCH);continue;
                    }
                    if(fix142Enabled && !readinessJdbc.queryForList("""
                        SELECT d.source_event_id FROM shared_market_event_delivery d WHERE d.symbol=? AND phase BETWEEN 1 AND 3
                        AND NOT EXISTS (SELECT 1 FROM shared_market_consumer_state s WHERE s.symbol=d.symbol
                          AND s.active_delivery_event_id=d.source_event_id AND s.active_delivery_state IN ('ACTIVE','REVIEW_REQUIRED')) LIMIT 1
                        """,symbol).isEmpty()) {
                        recordReadiness(symbol,SharedTradingReadiness.Reason.PARTIAL_DELIVERY);continue;
                    }
                    // Avoid periodic INSERT/locking for already published history. A changed
                    // boundary is revalidated next pass; dispatch still checks durable approval.
                    Integer history=readinessJdbc.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_history WHERE symbol=? AND cutover_at=? AND cutover_sequence=?",Integer.class,
                        symbol,validated.get("cutover_at"),validated.get("cutover_sequence"));
                    Boolean stillApproved=history!=null && history>0;
                    if(!stillApproved)stillApproved=readinessTransaction.execute(tx->{
                        var state=readinessJdbc.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE",symbol);
                        if(!SharedCutoverPolicy.approved(state))return false;
                        for(String field:List.of("cutover_at","cutover_sequence","approved_sequence","approved_cutover_at","approved_at","approved_by","approval_reference"))
                            if(!Objects.equals(state.get(field),validated.get(field)))return false;
                        readinessJdbc.update("INSERT IGNORE INTO shared_market_cutover_history(symbol,cutover_at,cutover_sequence) VALUES(?,?,?)",
                            symbol,state.get("cutover_at"),state.get("cutover_sequence"));return true;
                    });
                    recordReadiness(symbol,Boolean.TRUE.equals(stillApproved)?SharedTradingReadiness.Reason.READY:SharedTradingReadiness.Reason.CUTOVER_REQUIRED);
                } catch(RuntimeException failure) {
                    recordReadiness(symbol,readinessReason(failure));
                    log.debug("[FIX-146][READINESS_CHECK_FAILED] symbol={}",symbol,failure);
                }
            }
            if(System.nanoTime()>=deadline)break;
        }
    }
    private SharedTradingReadiness.Reason readinessReason(RuntimeException failure) {
        String message=String.valueOf(failure.getMessage());
        if(message.contains("missing/unapproved/review-required cutover"))return automaticFirstCutoverEnabled
            ?SharedTradingReadiness.Reason.AUTOMATIC_ACTIVATION_WAITING:SharedTradingReadiness.Reason.CUTOVER_REQUIRED;
        if(message.contains("missing approval audit"))return SharedTradingReadiness.Reason.APPROVAL_AUDIT_MISSING;
        if(message.contains("invalid sequence boundary"))return SharedTradingReadiness.Reason.INVALID_BOUNDARY;
        if(message.contains("source sequence regressed"))return SharedTradingReadiness.Reason.SOURCE_REGRESSED;
        if(message.contains("incomplete source history"))return SharedTradingReadiness.Reason.SOURCE_HISTORY_MISSING;
        return SharedTradingReadiness.Reason.CHECK_FAILED;
    }
    private void recordReadiness(String symbol,SharedTradingReadiness.Reason reason) {
        var before=tradingReadiness.record(symbol,reason);
        if(before==null || before.reason()!=reason) {
            Fix142Metrics.count("READINESS_"+reason.name());
            log.warn("[FIX-146][SYMBOL_READINESS] symbol={}, reason={}, checkedUtc={}, checkedKsa={}; audited first-activation policy, no uncertain retry",
                symbol,reason,Instant.now(),Instant.now().atOffset(java.time.ZoneOffset.ofHours(3)));
        }
    }

    public void discover() {
        if(stopped || "OFF".equals(source.mode())) return;
        try {
            for(String symbol:coins.enabledSymbols()) {
                if(!symbolAllowed(symbol))continue;
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
                int pending=fix142Enabled?-1:jdbc.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE symbol=? AND phase<4",Integer.class,symbol);
                boolean discoveryHeartbeat=System.nanoTime()-discoveryLogTimes.getOrDefault(symbol,0L)>=TimeUnit.SECONDS.toNanos(30);
                if(discoveryHeartbeat)discoveryLogTimes.put(symbol,System.nanoTime());
                if(!fix142Enabled && pending>=200) {
                    if(discoveryHeartbeat)log.warn("[FIX-132][CONSUMER_LAG] symbol={}, pending={}; discovery paused before analysis admission",symbol,pending);
                    continue;
                }
                long after=((Number)state.getFirst().get("discovered_sequence")).longValue();
                var rows=source.feedReader().queryForList("SELECT * FROM market_data_stream_event WHERE symbol=? AND symbol_sequence>? ORDER BY symbol_sequence LIMIT ?",symbol,after,fix142Enabled?32:Math.min(100,200-pending));
                log.debug("[FIX-137][DISCOVERY_BATCH] symbol={}, afterSequence={}, pending={}, fetched={}",
                    symbol,after,pending,rows.size());
                if(discoveryHeartbeat)log.info("[FIX-138][DISCOVERY_PROGRESS] symbol={}, afterSequence={}, pendingPricePhases={}, fetched={}, lastFetchedObservationUtc={}",
                    symbol,after,pending,rows.size(),rows.isEmpty()?null:time(rows.getLast(),"observed_at"));
                // Shared connection is returned before any local transaction starts.
                transaction.executeWithoutResult(tx->{
                    var locked=lockSymbol(symbol);
                    if(!symbolAllowed(symbol) || !SharedCutoverPolicy.admitted(locked,source.enabled()) || ((Number)locked.get("discovered_sequence")).longValue()!=after) return;
                    long next=after;
                    Map<String,Integer> batchSlots=new HashMap<>();
                    for(var e:rows) {
                        long sequence=num(e,"symbol_sequence");
                        if(sequence!=next+1) {
                            jdbc.update("UPDATE shared_market_consumer_state SET status='SEQUENCE_GAP' WHERE symbol=?",symbol);
                            log.error("[FIX-132][SEQUENCE_GAP] symbol={}, expected={}, found={}",symbol,next+1,sequence);break;
                        }
                        if(fix142Enabled) {
                            String interval=str(e,"interval_code");
                            int available=batchSlots.computeIfAbsent(interval,key->Math.max(0,protocol.ceiling-protocol.reserve-protocol.exposure(symbol,key,Instant.now())));
                            var admission=protocol.admit(e,symbol,available>0);
                            if(admission==Fix142DeliveryProtocol.AdmissionResult.NO_CAPACITY)break;
                            if(admission==Fix142DeliveryProtocol.AdmissionResult.INSERTED)batchSlots.put(interval,available-1); // duplicates validated; checkpoint remains contiguous
                        } else {
                        jdbc.update("""
                            INSERT INTO shared_market_event_delivery(source_event_id,symbol,symbol_sequence,interval_code,
                            candle_open_time,candle_close_time,closed,observed_at,received_at,source_created_at,price,source,classification)
                            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                            """,e.get("id"),symbol,sequence,e.get("interval_code"),e.get("candle_open_time"),e.get("candle_close_time"),
                            e.get("closed"),e.get("observed_at"),e.get("received_at"),e.get("created_at"),e.get("price"),e.get("source"),e.get("classification"));
                        }
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
                if(!symbolAllowed(symbol) || !activePrices.add(symbol))continue;
                try { prices.execute(()->{try {
                    int count=fix142Enabled?jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE symbol=? AND phase<4 LIMIT 8",symbol).size():jdbc.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE symbol=? AND phase<4",Integer.class,symbol);
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
            if(!symbolAllowed(symbol) || !SharedCutoverPolicy.admitted(state,source.enabled()))return false;
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
        if(!symbolAllowed(symbol))return;
        Long id=null;
        try(var timing=com.crypto.infrastructure.diagnostics.JdbcTimingDataSource.context("symbol="+symbol+" phase=SELECT")) {
            var candidates=fix142Enabled ? transaction.execute(tx->{
                var state=lockSymbol(symbol);
                if(!symbolAllowed(symbol) || !SharedCutoverPolicy.admitted(state,source.enabled()))return List.<Map<String,Object>>of();
                var selected=protocol.select(symbol,state);
                return selected==null?List.<Map<String,Object>>of():List.of(selected);
            }) : jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE symbol=? AND phase<4 ORDER BY symbol_sequence LIMIT 1",symbol);
            if(candidates.isEmpty())return;
            id=num(candidates.getFirst(),"source_event_id");
            log.debug("[FIX-137][DELIVERY_START] event={}, symbol={}",id,symbol);
            final long eventId=id;
            final String attemptToken=fix142Enabled?jdbc.queryForObject("SELECT active_delivery_owner_token FROM shared_market_consumer_state WHERE symbol=?",String.class,symbol):UUID.randomUUID().toString();
            // Small bounded turn: one event, four independently committed phases.
            for(int stage=0;stage<4;stage++) {
                final int expected=stage;
                if(source.enabled() && expected==1) {
                    if(fix142Enabled) {
                        ProtectionClaim result=claimProtectionAttempt(symbol,eventId,attemptToken);
                        if(result==ProtectionClaim.BLOCKED || result==ProtectionClaim.REVIEW)return;
                        if(result==ProtectionClaim.CLAIMED && !prepareProtectionApply(symbol,eventId,attemptToken))return;
                    } else if(!beginProtection(symbol,eventId,attemptToken))return;
                }
                int attempts=0;
                while(true) {
                try(var phaseTiming=com.crypto.infrastructure.diagnostics.JdbcTimingDataSource.context("symbol="+symbol+" event="+eventId+" phase="+expected);var phaseSpan=Fix142Metrics.phase(symbol,eventId,expected)) {
                transaction.executeWithoutResult(tx->{
                    var state=lockSymbol(symbol);
                    if(!symbolAllowed(symbol) || !SharedCutoverPolicy.admitted(state,source.enabled())) {
                        if(fix142Enabled&&expected==1)restoreUnenteredClaim(eventId,attemptToken);
                        return;
                    }
                    if(fix142Enabled && !protocol.owned(state,eventId,attemptToken)) {
                        if(expected==1)restoreUnenteredClaim(eventId,attemptToken);
                        return;
                    }
                    var e=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",eventId);
                    if(num(e,"phase")!=expected || "REVIEW_REQUIRED".equals(e.get("status"))) {
                        if(fix142Enabled&&expected==1)restoreUnenteredClaim(eventId,attemptToken);
                        return;
                    }
                    if(source.enabled() && expected==1 && "1m".equals(e.get("interval_code"))
                            && e.get("protection_completed_at")==null && !attemptToken.equals(e.get("protection_owner")))return;
                    if(!source.enabled()) {
                        jdbc.update("UPDATE shared_market_event_delivery SET phase=4,status='OBSERVED',analysis_status='OBSERVED' WHERE source_event_id=?",eventId);return;
                    }
                    boolean postCutover=num(e,"symbol_sequence")>num(state,"cutover_sequence")
                        && time(e,"observed_at")!=null && time(e,"observed_at").isAfter(time(state,"cutover_at"));
                    boolean canonical="1m".equals(e.get("interval_code"));
                    String reason = protectionReason(symbol,state,e,Instant.now());
                    boolean eligible="ELIGIBLE".equals(reason);
                    jdbc.update("UPDATE shared_market_event_delivery SET owner_token=?,status='PROCESSING' WHERE source_event_id=?",owner,eventId);
                    if(expected==0 && canonical && e.get("observed_at")!=null) {
                        jdbc.update("""
                            INSERT IGNORE INTO market_price_event(symbol,observed_at,price,source,source_event_id,source_sequence,source_received_at,delivery_status)
                            VALUES(?,?,?,'COLLECTOR_KLINE_LIVE',?,?,?,'PENDING')
                            """,symbol,e.get("observed_at"),e.get("price"),eventId,e.get("symbol_sequence"),e.get("received_at"));
                    }
                    if(expected==1 && (!fix142Enabled || e.get("protection_completed_at")==null)) {
                        jdbc.update("UPDATE shared_market_event_delivery SET eligibility_reason=? WHERE source_event_id=?",reason,eventId);
                    }
                    if(expected==1 && canonical) {
                        if(fix142Enabled && e.get("protection_completed_at")!=null) {
                            Fix142Metrics.count("SEQUENTIAL_PROTECTION_ALREADY_COMPLETED");
                            String outcome=str(e,"protection_outcome");
                            if(!Set.of("APPLIED","REJECTED").contains(outcome))throw new IllegalStateException("FIX-142 legacy protection outcome requires review");
                            jdbc.update("UPDATE market_price_event SET delivery_status=? WHERE source_event_id=?","APPLIED".equals(outcome)?"APPLIED":"HISTORICAL_ONLY",eventId);
                        } else {
                            // FIX-150: entry intent committed before any in-process/DB effect.
                            if(fix142Enabled && !"APPLYING".equals(e.get("protection_apply_state")))return;
                            if(fix142Enabled && earlierProtection(symbol,num(e,"symbol_sequence"),Instant.now())) {
                                // A late earlier admission can arrive AFTER entry-intent commit.
                                // This return precedes onPrice, so restoring CLAIMED is proven safe.
                                jdbc.update("UPDATE shared_market_event_delivery SET protection_apply_state='CLAIMED' WHERE source_event_id=? AND protection_owner=? AND protection_apply_state='APPLYING'",eventId,attemptToken);
                                return;
                            }
                            if(eligible) {
                                protection.onPrice(symbol,(java.math.BigDecimal)e.get("price"));
                                jdbc.update("UPDATE shared_market_consumer_state SET last_observed_at=?,last_price=? WHERE symbol=?",e.get("observed_at"),e.get("price"),symbol);
                            }
                            jdbc.update("UPDATE market_price_event SET delivery_status=? WHERE source_event_id=?",eligible?"APPLIED":"HISTORICAL_ONLY",eventId);
                            if(fix142Enabled)jdbc.update("UPDATE shared_market_event_delivery SET protection_completed_at=CURRENT_TIMESTAMP(6),protection_outcome=?,protection_apply_state='COMPLETED' WHERE source_event_id=?",eligible?"APPLIED":"REJECTED",eventId);
                            else jdbc.update("UPDATE shared_market_event_delivery SET protection_completed_at=CURRENT_TIMESTAMP(6) WHERE source_event_id=?",eventId);
                        }
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
                    if(fix142Enabled)protocol.progress(symbol,eventId,attemptToken,expected+1);
                });
                phaseSpan.returned();
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
                        if(fix142Enabled)protocol.progress(symbol,eventId,attemptToken,3);
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
                    if(fix142Enabled)jdbc.update("UPDATE shared_market_consumer_state SET active_delivery_state='REVIEW_REQUIRED' WHERE symbol=? AND active_delivery_event_id=?",symbol,failedId);
                    else jdbc.update("UPDATE shared_market_consumer_state SET status='REVIEW_REQUIRED' WHERE symbol=?",symbol);
                    jdbc.update("UPDATE shared_market_event_delivery SET status='REVIEW_REQUIRED',last_error=? WHERE source_event_id=?",shortError(failure),failedId);
                }); } catch(Exception recording) { log.error("[FIX-132][FAILURE_RECORD_FAILED] symbol={}",symbol,recording); }
            }
            log.error("[FIX-132][REVIEW_REQUIRED] symbol={}, event={}; no automatic wallet retry",symbol,id,failure);
        }
    }
    // FIX-142: independent source reads, whole-batch reservations under the same symbol lock.
    public void discoverFresh() {
        if(stopped || !fix142Enabled || !source.enabled())return;
        for(String symbol:coins.enabledSymbols()) {
        if(!symbolAllowed(symbol))continue;
        try(var timing=com.crypto.infrastructure.diagnostics.JdbcTimingDataSource.context("symbol="+symbol+" phase=FRESH_ADMISSION")) {
            Instant now=Instant.now();List<Map<String,Object>> found=new ArrayList<>();
            String columns="id,symbol,symbol_sequence,interval_code,candle_open_time,candle_close_time,closed,observed_at,received_at,created_at,price,source,classification";
            found.addAll(source.feedReader().queryForList("SELECT "+columns+" FROM market_data_stream_event WHERE symbol=? AND interval_code='1m' AND source='LIVE_WEBSOCKET' AND classification='LIVE' AND observed_at BETWEEN ? AND ? ORDER BY symbol_sequence LIMIT 32",symbol,Timestamp.from(now.minusSeconds(15)),Timestamp.from(now)));
            for(String interval:List.of("1m","5m","15m","1h","4h","1d"))
                found.addAll(source.feedReader().queryForList("SELECT "+columns+" FROM market_data_stream_event WHERE symbol=? AND interval_code=? AND closed=1 AND source='LIVE_WEBSOCKET' AND classification='LIVE' AND candle_close_time BETWEEN ? AND ? ORDER BY candle_close_time DESC,symbol_sequence LIMIT 2",symbol,interval,Timestamp.from(now.minusSeconds(SharedEventPolicy.closeGraceSeconds(interval))),Timestamp.from(now)));
            transaction.executeWithoutResult(tx->{
                var state=lockSymbol(symbol);
                if(!symbolAllowed(symbol) || !SharedCutoverPolicy.admitted(state,true))return;
                Map<String,Integer> slots=new HashMap<>();Set<Long> seen=new HashSet<>();int inserted=0;
                for(var e:found) {
                    if(!seen.add(num(e,"id")))continue;
                    if(num(e,"symbol_sequence")<=num(state,"cutover_sequence") || time(e,"observed_at")==null || !time(e,"observed_at").isAfter(time(state,"cutover_at")))continue;
                    String interval=str(e,"interval_code");
                    int available=slots.computeIfAbsent(interval,key->Math.max(0,protocol.ceiling-protocol.exposure(symbol,key,Instant.now())));
                    var admission=protocol.admit(e,symbol,available>0);
                    if(admission==Fix142DeliveryProtocol.AdmissionResult.INSERTED){slots.put(interval,available-1);inserted++;}
                }
                for(int i=0;i<inserted;i++)Fix142Metrics.count("FRESH_ADMITTED_COMMIT_PENDING");
                log.debug("[FIX-142][FRESH_ADMISSION] symbol={}, inserted={}, contiguousCheckpointUnchanged=true",symbol,inserted);
            });
        }catch(Exception failure){log.error("[FIX-142][FRESH_ADMISSION_FAILED] symbol={}",symbol,failure);}
        }
    }
    private String protectionReason(String symbol,Map<String,Object> state,Map<String,Object> e,Instant now) {
        if(!symbol.equals(e.get("symbol")))throw new IllegalArgumentException("Protection identity mismatch");
        if(!"1m".equals(e.get("interval_code")))return "NON_CANONICAL_INTERVAL";
        if(num(e,"symbol_sequence")<=num(state,"cutover_sequence")||time(e,"observed_at")==null
            || !time(e,"observed_at").isAfter(time(state,"cutover_at")))return "PRE_CUTOVER_OR_UNKNOWN_TIME";
        if(((java.math.BigDecimal)e.get("price")).signum()<=0)return "NON_POSITIVE_PRICE";
        return SharedEventPolicy.priceReason(str(e,"source"),str(e,"classification"),time(e,"observed_at"),time(state,"last_observed_at"),now);
    }
    enum ProtectionClaim { CLAIMED, COMPLETED, NOT_APPLICABLE, BLOCKED, REVIEW }
    private boolean earlierProtection(String symbol,long sequence,Instant now) {
        // Split the OR: old never-started backlog must not force a chronological scan
        // before the recent window. Started uncertainty is retained at any age.
        if(!jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE symbol=? AND interval_code='1m' AND protection_completed_at IS NULL AND protection_started_at IS NOT NULL AND symbol_sequence<? LIMIT 1",symbol,sequence).isEmpty())return true;
        return !jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE symbol=? AND interval_code='1m' AND protection_completed_at IS NULL AND protection_started_at IS NULL AND observed_at BETWEEN ? AND ? AND symbol_sequence<? LIMIT 1",symbol,Timestamp.from(now.minusSeconds(15)),Timestamp.from(now),sequence).isEmpty();
    }
    ProtectionClaim claimProtectionAttempt(String symbol,long id,String attempt) {
        return transaction.execute(tx->{
            var state=lockSymbol(symbol);
            if(!symbolAllowed(symbol) || !SharedCutoverPolicy.admitted(state,source.enabled()))return ProtectionClaim.BLOCKED;
            var e=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",id);
            if(!symbol.equals(e.get("symbol")))throw new IllegalArgumentException("FIX-142 protection symbol mismatch");
            if(!"1m".equals(e.get("interval_code")))return ProtectionClaim.NOT_APPLICABLE;
            if(e.get("protection_completed_at")!=null)return ProtectionClaim.COMPLETED;
            if("REVIEW_REQUIRED".equals(e.get("status")))return ProtectionClaim.REVIEW;
            if(e.get("protection_started_at")!=null) {
                if(attempt.equals(e.get("protection_owner")))return ProtectionClaim.CLAIMED;
                if(time(e,"protection_started_at").plusSeconds(30).isBefore(Instant.now())) {
                    // FIX-150: a later timeout must never erase the initiating exception.
                    jdbc.update("UPDATE shared_market_event_delivery SET status='REVIEW_REQUIRED',last_error=COALESCE(last_error,'FIX-142 unresolved protection; no automatic retry') WHERE source_event_id=?",id);
                    return ProtectionClaim.REVIEW;
                }
                return ProtectionClaim.BLOCKED;
            }
            if(earlierProtection(symbol,num(e,"symbol_sequence"),Instant.now()))return ProtectionClaim.BLOCKED;
            // FIX-142 review correction: an earlier-only barrier does not bound claims arriving
            // out of sequence. Permit two outstanding attempts, explicitly counted under this lock.
            // Saturation defers new attempts; it NEVER steals or resets an uncertain attempt.
            Integer outstanding=jdbc.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE symbol=? AND interval_code='1m' AND protection_started_at IS NOT NULL AND protection_completed_at IS NULL",Integer.class,symbol);
            if(outstanding>=2){Fix142Metrics.count("PROTECTION_OVERLOAD");log.error("[FIX-142][PROTECTION_OVERLOAD] symbol={}, outstanding={}; no claim evicted",symbol,outstanding);return ProtectionClaim.BLOCKED;}
            jdbc.update("UPDATE shared_market_event_delivery SET protection_started_at=CURRENT_TIMESTAMP(6),protection_owner=?,status='PROTECTION_RUNNING',protection_apply_state='CLAIMED' WHERE source_event_id=?",attempt,id);
            return ProtectionClaim.CLAIMED;
        });
    }
    /** FIX-150: durable entry intent is a separate, non-nested transaction. CLAIMED
     * can therefore be retired as never entered, even after a crash. APPLYING is
     * uncertainty unless completion committed; token equality alone never permits retry. */
    boolean prepareProtectionApply(String symbol,long id,String token) {
        return Boolean.TRUE.equals(transaction.execute(tx->{
            var state=lockSymbol(symbol);
            if(!SharedCutoverPolicy.admitted(state,true))return false;
            var e=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",id);
            if(!symbol.equals(e.get("symbol"))||!"1m".equals(e.get("interval_code")))return false;
            // FIX-153 validation found the pre-existing FIX-152 test failure: stale tokens
            // must not receive a success acknowledgment merely because an attempt is terminal.
            if(!token.equals(e.get("protection_owner")))return false;
            if(e.get("protection_completed_at")!=null)return true;
            if("REVIEW_REQUIRED".equals(e.get("status")))return false;
            if(!"CLAIMED".equals(e.get("protection_apply_state")))return false;
            if(earlierProtection(symbol,num(e,"symbol_sequence"),Instant.now()))return false;
            if(!time(e,"observed_at").isAfter(Instant.now().minusSeconds(15))) {
                // Leave CLAIMED for the atomic retirement+marker+audit transaction.
                // Separately completing it here could strand a marker after a crash.
                return false;
            }
            int changed=jdbc.update("UPDATE shared_market_event_delivery SET protection_apply_state='APPLYING' WHERE source_event_id=? AND protection_owner=? AND protection_apply_state='CLAIMED' AND protection_completed_at IS NULL",id,token);
            if(changed!=1)throw new IllegalStateException("FIX-150 protection entry conflict");
            return true;
        }));
    }
    public void dispatchProtectionTicks() {
        if(stopped || !fix142Enabled || !source.enabled())return;
        var symbols=coins.enabledSymbols();
        for(int checked=0;checked<symbols.size();checked++) {
            // FIX-146: readiness deadline belongs only to its dedicated probe loop.
            String symbol=symbols.get(Math.floorMod(nextProtectionSymbol++,symbols.size()));
            if(activeProtection.size()>=2)break;
            if(!symbolAllowed(symbol) || !activeProtection.add(symbol))continue;
            try{protectionPool.execute(()->{
                try(var timing=com.crypto.infrastructure.diagnostics.JdbcTimingDataSource.context("symbol="+symbol+" phase=PROTECTION")) {
                    Instant now=Instant.now();
                    // Chronological recent observations preserve stop crossings; never newest-only.
                    var candidates=jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE symbol=? AND interval_code='1m' AND protection_completed_at IS NULL AND observed_at BETWEEN ? AND ? ORDER BY symbol_sequence LIMIT 8",symbol,Timestamp.from(now.minusSeconds(15)),Timestamp.from(now));
                    for(var candidate:candidates) {
                        long id=num(candidate,"source_event_id");String token=deferredProtection.getOrDefault(id,UUID.randomUUID().toString());
                        ProtectionClaim result=claimProtectionAttempt(symbol,id,token);
                        Fix142Metrics.count("PROTECTION_CLAIM_"+result);
                        if(result==ProtectionClaim.BLOCKED||result==ProtectionClaim.REVIEW)break;
                        if(result==ProtectionClaim.CLAIMED) {
                            // Retain only when the locked apply returns before invoking business code.
                            // Any exception/unknown commit removes local continuation authority.
                            boolean finished;
                            try{finished=applyProtection(symbol,id,token);}
                            catch(Exception failure){deferredProtection.remove(id);recordProtectionApplyFailure(symbol,id,token,failure);throw failure;}
                            if(finished){Fix142Metrics.count("PROTECTION_APPLY_RETURNED");deferredProtection.remove(id);}else{Fix142Metrics.count("PROTECTION_APPLY_ORDER_DEFERRED");deferredProtection.put(id,token);break;}
                        } else deferredProtection.remove(id);
                    }
                    // Best-effort terminal bookkeeping, not a correctness-bearing prerequisite.
                    transaction.executeWithoutResult(tx->{lockSymbol(symbol);jdbc.update("UPDATE shared_market_event_delivery SET protection_completed_at=CURRENT_TIMESTAMP(6),protection_outcome='REJECTED' WHERE symbol=? AND interval_code='1m' AND protection_started_at IS NULL AND protection_completed_at IS NULL AND observed_at<? LIMIT 50",symbol,Timestamp.from(Instant.now().minusSeconds(15)));});
                }catch(Exception failure){log.error("[FIX-142][PROTECTION_DISPATCH_FAILED] symbol={}; started attempts retained",symbol,failure);}
                finally{activeProtection.remove(symbol);}
            });}catch(RuntimeException failure){activeProtection.remove(symbol);log.error("[FIX-142][PROTECTION_SUBMISSION_FAILED] symbol={}",symbol,failure);}
        }
    }
    boolean applyProtection(String symbol,long id,String token) {
        // FIX-150: this call must occur before opening the apply transaction. An
        // APPLYING attempt from a prior invocation cannot silently enter business again.
        if(!prepareProtectionApply(symbol,id,token))return false;
        // Claim is already committed. Unknown apply/commit leaves its durable start intact.
        return transaction.execute(tx->{
            var state=lockSymbol(symbol);
            if(!SharedCutoverPolicy.admitted(state,true)) {restoreUnenteredClaim(id,token);return false;}
            var e=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",id);
            if(!token.equals(e.get("protection_owner")))return true;
            if("REVIEW_REQUIRED".equals(e.get("status"))) {restoreUnenteredClaim(id,token);return true;}
            if(e.get("protection_completed_at")!=null)return true;
            if(!"APPLYING".equals(e.get("protection_apply_state")))return false;
            if(earlierProtection(symbol,num(e,"symbol_sequence"),Instant.now())) {
                // A known pre-business return restores recoverable claim-only evidence.
                jdbc.update("UPDATE shared_market_event_delivery SET protection_apply_state='CLAIMED' WHERE source_event_id=? AND protection_owner=? AND protection_apply_state='APPLYING'",id,token);
                return false;
            }
            String reason=protectionReason(symbol,state,e,Instant.now());
            boolean eligible="ELIGIBLE".equals(reason);
            if(eligible){protection.onPrice(symbol,(java.math.BigDecimal)e.get("price"));jdbc.update("UPDATE shared_market_consumer_state SET last_observed_at=?,last_price=? WHERE symbol=?",e.get("observed_at"),e.get("price"),symbol);}
            int completed=jdbc.update("UPDATE shared_market_event_delivery SET protection_completed_at=CURRENT_TIMESTAMP(6),protection_outcome=?,eligibility_reason=?,protection_apply_state='COMPLETED' WHERE source_event_id=? AND protection_owner=? AND protection_apply_state='APPLYING' AND protection_completed_at IS NULL",eligible?"APPLIED":"REJECTED",reason,id,token);
            if(completed!=1)throw new IllegalStateException("FIX-150 apply completion guard changed");
            return true;
        });
    }
    /** Only call from an apply transaction's known pre-business return while its
     * symbol lock is held. Never call after an exception, commit failure or onPrice. */
    private void restoreUnenteredClaim(long id,String token) {
        jdbc.update("UPDATE shared_market_event_delivery SET protection_apply_state='CLAIMED' WHERE source_event_id=? AND protection_owner=? AND protection_apply_state='APPLYING' AND protection_completed_at IS NULL",id,token);
    }
    /** FIX-150: record the initiating failure AFTER the apply transaction unwinds.
     * Recording cannot certify rollback or enable replay; completion wins if the
     * apply actually committed. Keep the first concrete failure through later monitors. */
    private void recordProtectionApplyFailure(String symbol,long id,String token,Exception failure) {
        StringBuilder detail=new StringBuilder("FIX-150 apply failure: ");
        Throwable cause=failure;
        for(int depth=0;cause!=null&&depth<6;depth++,cause=cause.getCause()) {
            if(depth>0)detail.append(" causedBy=");
            detail.append(cause);
        }
        String evidence=detail.substring(0,Math.min(1000,detail.length()));
        try {
            transaction.executeWithoutResult(tx->{
                lockSymbol(symbol);
                jdbc.update("UPDATE shared_market_event_delivery SET status='REVIEW_REQUIRED',last_error=CASE WHEN last_error IS NULL OR last_error LIKE 'FIX-142 %' THEN ? ELSE last_error END WHERE source_event_id=? AND protection_owner=? AND protection_completed_at IS NULL",evidence,id,token);
            });
        }catch(Exception recording) {
            log.error("[FIX-150][PROTECTION_FAILURE_RECORD_FAILED] symbol={}, event={}; original error follows",symbol,id,recording);
        }
        log.error("[FIX-150][PROTECTION_APPLY_FAILED] symbol={}, event={}; no automatic effect retry",symbol,id,failure);
    }
    /** Fence and alert aged starts independently of the recent observation window.
     * Timeout is uncertainty, never proof that no effect occurred. Three rows per symbol
     * bounds the normal two-slot inventory plus legacy overflow returned, not rows examined. */
    void monitorProtectionAttempts() {
        if(stopped||!fix142Enabled||!source.enabled())return;
        final List<String> symbols;
        try{symbols=coins.enabledSymbols();}catch(Exception failure){
            // A scheduler must survive inventory/DB failures rather than silently cancel itself.
            Fix142Metrics.count("PROTECTION_MONITOR_FAILED");
            log.error("[FIX-142][PROTECTION_MONITOR_FAILED] symbol inventory unavailable; protection health unknown",failure);
            return;
        }
        for(String symbol:symbols)try {
            // FIX-150 runs even when operational readiness is blocked. Its own durable
            // cutover/position/ownership checks authorize only narrow no-replay retirement.
            if(protectionRecovery!=null) {
                // FIX-152: completed, never-started REJECTED protection can strand a
                // phase-1 marker. Resume bookkeeping with a new owner token BEFORE
                // the separate incomplete-attempt recovery; no nested transaction.
                protectionRecovery.resumeRejectedMarker(symbol,protocol.processOwner);
                var recovered=protectionRecovery.recover(symbol);
                deferredProtection.keySet().removeAll(recovered); // Commit succeeded; drop obsolete local continuation tokens.
                if(!recovered.isEmpty())log.warn("[FIX-150][EXPIRED_PROTECTION_RETIRED] symbol={}, count={}; no old price/signal replay; inspect atomic audits",symbol,recovered.size());
            }
            var aged=transaction.execute(tx->{
                lockSymbol(symbol);
                var rows=jdbc.queryForList("SELECT source_event_id,protection_owner,protection_started_at FROM shared_market_event_delivery WHERE symbol=? AND interval_code='1m' AND protection_completed_at IS NULL AND protection_started_at<? ORDER BY protection_started_at,source_event_id LIMIT 3 FOR UPDATE",symbol,Timestamp.from(Instant.now().minusSeconds(30)));
                for(var row:rows) {
                    // Only fence the started attempt. Never set completed_at or reset its owner.
                    jdbc.update("UPDATE shared_market_event_delivery SET status='REVIEW_REQUIRED',last_error=COALESCE(last_error,'FIX-142 protection timeout requires audited operator resolution; no automatic retry') WHERE source_event_id=? AND protection_completed_at IS NULL",row.get("source_event_id"));
                }
                return rows;
            });
            if(!aged.isEmpty()) {
                Fix142Metrics.count("PROTECTION_OPERATOR_RECOVERY_REQUIRED");
                log.error("[FIX-142][PROTECTION_RECOVERY_REQUIRED] symbol={}, attempts={}; live protection may be blocked. Resolve exact attempts with independently verified owner fencing and effect evidence; no automatic release",symbol,aged);
            }
        }catch(Exception failure){
            Fix142Metrics.count("PROTECTION_MONITOR_FAILED");
            log.error("[FIX-142][PROTECTION_MONITOR_FAILED] symbol={}; protection health unknown, inspect immediately",symbol,failure);
        }
    }
    /** Explicitly close an operator-proven protection outcome without replaying onPrice.
     * Evidence text is an authenticated operator attestation, NOT machine liveness proof.
     * Keep status/marker/analysis/observer review independent; marker RESUME remains separate. */
    public void resolveProtection(String symbol,long eventId,String expectedOwner,Instant expectedStartedAt,
            String disposition,String transactionOutcome,String reviewer,String fenceProof,String effectEvidence) {
        if(!fix142Enabled)throw new IllegalStateException("FIX-142 scheduling must be enabled for protection resolution");
        if(symbol==null||symbol.isBlank()||expectedOwner==null||expectedOwner.isBlank()||expectedStartedAt==null)
            throw new IllegalArgumentException("Exact symbol, owner and protection start required");
        if(reviewer==null||reviewer.isBlank()||reviewer.length()>160||fenceProof==null||fenceProof.strip().length()<20
                ||effectEvidence==null||effectEvidence.strip().length()<20)
            throw new IllegalArgumentException("Authenticated reviewer, independently established owner fence and detailed effect evidence required");
        final String outcome;
        if("CONFIRMED_APPLIED".equals(disposition)&&"COMMITTED".equals(transactionOutcome))outcome="APPLIED";
        else if("CONFIRMED_NOT_APPLIED".equals(disposition)&&"CONFIRMED_NO_EFFECTS".equals(transactionOutcome))outcome="REJECTED";
        else throw new IllegalArgumentException("Only committed applied effects or independently confirmed no effects may resolve protection; rollback alone is insufficient");
        String proof="startedUtc="+expectedStartedAt+" disposition="+disposition+" transaction="+transactionOutcome+" fenceProof="+fenceProof+" effects="+effectEvidence;
        if(proof.length()>2000)throw new IllegalArgumentException("Resolution evidence exceeds 2000 characters");
        transaction.executeWithoutResult(tx->{
            lockSymbol(symbol); // Same symbol -> delivery lock order as claim/apply/marker resolution.
            var row=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",eventId);
            if(!symbol.equals(row.get("symbol"))||!"1m".equals(row.get("interval_code"))
                    ||!"REVIEW_REQUIRED".equals(row.get("status"))||row.get("protection_completed_at")!=null
                    ||!Objects.equals(expectedOwner,row.get("protection_owner"))
                    ||!expectedStartedAt.equals(time(row,"protection_started_at")))
                throw new IllegalStateException("Protection attempt changed, completed, non-1m or not fenced");
            int changed=jdbc.update("UPDATE shared_market_event_delivery SET protection_completed_at=CURRENT_TIMESTAMP(6),protection_outcome=?,protection_apply_state='OPERATOR_RESOLVED' WHERE source_event_id=? AND symbol=? AND interval_code='1m' AND status='REVIEW_REQUIRED' AND protection_owner=? AND protection_started_at=? AND protection_completed_at IS NULL",outcome,eventId,symbol,expectedOwner,Timestamp.from(expectedStartedAt));
            if(changed!=1)throw new IllegalStateException("Protection resolution must affect exactly one row");
            int audited=jdbc.update("INSERT INTO shared_delivery_resolution_audit(source_event_id,symbol,operation,old_owner_token,reviewer,evidence) VALUES(?,?,?,?,?,?)",eventId,symbol,"PROTECTION_"+disposition,expectedOwner,reviewer,proof);
            if(audited!=1)throw new IllegalStateException("Protection audit must affect exactly one row");
        });
        deferredProtection.remove(eventId);
        Fix142Metrics.count("PROTECTION_RESOLVED_"+disposition);
        log.warn("[FIX-142][PROTECTION_RESOLVED] symbol={}, event={}, disposition={}, reviewer={}; old observation never replayed; marker/review state unchanged",symbol,eventId,disposition,reviewer);
    }
    /** FIX-142: explicit human-reviewed marker resolution. No PID/age-based release.
     * The authenticated operator supplies independently established old-owner/transaction
     * evidence. Unresolved protection is still refused, regardless of that attestation.
     * Phase-2 observer uncertainty requires a separate explicit disposition. */
    public void resolveMarker(String symbol,long eventId,String expectedOwner,String operation,
            String observerDisposition,String reviewer,String fenceProof,String evidence) {
        if(!fix142Enabled)throw new IllegalStateException("FIX-142 scheduling must be enabled for marker resolution");
        if(reviewer==null||reviewer.isBlank()||fenceProof==null||fenceProof.length()<20||evidence==null||evidence.length()<20)
            throw new IllegalArgumentException("Independent owner/transaction proof and detailed evidence are required");
        if(!Set.of("RESUME","RELEASE").contains(operation))throw new IllegalArgumentException("Invalid resolution");
        transaction.executeWithoutResult(tx->{
            var state=lockSymbol(symbol);
            if(!"REVIEW_REQUIRED".equals(state.get("active_delivery_state"))
                    || !Objects.equals(state.get("active_delivery_event_id"),eventId)
                    || !Objects.equals(state.get("active_delivery_owner_token"),expectedOwner))
                throw new IllegalStateException("Marker changed or is not fenced");
            var row=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",eventId);
            if(!symbol.equals(row.get("symbol")))throw new IllegalStateException("Marker identity mismatch");
            if(row.get("protection_started_at")!=null&&row.get("protection_completed_at")==null)
                throw new IllegalStateException("Unresolved protection retains its fence; no release/retry");
            if(row.get("protection_completed_at")!=null&&!Set.of("APPLIED","REJECTED").contains(str(row,"protection_outcome")))
                throw new IllegalStateException("Unknown legacy protection outcome requires separate evidence repair");
            long phase=num(row,"phase");
            if(operation.equals("RELEASE") && phase!=4)
                throw new IllegalStateException("Only a durably finished phase stream may release its marker");
            if(operation.equals("RESUME") && phase==4)
                throw new IllegalStateException("Finished phase stream must release, not resume");
            if(phase==2) {
                if(!Set.of("CONFIRMED_NOT_INVOKED","RETAIN_REVIEW_SKIP_OBSERVER").contains(observerDisposition))
                    throw new IllegalArgumentException("Phase-2 observer disposition required; never infer absence from rollback");
                if("RETAIN_REVIEW_SKIP_OBSERVER".equals(observerDisposition))
                    jdbc.update("UPDATE shared_market_event_delivery SET phase=3,observer_status='REVIEW_REQUIRED' WHERE source_event_id=?",eventId);
            }
            String newOwner=operation.equals("RESUME")?UUID.randomUUID().toString():null;
            int changed;
            if(operation.equals("RESUME")) {
                changed=jdbc.update("UPDATE shared_market_consumer_state SET active_delivery_state='ACTIVE',active_delivery_owner_token=?,active_delivery_process_owner=?,active_delivery_updated_at=CURRENT_TIMESTAMP(6) WHERE symbol=? AND active_delivery_event_id=? AND active_delivery_owner_token=? AND active_delivery_state='REVIEW_REQUIRED'",newOwner,protocol.processOwner,symbol,eventId,expectedOwner);
                // Any delivery error text remains; this is an audited continuation, never a trade replay.
                jdbc.update("UPDATE shared_market_event_delivery SET status='PROCESSING' WHERE source_event_id=? AND phase<4",eventId);
            } else changed=jdbc.update("UPDATE shared_market_consumer_state SET active_delivery_state='NONE',active_delivery_event_id=NULL,active_delivery_owner_token=NULL,active_delivery_process_owner=NULL,active_delivery_started_at=NULL,active_delivery_updated_at=CURRENT_TIMESTAMP(6) WHERE symbol=? AND active_delivery_event_id=? AND active_delivery_owner_token=? AND active_delivery_state='REVIEW_REQUIRED'",symbol,eventId,expectedOwner);
            if(changed!=1)throw new IllegalStateException("Marker resolution must affect exactly one row");
            String fullEvidence="oldProcess="+state.get("active_delivery_process_owner")+" fenceProof="+fenceProof+" observer="+observerDisposition+" evidence="+evidence;
            if(fullEvidence.length()>2000)throw new IllegalArgumentException("Resolution evidence exceeds 2000 characters");
            jdbc.update("INSERT INTO shared_delivery_resolution_audit(source_event_id,symbol,operation,old_owner_token,new_owner_token,reviewer,evidence) VALUES(?,?,?,?,?,?,?)",eventId,symbol,operation,expectedOwner,newOwner,reviewer,fullEvidence);
        });
    }
    /** FIX-142 exceptional manual repair: the caller supplies independently verified
     * owner/transaction resolution, and this transaction rechecks all durable evidence.
     * This does NOT relax isolateAnalysisOnlyReview() or retry an old analysis/trade. */
    public void resolveHistoricalAnalysisOnly(long eventId,String expectedOwner,long savedSignal,
            String reviewer,String fenceProof,String evidence) {
        if(reviewer==null||reviewer.isBlank()||fenceProof==null||fenceProof.length()<20||evidence==null||evidence.length()<20)
            throw new IllegalArgumentException("Independent owner/transaction resolution and completion evidence required");
        var identity=jdbc.queryForMap("SELECT symbol FROM shared_market_event_delivery WHERE source_event_id=?",eventId);
        String symbol=str(identity,"symbol");
        transaction.executeWithoutResult(tx->{
            lockSymbol(symbol);
            var row=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",eventId);
            if(num(row,"phase")!=4||!"HISTORICAL_ONLY".equals(row.get("status"))
                || !"REVIEW_REQUIRED".equals(row.get("analysis_status"))||!Objects.equals(expectedOwner,row.get("owner_token")))
                throw new IllegalStateException("Only the exact fenced historical analysis-only attempt is eligible");
            if(row.get("protection_started_at")!=null&&row.get("protection_completed_at")==null)
                throw new IllegalStateException("Unresolved protection is retained");
            if(!jdbc.queryForList("SELECT signal_id FROM signal_processing_work WHERE source_event_id=? OR (symbol=? AND interval_code=? AND candle_open_time=?) FOR UPDATE",eventId,symbol,row.get("interval_code"),row.get("candle_open_time")).isEmpty())
                throw new IllegalStateException("Processing work evidence retains review");
            var signals=jdbc.queryForList("SELECT id,symbol,interval_code,candle_open_time FROM trade_signal WHERE id=? FOR UPDATE",savedSignal);
            if(signals.size()!=1)throw new IllegalStateException("Exact persisted signal evidence required");
            for(String key:List.of("symbol","interval_code","candle_open_time"))if(!Objects.equals(row.get(key),signals.getFirst().get(key)))
                throw new IllegalStateException("Persisted signal identity mismatch");
            if(!jdbc.queryForList("SELECT wt.id FROM wallet_trade wt JOIN trade_signal signal ON signal.id=wt.signal_id WHERE signal.symbol=? AND signal.interval_code=? AND signal.candle_open_time=? FOR UPDATE",symbol,row.get("interval_code"),row.get("candle_open_time")).isEmpty())
                throw new IllegalStateException("Wallet evidence retains review");
            String proof="signal="+savedSignal+" fenceProof="+fenceProof+" evidence="+evidence;
            if(proof.length()>2000)throw new IllegalArgumentException("Evidence exceeds 2000 characters");
            int changed=jdbc.update("UPDATE shared_market_event_delivery SET analysis_status='ANALYSIS_ONLY_REVIEW' WHERE source_event_id=? AND phase=4 AND status='HISTORICAL_ONLY' AND analysis_status='REVIEW_REQUIRED' AND (owner_token=? OR (owner_token IS NULL AND ? IS NULL))",eventId,expectedOwner,expectedOwner);
            if(changed!=1)throw new IllegalStateException("Exceptional repair must affect exactly one row");
            jdbc.update("INSERT INTO shared_delivery_resolution_audit(source_event_id,symbol,operation,old_owner_token,reviewer,evidence) VALUES(?,?,'EXCEPTIONAL_ANALYSIS_ONLY',?,?,?)",eventId,symbol,expectedOwner,reviewer,proof);
        });
    }
    public void dispatchAnalysis() { analysisDispatch.live(); }
    public void dispatchHistoricalAnalysis() { analysisDispatch.historical(); }
    static long num(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    static String str(Map<String,Object> row,String key) { return String.valueOf(row.get(key)); }
    static Instant time(Map<String,Object> row,String key) { return row.get(key)==null?null:((Timestamp)row.get(key)).toInstant(); }
    static boolean truth(Object value) { return Boolean.TRUE.equals(value)||"1".equals(String.valueOf(value)); }
    static String shortError(Exception e) { String s=e.toString();return s.substring(0,Math.min(1000,s.length())); }
    @PreDestroy public void stop() {
        stopped=true;readinessClock.shutdownNow();protectionClock.shutdownNow();freshClock.shutdownNow();protectionPool.shutdown();analysisDispatch.stop();historyClock.shutdown();monitorClock.shutdown();discoveryClock.shutdownNow();priceClock.shutdownNow();analysisClock.shutdown();prices.shutdown();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        try {
            boolean priceDone=prices.awaitTermination(30,TimeUnit.SECONDS);
            boolean analysisDone=analysisClock.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            boolean historyDone=historyClock.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            boolean monitorDone=monitorClock.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            boolean readinessDone=readinessClock.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            boolean protectionDone=protectionPool.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            if(!readinessDone || !protectionDone || !priceDone || !analysisDone || !historyDone || !monitorDone)log.error("[FIX-132][SHUTDOWN_REVIEW_REQUIRED] worker did not drain within 30 seconds; reconcile durable phases before restarting execution");
        } catch(InterruptedException e) {Thread.currentThread().interrupt();log.error("[FIX-132][SHUTDOWN_REVIEW_REQUIRED] drain interrupted",e);}
    }
}
