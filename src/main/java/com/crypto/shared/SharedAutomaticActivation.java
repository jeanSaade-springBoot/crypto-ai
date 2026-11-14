package com.crypto.shared;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** FIX-148: automatic FIRST cutover only. Never reset an approved boundary or
 * infer that a timed-out business attempt had no effects. Source reads complete
 * before taking the local symbol lock. Activation and its audit commit together. */
public final class SharedAutomaticActivation {
    private static final Logger log=LoggerFactory.getLogger(SharedAutomaticActivation.class);
    private final SharedMarketSource source;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public SharedAutomaticActivation(SharedMarketSource source,JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.source=source;this.jdbc=jdbc;tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setTimeout(2);
    }
    private static boolean candidate(Map<String,Object> state) {
        return "PENDING_CUTOVER".equals(state.get("status")) && "AUTO_OBSERVED".equals(state.get("cutover_source"))
            && state.get("approved_at")==null && state.get("approved_by")==null
            && state.get("approval_reference")==null && state.get("approved_sequence")==null
            && state.get("approved_cutover_at")==null && "NONE".equals(state.get("active_delivery_state"))
            && state.get("active_delivery_event_id")==null && state.get("active_delivery_owner_token")==null
            && state.get("active_delivery_process_owner")==null;
    }
    public boolean activateFirst(String symbol,List<String> intervals) {
        // Existing JDBC interceptor records execute/fetch/commit spans with phase context.
        try(var timing=com.crypto.infrastructure.diagnostics.JdbcTimingDataSource.context("symbol="+symbol+" phase=AUTO_FIRST_ACTIVATION")) {
            return activateChecked(symbol,intervals);
        }
    }
    private boolean activateChecked(String symbol,List<String> intervals) {
        if(!source.enabled())return false;
        var rows=jdbc.queryForList("SELECT * FROM shared_market_consumer_state WHERE symbol=?",symbol);
        if(rows.size()>1 || !rows.isEmpty() && !candidate(rows.getFirst()))return false;
        var captured=source.feedReader().queryForMap("SELECT last_sequence,CURRENT_TIMESTAMP(6) AS cutover_at FROM market_data_stream_cursor WHERE symbol=?",symbol);
        long sequence=((Number)captured.get("last_sequence")).longValue();
        Instant boundary=jdbcInstant(captured.get("cutover_at"));
        if(sequence<0)return false;
        if(rows.isEmpty()) {
            // New enabled symbols bootstrap observation state only, never execution.
            tx.executeWithoutResult(status->jdbc.update("INSERT IGNORE INTO shared_market_consumer_state(symbol,cutover_sequence,cutover_at,discovered_sequence,status,cutover_source) VALUES(?,?,?,?,'PENDING_CUTOVER','AUTO_OBSERVED')",symbol,sequence,Timestamp.from(boundary),sequence));
            rows=jdbc.queryForList("SELECT * FROM shared_market_consumer_state WHERE symbol=?",symbol);
            if(rows.size()!=1 || !candidate(rows.getFirst()))return false;
        }
        var expected=rows.getFirst();
        if(sequence<((Number)expected.get("discovered_sequence")).longValue())return false;
        // Current closed 1m source context and sufficient canonical history are
        // mandatory even though activation itself must not replay that old close.
        var recent=source.feedReader().queryForList("SELECT id FROM market_data_stream_event WHERE symbol=? AND interval_code='1m' AND closed=1 AND source='LIVE_WEBSOCKET' AND classification='LIVE' AND candle_close_time BETWEEN ? AND ? AND symbol_sequence>? AND symbol_sequence<=? LIMIT 1",
            symbol,Timestamp.from(boundary.minusSeconds(90)),Timestamp.from(boundary),Math.max(0,sequence-512),sequence);
        if(recent.isEmpty())return false;
        Set<String> required=new HashSet<>(intervals);required.add("1m");
        for(String interval:required) {
            Long count=source.reader().queryForObject("SELECT COUNT(*) FROM (SELECT id FROM candle WHERE symbol=? AND interval_code=? AND closed=1 LIMIT 300) c",Long.class,symbol,interval);
            if(count==null || count<300)return false;
        }
        String reference="FIX-148-AUTO-"+UUID.randomUUID();
        Boolean activated=tx.execute(status->{
            var state=jdbc.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE",symbol);
            if(!candidate(state))return false;
            for(String field:List.of("cutover_sequence","cutover_at","discovered_sequence"))
                if(!Objects.equals(expected.get(field),state.get(field)))return false;
            Integer prior=jdbc.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_approval WHERE symbol=?",Integer.class,symbol);
            Integer history=jdbc.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_history WHERE symbol=?",Integer.class,symbol);
            if(prior!=0 || history!=0)return false; // no automatic reapproval/reset
            if(!jdbc.queryForList(OperatorWorkCancellation.APPROVAL_BLOCKERS,symbol).isEmpty())return false;
            retireExpiredNeverStarted(symbol,boundary);
            var unresolved=jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE symbol=? AND (phase<4 OR status='REVIEW_REQUIRED' OR analysis_status IN ('PENDING','RUNNING','REVIEW_REQUIRED','HISTORICAL_DEFERRED','EXECUTION_RETRY') OR (protection_started_at IS NOT NULL AND protection_completed_at IS NULL)) LIMIT 1",symbol);
            if(!unresolved.isEmpty())return false;
            // Do not activate from a source snapshot that aged during checks/lock waits.
            Instant now=Instant.now();
            if(boundary.isAfter(now.plusSeconds(5)) || boundary.isBefore(now.minusSeconds(15)))return false;
            Timestamp at=Timestamp.from(boundary),recorded=Timestamp.from(now);
            jdbc.update("INSERT INTO shared_market_cutover_approval(symbol,cutover_sequence,cutover_at,approved_by,approved_at,approval_reference) VALUES(?,?,?,'FIX148_SYSTEM',?,?)",symbol,sequence,at,recorded,reference);
            int changed=jdbc.update("UPDATE shared_market_consumer_state SET status='READY',cutover_source='SYSTEM_VALIDATED',cutover_sequence=?,cutover_at=?,discovered_sequence=?,last_observed_at=NULL,last_price=NULL,approved_by='FIX148_SYSTEM',approved_at=?,approval_reference=?,approved_sequence=?,approved_cutover_at=? WHERE symbol=? AND status='PENDING_CUTOVER' AND cutover_source='AUTO_OBSERVED'",
                sequence,at,sequence,recorded,reference,sequence,at,symbol);
            if(changed!=1)throw new IllegalStateException("FIX-148 activation CAS failed; audit rollback");
            jdbc.update("INSERT INTO shared_market_cutover_history(symbol,cutover_at,cutover_sequence) VALUES(?,?,?)",symbol,at,sequence);
            return true;
        });
        if(Boolean.TRUE.equals(activated))log.info("[FIX-148][AUTOMATIC_FIRST_CUTOVER] symbol={}, sequence={}, boundaryUtc={}, boundaryKsa={}, reference={}; no historical execution replay",
            symbol,sequence,boundary,boundary.atOffset(java.time.ZoneOffset.ofHours(3)),reference);
        return Boolean.TRUE.equals(activated);
    }
    private void retireExpiredNeverStarted(String symbol,Instant now) {
        // Phase zero proves no phase claim, but also reject ANY durable evidence.
        // LIMIT bounds returned rows only. Symbol lock precedes delivery row locks.
        var rows=jdbc.queryForList("SELECT * FROM shared_market_event_delivery WHERE symbol=? AND phase=0 AND status='PENDING' AND analysis_status='WAITING' AND observer_status='PENDING' AND owner_token IS NULL AND protection_owner IS NULL AND protection_started_at IS NULL AND protection_completed_at IS NULL AND analysis_started_at IS NULL AND analysis_completed_at IS NULL AND observed_at<? ORDER BY symbol_sequence LIMIT 8 FOR UPDATE",
            symbol,Timestamp.from(now.minusSeconds(15)));
        for(var row:rows) {
            String interval=(String)row.get("interval_code");
            Instant close=jdbcInstant(row.get("candle_close_time"));
            if(Boolean.TRUE.equals(row.get("closed")) || row.get("closed") instanceof Number n && n.intValue()!=0)
                if(SharedEventPolicy.closeEligible(interval,close,now))continue;
            long id=((Number)row.get("source_event_id")).longValue();
            if(!jdbc.queryForList("SELECT signal_id FROM signal_processing_work WHERE source_event_id=? OR (symbol=? AND interval_code=? AND candle_open_time=?) LIMIT 1",id,symbol,interval,row.get("candle_open_time")).isEmpty())continue;
            if(!jdbc.queryForList("SELECT id FROM trade_signal WHERE symbol=? AND interval_code=? AND candle_open_time=? LIMIT 1",symbol,interval,row.get("candle_open_time")).isEmpty())continue;
            if(!jdbc.queryForList("SELECT id FROM market_price_event WHERE source_event_id=? LIMIT 1",id).isEmpty())continue;
            jdbc.update("INSERT INTO shared_delivery_resolution_audit(source_event_id,symbol,operation,reviewer,evidence) VALUES(?,?,'AUTO_EXPIRE_NEVER_STARTED','FIX148_SYSTEM',?)",
                id,symbol,"phase=0; expired price and analysis eligibility; no owners/start/completion/work/signal/price effect evidence; no replay; cutoffUtc="+now);
            int changed=jdbc.update("UPDATE shared_market_event_delivery SET phase=4,status='SKIPPED',analysis_status='NOT_APPLICABLE',observer_status='SKIPPED',eligibility_reason='AUTO_EXPIRED_NEVER_STARTED',last_error='FIX-148 expired unstarted observation; no replay',updated_at=CURRENT_TIMESTAMP(6) WHERE source_event_id=? AND phase=0 AND status='PENDING' AND owner_token IS NULL AND protection_started_at IS NULL AND analysis_started_at IS NULL",id);
            if(changed!=1)throw new IllegalStateException("FIX-148 expiry CAS failed; audit rollback");
        }
    }
    // FIX-148: queryForMap uses JDBC getObject; H2 CURRENT_TIMESTAMP returns
    // OffsetDateTime while MySQL commonly returns Timestamp. Preserve offsets;
    // timezone-free values belong to this application's canonical UTC schema.
    // Invalid/null evidence fails closed rather than inventing a fresh time.
    static Instant jdbcInstant(Object value) {
        if(value instanceof Timestamp timestamp)return timestamp.toInstant();
        if(value instanceof java.time.OffsetDateTime offset)return offset.toInstant();
        if(value instanceof Instant instant)return instant;
        if(value instanceof java.time.LocalDateTime local)return local.toInstant(java.time.ZoneOffset.UTC);
        throw new IllegalArgumentException("FIX-148 missing or unsupported JDBC timestamp type: "+(value==null?"null":value.getClass().getName()));
    }
}
