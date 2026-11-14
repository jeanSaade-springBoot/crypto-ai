package com.crypto.shared;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.infrastructure.diagnostics.JdbcTimingDataSource;
import java.util.*;
import java.time.*;
import java.sql.Timestamp;

/** FIX-142: separate bounded operational views; JDBC timings never pretend to be server I/O. */
@Service
public class Fix142HealthService {
    // FIX-153: OFF returns local health before any retired-table query.
    @org.springframework.beans.factory.annotation.Value("${shared-market.mode:OFF}") private String mode="OFF";
    @org.springframework.beans.factory.annotation.Autowired private com.crypto.local.LocalCandleHealth localHealth;
    private final JdbcTemplate jdbc;
    private final CoinConfigurationService coins;
    public Fix142HealthService(JdbcTemplate jdbc,CoinConfigurationService coins) {
        this.jdbc=new JdbcTemplate(Objects.requireNonNull(jdbc.getDataSource()));this.jdbc.setQueryTimeout(2);this.coins=coins;
    }
    private volatile Map<String,Object> cached;
    private volatile Instant cachedAt=Instant.EPOCH;
    public synchronized Map<String,Object> snapshot() {
        if("OFF".equals(mode))return localHealth.snapshot();
        if(cached!=null&&cachedAt.plusSeconds(15).isAfter(Instant.now()))return cached;
        Map<String,Object> result=new LinkedHashMap<>();Instant now=Instant.now();
        result.put("observedUtc",now);result.put("observedKsa",now.atOffset(ZoneOffset.ofHours(3)));
        result.put("jdbc",JdbcTimingDataSource.statistics());result.put("processingCountersSinceStart",Fix142Metrics.snapshot());
        // FIX-150: retirement restores availability but never hides legacy effect uncertainty.
        try {
            result.put("automaticProtectionRetirements",jdbc.queryForList("SELECT source_event_id,symbol,operation,evidence,created_at AS created_utc,DATE_ADD(created_at,INTERVAL 3 HOUR) AS created_ksa FROM shared_delivery_resolution_audit WHERE reviewer='FIX150_SYSTEM' ORDER BY id DESC LIMIT 51"));
        }catch(RuntimeException failure){result.put("automaticRetirementHistoryUnavailable",failure.getClass().getSimpleName());}
        // FIX-142: a protection fence is a live-management incident, not merely a queue metric.
        // Separate try/catch keeps its severity visible even if another health query fails.
        try {
            var protectionFences=jdbc.queryForList("SELECT symbol,source_event_id,protection_owner,protection_started_at,status FROM shared_market_event_delivery WHERE interval_code='1m' AND protection_completed_at IS NULL AND protection_started_at<? ORDER BY protection_started_at,source_event_id LIMIT 201",Timestamp.from(now.minusSeconds(30)));
            result.put("protectionRecoveryRequired",protectionFences);
            result.put("protectionRecoveryTruncated",protectionFences.size()==201);
            result.put("protectionSeverity",protectionFences.isEmpty()?"NO_AGED_FENCES_DETECTED":"CRITICAL_OPERATOR_RECOVERY_REQUIRED");
        }catch(RuntimeException failure){result.put("protectionSeverity","UNKNOWN_MONITORING_FAILED");result.put("protectionHealthUnavailable",failure.getClass().getSimpleName());}
        // LIMIT bounds rows returned, not server rows examined; timeout/failure stays visible.
        try {
            var reviews=jdbc.queryForList("SELECT source_event_id FROM shared_market_event_delivery WHERE analysis_status='REVIEW_REQUIRED' ORDER BY source_event_id LIMIT 201");
            result.put("analysisReviewsAtLeast",reviews.size());result.put("analysisReviewsTruncated",reviews.size()==201);
            result.put("fencedMarkers",jdbc.queryForList("SELECT symbol,active_delivery_event_id,active_delivery_started_at,active_delivery_updated_at FROM shared_market_consumer_state WHERE active_delivery_state='REVIEW_REQUIRED'"));
            var retry=jdbc.queryForList("SELECT source_event_id,analysis_not_before FROM shared_market_event_delivery WHERE analysis_status='EXECUTION_RETRY' ORDER BY source_event_id LIMIT 201");
            result.put("executionRetryRowsAtLeast",retry.size());result.put("executionRetryRowsTruncated",retry.size()==201);
            List<Map<String,Object>> lanes=new ArrayList<>();
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            laneScan: for(String symbol:coins.enabledSymbols())for(String interval:List.of("1m","5m","15m","1h","4h","1d")) {
                if(System.nanoTime()>=deadline){result.put("laneScanTruncated",true);break laneScan;}
                // Candle ordering protects this view against freshly generated old backfills.
                var latest=jdbc.queryForList("SELECT id,candle_open_time,generated_at,btc_context_status,final_entry_allowed FROM trade_signal WHERE symbol=? AND interval_code=? AND candle_open_time<=? ORDER BY candle_open_time DESC LIMIT 1",symbol,interval,Timestamp.from(now));
                Map<String,Object> lane=new LinkedHashMap<>();lane.put("symbol",symbol);lane.put("interval",interval);lane.put("signal",latest);
                if(!latest.isEmpty()) {
                    Instant close=((Timestamp)latest.getFirst().get("candle_open_time")).toInstant().plusSeconds(cadence(interval));
                    lane.put("candleCloseUtc",close);lane.put("closeAgeSeconds",Duration.between(close,now).getSeconds());
                    lane.put("withinAnalysisDispatchGrace",SharedEventPolicy.closeEligible(interval,close,now));
                } else lane.put("withinAnalysisDispatchGrace",false);
                lanes.add(lane);
            }
            result.put("lanes",lanes);
            List<Map<String,Object>> categories=new ArrayList<>();
            long categoryDeadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            for(String symbol:coins.enabledSymbols()) {
                if(System.nanoTime()>=categoryDeadline){result.put("categoryScanTruncated",true);break;}
                // Exact counts in a bounded-time, operator-requested scan, not a hot-path COUNT.
                // EXPLAIN/rows-examined acceptance remains mandatory; timeout is visible.
                categories.addAll(categoryCounts(symbol,now));
            }
            result.put("deliveryCategories",categories);
            result.put("activeOwnershipAndProtection",jdbc.queryForList("SELECT symbol,last_observed_at,active_delivery_event_id,active_delivery_state,active_delivery_started_at,active_delivery_updated_at FROM shared_market_consumer_state"));

        }catch(RuntimeException failure){result.put("localHealthUnavailable",failure.getClass().getSimpleName());}
        try {
            result.put("mysqlStatus",jdbc.queryForList("SELECT VARIABLE_NAME,VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME IN ('Innodb_buffer_pool_read_requests','Innodb_buffer_pool_reads','Innodb_buffer_pool_wait_free','Innodb_data_pending_reads','Innodb_data_pending_writes','Innodb_log_waits','Threads_running','Uptime')"));
            result.put("mysqlIdentity",jdbc.queryForMap("SELECT @@hostname AS hostname,@@port AS port,@@server_uuid AS server_uuid,@@innodb_buffer_pool_size AS buffer_pool_bytes"));
        }catch(RuntimeException failure){result.put("mysqlStatusUnavailable",failure.getClass().getSimpleName());}
        cached=result;cachedAt=Instant.now();return result;
    }
    private List<Map<String,Object>> categoryCounts(String symbol,Instant now) {
        String grace="CASE d.interval_code";
        for(String interval:List.of("1m","5m","15m","1h","4h","1d"))grace+=" WHEN '"+interval+"' THEN "+SharedEventPolicy.closeGraceSeconds(interval);
        grace+=" ELSE 0 END";
        String analysis="d.closed=1 AND d.candle_close_time BETWEEN TIMESTAMPADD(SECOND,-("+grace+"),CAST(? AS DATETIME(6))) AND ?";
        String protection="d.interval_code='1m' AND d.observed_at BETWEEN ? AND ?";
        String marker="s.active_delivery_event_id=d.source_event_id AND s.active_delivery_state IN ('ACTIVE','REVIEW_REQUIRED')";
        String sql="SELECT d.symbol,d.interval_code,"
            +"SUM(CASE WHEN "+protection+" AND d.protection_completed_at IS NULL THEN 1 ELSE 0 END) eligible_protection,"
            +"SUM(CASE WHEN "+analysis+" AND d.analysis_status IN ('WAITING','PENDING','HISTORICAL_DEFERRED') THEN 1 ELSE 0 END) eligible_analysis,"
            +"SUM(CASE WHEN d.interval_code='1m' AND d.observed_at<? AND "+analysis+" THEN 1 ELSE 0 END) protection_expired_analysis_eligible,"
            +"SUM(CASE WHEN d.protection_started_at IS NULL AND d.analysis_started_at IS NULL AND NOT ("+marker+") AND NOT ("+analysis+") AND (d.interval_code<>'1m' OR d.observed_at<?) AND d.analysis_status NOT IN ('REVIEW_REQUIRED','EXECUTION_RETRY','RUNNING') THEN 1 ELSE 0 END) fully_expired_untouched,"
            +"SUM(CASE WHEN "+marker+" AND s.active_delivery_state='ACTIVE' THEN 1 ELSE 0 END) owned,"
            +"SUM(CASE WHEN "+marker+" AND s.active_delivery_state='REVIEW_REQUIRED' THEN 1 ELSE 0 END) fenced,"
            +"SUM(CASE WHEN d.phase=4 AND d.analysis_status IN ('PENDING','HISTORICAL_DEFERRED') THEN 1 ELSE 0 END) phase4_pending,"
            +"SUM(CASE WHEN d.status='REVIEW_REQUIRED' OR d.analysis_status='REVIEW_REQUIRED' THEN 1 ELSE 0 END) failed_attempt,"
            +"MAX(CASE WHEN d.created_at>=? THEN TIMESTAMPDIFF(SECOND,d.observed_at,d.created_at) ELSE NULL END) recent_admission_lag_seconds "
            +"FROM shared_market_event_delivery d JOIN shared_market_consumer_state s ON s.symbol=d.symbol WHERE d.symbol=? GROUP BY d.symbol,d.interval_code";
        Timestamp point=Timestamp.from(now),priceCutoff=Timestamp.from(now.minusSeconds(15));
        return jdbc.queryForList(sql,priceCutoff,point,point,point,priceCutoff,point,point,point,point,priceCutoff,Timestamp.from(now.minusSeconds(60)),symbol);
    }
    private long cadence(String interval){return switch(interval){case "1m"->60;case "5m"->300;case "15m"->900;case "1h"->3600;case "4h"->14400;case "1d"->86400;default->throw new IllegalArgumentException("Unknown interval");};}
}
