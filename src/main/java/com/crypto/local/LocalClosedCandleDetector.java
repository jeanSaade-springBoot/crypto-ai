package com.crypto.local;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.indicator.event.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

/** FIX-153: local durable candles close the commit-to-memory-dispatch crash window.
 * BACKFILL is never admitted. Legacy NULL provenance is eligible only when the
 * live stream wrote a close-transition token.
 * Small lookback and one admission per idle lane keep detector below live work. */
@Component
public class LocalClosedCandleDetector {
    private static final Logger log=LoggerFactory.getLogger(LocalClosedCandleDetector.class);
    private final JdbcTemplate jdbc;private final CoinConfigurationService coins;
    private final CandleAnalysisDispatcher dispatcher;private final CandleAnalysisExecutionCoordinator coordination;
    private final com.crypto.client.config.binance.BinanceMarketDataProperties properties;
    @Value("${shared-market.mode:OFF}") private String mode;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private ExchangeClockSynchronizer exchangeClock;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private CandleAnalysisOutcomeStore outcomes;
    private int nextLane;
    private final Map<String,Retry> retries=new HashMap<>();
    private final Map<String,Instant> analysedUntil=new HashMap<>();
    public LocalClosedCandleDetector(JdbcTemplate jdbc,CoinConfigurationService coins,CandleAnalysisDispatcher dispatcher,CandleAnalysisExecutionCoordinator coordination,com.crypto.client.config.binance.BinanceMarketDataProperties properties){
        this.jdbc=new JdbcTemplate(Objects.requireNonNull(jdbc.getDataSource()));this.jdbc.setQueryTimeout(2);
        this.coins=coins;this.dispatcher=dispatcher;this.coordination=coordination;this.properties=properties;
    }
    @Scheduled(initialDelay=5000,fixedDelayString="${local-candle.detector-delay-ms:2000}")
    public void detect(){
        if(!"OFF".equals(mode))return;
        Instant localNow=Instant.now();Instant now=exchangeClock==null?localNow:exchangeClock.correctJvm(localNow);long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        try {
            var symbols=coins.enabledSymbols();var intervals=properties.getIntervals();
            int total=symbols.size()*intervals.size();
            for(int checked=0;checked<total;checked++){
                if(System.nanoTime()>deadline)return;
                // FIX-153: rotate across bounded passes so a slow first lane cannot starve later symbols.
                int lane=Math.floorMod(nextLane++,total);
                String symbol=symbols.get(lane/intervals.size()),interval=intervals.get(lane%intervals.size());
                if(!dispatcher.idle(symbol,interval))continue;
                long lookback=Math.max(180,com.crypto.shared.SharedEventPolicy.closeGraceSeconds(interval)*2L);
                // Index is on open_time: include one full interval before the close-age lookback.
                Instant lowerOpen=now.minusSeconds(intervalSeconds(interval)+lookback);
                var candidates=jdbc.query("SELECT open_time,close_time FROM candle WHERE symbol=? AND interval_code=? AND closed=1 AND close_transition_token IS NOT NULL AND (ingestion_source IS NULL OR ingestion_source<>'BACKFILL') AND open_time>=? AND close_time<=? ORDER BY open_time ASC LIMIT 4",
                    (rs,n)->new CandleClosedEvent(symbol,interval,rs.getTimestamp(1).toInstant(),rs.getTimestamp(2).toInstant()),symbol,interval,Timestamp.from(lowerOpen),Timestamp.from(now));
                for(var event:candidates){
                    String retryKey=key(event);
                    Instant knownAnalysedUntil=analysedUntil.get(retryKey);
                    if(knownAnalysedUntil!=null && knownAnalysedUntil.isAfter(now))continue;
                    Retry retry=retries.get(retryKey);
                    if(retry!=null && retry.nextAttempt().isAfter(now))continue;
                    // A healthy analysis is terminal for detector purposes. Do this
                    // before dispatcher admission so ordinary completed candles never
                    // become retry/expiry noise.
                    if(!missing(event)){retries.remove(retryKey);analysedUntil.put(retryKey,eligibilityEndsAfter(event,lookback));continue;}
                    // No database fence; existing striped candle lock covers only check+handoff.
                    var acquired=coordination.tryLock(symbol,interval,event.openTime());
                    if(acquired.isEmpty())continue;
                    try(var ignored=acquired.get()){
                        if(dispatcher.submitDetected(event,()->missing(event))){
                            // Admission is not completion. Retain bounded state until
                            // durable signal/work evidence appears on a later pass.
                            // Every re-admission consumes the same bounded retry budget;
                            // a repeatedly failing worker must not execute forever.
                            recordAdmission(retryKey,event,now,lookback);
                            break;
                        }
                        defer(retryKey,event,now,"NOT_ADMITTED");
                    }
                }
            }
            for(var iterator=retries.entrySet().iterator();iterator.hasNext();){
                var entry=iterator.next();
                // Retention is strictly after the SQL lower-open-time boundary.
                // Therefore pruning cannot make a still-detectable candle restart
                // with a fresh retry budget.
                if(entry.getValue().retainUntil().isBefore(now)){
                    log.info("[FIX-153][DETECTOR_RETRY_PRUNED] candle={}, attempts={}, reason=NO_LONGER_ELIGIBLE",entry.getKey(),entry.getValue().attempts());
                    iterator.remove();
                }
            }
            analysedUntil.entrySet().removeIf(e->!e.getValue().isAfter(now));
        }catch(RuntimeException failure){LocalRuntimeDiagnostics.count(LocalRuntimeDiagnostics.Event.DETECTOR_FAILED);
            log.warn("[FIX-153][DETECTOR_FAILED] no checkpoint advanced; next bounded pass may retry",failure);}
    }
    private static String key(CandleClosedEvent e){return e.symbol()+"|"+e.intervalCode()+"|"+e.openTime();}
    private void defer(String key,CandleClosedEvent event,Instant now,String reason){
        Retry prior=retries.get(key);int attempts=prior==null?1:prior.attempts()+1;
        long delay=Math.min(60,1L<<Math.min(6,attempts));
        long lookback=Math.max(180,com.crypto.shared.SharedEventPolicy.closeGraceSeconds(event.intervalCode())*2L);
        Instant retryDeadline=event.closeTime().plusSeconds(lookback);
        Instant retainUntil=eligibilityEndsAfter(event,lookback);
        if(attempts>6 || !retryDeadline.isAfter(now)){
            // Keep a terminal marker until this candle has aged out of the SQL
            // lookback. Removing it immediately would restart the retry budget.
            retries.put(key,new Retry(attempts,Instant.MAX,retryDeadline,retainUntil));
            recordExpired(event,reason);
            log.warn("[FIX-153][DETECTOR_GAP_EXPIRED] candle={}, attempts={}, reason={}",key,attempts,reason);return;
        }
        retries.put(key,new Retry(attempts,now.plusSeconds(delay),retryDeadline,retainUntil));
        log.info("[FIX-153][DETECTOR_RETRY_DEFERRED] candle={}, attempts={}, nextUtc={}, reason={}",key,attempts,now.plusSeconds(delay),reason);
    }
    private void recordAdmission(String key,CandleClosedEvent event,Instant now,long lookback){
        Retry prior=retries.get(key);int attempts=prior==null?1:prior.attempts()+1;
        Instant retryDeadline=event.closeTime().plusSeconds(lookback);
        Instant retainUntil=eligibilityEndsAfter(event,lookback);
        if(attempts>6 || !retryDeadline.isAfter(now)){
            // See defer(): this marker is deliberately retained until the query
            // can no longer rediscover this candle.
            retries.put(key,new Retry(attempts,Instant.MAX,retryDeadline,retainUntil));
            recordExpired(event,"ADMISSION_RETRY_LIMIT");
            log.warn("[FIX-153][DETECTOR_GAP_EXPIRED] candle={}, attempts={}, reason=ADMISSION_RETRY_LIMIT",key,attempts);
            return;
        }
        long delay=retryDelaySeconds(attempts);
        retries.put(key,new Retry(attempts,now.plusSeconds(delay),retryDeadline,retainUntil));
        log.info("[FIX-153][DETECTOR_ADMITTED_AWAITING_EVIDENCE] candle={}, attempts={}, nextUtc={}",key,attempts,now.plusSeconds(delay));
    }
    /** Strictly after the candidate predicate: open_time >= now - interval - lookback. */
    private static Instant eligibilityEndsAfter(CandleClosedEvent event,long lookback){
        return event.openTime().plusSeconds(intervalSeconds(event.intervalCode())+lookback+1);
    }
    private static long retryDelaySeconds(int attempts){return Math.min(60,1L<<Math.min(6,attempts));}
    private record Retry(int attempts,Instant nextAttempt,Instant retryDeadline,Instant retainUntil) {}
    private static long intervalSeconds(String interval){
        return switch(interval){case "1m"->60;case "5m"->300;case "15m"->900;case "1h"->3600;case "4h"->14400;case "1d"->86400;
            default->throw new IllegalArgumentException("FIX-153 unsupported detector interval: "+interval);};
    }
    private boolean missing(CandleClosedEvent e){
        if(outcomes!=null && outcomes.exists(e.symbol(),e.intervalCode(),e.openTime()))return false;
        return jdbc.queryForObject("SELECT (EXISTS(SELECT 1 FROM trade_signal WHERE symbol=? AND interval_code=? AND candle_open_time=?) OR EXISTS(SELECT 1 FROM signal_processing_work WHERE symbol=? AND interval_code=? AND candle_open_time=?))",Integer.class,
            e.symbol(),e.intervalCode(),Timestamp.from(e.openTime()),e.symbol(),e.intervalCode(),Timestamp.from(e.openTime()))==0;
    }
    private void recordExpired(CandleClosedEvent event,String reason){
        if(outcomes!=null)outcomes.record(event.symbol(),event.intervalCode(),event.openTime(),"ANALYSIS_EXPIRED",reason,null,"LOCAL_DETECTOR");
    }
}
