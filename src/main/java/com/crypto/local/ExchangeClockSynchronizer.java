package com.crypto.local;

import com.crypto.client.binance.BinanceMarketDataClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Maintains a bounded correction from the local database UTC clock to Binance
 * time. It never performs HTTP inside a protection/position-lock transaction
 * and never acts as a trading fence: a failed observation retains safe database
 * time while making the missing evidence visible to operators.
 */
@Component
public class ExchangeClockSynchronizer {
    private static final Logger log=LoggerFactory.getLogger(ExchangeClockSynchronizer.class);
    private final JdbcTemplate jdbc;
    private final BinanceMarketDataClient binance;
    private final Environment environment;
    private volatile Observation observation;
    @Value("${local-candle.clock-max-observation-age-seconds:120}") private long maxAgeSeconds=120;
    @Value("${local-candle.clock-warn-skew-seconds:2}") private long warnSkewSeconds=2;

    public ExchangeClockSynchronizer(JdbcTemplate jdbc,BinanceMarketDataClient binance,Environment environment){
        this.jdbc=jdbc;this.binance=binance;this.environment=environment;
    }
    @PostConstruct void startupCheck(){refresh();}
    @Scheduled(initialDelayString="${local-candle.clock-initial-delay-ms:30000}",fixedDelayString="${local-candle.clock-refresh-ms:60000}")
    public void refresh(){
        if(!"OFF".equals(environment.getProperty("shared-market.mode","OFF")))return;
        try {
            Timestamp databaseBefore=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)",Timestamp.class);
            if(databaseBefore==null)throw new IllegalStateException("database did not return CURRENT_TIMESTAMP");
            Instant jvmBefore=Instant.now();
            Instant exchange=binance.getServerTime();
            Instant jvmAfter=Instant.now();
            Timestamp databaseAfter=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)",Timestamp.class);
            if(databaseAfter==null)throw new IllegalStateException("database did not return CURRENT_TIMESTAMP");
            Instant before=databaseBefore.toInstant(),after=databaseAfter.toInstant();
            Instant databaseMidpoint=before.plusMillis(Duration.between(before,after).toMillis()/2);
            Instant jvmMidpoint=jvmBefore.plusMillis(Duration.between(jvmBefore,jvmAfter).toMillis()/2);
            Duration exchangeMinusDatabase=Duration.between(databaseMidpoint,exchange);
            Duration exchangeMinusJvm=Duration.between(jvmMidpoint,exchange);
            observation=new Observation(exchangeMinusDatabase,exchangeMinusJvm,System.nanoTime());
            long skewMs=Math.max(Math.abs(exchangeMinusDatabase.toMillis()),Math.abs(exchangeMinusJvm.toMillis()));
            if(skewMs>Duration.ofSeconds(warnSkewSeconds).toMillis())
                log.warn("[FIX-153][CLOCK_SKEW] databaseMidpointUtc={}, exchangeUtc={}, exchangeMinusDatabaseMs={}, exchangeMinusJvmMs={}; automatic corrections active",databaseMidpoint,exchange,exchangeMinusDatabase.toMillis(),exchangeMinusJvm.toMillis());
            else log.info("[FIX-153][CLOCK_SYNC] exchangeMinusDatabaseMs={}, exchangeMinusJvmMs={}",exchangeMinusDatabase.toMillis(),exchangeMinusJvm.toMillis());
        } catch(RuntimeException failure) {
            log.warn("[FIX-153][CLOCK_SYNC_UNAVAILABLE] retaining database UTC without exchange correction",failure);
        }
    }
    public Instant correctDatabase(Instant databaseNow){
        Observation current=observation;
        if(current==null || System.nanoTime()-current.capturedNanos()>Duration.ofSeconds(maxAgeSeconds).toNanos())return databaseNow;
        return databaseNow.plus(current.exchangeMinusDatabase());
    }
    public Instant correctJvm(Instant jvmNow){
        Observation current=observation;
        if(current==null || System.nanoTime()-current.capturedNanos()>Duration.ofSeconds(maxAgeSeconds).toNanos())return jvmNow;
        return jvmNow.plus(current.exchangeMinusJvm());
    }
    public java.util.Map<String,Object> health(){
        Observation current=observation;boolean fresh=current!=null&&System.nanoTime()-current.capturedNanos()<=Duration.ofSeconds(maxAgeSeconds).toNanos();
        java.util.Map<String,Object> result=new java.util.LinkedHashMap<>();
        result.put("exchangeClockReferenceAvailable",fresh);
        result.put("exchangeMinusDatabaseMs",current==null?null:current.exchangeMinusDatabase().toMillis());
        result.put("exchangeMinusJvmMs",current==null?null:current.exchangeMinusJvm().toMillis());
        result.put("maxObservationAgeSeconds",maxAgeSeconds);
        return result;
    }
    private record Observation(Duration exchangeMinusDatabase,Duration exchangeMinusJvm,long capturedNanos) {}
}
