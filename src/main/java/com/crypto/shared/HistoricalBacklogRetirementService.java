package com.crypto.shared;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.crypto.administration.service.CoinConfigurationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** FIX-140: opt-in bounded retirement of untouched analysis queue entries.
 * Keeps source candles, saved signals and every uncertain execution/protection record.
 * Defaults are conservative pending Production/Replay consumer-window acceptance. */
@Service
public class HistoricalBacklogRetirementService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(HistoricalBacklogRetirementService.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CoinConfigurationService coins;
    private final SharedMarketSource source;
    private final String operation=UUID.randomUUID().toString();
    private int cursor;
    private record ScanPosition(Instant close,long sequence) {}
    private final java.util.Map<String,ScanPosition> scans=new java.util.HashMap<>();
    /** FIX-140: keyset progress skips ineligible heads without an expensive backlog anti-join.
     * Cursor is advisory only; every mutation still rechecks the locked current row. */
    private java.util.List<java.util.Map<String,Object>> candidates(String symbol,String interval,String status,Instant cutoff) {
        String key=symbol+"|"+interval+"|"+status;
        ScanPosition position=scans.getOrDefault(key,new ScanPosition(Instant.EPOCH,-1));
        var result=jdbc.queryForList("SELECT source_event_id,candle_close_time,symbol_sequence FROM shared_market_event_delivery WHERE symbol=? AND interval_code=? AND phase=4 AND analysis_status=? AND candle_close_time<? AND analysis_started_at IS NULL AND analysis_completed_at IS NULL AND (candle_close_time,symbol_sequence)>(?,?) ORDER BY candle_close_time,symbol_sequence LIMIT 10",symbol,interval,status,Timestamp.from(cutoff),Timestamp.from(position.close()),position.sequence());
        if(result.isEmpty())scans.remove(key);
        else {
            var last=result.getLast();
            scans.put(key,new ScanPosition(SharedMarketConsumer.time(last,"candle_close_time"),SharedMarketConsumer.num(last,"symbol_sequence")));
        }
        return result;
    }
    @Value("${shared-market.analysis.historical-enabled:true}") private boolean historyEnabled=true;
    @Value("${shared-market.analysis.retirement-enabled:false}") private boolean retirementEnabled;
    @Value("${shared-market.analysis.retirement-dry-run:true}") private boolean dryRun=true;
    @Value("${shared-market.analysis.retirement-retain-seconds:172800}") private long retainSeconds=172800;
    public HistoricalBacklogRetirementService(JdbcTemplate local,PlatformTransactionManager manager,
            CoinConfigurationService coins,SharedMarketSource source) {
        jdbc=new JdbcTemplate(java.util.Objects.requireNonNull(local.getDataSource()));jdbc.setQueryTimeout(2);
        tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setTimeout(3);
        this.coins=coins;this.source=source;
    }
    @Scheduled(fixedDelayString="${shared-market.analysis.retirement-delay-ms:5000}")
    public void retire() {
        if(!retirementEnabled || historyEnabled || !source.enabled())return;
        // FIX-140: fresh windows always survive, even when the operator explicitly
        // chooses zero extra retention to retire all untouched expired queue entries.
        if(retainSeconds<0) {log.error("[FIX-140][RETIREMENT_CONFIG_REJECTED] retainSeconds must be nonnegative");return;}
        List<String> symbols=coins.enabledSymbols();if(symbols.isEmpty())return;
        List<String> intervals=List.of("1m","5m","15m","1h","4h","1d");
        int lane=Math.floorMod(cursor++,symbols.size()*intervals.size());
        String symbol=symbols.get(lane/intervals.size()),interval=intervals.get(lane%intervals.size());
        Instant cutoff=Instant.now().minusSeconds(Math.max(retainSeconds,SharedEventPolicy.closeGraceSeconds(interval)));
        try {
            var ids=new java.util.ArrayList<>(candidates(symbol,interval,"PENDING",cutoff));
            ids.addAll(candidates(symbol,interval,"HISTORICAL_DEFERRED",cutoff));
            int eligible=0,changed=0;
            for(var id:ids) {
                Boolean retired=tx.execute(t->{
                    jdbc.queryForMap("SELECT symbol FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE",symbol);
                    var row=jdbc.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=? FOR UPDATE",id.get("source_event_id"));
                    if(SharedMarketConsumer.num(row,"phase")!=4 || row.get("analysis_started_at")!=null || row.get("analysis_completed_at")!=null
                        || !List.of("PENDING","HISTORICAL_DEFERRED").contains(String.valueOf(row.get("analysis_status")))
                        || !SharedMarketConsumer.time(row,"candle_close_time").isBefore(cutoff))return false;
                    // FIX-140: owner_token may belong to phase-0/price delivery; locked
                    // pending status plus absent analysis markers proves no analysis claim.
                    // Event and candle checks protect attributed and legacy processing evidence.
                    if(!jdbc.queryForList("SELECT signal_id FROM signal_processing_work WHERE source_event_id=? LIMIT 1",id.get("source_event_id")).isEmpty())return false;
                    if(!jdbc.queryForList("SELECT signal_id FROM signal_processing_work WHERE symbol=? AND interval_code=? AND candle_open_time=? LIMIT 1",symbol,interval,row.get("candle_open_time")).isEmpty())return false;
                    if("REVIEW_REQUIRED".equals(String.valueOf(row.get("status"))) || row.get("protection_started_at")!=null && row.get("protection_completed_at")==null)return false;
                    if(!jdbc.queryForList("SELECT wt.id FROM wallet_trade wt JOIN trade_signal s ON s.id=wt.signal_id WHERE s.symbol=? AND s.interval_code=? AND s.candle_open_time=? LIMIT 1",symbol,interval,row.get("candle_open_time")).isEmpty())return false;
                    if(dryRun)return true;
                    return jdbc.update("UPDATE shared_market_event_delivery SET analysis_status='HISTORICAL_SKIPPED',analysis_retired_at=CURRENT_TIMESTAMP(6),analysis_retirement_operation=?,analysis_retirement_reason='FIX-140 operator-enabled untouched historical queue retirement; candles and saved signals retained' WHERE source_event_id=? AND analysis_status IN ('PENDING','HISTORICAL_DEFERRED') AND analysis_started_at IS NULL AND analysis_completed_at IS NULL",operation,id.get("source_event_id"))==1;
                });
                if(Boolean.TRUE.equals(retired)) {eligible++;if(!dryRun)changed++;}
            }
            if(eligible>0)log.info("[FIX-140][HISTORY_RETIREMENT] operation={}, symbol={}, interval={}, dryRun={}, eligible={}, retired={}",operation,symbol,interval,dryRun,eligible,changed);
        } catch(RuntimeException failure) {log.warn("[FIX-140][HISTORY_RETIREMENT_FAILED] operation={}, symbol={}, interval={}",operation,symbol,interval,failure);}
    }
}
