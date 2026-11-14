package com.crypto.local;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.indicator.event.CandleAnalysisDispatcher;
import java.time.*;
import java.util.*;

/** FIX-153: stage health from local data only. Mode/configuration is not proof of freshness.
 * Cached operator view; per-query timeout and scan budget, never a hot-path full-table count. */
@Service
public class LocalCandleHealth {
    private final JdbcTemplate jdbc;private final CoinConfigurationService coins;
    private final BinanceMarketDataProperties properties;private final CandleAnalysisDispatcher dispatcher;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private ExchangeClockSynchronizer exchangeClock;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private LocalCandleReconciliationService reconciliation;
    private Map<String,Object> cached;private Instant cachedAt=Instant.EPOCH;
    public LocalCandleHealth(JdbcTemplate jdbc,CoinConfigurationService coins,BinanceMarketDataProperties properties,CandleAnalysisDispatcher dispatcher){
        this.jdbc=new JdbcTemplate(Objects.requireNonNull(jdbc.getDataSource()));this.jdbc.setQueryTimeout(2);
        this.coins=coins;this.properties=properties;this.dispatcher=dispatcher;
    }
    private List<Map<String,Object>> utcRows(String sql,Object... args){
        var rows=jdbc.queryForList(sql,args);
        for(var row:rows)row.replaceAll((key,value)->value instanceof java.sql.Timestamp ts?ts.toInstant():value);
        return rows;
    }
    public synchronized Map<String,Object> snapshot(){
        Instant now=Instant.now();if(cached!=null&&cachedAt.plusSeconds(15).isAfter(now))return cached;
        Map<String,Object> result=new LinkedHashMap<>();result.put("mode","OFF");result.put("observedUtc",now);result.put("observedKsa",now.atOffset(ZoneOffset.ofHours(3)));
        result.put("collectorDependency",false);result.put("runtime",LocalRuntimeDiagnostics.snapshot());result.put("lanes",dispatcher.snapshot());
        result.put("reconciliation",reconciliation==null?Map.of("status","NOT_WIRED"):reconciliation.health());
        result.put("exchangeClock",exchangeClock==null?Map.of("exchangeClockReferenceAvailable",false,"reason","NOT_WIRED"):exchangeClock.health());
        List<Map<String,Object>> stages=new ArrayList<>();long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        try {scan:for(String symbol:coins.enabledSymbols())for(String interval:properties.getIntervals()){
            if(System.nanoTime()>deadline){result.put("scanTruncated",true);break scan;}
            Map<String,Object> stage=new LinkedHashMap<>();stage.put("symbol",symbol);stage.put("interval",interval);
            stage.put("lastClosedCandle",utcRows("SELECT open_time AS open_utc,close_time AS close_utc,ingestion_source FROM candle WHERE symbol=? AND interval_code=? AND closed=1 ORDER BY open_time DESC LIMIT 1",symbol,interval));
            stage.put("lastSignal",utcRows("SELECT id,candle_open_time AS open_utc,generated_at AS generated_utc,created_at AS inserted_utc,signal_mode FROM trade_signal WHERE symbol=? AND interval_code=? ORDER BY candle_open_time DESC LIMIT 1",symbol,interval));stages.add(stage);
        }}catch(RuntimeException failure){result.put("localStageQueryFailed",failure.getClass().getSimpleName());}
        result.put("stages",stages);result.put("status",result.containsKey("localStageQueryFailed")?"CHECK_FAILED":"LOCAL_STAGE_OBSERVATIONS");
        // No READY claim is made merely because local ingestion is configured.
        cached=result;cachedAt=now;return result;
    }
}
