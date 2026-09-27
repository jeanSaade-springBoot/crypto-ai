package com.fix132.approval;

import com.crypto.shared.SharedMarketSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.*;
import java.util.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** FIX-132: isolated maintenance approval. Source reads finish before local
 * locking begins: there is no cross-datasource atomicity or XA claim. Advancing
 * source cursors do not change the reviewed boundary. Freshness is still required
 * later by the LIVE consumer. All old Trader processes MUST be stopped first. */
public class CutoverApprovalService {
    private static final Logger log=LoggerFactory.getLogger(CutoverApprovalService.class);
    private final JdbcTemplate jdbc;
    private final SharedMarketSource source;
    private final TransactionTemplate tx;
    public CutoverApprovalService(JdbcTemplate jdbc,SharedMarketSource source,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.source=source;tx=new TransactionTemplate(manager);tx.setTimeout(5);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    private String authorize(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(a->"ROLE_CUTOVER_APPROVER".equals(a.getAuthority())))
            throw new AccessDeniedException("FIX-132 CUTOVER_APPROVER permission required");
        if(!"OBSERVE".equals(source.mode()))throw new IllegalStateException("FIX-132 approval requires isolated OBSERVE console");
        if(auth.getName()==null || auth.getName().isBlank() || auth.getName().length()>160)throw new AccessDeniedException("Invalid approval identity");
        return auth.getName();
    }
    public Map<String,Object> preview(String symbol,String operation,Authentication auth) {
        String actor=authorize(auth);
        if(symbol==null || !symbol.matches("[A-Z0-9]{1,26}USDT") || !Set.of("FIRST_CUTOVER","REAPPROVAL").contains(operation==null?"":operation))
            throw new IllegalArgumentException("Exact symbol and FIRST_CUTOVER or REAPPROVAL required");
        // One source statement captures committed cursor plus explicit UTC time.
        // SharedMarketSource forces this connection session and JDBC mapping to UTC.
        var boundary=source.feedReader().queryForObject("SELECT last_sequence,CURRENT_TIMESTAMP(6) AS cutover_at FROM market_data_stream_cursor WHERE symbol=?",(rs,n)->Map.<String,Object>of("last_sequence",rs.getLong("last_sequence"),"cutover_at",rs.getTimestamp("cutover_at")),symbol);
        return tx.execute(t->{
            var state=state(symbol);check(state,symbol,operation);
            long latest=((Number)boundary.get("last_sequence")).longValue();
            if(latest<((Number)state.get("discovered_sequence")).longValue())throw new IllegalStateException("Source sequence regressed");
            // Re-approval preserves the original boundary and progress. It is NOT
            // permission to skip history by moving an already-live cutover forward.
            long sequence=((Number)("REAPPROVAL".equals(operation)?state.get("cutover_sequence"):boundary.get("last_sequence"))).longValue();
            String id=UUID.randomUUID().toString();Timestamp at=(Timestamp)("REAPPROVAL".equals(operation)?state.get("cutover_at"):boundary.get("cutover_at"));
            Timestamp expires=Timestamp.from(Instant.now().plusSeconds(300));
            jdbc.update("INSERT INTO shared_market_cutover_proposal(id,symbol,operation,requested_by,base_state_hash,cutover_sequence,cutover_at,expires_at) VALUES(?,?,?,?,?,?,?,?)",
                id,symbol,operation,actor,hash(state),sequence,at,expires);
            log.info("[FIX-132][CUTOVER_PREVIEW] proposal={}, symbol={}, operation={}, actor={}, sequence={}, cutoverUtc={}",id,symbol,operation,actor,sequence,at.toInstant());
            return Map.<String,Object>of("proposalId",id,"symbol",symbol,"operation",operation,"cutoverSequence",sequence,"cutoverUtc",at.toInstant(),"expiresUtc",expires.toInstant());
        });
    }
    public Map<String,Object> approve(String id,String reference,Authentication auth) {
        String actor=authorize(auth);
        if(id==null || !id.matches("[a-f0-9-]{36}") || reference==null || reference.isBlank() || reference.length()>255 || reference.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Reviewed proposalId and nonblank approval reference required");
        var proposal=jdbc.queryForMap("SELECT * FROM shared_market_cutover_proposal WHERE id=?",id);
        String symbol=(String)proposal.get("symbol");
        Long latest=source.feedReader().queryForObject("SELECT last_sequence FROM market_data_stream_cursor WHERE symbol=?",Long.class,symbol);
        var result=tx.execute(t->{
            // Same symbol serialization as consumer phase/claim transactions.
            var state=state(symbol);
            var p=jdbc.queryForMap("SELECT * FROM shared_market_cutover_proposal WHERE id=? FOR UPDATE",id);
            if(p.get("consumed_at")!=null || ((Timestamp)p.get("expires_at")).toInstant().isBefore(Instant.now())
                || !actor.equals(p.get("requested_by")) || !hash(state).equals(p.get("base_state_hash")))
                throw new IllegalStateException("FIX-132 consumed, expired, changed or differently-owned proposal; preview again");
            check(state,symbol,(String)p.get("operation"));
            long sequence=((Number)p.get("cutover_sequence")).longValue();
            if(latest==null || latest<Math.max(sequence,((Number)state.get("discovered_sequence")).longValue()))throw new IllegalStateException("Source sequence regressed");
            Timestamp at=(Timestamp)p.get("cutover_at"),approved=Timestamp.from(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            jdbc.update("INSERT INTO shared_market_cutover_approval(symbol,cutover_sequence,cutover_at,approved_by,approved_at,approval_reference) VALUES(?,?,?,?,?,?)",symbol,sequence,at,actor,approved,reference);
            if("FIRST_CUTOVER".equals(p.get("operation")))
                jdbc.update("UPDATE shared_market_consumer_state SET cutover_sequence=?,cutover_at=?,discovered_sequence=?,last_observed_at=NULL,last_price=NULL WHERE symbol=?",sequence,at,sequence,symbol);
            jdbc.update("""
                UPDATE shared_market_consumer_state SET status='READY',cutover_source='OPERATOR_APPROVED',
                approved_sequence=?,approved_cutover_at=?,approved_by=?,approved_at=?,approval_reference=? WHERE symbol=?
                """,sequence,at,actor,approved,reference,symbol);
            jdbc.update("UPDATE shared_market_cutover_proposal SET consumed_at=? WHERE id=?",approved,id);
            return Map.<String,Object>of("symbol",symbol,"cutoverSequence",sequence,"cutoverUtc",at.toInstant(),"approvedBy",actor,"approvalReference",reference,"liveEnabled",false);
        });
        log.info("[FIX-132][CUTOVER_APPROVED] proposal={}, symbol={}, actor={}, reference={}; activation remains separate",id,symbol,actor,reference);
        return result;
    }
    private Map<String,Object> state(String symbol) {
        var rows=jdbc.queryForList("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE",symbol);
        if(rows.size()!=1)throw new IllegalStateException("Observe symbol first; missing state");return rows.getFirst();
    }
    private void check(Map<String,Object> state,String symbol,String operation) {
        if(!Set.of("PENDING_CUTOVER","READY").contains(state.get("status")))throw new IllegalStateException("Quarantined symbol requires separate reconciliation");
        int prior=jdbc.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_approval WHERE symbol=?",Integer.class,symbol);
        int history=jdbc.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_history WHERE symbol=?",Integer.class,symbol);
        if((prior==0 && history==0)!=operation.equals("FIRST_CUTOVER"))throw new IllegalStateException("Use explicit FIRST_CUTOVER or REAPPROVAL for this symbol");
        var work=jdbc.queryForList("SELECT signal_id,status,source_event_id FROM signal_processing_work WHERE symbol=? AND status NOT IN ('COMPLETED','EXPIRED') LIMIT 25",symbol);
        if(!work.isEmpty())throw new IllegalStateException("Unfinished processing work: "+work);
        var delivery=jdbc.queryForList("""
            SELECT source_event_id,status,analysis_status FROM shared_market_event_delivery WHERE symbol=?
            AND (phase<4 OR status='REVIEW_REQUIRED' OR analysis_status IN ('PENDING','RUNNING','REVIEW_REQUIRED','HISTORICAL_DEFERRED')) LIMIT 25
            """,symbol);
        if(!delivery.isEmpty())throw new IllegalStateException("Unfinished delivery work: "+delivery);
    }
    private static String hash(Map<String,Object> state) {
        try {
            // Includes approval provenance and discovery position: no stale preview may
            // overwrite a concurrently progressed, approved or quarantined symbol.
            String value=new TreeMap<>(state).toString();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
