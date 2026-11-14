package com.crypto.market.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * FIX-052 exact Replay/Production parity support.
 *
 * Production protects open positions from every canonical 1m Binance live-price
 * update, not only from candle-close analysis signals. Persisting that exact feed
 * lets Replay evaluate TP/SL/profit-lock against the same observations and order.
 * Timestamps are always stored/interpreted as UTC Instants; timezone conversion is
 * a presentation concern only.
 */
@Service
@org.springframework.context.annotation.DependsOn("localTraderInstance")
@RequiredArgsConstructor
public class MarketPriceEventService {
    private static final int PRICE_SCALE=12;
    private final JdbcTemplate jdbcTemplate;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.crypto.local.ExchangeClockSynchronizer exchangeClock;

    @org.springframework.beans.factory.annotation.Value("${shared-market.mode:OFF}") private String mode="OFF";
    @org.springframework.beans.factory.annotation.Value("${local-candle.price-max-age-seconds:15}") private long maxAge=15;
    @org.springframework.beans.factory.annotation.Value("${local-candle.price-future-skew-seconds:2}") private long futureSkew=2;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(MarketPriceEventService.class);
    private final java.util.Set<Long> inFlight=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static final String DIRECT_SOURCE="BINANCE_KLINE_DIRECT_FIX153";
    public static BigDecimal normalizePrice(BigDecimal price){
        return price==null?null:price.setScale(PRICE_SCALE,java.math.RoundingMode.HALF_UP);
    }
    private Instant referenceNow(){Instant local=Instant.now();return exchangeClock==null?local:exchangeClock.correctJvm(local);}
    public String invalidReason(BigDecimal price,Instant observed,Instant now){
        if(price==null || price.signum()<=0)return "INVALID_PRICE";
        if(observed==null)return "MISSING_TIMESTAMP";
        if(observed.isAfter(now.plusSeconds(futureSkew)))return "FUTURE_TIMESTAMP";
        if(observed.isBefore(now.minusSeconds(maxAge)))return "STALE_PRICE";
        return null;
    }
    /** FIX-153: called in INPUT transaction. Invalid observations never occupy slot 1.
     * A duplicate-key error is classified only if the exact canonical key exists; other
     * duplicate constraints/errors propagate. JDBC statement errors do not poison a JPA proxy TX. */
    public Long recordPending(String symbol,BigDecimal price,Instant observed){
        BigDecimal normalizedPrice=normalizePrice(price);
        Instant now=referenceNow();String invalid=invalidReason(normalizedPrice,observed,now);
        if(invalid!=null){
            com.crypto.local.LocalRuntimeDiagnostics.count(com.crypto.local.LocalRuntimeDiagnostics.Event.PROTECTION_REJECTED);
            Long ageMs=observed==null?null:java.time.Duration.between(observed,now).toMillis();
            log.info("[FIX-153][PRICE_NOT_CANONICAL] symbol={}, price={}, observedUtc={}, observationAgeMs={}, reason={}",symbol,normalizedPrice,observed,ageMs,invalid);
            return null;
        }
        org.springframework.jdbc.support.GeneratedKeyHolder keys=new org.springframework.jdbc.support.GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection->{
                var ps=connection.prepareStatement("INSERT INTO market_price_event(symbol,observed_at,price,source,delivery_status,ordering_slot) VALUES(?,?,?,?,'PENDING',1)",new String[]{"id"});
                ps.setString(1,symbol);ps.setTimestamp(2,Timestamp.from(observed));ps.setBigDecimal(3,normalizedPrice);ps.setString(4,DIRECT_SOURCE);return ps;
            },keys);
            long id=java.util.Objects.requireNonNull(keys.getKey(),"generated price id").longValue();inFlight.add(id);return id;
        }catch(org.springframework.dao.DuplicateKeyException duplicate){
            var first=jdbcTemplate.queryForList("SELECT id,price FROM market_price_event WHERE symbol=? AND observed_at=? AND ordering_slot=1",symbol,Timestamp.from(observed));
            if(first.size()!=1)throw duplicate;
            BigDecimal existing=(BigDecimal)first.getFirst().get("price");
            log.warn("[FIX-153][EQUAL_TIMESTAMP_REJECTED] symbol={}, observedUtc={}, firstEvent={}, firstPrice={}, rejectedPrice={}, observationAgeMs={}, priceDisagreement={}",symbol,observed,first.getFirst().get("id"),existing,normalizedPrice,java.time.Duration.between(observed,now).toMillis(),existing.compareTo(normalizedPrice)!=0);
            jdbcTemplate.update("INSERT INTO market_price_event(symbol,observed_at,price,source,delivery_status,ordering_slot,outcome_reason) VALUES(?,?,?,?,'NON_CANONICAL',NULL,?)",symbol,Timestamp.from(observed),normalizedPrice,DIRECT_SOURCE,existing.compareTo(normalizedPrice)==0?"DUPLICATE_TIMESTAMP":"AMBIGUOUS_EQUAL_TIMESTAMP");
            com.crypto.local.LocalRuntimeDiagnostics.count(com.crypto.local.LocalRuntimeDiagnostics.Event.PROTECTION_REJECTED);return null;
        }
    }
    public void release(long id){inFlight.remove(id);}
    /** Runs after existing symbol/position lock at READ_COMMITTED, never counts unresolved receipts. */
    public boolean laterApplied(String symbol,Instant observed){
        return !jdbcTemplate.queryForList("SELECT id FROM market_price_event WHERE symbol=? AND observed_at>? AND source IN ('BINANCE_KLINE_LIVE_CLOSE',?) AND delivery_status IN ('LEGACY','APPLIED') ORDER BY observed_at DESC LIMIT 1",symbol,Timestamp.from(observed),DIRECT_SOURCE).isEmpty();
    }
    /** Exact event lineage prevents a caller from completing a different receipt.
     * A lost resolver race rolls back the entire effects transaction. Slot 1 is retained. */
    public void finish(long id,String symbol,Instant observed,BigDecimal price,String state,String reason){
        price=normalizePrice(price);
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("FIX-153 price outcome requires effects transaction");
        if(!java.util.Set.of("APPLIED","NON_CANONICAL").contains(state))throw new IllegalArgumentException("FIX-153 invalid effects outcome");
        int n=jdbcTemplate.update("UPDATE market_price_event SET delivery_status=?,outcome_reason=? WHERE id=? AND source=? AND symbol=? AND observed_at=? AND price=? AND ordering_slot=1 AND delivery_status='PENDING'",state,reason,id,DIRECT_SOURCE,symbol,Timestamp.from(observed),price);
        if(n!=1)throw new IllegalStateException("FIX-153 price outcome ownership/lineage changed: event="+id);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
            @Override public void afterCompletion(int status){
                if(status==STATUS_COMMITTED){
                    com.crypto.local.LocalRuntimeDiagnostics.count("APPLIED".equals(state)?com.crypto.local.LocalRuntimeDiagnostics.Event.PROTECTION_APPLIED:com.crypto.local.LocalRuntimeDiagnostics.Event.PROTECTION_REJECTED);
                    if("APPLIED".equals(state))com.crypto.local.LocalRuntimeDiagnostics.protection(symbol,observed);
                    log.info("[FIX-153][PRICE_OUTCOME_COMMITTED] event={}, symbol={}, observedUtc={}, observedKsa={}, outcome={}, reason={}",id,symbol,observed,observed.atOffset(java.time.ZoneOffset.ofHours(3)),state,reason);
                }else log.debug("[FIX-153][PRICE_OUTCOME_NOT_COMMITTED] event={}, symbol={}, proposedOutcome={}, transactionCompletion={}; durable outcome must be read",id,symbol,state,status);
            }
        });
    }
    /** Startup and periodic conditional resolution: no replay and no symbol-entry fence.
     * The conditional update waits behind an in-flight effect TX and cannot override APPLIED.
     * Runtime absence alone is insufficient; candidates must also exceed a configured bound. */
    @org.springframework.scheduling.annotation.Scheduled(initialDelayString="${local-candle.resolver-initial-delay-ms:5000}",fixedDelayString="${local-candle.resolver-delay-ms:5000}")
    public void resolvePending(){
        if(!"OFF".equals(mode))return;
        try {
            // FIX-153: housekeeping uses its own statement timeout, without mutating the shared template.
            var bounded=new JdbcTemplate(java.util.Objects.requireNonNull(jdbcTemplate.getDataSource()));bounded.setQueryTimeout(2);
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            var rows=bounded.queryForList("SELECT id,symbol,observed_at FROM market_price_event WHERE source=? AND delivery_status='PENDING' AND created_at<? ORDER BY created_at,id LIMIT 100",DIRECT_SOURCE,Timestamp.from(Instant.now().minusSeconds(120)));
            for(var row:rows){if(System.nanoTime()>deadline)break;long id=((Number)row.get("id")).longValue();if(inFlight.contains(id))continue;
                int n=bounded.update("UPDATE market_price_event SET delivery_status='PROTECTION_NOT_APPLIED',outcome_reason='AUTOMATIC_ATOMIC_NO_COMMIT' WHERE id=? AND source=? AND delivery_status='PENDING'",id,DIRECT_SOURCE);
                if(n==1){com.crypto.local.LocalRuntimeDiagnostics.count(com.crypto.local.LocalRuntimeDiagnostics.Event.PROTECTION_PENDING_RESOLVED);
                    log.info("[FIX-153][PENDING_RESOLVED] event={}, symbol={}, observedUtc={}, outcome=PROTECTION_NOT_APPLIED, evidence=ATOMIC_EFFECTS_AND_STATUS_NO_COMMIT, replay=false",id,row.get("symbol"),row.get("observed_at"));}
            }
        }catch(RuntimeException failure){log.warn("[FIX-153][PENDING_RESOLUTION_FAILED] durable outcome unchanged; no live fence introduced",failure);}
    }

    public List<PriceEvent> find(String rawSymbol, Instant startInclusive, Instant endInclusive) {
        String symbol = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        return jdbcTemplate.query("""
                SELECT observed_at, price, source
                FROM market_price_event
                WHERE delivery_status IN ('LEGACY','APPLIED') AND symbol = ? AND observed_at >= ? AND observed_at <= ?
                ORDER BY observed_at ASC, id ASC
                """, (rs, rowNum) -> new PriceEvent(
                        rs.getTimestamp("observed_at").toInstant(),
                        rs.getBigDecimal("price"), rs.getString("source")),
                symbol, Timestamp.from(startInclusive), Timestamp.from(endInclusive));
    }


    /** FIX-056: newest canonical live-price observation at or before the execution clock. */
    public java.util.Optional<PriceEvent> findLatestAtOrBefore(String rawSymbol, Instant reference) {
        String symbol = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        List<PriceEvent> rows = jdbcTemplate.query("""
                SELECT observed_at, price, source
                FROM market_price_event
                WHERE delivery_status IN ('LEGACY','APPLIED') AND symbol = ? AND observed_at <= ?
                ORDER BY observed_at DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> new PriceEvent(
                        rs.getTimestamp("observed_at").toInstant(),
                        rs.getBigDecimal("price"), rs.getString("source")),
                symbol, Timestamp.from(reference));
        return rows.stream().findFirst();
    }

    /** FIX-132: known skipped/unconfirmed collector delivery must not silently
     * become legacy candle-price fallback. Historical pre-collector windows retain
     * their established fallback. This reads immutable evidence, never live claims. */
    public void assertReplayEvidence(String symbol,Instant from,Instant to) {
        Long pending=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM market_price_event WHERE symbol=? AND observed_at BETWEEN ? AND ? AND delivery_status='PENDING'",
            Long.class,symbol,Timestamp.from(from),Timestamp.from(to));
        if(pending!=null && pending>0)throw new IllegalStateException("FIX-132 Replay contains unconfirmed Production price outcomes; reconcile before claiming parity");
        // FIX-153 OFF Replay never queries collector-era activation tables.
        if("OFF".equals(mode))return;
        Long cutovers=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_history WHERE symbol=? AND cutover_at<=?",Long.class,symbol,Timestamp.from(to));
        if(cutovers!=null && cutovers>0) {
            Long applied=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM market_price_event WHERE symbol=? AND observed_at BETWEEN ? AND ? AND delivery_status IN ('LEGACY','APPLIED')",
                Long.class,symbol,Timestamp.from(from),Timestamp.from(to));
            if(applied==null || applied==0)throw new IllegalStateException("FIX-132 no applied price evidence in collector-era Replay window; signal-price fallback is not Production reproduction");
        }
    }

    /** FIX-153: report excluded evidence explicitly; this never promotes it to a Replay price. */
    public java.util.Map<String,Long> replayOutcomeCounts(String symbol,Instant from,Instant to){
        java.util.Map<String,Long> result=new java.util.LinkedHashMap<>();
        jdbcTemplate.query("SELECT delivery_status,COUNT(*) AS n FROM market_price_event WHERE symbol=? AND observed_at BETWEEN ? AND ? GROUP BY delivery_status",
            (org.springframework.jdbc.core.RowCallbackHandler)rs->result.put(rs.getString(1),rs.getLong(2)),symbol,Timestamp.from(from),Timestamp.from(to));
        return result;
    }

    public record PriceEvent(Instant observedAt, BigDecimal price, String source) {
        public PriceEvent(Instant observedAt,BigDecimal price) { this(observedAt,price,"BINANCE_KLINE_LIVE_CLOSE"); }
    }
}
