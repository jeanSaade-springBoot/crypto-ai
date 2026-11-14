package com.crypto.shared;

import com.crypto.wallet.service.WalletTransactionCoordination;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/** FIX-150: abandon expired price observations without replay, not without evidence.
 * CLAIMED means the separately committed entry gate was never crossed. Legacy/APPLYING
 * attempts retain unknown past effects and may be abandoned only with no open positions.
 * Both paths keep the original owners/error and write an atomic, explicit audit. */
@Component
public class Fix150ProtectionRecovery {
    private static final Logger log=LoggerFactory.getLogger(Fix150ProtectionRecovery.class);
    private final JdbcTemplate jdbc;
    private final WalletTransactionCoordination coordination;
    private final TransactionTemplate transaction;
    @org.springframework.beans.factory.annotation.Value("${shared-market.fix150.automatic-recovery-enabled:true}")
    private boolean enabled=true;

    public Fix150ProtectionRecovery(JdbcTemplate jdbc,WalletTransactionCoordination coordination,
            PlatformTransactionManager manager) {
        // Dedicated statement bounds do not change the application's shared JdbcTemplate.
        this.jdbc=new JdbcTemplate(Objects.requireNonNull(jdbc.getDataSource()));
        this.jdbc.setQueryTimeout(2);
        this.coordination=coordination;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(5);
    }

    public List<Long> recover(String symbol) {
        if(!enabled)return List.of();
        Instant start=Instant.now();long clock=System.nanoTime();String outcome="FAILED";
        try {
            List<Long> result=transaction.execute(tx->{
                // Apply and every phase walker hold this same row THROUGH commit. An old
                // apply cannot be executing concurrently or resume after the terminal write.
                var state=jdbc.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE",symbol);
                if(!SharedCutoverPolicy.admitted(state,true))return List.<Long>of();
                // Never-entered claims need no 30-second uncertainty timeout: reclaim
                // once their price window expires. Existing/entered attempts still age 30s.
                var rows=jdbc.queryForList("SELECT * FROM shared_market_event_delivery WHERE symbol=? AND interval_code='1m' AND protection_started_at IS NOT NULL AND protection_completed_at IS NULL AND (protection_started_at<? OR (protection_apply_state='CLAIMED' AND observed_at<?)) ORDER BY protection_started_at,source_event_id LIMIT 3 FOR UPDATE",
                    symbol,Timestamp.from(start.minusSeconds(30)),Timestamp.from(start.minusSeconds(15)));
                if(rows.isEmpty())return List.<Long>of();
                // The normal position-creation coordinator uses this wallet fence too.
                // Never nest REQUIRES_NEW while these locks are held.
                coordination.symbol(symbol);
                boolean hasPosition=!jdbc.queryForList("SELECT id FROM wallet_managed_position WHERE symbol=? AND status='OPEN' LIMIT 1",symbol).isEmpty()
                    || !jdbc.queryForList("SELECT id FROM paper_position WHERE symbol=? AND status='OPEN' LIMIT 1",symbol).isEmpty();
                var recovered=new ArrayList<Long>();
                for(var row:rows) {
                    long event=((Number)row.get("source_event_id")).longValue();
                    int phase=((Number)row.get("phase")).intValue();
                    if(phase<0||phase>1||!timestamp(row,"observed_at").isBefore(start.minusSeconds(15)))continue;
                    // Protection retirement never overwrites a separate analysis owner/evidence.
                    if(row.get("analysis_started_at")!=null||!"WAITING".equals(row.get("analysis_status")))continue;
                    if(!jdbc.queryForList("SELECT signal_id FROM signal_processing_work WHERE source_event_id=? LIMIT 1",event).isEmpty())continue;
                    if(!java.util.Set.of("PROTECTION_RUNNING","PROCESSING","REVIEW_REQUIRED","PENDING").contains(row.get("status")))continue;
                    boolean neverEntered="CLAIMED".equals(row.get("protection_apply_state"));
                    if(!neverEntered&&hasPosition)continue; // A legacy/entered attempt with a position remains uncertain.
                    Object marker=state.get("active_delivery_event_id");
                    boolean sameMarker=marker instanceof Number n&&n.longValue()==event;
                    if(!"NONE".equals(state.get("active_delivery_state"))&&!sameMarker)continue;
                    if(sameMarker&&!java.util.Set.of("ACTIVE","REVIEW_REQUIRED").contains(state.get("active_delivery_state")))continue;
                    String operation=neverEntered?"PRICE_NOT_ENTERED":"PRICE_ABANDONED_FLAT";
                    String evidence="FIX-150 startUtc="+row.get("protection_started_at")+" recoveryUtc="+start
                        +" recoveryKsa="+start.atOffset(ZoneOffset.ofHours(3))+" oldProcess="+state.get("active_delivery_process_owner")
                        +" oldMarkerOwner="+state.get("active_delivery_owner_token")+" walletFence=HELD openPositions="+hasPosition
                        +" pastEffects="+(neverEntered?"BUSINESS_NOT_ENTERED":"UNKNOWN_RETAINED")
                        +" decision=EXPIRED_PRICE_ABANDONED_NO_REPLAY; not a COMMITTED/NO_EFFECTS certification";
                    // Operational completion closes the slot. ABANDONED explicitly avoids
                    // falsely declaring an unknown legacy attempt APPLIED or REJECTED.
                    int changed=jdbc.update("UPDATE shared_market_event_delivery SET protection_completed_at=CURRENT_TIMESTAMP(6),protection_outcome=?,protection_apply_state=?,phase=4,status='HISTORICAL_ONLY',analysis_status='NOT_APPLICABLE',observer_status='NOT_APPLICABLE' WHERE source_event_id=? AND protection_owner=? AND protection_started_at=? AND protection_completed_at IS NULL AND phase=?",
                        neverEntered?"REJECTED":"ABANDONED",neverEntered?"NOT_ENTERED":"ABANDONED",event,row.get("protection_owner"),row.get("protection_started_at"),phase);
                    if(changed!=1)throw new IllegalStateException("FIX-150 attempt guard changed");
                    jdbc.update("UPDATE market_price_event SET delivery_status='HISTORICAL_ONLY' WHERE source_event_id=? AND delivery_status='PENDING'",event);
                    if(sameMarker) {
                        int released=jdbc.update("UPDATE shared_market_consumer_state SET active_delivery_event_id=NULL,active_delivery_owner_token=NULL,active_delivery_process_owner=NULL,active_delivery_state='NONE',active_delivery_started_at=NULL,active_delivery_updated_at=CURRENT_TIMESTAMP(6) WHERE symbol=? AND active_delivery_event_id=? AND active_delivery_owner_token=? AND active_delivery_state=?",
                            symbol,event,state.get("active_delivery_owner_token"),state.get("active_delivery_state"));
                        if(released!=1)throw new IllegalStateException("FIX-150 marker guard changed");
                        // Local state follows this transaction so another row cannot release it again.
                        state.put("active_delivery_state","NONE");state.put("active_delivery_event_id",null);
                    }
                    if(jdbc.update("INSERT INTO shared_delivery_resolution_audit(source_event_id,symbol,operation,old_owner_token,reviewer,evidence) VALUES(?,?,?,?,?,?)",
                            event,symbol,operation,row.get("protection_owner"),"FIX150_SYSTEM",evidence)!=1)
                        throw new IllegalStateException("FIX-150 audit insert failed");
                    recovered.add(event);
                }
                return List.copyOf(recovered);
            });
            outcome="RETURNED";
            if(result!=null&&!result.isEmpty()) {
                Fix142Metrics.count("FIX150_AUTOMATIC_RETIREMENT");
                log.warn("[FIX-150][RECOVERY_COMMITTED] symbol={}, count={}, recoveryUtc={}, recoveryKsa={}; old observations never replayed; see durable effect disposition",symbol,result.size(),Instant.now(),Instant.now().atOffset(ZoneOffset.ofHours(3)));
            }
            return result==null?List.of():result;
        } finally {
            // End follows transaction return, not merely the final UPDATE. No bind values or passwords.
            log.debug("[FIX-150][RECOVERY_TRANSACTION] symbol={}, startUtc={}, endUtc={}, elapsedMs={}, outcome={}",
                symbol,start,Instant.now(),(System.nanoTime()-clock)/1_000_000,outcome);
        }
    }
    /** FIX-152: resume ONLY phase-1 work whose protection was rejected without an
     * attempt ever starting. The shared lock serializes all effect/phase writers;
     * replacing the marker token fences their subsequent ownership rechecks. This
     * is continuation of bookkeeping, not a protection retry or an effect inference
     * from timeout. Incomplete protection, APPLIED/unknown outcomes and phase-2
     * observer uncertainty are deliberately outside this recovery contract. */
    public boolean resumeRejectedMarker(String symbol,String currentProcessOwner) {
        if(!enabled)return false;
        if(currentProcessOwner==null||currentProcessOwner.isBlank())throw new IllegalArgumentException("FIX-152 current process identity required");
        // Cheap PK probe avoids opening another locked transaction for healthy symbols.
        if(jdbc.queryForList("SELECT active_delivery_event_id FROM shared_market_consumer_state WHERE symbol=? AND active_delivery_state='REVIEW_REQUIRED'",symbol).isEmpty())return false;
        Instant start=Instant.now();long clock=System.nanoTime();
        Boolean resumed=transaction.execute(tx->{
            var state=jdbc.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE",symbol);
            if(!SharedCutoverPolicy.admitted(state,true)||!"REVIEW_REQUIRED".equals(state.get("active_delivery_state")))return false;
            Object event=state.get("active_delivery_event_id"),oldOwner=state.get("active_delivery_owner_token");
            if(!(event instanceof Number)||oldOwner==null)return false;
            var row=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",event);
            if(!symbol.equals(row.get("symbol"))||!"1m".equals(row.get("interval_code"))
                    ||((Number)row.get("phase")).intValue()!=1
                    ||!java.util.Set.of("PROCESSING","PENDING").contains(row.get("status"))
                    ||row.get("protection_started_at")!=null||row.get("protection_owner")!=null
                    ||row.get("protection_completed_at")==null||!"REJECTED".equals(row.get("protection_outcome"))
                    ||!java.util.Set.of("LEGACY_UNKNOWN","NOT_ENTERED").contains(row.get("protection_apply_state"))
                    ||row.get("analysis_started_at")!=null||row.get("analysis_completed_at")!=null
                    ||!"WAITING".equals(row.get("analysis_status"))||!"PENDING".equals(row.get("observer_status")))return false;
            // Existing phase 0 must have durably registered the pending price event.
            // Refuse a conflicting application result rather than overwriting evidence.
            var price=jdbc.queryForList("SELECT delivery_status FROM market_price_event WHERE source_event_id=? FOR UPDATE",event);
            if(price.size()!=1||!"PENDING".equals(price.getFirst().get("delivery_status")))return false;
            if(!jdbc.queryForList("SELECT signal_id FROM signal_processing_work WHERE source_event_id=? LIMIT 1",event).isEmpty())return false;
            String newOwner=java.util.UUID.randomUUID().toString();
            int changed=jdbc.update("UPDATE shared_market_consumer_state SET active_delivery_state='ACTIVE',active_delivery_owner_token=?,active_delivery_process_owner=?,active_delivery_updated_at=CURRENT_TIMESTAMP(6) WHERE symbol=? AND active_delivery_event_id=? AND active_delivery_owner_token=? AND active_delivery_state='REVIEW_REQUIRED'",
                newOwner,currentProcessOwner,symbol,event,oldOwner);
            if(changed!=1)throw new IllegalStateException("FIX-152 marker compare-and-set failed");
            String evidence="FIX-152 protection=REJECTED completedUtc="+row.get("protection_completed_at")
                +" started=NULL protectionOwner=NULL price=PENDING phase=1 analysis=WAITING observer=PENDING"
                +" sharedFence=HELD oldProcess="+state.get("active_delivery_process_owner")
                +" newProcess="+currentProcessOwner+" resumedUtc="+start+" resumedKsa="+start.atOffset(ZoneOffset.ofHours(3))
                +" oldToken=FENCED; continuation through normal phase checks; no old onPrice replay";
            if(jdbc.update("INSERT INTO shared_delivery_resolution_audit(source_event_id,symbol,operation,old_owner_token,new_owner_token,reviewer,evidence) VALUES(?,?,'REJECTED_MARKER_RESUMED',?,?,?,?)",
                    event,symbol,oldOwner,newOwner,"FIX152_SYSTEM",evidence)!=1)throw new IllegalStateException("FIX-152 audit insert failed");
            return true;
        });
        if(Boolean.TRUE.equals(resumed))log.warn("[FIX-152][REJECTED_MARKER_RESUMED] symbol={}, startUtc={}, endUtc={}, endKsa={}, elapsedMs={}; old token fenced; no protection replay; inspect atomic audit",
            symbol,start,Instant.now(),Instant.now().atOffset(ZoneOffset.ofHours(3)),(System.nanoTime()-clock)/1_000_000);
        return Boolean.TRUE.equals(resumed);
    }
    private static Instant timestamp(Map<String,Object> row,String key) {
        Object value=row.get(key);
        if(value instanceof Timestamp t)return t.toInstant();
        if(value instanceof java.time.LocalDateTime t)return t.toInstant(ZoneOffset.UTC);
        if(value instanceof Instant t)return t;
        throw new IllegalStateException("FIX-150 invalid UTC timestamp "+key);
    }
}
