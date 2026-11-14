package com.crypto.shared;

import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static com.crypto.shared.SharedMarketConsumer.*;

/** FIX-142: all methods which mutate or reserve local state run under the caller's
 * symbol lock. Source reads happen before that lock. Review evidence is never evicted.
 * Exposure is an admission throttle, not a universal bound on retained durable rows. */
final class Fix142DeliveryProtocol {
    private final JdbcTemplate jdbc;
    final String processOwner=UUID.randomUUID().toString();
    int ceiling=32, reserve=4;
    Fix142DeliveryProtocol(JdbcTemplate jdbc){this.jdbc=jdbc;}
    // FIX-151: each branch uses a separate eligibility category instead of a broad
    // OR scan of a symbol's history. UNION (not UNION ALL) counts overlapping
    // marker/protection/window evidence exactly once. The caller's symbol lock
    // and the single statement snapshot preserve the existing reservation contract.
    static final String EXPOSURE_SQL="""
        SELECT COUNT(*) FROM (
          SELECT source_event_id FROM shared_market_event_delivery
          WHERE symbol=? AND interval_code=? AND phase=4
            AND analysis_status IN ('RUNNING','EXECUTION_RETRY','REVIEW_REQUIRED')
          UNION
          SELECT source_event_id FROM shared_market_event_delivery
          WHERE symbol=? AND interval_code=? AND phase=4
            AND analysis_status IN ('PENDING','HISTORICAL_DEFERRED') AND closed=1
            AND candle_close_time BETWEEN ? AND ?
          UNION
          SELECT d.source_event_id FROM shared_market_consumer_state s
          JOIN shared_market_event_delivery d ON d.source_event_id=s.active_delivery_event_id
          WHERE s.symbol=? AND d.symbol=? AND d.interval_code=? AND d.phase<4
            AND s.active_delivery_state IN ('ACTIVE','REVIEW_REQUIRED')
          UNION
          SELECT source_event_id FROM shared_market_event_delivery
          WHERE symbol=? AND interval_code=? AND phase<4
            AND protection_started_at IS NOT NULL AND protection_completed_at IS NULL
          UNION
          SELECT source_event_id FROM shared_market_event_delivery
          WHERE symbol=? AND interval_code=? AND interval_code='1m' AND phase<4
            AND observed_at BETWEEN ? AND ?
          UNION
          SELECT source_event_id FROM shared_market_event_delivery
          WHERE symbol=? AND interval_code=? AND phase<4 AND closed=1
            AND candle_close_time BETWEEN ? AND ?
        ) exposure_ids
        """;
    int exposure(String symbol,String interval,Instant now) {
        Timestamp closeFrom=Timestamp.from(now.minusSeconds(SharedEventPolicy.closeGraceSeconds(interval)));
        Timestamp until=Timestamp.from(now),priceFrom=Timestamp.from(now.minusSeconds(15));
        return jdbc.queryForObject(EXPOSURE_SQL,Integer.class,
            symbol,interval,symbol,interval,closeFrom,until,
            symbol,symbol,interval,symbol,interval,
            symbol,interval,priceFrom,until,symbol,interval,closeFrom,until);
    }
    enum AdmissionResult { INSERTED, DUPLICATE, NO_CAPACITY }
    boolean insert(Map<String,Object> e,String symbol){return admit(e,symbol,true)==AdmissionResult.INSERTED;}
    AdmissionResult admit(Map<String,Object> e,String symbol,boolean allowNew) {
        // Identity comparison is performed even on duplicates; INSERT IGNORE would conceal corruption.
        if(!symbol.equals(e.get("symbol")))throw new IllegalArgumentException("FIX-142 source symbol mismatch");
        var existing=jdbc.queryForList("SELECT * FROM shared_market_event_delivery WHERE source_event_id=?",e.get("id"));
        if(!existing.isEmpty()) {
            var row=existing.getFirst();
            for(String key:List.of("symbol","symbol_sequence","interval_code","candle_open_time","candle_close_time","closed","observed_at","received_at","price","source","classification")) {
                Object a=row.get(key),b=e.get(key);
                boolean equal=key.equals("closed")?truth(a)==truth(b):a instanceof Number x&&b instanceof Number y
                    ?new java.math.BigDecimal(x.toString()).compareTo(new java.math.BigDecimal(y.toString()))==0:Objects.equals(a,b);
                if(!equal)throw new IllegalStateException("FIX-142 identity conflict event="+e.get("id")+" field="+key);
            }
            if(!Objects.equals(row.get("source_created_at"),e.get("created_at")))throw new IllegalStateException("FIX-142 identity conflict source_created_at");
            return AdmissionResult.DUPLICATE;
        }
        if(!allowNew)return AdmissionResult.NO_CAPACITY;
        jdbc.update("""
            INSERT INTO shared_market_event_delivery(source_event_id,symbol,symbol_sequence,interval_code,
            candle_open_time,candle_close_time,closed,observed_at,received_at,source_created_at,price,source,classification)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,e.get("id"),symbol,e.get("symbol_sequence"),e.get("interval_code"),e.get("candle_open_time"),e.get("candle_close_time"),
            e.get("closed"),e.get("observed_at"),e.get("received_at"),e.get("created_at"),e.get("price"),e.get("source"),e.get("classification"));
        return AdmissionResult.INSERTED;
    }
    Map<String,Object> select(String symbol,Map<String,Object> state) {
        Instant now=Instant.now();
        if(!"NONE".equals(state.get("active_delivery_state"))) {
            if(!"ACTIVE".equals(state.get("active_delivery_state")))return null;
            if(!processOwner.equals(state.get("active_delivery_process_owner")) || time(state,"active_delivery_updated_at").plusSeconds(30).isBefore(now)) {
                jdbc.update("UPDATE shared_market_consumer_state SET active_delivery_state='REVIEW_REQUIRED' WHERE symbol=? AND active_delivery_state='ACTIVE'",symbol);
                return null; // crash/timeout is a fence, never a release or adoption
            }
            return jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=?",state.get("active_delivery_event_id"));
        }
        var rows=jdbc.queryForList("""
            SELECT * FROM shared_market_event_delivery WHERE symbol=? AND phase<4 AND status<>'REVIEW_REQUIRED'
            AND interval_code='1m' AND observed_at BETWEEN ? AND ? ORDER BY symbol_sequence LIMIT 1
            """,symbol,Timestamp.from(now.minusSeconds(15)),Timestamp.from(now));
        if(rows.isEmpty()) {
            // Candle-close eligibility is interval-specific; select one candidate from each bounded lane.
            Map<String,Object> best=null;
            for(String interval:List.of("1m","5m","15m","1h","4h","1d")) {
                var lane=jdbc.queryForList("SELECT * FROM shared_market_event_delivery WHERE symbol=? AND interval_code=? AND phase<4 AND status<>'REVIEW_REQUIRED' AND closed=1 AND candle_close_time BETWEEN ? AND ? ORDER BY candle_close_time DESC,symbol_sequence LIMIT 1",
                    symbol,interval,Timestamp.from(now.minusSeconds(SharedEventPolicy.closeGraceSeconds(interval))),Timestamp.from(now));
                if(!lane.isEmpty()&&(best==null||time(lane.getFirst(),"candle_close_time").isAfter(time(best,"candle_close_time"))))best=lane.getFirst();
            }
            if(best!=null)rows=List.of(best);
        }
        if(rows.isEmpty()) {
            // FIX-151: the generated flag is the EXACT former phase/status predicate.
            // The equality-prefix index skips completed/review history while retaining
            // oldest-sequence ordering across ALL unfinished phases. Read only the ID
            // from the index, then fetch one full row by PK under the same symbol lock.
            var ids=jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE symbol=? AND phase_walk_pending=1 ORDER BY symbol_sequence LIMIT 1",symbol);
            if(!ids.isEmpty())rows=List.of(jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=?",ids.getFirst().get("source_event_id")));
        }
        if(rows.isEmpty())return null;
        var e=rows.getFirst();String token=UUID.randomUUID().toString();
        int changed=jdbc.update("UPDATE shared_market_consumer_state SET active_delivery_event_id=?,active_delivery_owner_token=?,active_delivery_process_owner=?,active_delivery_state='ACTIVE',active_delivery_started_at=?,active_delivery_updated_at=? WHERE symbol=? AND active_delivery_state='NONE'",
            e.get("source_event_id"),token,processOwner,Timestamp.from(now),Timestamp.from(now),symbol);
        if(changed!=1)throw new IllegalStateException("FIX-142 marker reservation conflict");
        return e;
    }
    boolean owned(Map<String,Object> state,long event,String token) {
        return "ACTIVE".equals(state.get("active_delivery_state"))&&Objects.equals(state.get("active_delivery_event_id"),event)
            &&Objects.equals(state.get("active_delivery_owner_token"),token)&&Objects.equals(state.get("active_delivery_process_owner"),processOwner);
    }
    void progress(String symbol,long event,String token,int phase) {
        int changed;
        if(phase==4)changed=jdbc.update("UPDATE shared_market_consumer_state SET active_delivery_state='NONE',active_delivery_event_id=NULL,active_delivery_owner_token=NULL,active_delivery_process_owner=NULL,active_delivery_started_at=NULL,active_delivery_updated_at=CURRENT_TIMESTAMP(6) WHERE symbol=? AND active_delivery_event_id=? AND active_delivery_owner_token=?",symbol,event,token);
        else changed=jdbc.update("UPDATE shared_market_consumer_state SET active_delivery_updated_at=CURRENT_TIMESTAMP(6) WHERE symbol=? AND active_delivery_event_id=? AND active_delivery_owner_token=?",symbol,event,token);
        if(changed!=1)throw new IllegalStateException("FIX-142 marker progression conflict");
    }
}
