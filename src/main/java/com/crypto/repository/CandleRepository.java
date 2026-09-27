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

/** FIX-132 read facade. Exact predicates and ordering of the original JPA queries
 * are retained. Candle is a read DTO, so local JPA validation no longer requires
 * the retired table. No IDs are translated between the two schemas. */
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
        return local.update("""
            INSERT INTO crypto_ai.candle (
                symbol,
                interval_code,
                open_time,
                close_time,
                open_price,
                high_price,
                low_price,
                close_price,
                volume,
                quote_asset_volume,
                number_of_trades,
                taker_buy_base_volume,
                taker_buy_quote_volume,
                closed,
                created_at,
                updated_at
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
            ON DUPLICATE KEY UPDATE
                close_time = VALUES(close_time),
                open_price = VALUES(open_price),
                high_price = VALUES(high_price),
                low_price = VALUES(low_price),
                close_price = VALUES(close_price),
                volume = VALUES(volume),
                quote_asset_volume = VALUES(quote_asset_volume),
                number_of_trades = VALUES(number_of_trades),
                taker_buy_base_volume = VALUES(taker_buy_base_volume),
                taker_buy_quote_volume = VALUES(taker_buy_quote_volume),
                closed = VALUES(closed),
                updated_at = CURRENT_TIMESTAMP(6)

            """, symbol, intervalCode, Timestamp.from(openTime), Timestamp.from(closeTime), openPrice, highPrice, lowPrice, closePrice, volume, quoteAssetVolume, numberOfTrades, takerBuyBaseVolume, takerBuyQuoteVolume, closed);
    }
}
