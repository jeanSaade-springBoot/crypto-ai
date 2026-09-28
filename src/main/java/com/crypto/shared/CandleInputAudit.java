package com.crypto.shared;

import com.crypto.domain.Candle;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import java.nio.charset.StandardCharsets;

/** Approved current-committed input contract. Hashes detect corrected inputs;
 * they do not reconstruct a previous candle version or claim bitemporal Replay. */
public final class CandleInputAudit implements AutoCloseable {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CandleInputAudit.class);
    private static final ThreadLocal<CandleInputAudit> CURRENT = new ThreadLocal<>();
    private final CandleInputAudit previous;
    private final JdbcTemplate jdbc;
    private final String context;
    private final TransactionTemplate writes;
    private int index;
    private CandleInputAudit(JdbcTemplate jdbc, PlatformTransactionManager manager, String context) {
        this.jdbc=jdbc; this.context=context;
        writes=new TransactionTemplate(manager);
        // FIX-136: candle validation enters a local read-only JPA transaction.
        // Use the SAME transaction manager to suspend its EntityManager/connection;
        // an inner REQUIRED transaction cannot make that connection writable.
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writes.setReadOnly(false);
        writes.setTimeout(5);
        previous=CURRENT.get(); CURRENT.set(this);
    }
    public static CandleInputAudit open(JdbcTemplate jdbc, PlatformTransactionManager manager, String context) {
        return new CandleInputAudit(jdbc,manager,context);
    }
    /** Standalone JDBC callers/tests only. Managed JPA callers must pass their manager. */
    public static CandleInputAudit open(JdbcTemplate jdbc,String context) {
        return open(jdbc,new JdbcTransactionManager(Objects.requireNonNull(jdbc.getDataSource())),context);
    }
    public static void capture(String query,List<Candle> rows) {
        CandleInputAudit audit=CURRENT.get(); if(audit==null)return;
        try {
            var hash=java.security.MessageDigest.getInstance("SHA-256");
            for(Candle c:rows) hash.update((c.getSymbol()+"|"+c.getIntervalCode()+"|"+c.getOpenTime()+"|"+c.getCloseTime()+"|"+
                c.getOpenPrice()+"|"+c.getHighPrice()+"|"+c.getLowPrice()+"|"+c.getClosePrice()+"|"+c.getVolume()+"|"+
                c.getQuoteAssetVolume()+"|"+c.getNumberOfTrades()+"|"+c.getTakerBuyBaseVolume()+"|"+c.getTakerBuyQuoteVolume()+"|"+c.isClosed()+"\n").getBytes(StandardCharsets.UTF_8));
            audit.writes.executeWithoutResult(tx -> audit.jdbc.update("INSERT INTO shared_candle_input_audit(context_key,read_index,input_hash,row_count,query_text) VALUES(?,?,?,?,?)",
                audit.context,++audit.index,HexFormat.of().formatHex(hash.digest()),rows.size(),query.substring(0,Math.min(1000,query.length()))));
        } catch(RuntimeException failure) {
            log.error("[FIX-136][AUDIT_WRITE_FAILED] context={}, readIndex={}; analysis must not continue without input evidence",
                    audit.context,audit.index,failure);
            throw failure;
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Override public void close() { if(previous==null) CURRENT.remove(); else CURRENT.set(previous); }
}
