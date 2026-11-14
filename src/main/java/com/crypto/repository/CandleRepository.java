package com.crypto.repository;

import com.crypto.domain.Candle;
import com.crypto.shared.SharedMarketSource;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** FIX-153: local candle facade in OFF; shared reads remain a rollback capability.
 * Exact predicates/order are retained. Candle is a DTO; SQL migrations own its schema.
 * Runtime and backfill write policies are deliberately distinct. */
@Repository
public class CandleRepository {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CandleRepository.class);
    private final SharedMarketSource source;
    private final JdbcTemplate local;
    public CandleRepository(SharedMarketSource source, JdbcTemplate local) { this.source=source; this.local=local; }
    public static final RowMapper<Candle> MAPPER = (r,n) -> Candle.builder()
        .id(r.getLong("id")).symbol(r.getString("symbol")).intervalCode(r.getString("interval_code"))
        .openTime(r.getTimestamp("open_time").toInstant()).closeTime(r.getTimestamp("close_time").toInstant())
        .openPrice(r.getBigDecimal("open_price")).highPrice(r.getBigDecimal("high_price"))
        .lowPrice(r.getBigDecimal("low_price")).closePrice(r.getBigDecimal("close_price"))
        .volume(r.getBigDecimal("volume")).quoteAssetVolume(r.getBigDecimal("quote_asset_volume"))
        .numberOfTrades(r.getObject("number_of_trades",Long.class))
        .takerBuyBaseVolume(r.getBigDecimal("taker_buy_base_volume"))
        .takerBuyQuoteVolume(r.getBigDecimal("taker_buy_quote_volume")).closed(r.getBoolean("closed")).build();
    private List<Candle> read(String tail,Object... args) {
        Object[] converted=Arrays.stream(args).map(v->v instanceof Instant i ? Timestamp.from(i) : v).toArray();
        List<Candle> rows=source.reader().query("SELECT * FROM candle WHERE "+tail,MAPPER,converted);
        com.crypto.shared.CandleInputAudit.capture(tail,rows);
        return rows;
    }
    private String page(Pageable p) { return p.isUnpaged()? "" : " LIMIT "+p.getPageSize()+" OFFSET "+p.getOffset(); }
    public Optional<Candle> findBySymbolAndIntervalCodeAndOpenTime(String s,String i,Instant t) {
        return read("symbol=? AND interval_code=? AND open_time=?",s,i,t).stream().findFirst();
    }
    public List<Candle> findTop500BySymbolAndIntervalCodeOrderByOpenTimeDesc(String s,String i) {
        return read("symbol=? AND interval_code=? ORDER BY open_time DESC LIMIT 500",s,i);
    }
    public List<Candle> findTop200BySymbolAndIntervalCodeAndClosedTrueOrderByOpenTimeDesc(String s,String i) {
        return read("symbol=? AND interval_code=? AND closed=1 ORDER BY open_time DESC LIMIT 200",s,i);
    }
    public List<Candle> findClosedCandles(String s,String i,Pageable p) {
        return read("symbol=? AND interval_code=? AND closed=1 ORDER BY open_time DESC"+page(p),s,i);
    }
    public List<Candle> findClosedCandlesAtOrBefore(String s,String i,Instant t,Pageable p) {
        return read("symbol=? AND interval_code=? AND closed=1 AND open_time<=? ORDER BY open_time DESC"+page(p),s,i,t);
    }
    public List<Candle> findClosedCandlesClosedAtOrBefore(String s,String i,Instant t,Pageable p) {
        return read("symbol=? AND interval_code=? AND closed=1 AND close_time<=? ORDER BY open_time DESC"+page(p),s,i,t);
    }
    public List<Candle> findBySymbolAndIntervalCodeAndClosedTrueAndOpenTimeBetweenOrderByOpenTimeAsc(String s,String i,Instant a,Instant b) {
        return read("symbol=? AND interval_code=? AND closed=1 AND open_time BETWEEN ? AND ? ORDER BY open_time ASC",s,i,a,b);
    }
    public List<Candle> findBySymbolAndIntervalCodeAndOpenTimeBetweenOrderByOpenTimeAsc(String s,String i,Instant a,Instant b) {
        return read("symbol=? AND interval_code=? AND open_time BETWEEN ? AND ? ORDER BY open_time ASC",s,i,a,b);
    }
    public List<Candle> findBySymbolAndIntervalCodeAndClosedTrueOrderByOpenTimeAsc(String s,String i) {
        return read("symbol=? AND interval_code=? AND closed=1 ORDER BY open_time ASC",s,i);
    }
    public Optional<Candle> findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc(String s,String i) {
        return read("symbol=? AND interval_code=? AND closed=1 ORDER BY close_time DESC LIMIT 1",s,i).stream().findFirst();
    }
    public Optional<Candle> findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByOpenTimeAsc(String s,String i) {
        return read("symbol=? AND interval_code=? AND closed=1 ORDER BY open_time ASC LIMIT 1",s,i).stream().findFirst();
    }
    public List<String> findDistinctSymbols() { return source.reader().queryForList("SELECT DISTINCT symbol FROM candle ORDER BY symbol",String.class); }
    public long countBySymbolAndIntervalCodeAndClosedTrue(String s,String i) {
        return source.reader().queryForObject("SELECT COUNT(*) FROM candle WHERE symbol=? AND interval_code=? AND closed=1",Long.class,s,i);
    }
    public List<Candle> findClosedCandlesMissingAnalysisThrough(String s,String i,Instant a,Instant b,Pageable p) {
        // Shared credentials never need access to Trader's indicators/signals. Fetch
        // local identities separately, then paginate AFTER applying the missing test.
        Set<Instant> indicators=new HashSet<>(local.query("SELECT candle_open_time FROM technical_indicator WHERE symbol=? AND interval_code=? AND candle_open_time BETWEEN ? AND ?",
            (r,n)->r.getTimestamp(1).toInstant(),s,i,Timestamp.from(a),Timestamp.from(b)));
        Set<Instant> signals=new HashSet<>(local.query("SELECT candle_open_time FROM trade_signal WHERE symbol=? AND interval_code=? AND candle_open_time BETWEEN ? AND ?",
            (r,n)->r.getTimestamp(1).toInstant(),s,i,Timestamp.from(a),Timestamp.from(b)));
        long started=System.nanoTime();
        var candles=findBySymbolAndIntervalCodeAndClosedTrueAndOpenTimeBetweenOrderByOpenTimeAsc(s,i,a,b);
        long fetchMs=(System.nanoTime()-started)/1_000_000;
        var result=candles.stream()
            .filter(c->!indicators.contains(c.getOpenTime()) || !signals.contains(c.getOpenTime()))
            .skip(p.isUnpaged()?0:p.getOffset()).limit(p.isUnpaged()?Long.MAX_VALUE:p.getPageSize()).toList();
        // Measurement only. Fetch includes pool acquisition, transfer and mapping;
        // it is not labelled database execution or commit time. Filtering unchanged.
        if(source.enabled()) {
            String detail="fetched="+candles.size()+", returned="+result.size()+", fetchIncludingPoolMs="+fetchMs+", pool="+source.poolPressure();
            log.info("[FIX-132][RECOVERY_SCAN] symbol={}, interval={}, from={}, through={}, {}",s,i,a,b,detail);
            try { local.update("INSERT INTO shared_market_health(component,status,detail) VALUES(?,'MEASURED',?) ON DUPLICATE KEY UPDATE status=VALUES(status),detail=VALUES(detail)","RECOVERY:"+s+":"+i,detail); }
            catch(RuntimeException unavailable) {log.warn("[FIX-132][RECOVERY_METRIC_NOT_PERSISTED] symbol={}, interval={}",s,i,unavailable);}
        }
        return result;
    }
    public int upsert(String symbol,String intervalCode,Instant openTime,Instant closeTime,
        BigDecimal openPrice,BigDecimal highPrice,BigDecimal lowPrice,BigDecimal closePrice,
        BigDecimal volume,BigDecimal quoteAssetVolume,Long numberOfTrades,BigDecimal takerBuyBaseVolume,
        BigDecimal takerBuyQuoteVolume,boolean closed) {
        source.requireLocalWriter();
        return write(symbol,intervalCode,openTime,closeTime,openPrice,highPrice,lowPrice,closePrice,
            volume,quoteAssetVolume,numberOfTrades,takerBuyBaseVolume,takerBuyQuoteVolume,closed,"BACKFILL",null);
    }
    /** FIX-153: result is valid only after the caller's INPUT transaction commits.
     * Token read-back compares immutable receipt identity, never a racy pre-read of closed state. */
    public Transition upsertRuntime(String symbol,String intervalCode,Instant openTime,Instant closeTime,
        BigDecimal openPrice,BigDecimal highPrice,BigDecimal lowPrice,BigDecimal closePrice,
        BigDecimal volume,BigDecimal quoteAssetVolume,Long numberOfTrades,BigDecimal takerBuyBaseVolume,
        BigDecimal takerBuyQuoteVolume,boolean closed,String token) {
        source.requireLocalWriter();
        write(symbol,intervalCode,openTime,closeTime,openPrice,highPrice,lowPrice,closePrice,
            volume,quoteAssetVolume,numberOfTrades,takerBuyBaseVolume,takerBuyQuoteVolume,closed,"RUNTIME",closed?token:null);
        return local.queryForObject("SELECT closed,close_time,close_transition_token,ingestion_source FROM candle WHERE symbol=? AND interval_code=? AND open_time=?",
            // Provenance deliberately does not participate here. A legacy NULL row that
            // the live stream closes is a genuine close transition; BACKFILL never writes
            // a token, so it cannot manufacture a dispatchable transition.
            (rs,n)->new Transition(closed && token.equals(rs.getString(3)),
                rs.getTimestamp(2).toInstant(),rs.getString(3),rs.getBoolean(1)),symbol,intervalCode,Timestamp.from(openTime));
    }
    /** `alreadyClosed` is diagnostic only; the matching token is the sole dispatch authority. */
    public record Transition(boolean transitionedToClosed,Instant closeTime,String token,boolean alreadyClosed) {}

    /**
     * Authoritative recovery is intentionally update-only.  Unlike ordinary
     * REST backfill it can never insert an absent identity and it never makes a
     * runtime close token.  This keeps a late recovery from stealing a normal
     * live close admission.
     */
    public boolean finalizeExistingOpenFromAuthoritative(String symbol,String intervalCode,Instant openTime,Instant closeTime,
        BigDecimal openPrice,BigDecimal highPrice,BigDecimal lowPrice,BigDecimal closePrice,
        BigDecimal volume,BigDecimal quoteAssetVolume,Long numberOfTrades,BigDecimal takerBuyBaseVolume,
        BigDecimal takerBuyQuoteVolume) {
        source.requireLocalWriter();
        return local.update("""
            UPDATE candle SET close_time=?,open_price=?,high_price=?,low_price=?,close_price=?,volume=?,
              quote_asset_volume=?,number_of_trades=?,taker_buy_base_volume=?,taker_buy_quote_volume=?,
              closed=1,updated_at=CURRENT_TIMESTAMP(6)
            WHERE symbol=? AND interval_code=? AND open_time=? AND closed=0
              AND (ingestion_source IS NULL OR ingestion_source<>'BACKFILL')
            """, Timestamp.from(closeTime),openPrice,highPrice,lowPrice,closePrice,volume,quoteAssetVolume,
            numberOfTrades,takerBuyBaseVolume,takerBuyQuoteVolume,symbol,intervalCode,Timestamp.from(openTime)) == 1;
    }

    /**
     * Exact authoritative gap repair is pure insert-if-absent.  Do not replace
     * this with ON DUPLICATE KEY UPDATE: a live row may arrive while REST is in
     * flight and must remain untouched so its normal close can own the token.
     */
    public boolean insertAuthoritativeGapBackfill(String symbol,String intervalCode,Instant openTime,Instant closeTime,
        BigDecimal openPrice,BigDecimal highPrice,BigDecimal lowPrice,BigDecimal closePrice,
        BigDecimal volume,BigDecimal quoteAssetVolume,Long numberOfTrades,BigDecimal takerBuyBaseVolume,
        BigDecimal takerBuyQuoteVolume) {
        source.requireLocalWriter();
        try {
            return local.update("""
                INSERT INTO candle(symbol,interval_code,open_time,close_time,open_price,high_price,low_price,close_price,
                    volume,quote_asset_volume,number_of_trades,taker_buy_base_volume,taker_buy_quote_volume,
                    closed,ingestion_source,close_transition_token,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,1,'BACKFILL',NULL,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))
                """,symbol,intervalCode,Timestamp.from(openTime),Timestamp.from(closeTime),openPrice,highPrice,lowPrice,
                closePrice,volume,quoteAssetVolume,numberOfTrades,takerBuyBaseVolume,takerBuyQuoteVolume) == 1;
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            return false;
        }
    }
    private int write(String symbol,String intervalCode,Instant openTime,Instant closeTime,
        BigDecimal openPrice,BigDecimal highPrice,BigDecimal lowPrice,BigDecimal closePrice,
        BigDecimal volume,BigDecimal quoteAssetVolume,Long numberOfTrades,BigDecimal takerBuyBaseVolume,
        BigDecimal takerBuyQuoteVolume,boolean closed,String provenance,String token) {
        String duplicateClause = "BACKFILL".equals(provenance) ? """
            ON DUPLICATE KEY UPDATE
                -- REST/backfill is authoritative enough to finish an interrupted open
                -- candle, but never changes a completed candle, provenance or token.
                close_time=IF(closed=1,close_time,VALUES(close_time)),
                open_price=IF(closed=1,open_price,VALUES(open_price)),
                high_price=IF(closed=1,high_price,VALUES(high_price)),
                low_price=IF(closed=1,low_price,VALUES(low_price)),
                close_price=IF(closed=1,close_price,VALUES(close_price)),
                volume=IF(closed=1,volume,VALUES(volume)),
                quote_asset_volume=IF(closed=1,quote_asset_volume,VALUES(quote_asset_volume)),
                number_of_trades=IF(closed=1,number_of_trades,VALUES(number_of_trades)),
                taker_buy_base_volume=IF(closed=1,taker_buy_base_volume,VALUES(taker_buy_base_volume)),
                taker_buy_quote_volume=IF(closed=1,taker_buy_quote_volume,VALUES(taker_buy_quote_volume)),
                updated_at=IF(closed=1,updated_at,CURRENT_TIMESTAMP(6)),
                closed=GREATEST(closed,VALUES(closed))
            """ : """
            ON DUPLICATE KEY UPDATE
                close_time=IF(closed=1,close_time,VALUES(close_time)),
                open_price=IF(closed=1,open_price,VALUES(open_price)),
                high_price=IF(closed=1,high_price,VALUES(high_price)),
                low_price=IF(closed=1,low_price,VALUES(low_price)),
                close_price=IF(closed=1,close_price,VALUES(close_price)),
                volume=IF(closed=1,volume,VALUES(volume)),
                quote_asset_volume=IF(closed=1,quote_asset_volume,VALUES(quote_asset_volume)),
                number_of_trades=IF(closed=1,number_of_trades,VALUES(number_of_trades)),
                taker_buy_base_volume=IF(closed=1,taker_buy_base_volume,VALUES(taker_buy_base_volume)),
                taker_buy_quote_volume=IF(closed=1,taker_buy_quote_volume,VALUES(taker_buy_quote_volume)),
                close_transition_token=IF(closed=0 AND VALUES(closed)=1,VALUES(close_transition_token),close_transition_token),
                updated_at=IF(closed=1,updated_at,CURRENT_TIMESTAMP(6)),
                closed=GREATEST(closed,VALUES(closed))
            """;
        // FIX-153: backfill inserts missing identities only, so it cannot fabricate a runtime close.
        return local.update("""
            INSERT INTO candle(symbol,interval_code,open_time,close_time,open_price,high_price,low_price,close_price,
                volume,quote_asset_volume,number_of_trades,taker_buy_base_volume,taker_buy_quote_volume,
                closed,ingestion_source,close_transition_token,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))
            """ + duplicateClause,symbol,intervalCode,Timestamp.from(openTime),Timestamp.from(closeTime),openPrice,highPrice,lowPrice,
            closePrice,volume,quoteAssetVolume,numberOfTrades,takerBuyBaseVolume,takerBuyQuoteVolume,closed,provenance,token);
    }
}
