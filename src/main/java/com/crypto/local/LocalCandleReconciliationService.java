package com.crypto.local;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.client.binance.BinanceMarketDataClient;
import com.crypto.client.binance.dto.BinanceKline;
import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.repository.CandleRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Automatic, token-free repair of authoritative local candle evidence.  It is
 * deliberately not an analysis or execution service: recovered candles obtain
 * SKIPPED_NOT_LIVE outcome evidence and wait for a subsequent genuine live
 * close to resume ordinary analysis.
 */
@Service
public class LocalCandleReconciliationService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(LocalCandleReconciliationService.class);
    private final JdbcTemplate jdbc;
    private final CoinConfigurationService coins;
    private final BinanceMarketDataProperties properties;
    private final BinanceMarketDataClient binance;
    private final CandleRepository candles;
    private final LocalReconciliationAuditRepository audit;
    private final CandleAnalysisOutcomeStore outcomes;
    private final Executor executor;
    private final TransactionTemplate tx;
    private final AtomicBoolean running=new AtomicBoolean();
    private volatile Instant lastReconnectAdmission;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private LocalDrainService drain;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private ExchangeClockSynchronizer exchangeClock;
    @Value("${shared-market.mode:OFF}") private String mode;
    @Value("${local-candle.reconciliation.enabled:false}") private boolean enabled;
    @Value("${local-candle.reconciliation.request-budget-per-pass:10}") private int requestBudget;
    @Value("${analysis.scoring.data-quality.minimum-candles:210}") private int minimumCandles;
    @Value("${local-candle.reconciliation.live-close-grace-seconds:10}") private long liveCloseGraceSeconds;
    @Value("${local-candle.reconciliation.safety-margin-seconds:2}") private long safetyMarginSeconds;

    public LocalCandleReconciliationService(JdbcTemplate jdbc, CoinConfigurationService coins,
            BinanceMarketDataProperties properties, BinanceMarketDataClient binance, CandleRepository candles,
            LocalReconciliationAuditRepository audit, CandleAnalysisOutcomeStore outcomes,
            @Qualifier("candleReconciliationExecutor") Executor executor, PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.coins=coins;this.properties=properties;this.binance=binance;this.candles=candles;
        this.audit=audit;this.outcomes=outcomes;this.executor=executor;this.tx=new TransactionTemplate(manager);
    }

    @Scheduled(initialDelayString="${local-candle.reconciliation.initial-delay-ms:15000}",fixedDelayString="${local-candle.reconciliation.scan-delay-ms:5000}")
    public void scheduledScan(){ request("SCHEDULE"); }
    public void requestAfterReconnect(long generation){
        Instant local=Instant.now();lastReconnectAdmission=exchangeClock==null?local:exchangeClock.correctJvm(local);
        request("RECONNECT_"+generation);
    }
    public boolean isIdle(){return !running.get();}
    public Map<String,Object> health(){
        Map<String,Object> result=new LinkedHashMap<>();result.put("enabled",enabled);result.put("inFlight",running.get());
        try {
            result.put("pendingCandidates",jdbc.queryForObject("SELECT COUNT(*) FROM local_reconciliation_audit WHERE status='PENDING'",Long.class));
            result.put("recoveredCount",jdbc.queryForObject("SELECT COUNT(*) FROM local_reconciliation_audit WHERE status='RECOVERED'",Long.class));
            result.put("gapBackfilledCount",jdbc.queryForObject("SELECT COUNT(*) FROM local_reconciliation_audit WHERE status='GAP_BACKFILLED'",Long.class));
            result.put("expiredCount",jdbc.queryForObject("SELECT COUNT(*) FROM local_reconciliation_audit WHERE status='EXPIRED'",Long.class));
            Instant local=Instant.now();Instant reference=exchangeClock==null?local:exchangeClock.correctJvm(local);
            List<Map<String,Object>> activeGaps=new ArrayList<>();
            for(String symbol:coins.enabledSymbols())for(String interval:properties.getIntervals()){
                Instant lower=reference.minusSeconds(Math.max(1,minimumCandles)*intervalSeconds(interval));
                Timestamp first=jdbc.queryForObject("SELECT MIN(open_time) FROM candle WHERE symbol=? AND interval_code=?",Timestamp.class,symbol,interval);
                if(first==null)continue;
                Long count=jdbc.queryForObject("""
                    SELECT COUNT(*) FROM local_reconciliation_audit
                    WHERE subject_type='CANDLE_DATA' AND symbol=? AND interval_code=?
                      AND status='EXPIRED' AND failure_reason='NOT_ON_EXCHANGE'
                      AND candle_open_time>=? AND candle_open_time>?
                    """,Long.class,symbol,interval,Timestamp.from(lower),first);
                if(count!=null&&count>0)activeGaps.add(Map.of("symbol",symbol,"interval",interval,"count",count));
            }
            result.put("exchangeGapBlockedByLane",activeGaps);
            result.put("exchangeGapBlockedCount",activeGaps.stream().mapToLong(row->((Number)row.get("count")).longValue()).sum());
        }catch(RuntimeException unavailable){result.put("status","NOT_MIGRATED_OR_UNAVAILABLE");}
        return result;
    }

    private void request(String reason){
        if(!enabled || !"OFF".equals(mode) || !running.compareAndSet(false,true))return;
        try { executor.execute(()-> { try { runPass(reason); } finally { running.set(false); } }); }
        catch(RuntimeException rejected){running.set(false);log.warn("[FIX-153][RECONCILIATION_REJECTED] reason={}",reason,rejected);}
    }

    private void runPass(String reason){
        if(drain!=null&&!drain.beginReconciliation())return;
        try {
            // The server-time read happens before every REST kline request and is
            // the sole exchange-time reference for this pass.
            Instant exchangeNow=binance.getServerTime();
            Instant databaseNow=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)",Timestamp.class).toInstant();
            int[] remaining={Math.max(0,requestBudget)};
            for(Candidate candidate:openCandidates(exchangeNow,databaseNow)) {
                if(drain!=null&&drain.draining())return;
                if(remaining[0]--<=0)break;
                recover(candidate,exchangeNow,databaseNow,false);
            }
            if(remaining[0]>0) for(Candidate candidate:gapCandidates(exchangeNow,databaseNow)) {
                if(drain!=null&&drain.draining())return;
                if(remaining[0]--<=0)break;
                recover(candidate,exchangeNow,databaseNow,true);
            }
            log.debug("[FIX-153][RECONCILIATION_PASS] reason={}, exchangeNow={}, budget={}",reason,exchangeNow,requestBudget-remaining[0]);
        } catch(RuntimeException failure) {
            log.warn("[FIX-153][RECONCILIATION_PASS_FAILED] reason={}; no candle was treated as recovered",reason,failure);
        } finally {if(drain!=null)drain.completeReconciliation();}
    }

    private List<Candidate> openCandidates(Instant exchangeNow,Instant databaseNow){
        List<Candidate> result=new ArrayList<>();
        for(String interval:properties.getIntervals()) {
            long seconds=intervalSeconds(interval), window=Math.max(1,minimumCandles)*seconds;
            Instant lower=exchangeNow.minusSeconds(window);
            Instant eligible=exchangeNow.minusSeconds(liveCloseGraceSeconds+safetyMarginSeconds);
            Instant latestOpen=eligible.minusSeconds(seconds);
            result.addAll(jdbc.query("""
                SELECT symbol,interval_code,open_time FROM candle
                WHERE closed=0 AND interval_code=? AND open_time>=? AND open_time<=?
                  AND (ingestion_source IS NULL OR ingestion_source<>'BACKFILL')
                ORDER BY open_time ASC LIMIT 50
                """,(rs,n)->{
                    Instant open=rs.getTimestamp(3).toInstant();
                    return new Candidate(rs.getString(1),rs.getString(2),open,expectedClose(open,rs.getString(2)));
                },interval,Timestamp.from(lower),Timestamp.from(latestOpen)));
        }
        return result.stream().filter(c->audit.due(c.symbol,c.interval,c.openTime,databaseNow))
            .filter(this::positiveLossEvidence).toList();
    }

    /** Read-only discovery of absent expected identities in the active quality window. */
    private List<Candidate> gapCandidates(Instant exchangeNow,Instant databaseNow){
        List<Candidate> result=new ArrayList<>();
        for(String symbol:coins.enabledSymbols()) for(String interval:properties.getIntervals()) {
            long seconds=intervalSeconds(interval), window=Math.max(1,minimumCandles)*seconds;
            Instant eligible=exchangeNow.minusSeconds(liveCloseGraceSeconds+safetyMarginSeconds);
            Instant first=Instant.ofEpochSecond((exchangeNow.minusSeconds(window).getEpochSecond()/seconds)*seconds);
            Set<Instant> present=new HashSet<>(jdbc.query("""
                SELECT open_time FROM candle WHERE symbol=? AND interval_code=? AND open_time>=? AND open_time<?
                """,(rs,n)->rs.getTimestamp(1).toInstant(),symbol,interval,Timestamp.from(first),Timestamp.from(eligible)));
            for(Instant open=first;open.plusSeconds(seconds).compareTo(eligible)<=0;open=open.plusSeconds(seconds)) {
                if(!present.contains(open) && audit.due(symbol,interval,open,databaseNow))
                    result.add(new Candidate(symbol,interval,open,open.plusSeconds(seconds).minusMillis(1)));
            }
        }
        return result;
    }

    private void recover(Candidate candidate,Instant exchangeNow,Instant databaseNow,boolean gap) {
        var prior=audit.findCandle(candidate.symbol,candidate.interval,candidate.openTime);
        int attempts=prior.map(s->s.attempts()+1).orElse(1);
        try {
            List<BinanceKline> rows=binance.getKlines(candidate.symbol,candidate.interval,candidate.openTime,candidate.closeTime);
            List<BinanceKline> exact=rows.stream().filter(k->k.openTime().equals(candidate.openTime)&&k.closeTime().equals(candidate.closeTime)).toList();
            if(rows.isEmpty() && gap) {
                List<BinanceKline> first=binance.getKlinesAtOrAfter(candidate.symbol,candidate.interval,candidate.openTime);
                if(!first.isEmpty() && first.getFirst().openTime().isAfter(candidate.openTime)) {
                    terminalUnavailable(candidate,exchangeNow,"NOT_ON_EXCHANGE");return;
                }
                pending(candidate,attempts,databaseNow,"REST_EMPTY",exchangeNow);return;
            }
            if(rows.isEmpty()) { pending(candidate,attempts,databaseNow,"REST_EMPTY",exchangeNow);return; }
            if(candidate.closeTime.compareTo(exchangeNow)>=0) {
                pending(candidate,attempts,databaseNow,"REST_NOT_FINAL",exchangeNow);return;
            }
            if(exact.size()!=1) {
                pending(candidate,attempts,databaseNow,"REST_MISMATCH",exchangeNow);return;
            }
            BinanceKline k=exact.getFirst();
            tx.executeWithoutResult(status->{
                boolean changed=gap ? candles.insertAuthoritativeGapBackfill(candidate.symbol,candidate.interval,candidate.openTime,k.closeTime(),
                    k.openPrice(),k.highPrice(),k.lowPrice(),k.closePrice(),k.volume(),k.quoteAssetVolume(),k.numberOfTrades(),k.takerBuyBaseVolume(),k.takerBuyQuoteVolume())
                    : candles.finalizeExistingOpenFromAuthoritative(candidate.symbol,candidate.interval,candidate.openTime,k.closeTime(),
                    k.openPrice(),k.highPrice(),k.lowPrice(),k.closePrice(),k.volume(),k.quoteAssetVolume(),k.numberOfTrades(),k.takerBuyBaseVolume(),k.takerBuyQuoteVolume());
                String result=changed ? (gap?"GAP_BACKFILLED":"RECOVERED") : (gap?"ALREADY_PRESENT":"ALREADY_CLOSED");
                audit.terminal(candidate.symbol,candidate.interval,candidate.openTime,result,null,exchangeNow,k.openTime(),k.closeTime());
                if(changed) outcomes.record(candidate.symbol,candidate.interval,candidate.openTime,"SKIPPED_NOT_LIVE",result,null,gap?"AUTHORITATIVE_GAP_BACKFILL":"AUTHORITATIVE_RECOVERY");
            });
        } catch(RuntimeException failure) { pending(candidate,attempts,databaseNow,"REST_FAILURE",exchangeNow); }
    }

    private void pending(Candidate c,int attempts,Instant databaseNow,String reason,Instant exchangeNow){
        long delay=Math.min(300,attempts==1?5:attempts==2?15:60L*(1L<<Math.min(2,attempts-3)));
        audit.pending(c.symbol,c.interval,c.openTime,attempts,databaseNow.plusSeconds(delay),reason,exchangeNow);
    }
    private boolean positiveLossEvidence(Candidate candidate){
        // This relies on the current FIX-131 invariant: one primary WebSocket
        // session invokes processKline synchronously and in Binance receive
        // order. If streams are sharded or processing becomes asynchronous,
        // this successor proof must be redesigned and re-tested.
        Instant successor=candidate.openTime.plusSeconds(intervalSeconds(candidate.interval));
        Integer successorPresent=jdbc.queryForObject("SELECT COUNT(*) FROM candle WHERE symbol=? AND interval_code=? AND open_time=?",Integer.class,
            candidate.symbol,candidate.interval,Timestamp.from(successor));
        return successorPresent!=null&&successorPresent>0 || (lastReconnectAdmission!=null && !lastReconnectAdmission.isBefore(candidate.closeTime));
    }
    private void terminalUnavailable(Candidate candidate,Instant exchangeNow,String reason){
        tx.executeWithoutResult(status->{
            audit.terminal(candidate.symbol,candidate.interval,candidate.openTime,"EXPIRED",reason,exchangeNow,null,null);
            outcomes.record(candidate.symbol,candidate.interval,candidate.openTime,"SKIPPED_DATA_UNAVAILABLE",reason,null,"AUTHORITATIVE_RECONCILIATION");
        });
    }
    private static long intervalSeconds(String interval){return switch(interval){case "1m"->60;case "5m"->300;case "15m"->900;case "1h"->3600;case "4h"->14400;case "1d"->86400;default->throw new IllegalArgumentException("Unsupported interval: "+interval);};}
    private static Instant expectedClose(Instant open,String interval){return open.plusSeconds(intervalSeconds(interval)).minusMillis(1);}
    private record Candidate(String symbol,String interval,Instant openTime,Instant closeTime) {}
}
