package com.crypto.shared;

import com.crypto.domain.Candle;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Approved current-committed input contract. Hashes detect corrected inputs;
 * they do not reconstruct a previous candle version or claim bitemporal Replay. */
public final class CandleInputAudit implements AutoCloseable {
    private static final ThreadLocal<CandleInputAudit> CURRENT = new ThreadLocal<>();
    private final CandleInputAudit previous;
    private final JdbcTemplate jdbc;
    private final String context;
    private int index;
    private CandleInputAudit(JdbcTemplate jdbc,String context) { this.jdbc=jdbc;this.context=context;previous=CURRENT.get();CURRENT.set(this); }
    public static CandleInputAudit open(JdbcTemplate jdbc,String context) { return new CandleInputAudit(jdbc,context); }
    public static void capture(String query,List<Candle> rows) {
        CandleInputAudit audit=CURRENT.get(); if(audit==null)return;
        try {
            var hash=java.security.MessageDigest.getInstance("SHA-256");
            for(Candle c:rows) hash.update((c.getSymbol()+"|"+c.getIntervalCode()+"|"+c.getOpenTime()+"|"+c.getCloseTime()+"|"+
                c.getOpenPrice()+"|"+c.getHighPrice()+"|"+c.getLowPrice()+"|"+c.getClosePrice()+"|"+c.getVolume()+"|"+
                c.getQuoteAssetVolume()+"|"+c.getNumberOfTrades()+"|"+c.getTakerBuyBaseVolume()+"|"+c.getTakerBuyQuoteVolume()+"|"+c.isClosed()+"\n").getBytes(StandardCharsets.UTF_8));
            audit.jdbc.update("INSERT INTO shared_candle_input_audit(context_key,read_index,input_hash,row_count,query_text) VALUES(?,?,?,?,?)",
                audit.context,++audit.index,HexFormat.of().formatHex(hash.digest()),rows.size(),query.substring(0,Math.min(1000,query.length())));
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Override public void close() { if(previous==null) CURRENT.remove(); else CURRENT.set(previous); }
}
