package com.crypto.shared;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.position.service.LivePositionProtectionService;
import com.crypto.debug.monitor.service.PriceMoveMonitorService;
import com.crypto.indicator.event.CandleClosedAnalysisWorker;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class SharedMarketConsumerTest {
    JdbcTemplate local,feed; SharedMarketSource source; DataSourceTransactionManager manager;
    CandleClosedAnalysisWorker worker;
    LivePositionProtectionService protect; PriceMoveMonitorService observer; CoinConfigurationService coins;
    SharedMarketConsumer consumer; Instant now;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");
        local=new JdbcTemplate(ds); manager=new DataSourceTransactionManager(ds);
        feed=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        local.execute("CREATE TABLE market_price_event(id BIGINT AUTO_INCREMENT PRIMARY KEY,symbol VARCHAR(30),observed_at TIMESTAMP(6),price DECIMAL(30,12),source VARCHAR(40))");
        String ddl=Files.readString(Path.of("src/main/resources/db/migration/V89__fix_132_shared_market_delivery.sql"));
        for(String statement:ddl.split(";"))if(!statement.isBlank()) {
            // H2 accepts the MySQL table DDL; split MySQL's multi-ADD ALTER into
            // separate statements. Production still executes the unmodified migration.
            if(statement.stripLeading().startsWith("ALTER TABLE")) {
                for(String add:statement.substring(statement.indexOf(" ADD ")+5).split(",\\s*ADD "))local.execute("ALTER TABLE market_price_event ADD "+add);
            } else local.execute(statement);
        }
        local.execute(Files.readString(Path.of("src/main/resources/db/migration/V90__fix_132_delivery_exclusion_reason.sql")));
        local.execute("ALTER TABLE shared_market_event_delivery ADD COLUMN analysis_not_before TIMESTAMP(6)");
        local.execute("CREATE TABLE signal_processing_work(signal_id BIGINT PRIMARY KEY,status VARCHAR(30),symbol VARCHAR(30),interval_code VARCHAR(10),candle_open_time TIMESTAMP(6))");
        local.execute("INSERT INTO shared_market_consumer_state(symbol,cutover_sequence,cutover_at,discovered_sequence,status) VALUES('AMBIGUSDT',0,CURRENT_TIMESTAMP(6),0,'READY'),('REVIEWUSDT',0,CURRENT_TIMESTAMP(6),0,'REVIEW_REQUIRED')");
        String approvalDdl=Files.readString(Path.of("src/main/resources/db/migration/V91__fix_132_explicit_cutover_ownership.sql")).replaceAll("(?m)^\\s*--.*$", "");
        // Split multi-ADD only for H2 syntax compatibility; MySQL accepts either form.
        approvalDdl=approvalDdl.replaceAll(",\\s*ADD COLUMN", ";ALTER TABLE shared_market_consumer_state ADD COLUMN");
        for(String sql:approvalDdl.split(";"))if(!sql.isBlank())local.execute(sql);
        assertEquals("PENDING_CUTOVER",local.queryForObject("SELECT status FROM shared_market_consumer_state WHERE symbol='AMBIGUSDT'",String.class));
        assertEquals("UNKNOWN_LEGACY",local.queryForObject("SELECT cutover_source FROM shared_market_consumer_state WHERE symbol='AMBIGUSDT'",String.class));
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT status FROM shared_market_consumer_state WHERE symbol='REVIEWUSDT'",String.class));
        local.execute("DELETE FROM shared_market_consumer_state");
        for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V94__fix_132_reviewed_cutover_proposal.sql")).replaceAll("(?m)^\\s*--.*$", "").split(";"))if(!sql.isBlank())local.execute(sql);
        local.execute("CREATE TABLE effects(id BIGINT AUTO_INCREMENT PRIMARY KEY)");
        feed.execute("CREATE TABLE market_data_stream_event(id BIGINT,symbol VARCHAR(30),symbol_sequence BIGINT,interval_code VARCHAR(10),candle_open_time TIMESTAMP(6),candle_close_time TIMESTAMP(6),closed BOOLEAN,observed_at TIMESTAMP(6),received_at TIMESTAMP(6),created_at TIMESTAMP(6),price DECIMAL(30,12),source VARCHAR(30),classification VARCHAR(40))");
        source=mock(SharedMarketSource.class);when(source.enabled()).thenReturn(true);when(source.mode()).thenReturn("LIVE");when(source.feedReader()).thenReturn(feed);
        coins=mock(CoinConfigurationService.class);when(coins.enabledSymbols()).thenReturn(List.of("BTCUSDT"));
        protect=mock(LivePositionProtectionService.class);observer=mock(PriceMoveMonitorService.class);
        now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        local.update("INSERT INTO shared_market_consumer_state(symbol,cutover_sequence,cutover_at,discovered_sequence) VALUES('BTCUSDT',0,?,0)",Timestamp.from(now.minusSeconds(60)));
        worker=mock(CandleClosedAnalysisWorker.class);
        when(worker.processShared(any(),any(),anyBoolean(),anyLong())).thenReturn("COMPLETED");
        local.update("UPDATE shared_market_consumer_state SET status='READY',cutover_source='OPERATOR_APPROVED',approved_by='test',approved_at=CURRENT_TIMESTAMP(6),approval_reference='controlled-test',approved_sequence=cutover_sequence,approved_cutover_at=cutover_at");
        local.update("INSERT INTO shared_market_cutover_approval(symbol,cutover_sequence,cutover_at,approved_by,approved_at,approval_reference) SELECT symbol,cutover_sequence,cutover_at,approved_by,approved_at,approval_reference FROM shared_market_consumer_state");
        local.execute("CREATE TABLE trade_signal(id BIGINT PRIMARY KEY,symbol VARCHAR(30),interval_code VARCHAR(10),candle_open_time TIMESTAMP(6),UNIQUE(symbol,interval_code,candle_open_time))");
        local.execute("CREATE TABLE wallet_trade(id BIGINT PRIMARY KEY,signal_id BIGINT)");
        for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V95__fix_138_analysis_dispatch_ranges.sql")).replaceAll("(?m)^\\s*--.*$", "").split(";"))if(!sql.isBlank())local.execute(sql);
        for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V98__fix_141_reconciliation_scan_index.sql")).replaceAll("(?m)^\\s*--.*$", "").split(";"))if(!sql.isBlank())local.execute(sql);
        consumer=newConsumer();
    }
    SharedMarketConsumer newConsumer() { return new SharedMarketConsumer(source,local,coins,protect,observer,worker,Runnable::run,Runnable::run,manager); }
    void event(long seq,Instant observed) {
        feed.update("INSERT INTO market_data_stream_event VALUES(?,'BTCUSDT',?,'1m',?,?,false,?,?,?,10,'LIVE_WEBSOCKET','LIVE')",seq,seq,
            Timestamp.from(now.minusSeconds(10)),Timestamp.from(now.plusSeconds(50)),Timestamp.from(observed),Timestamp.from(now),Timestamp.from(now));
    }
    int blockedSessions() { return local.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL",Integer.class); }
    @AfterEach void cleanup() { consumer.stop(); }
    @Test void orphanProcessingOwnershipCannotAuthorizeRestart() {
        local.update("INSERT INTO signal_processing_work(signal_id,status,source_event_id) VALUES(999,'PENDING',9876)");
        org.springframework.test.util.ReflectionTestUtils.setField(consumer,"activationApproved",true);
        assertTrue(assertThrows(IllegalStateException.class,()->consumer.validateCutover()).getMessage().contains("PROCESSING_OWNER_INCONSISTENT"));
    }
    @Test void discoveryDoesNotModifySourceAndRetriesDoNotDuplicateHistoryOrEffects() {
        event(1,now);consumer.discover();consumer.discover();consumer.processNext("BTCUSDT");consumer.processNext("BTCUSDT");
        verify(protect,times(1)).onPrice(eq("BTCUSDT"),any());verify(observer,times(1)).onPrice(eq("BTCUSDT"),any(),any());
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM market_price_event",Integer.class));
        assertEquals("APPLIED",local.queryForObject("SELECT delivery_status FROM market_price_event",String.class));
        assertEquals(1L,feed.queryForObject("SELECT symbol_sequence FROM market_data_stream_event",Long.class));
    }
    @Test void unknownBusinessFailureRollsBackEffectAndRequiresReviewWithoutRetry() {
        doAnswer(call->{local.update("INSERT INTO effects(id) VALUES(DEFAULT)");throw new IllegalStateException("controlled business failure");}).when(protect).onPrice(any(),any());
        event(1,now);consumer.discover();consumer.processNext("BTCUSDT");consumer.processNext("BTCUSDT");
        verify(protect,times(1)).onPrice(any(),any());verifyNoInteractions(observer);
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT status FROM shared_market_consumer_state",String.class));
        assertEquals("PENDING",local.queryForObject("SELECT delivery_status FROM market_price_event",String.class));
    }
    @Test void sequenceGapNeverAdvancesPastMissingEvent() {
        event(2,now);consumer.discover();
        assertEquals("SEQUENCE_GAP",local.queryForObject("SELECT status FROM shared_market_consumer_state",String.class));
        assertEquals(0L,local.queryForObject("SELECT discovered_sequence FROM shared_market_consumer_state",Long.class));
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery",Integer.class));
    }
    @Test void higherSequenceWithPreCutoverObservationCannotExecute() {
        local.update("UPDATE shared_market_consumer_state SET cutover_at=?,approved_cutover_at=?",Timestamp.from(now.plusSeconds(1)),Timestamp.from(now.plusSeconds(1)));
        event(1,now);consumer.discover();consumer.processNext("BTCUSDT");
        verifyNoInteractions(protect,observer);
        assertEquals("HISTORICAL_ONLY",local.queryForObject("SELECT delivery_status FROM market_price_event",String.class));
    }
    @Test void observeModeHasNoPriceProtectionOrAnalysisEffects() {
        when(source.enabled()).thenReturn(false);when(source.mode()).thenReturn("OBSERVE");
        event(1,now);consumer.discover();consumer.processNext("BTCUSDT");
        verifyNoInteractions(protect,observer);
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM market_price_event",Integer.class));
        assertEquals("OBSERVED",local.queryForObject("SELECT status FROM shared_market_event_delivery",String.class));
    }
    @Test void twoConsumersCannotCommitTheSameProtectionEffect() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        doAnswer(call->{local.update("INSERT INTO effects(id) VALUES(DEFAULT)");entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return null;}).when(protect).onPrice(any(),any());
        event(1,now);consumer.discover();var second=newConsumer();var threads=Executors.newFixedThreadPool(2);
        try {
            Future<?> a=threads.submit(()->consumer.processNext("BTCUSDT"));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            // The second worker reaches the actual symbol-row lock while A holds it.
            CountDownLatch started=new CountDownLatch(1);
            Future<?> b=threads.submit(()->{started.countDown();second.processNext("BTCUSDT");});
            assertTrue(started.await(5,TimeUnit.SECONDS));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            boolean blocked=false;
            while(System.nanoTime()<deadline) {
                if(blockedSessions()>0) {blocked=true;break;}
                Thread.onSpinWait();
            }
            assertTrue(blocked,"B must actually be waiting on A's database lock before A is released");
            release.countDown();a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS);
            verify(protect,times(1)).onPrice(any(),any());
            assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
        } finally {release.countDown();threads.shutdownNow();second.stop();}
    }

    @Test void initialPositionLockRetryRetainsExistingFix124Boundary() {
        doThrow(new com.crypto.infrastructure.transaction.InitialPositionLockDeadlock(new IllegalStateException("controlled initial lock")))
            .doNothing().when(protect).onPrice(any(),any());
        event(1,now);consumer.discover();consumer.processNext("BTCUSDT");
        verify(protect,times(2)).onPrice(any(),any());
        assertEquals("APPLIED",local.queryForObject("SELECT delivery_status FROM market_price_event",String.class));
    }
    @Test void observerFailureDoesNotBlockFutureProtectionOrRetryObserver() {
        doThrow(new IllegalStateException("observer failure")).when(observer).onPrice(any(),any(),any());
        event(1,now);consumer.discover();consumer.processNext("BTCUSDT");
        assertEquals("READY",local.queryForObject("SELECT status FROM shared_market_consumer_state",String.class));
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT observer_status FROM shared_market_event_delivery",String.class));
        assertEquals(4,local.queryForObject("SELECT phase FROM shared_market_event_delivery",Integer.class));
        consumer.processNext("BTCUSDT");verify(observer,times(1)).onPrice(any(),any(),any());
    }

    @Test void restartDoesNotRetryAnUnconfirmedStartedProtectionAttempt() {
        event(1,now);consumer.discover();
        local.update("UPDATE shared_market_event_delivery SET phase=1,protection_started_at=?,protection_owner='stopped-process',status='PROTECTION_RUNNING'",Timestamp.from(now.minusSeconds(60)));
        consumer.processNext("BTCUSDT");
        verifyNoInteractions(protect,observer);
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT status FROM shared_market_consumer_state",String.class));
        assertEquals(1,local.queryForObject("SELECT phase FROM shared_market_event_delivery",Integer.class));
    }

    @Test void formingPriceStillProtectsButNeverTriggersAnalysis() {
        event(1,now);consumer.discover();consumer.processNext("BTCUSDT");consumer.dispatchAnalysis();
        verify(protect).onPrice(any(),any());verifyNoInteractions(worker);
        assertEquals("ELIGIBLE",local.queryForObject("SELECT eligibility_reason FROM shared_market_event_delivery",String.class));
    }
    @Test void historicalRepairWithHigherSequenceNeverProtectsOrAnalyzesLive() {
        event(1,now);
        feed.update("UPDATE market_data_stream_event SET closed=true,source='STARTUP_RECOVERY',classification='HISTORICAL_ONLY',candle_close_time=?",Timestamp.from(now.minusSeconds(100)));
        consumer.discover();consumer.processNext("BTCUSDT");consumer.dispatchHistoricalAnalysis();
        verifyNoInteractions(protect,observer);
        verify(worker).processShared(any(),any(),eq(false),eq(1L));
        assertEquals("NON_LIVE_SOURCE",local.queryForObject("SELECT eligibility_reason FROM shared_market_event_delivery",String.class));
    }
    @Test void closedLiveEventRegistersAnalysisOnlyAfterProtectionAndObserver() {
        event(1,now);feed.update("UPDATE market_data_stream_event SET closed=true,candle_close_time=?",Timestamp.from(now.minusSeconds(1)));
        consumer.discover();consumer.dispatchAnalysis();verifyNoInteractions(worker);
        consumer.processNext("BTCUSDT");consumer.dispatchAnalysis();
        var order=inOrder(protect,observer,worker);
        order.verify(protect).onPrice(any(),any());order.verify(observer).onPrice(any(),any(),any());
        order.verify(worker).processShared(any(),any(),eq(true),eq(1L));
        assertEquals("COMPLETED",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class));
    }
    @Test void reviewRequiredSymbolCannotStartQueuedAnalysis() {
        event(1,now);feed.update("UPDATE market_data_stream_event SET closed=true,candle_close_time=?",Timestamp.from(now.minusSeconds(1)));
        consumer.discover();consumer.processNext("BTCUSDT");
        local.update("UPDATE shared_market_consumer_state SET status='REVIEW_REQUIRED'");
        consumer.dispatchAnalysis();verifyNoInteractions(worker);
        assertEquals("PENDING",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class));
    }
    @Test void malformedCopyRollsBackCheckpointAndEntireLocalBatch() {
        event(1,now);event(2,now);feed.update("UPDATE market_data_stream_event SET price=NULL WHERE id=2");
        consumer.discover();
        assertEquals(0L,local.queryForObject("SELECT discovered_sequence FROM shared_market_consumer_state",Long.class));
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery",Integer.class));
    }
    @Test void discoveryIsBoundedAndDoesNotDropSourceBacklog() {
        for(int n=1;n<=205;n++)event(n,now);
        consumer.discover();consumer.discover();consumer.discover();
        assertEquals(200,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery",Integer.class));
        assertEquals(200L,local.queryForObject("SELECT discovered_sequence FROM shared_market_consumer_state",Long.class));
        assertEquals(205,feed.queryForObject("SELECT COUNT(*) FROM market_data_stream_event",Integer.class));
    }
    @Test void symbolQuarantinedAfterClaimCannotRunWaitingTask() {
        var waiting=new java.util.concurrent.atomic.AtomicReference<Runnable>();
        consumer.stop();
        consumer=new SharedMarketConsumer(source,local,coins,protect,observer,worker,waiting::set,Runnable::run,manager);
        event(1,now);feed.update("UPDATE market_data_stream_event SET closed=true,candle_close_time=?",Timestamp.from(now.minusSeconds(1)));
        consumer.discover();consumer.processNext("BTCUSDT");consumer.dispatchAnalysis();
        assertNotNull(waiting.get());
        assertEquals("RUNNING",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class));
        local.update("UPDATE shared_market_consumer_state SET status='REVIEW_REQUIRED'");
        waiting.get().run();
        verifyNoInteractions(worker);
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class));
    }

    void enableValidation() {
        org.springframework.test.util.ReflectionTestUtils.setField(consumer,"activationApproved",true);
        var properties=new com.crypto.client.config.binance.BinanceMarketDataProperties();
        properties.setIntervals(List.of("1m"));
        org.springframework.test.util.ReflectionTestUtils.setField(consumer,"intervals",properties);
    }
    @Test void observationAutoSeedAdvancesWithoutGrantingLiveApproval() {
        local.update("DELETE FROM shared_market_consumer_state");
        feed.execute("CREATE TABLE market_data_stream_cursor(symbol VARCHAR(30) PRIMARY KEY,last_sequence BIGINT)");
        feed.update("INSERT INTO market_data_stream_cursor VALUES('BTCUSDT',0)");
        when(source.enabled()).thenReturn(false);when(source.mode()).thenReturn("OBSERVE");
        consumer.discover();
        assertEquals("PENDING_CUTOVER",local.queryForObject("SELECT status FROM shared_market_consumer_state",String.class));
        assertEquals("AUTO_OBSERVED",local.queryForObject("SELECT cutover_source FROM shared_market_consumer_state",String.class));
        event(1,now);consumer.discover();consumer.processNext("BTCUSDT");
        assertEquals(1L,local.queryForObject("SELECT discovered_sequence FROM shared_market_consumer_state",Long.class));
        assertEquals("OBSERVED",local.queryForObject("SELECT status FROM shared_market_event_delivery",String.class));
        verifyNoInteractions(protect,observer,worker);
        when(source.enabled()).thenReturn(true);when(source.mode()).thenReturn("LIVE");enableValidation();
        assertThrows(IllegalStateException.class,consumer::validateCutover);
        local.update("UPDATE shared_market_consumer_state SET status='READY'");
        assertThrows(IllegalStateException.class,consumer::validateCutover);
    }
    @Test void approvalMustMatchBoundaryAndImmutableAudit() {
        enableValidation();
        local.update("UPDATE shared_market_consumer_state SET cutover_sequence=1");
        assertThrows(IllegalStateException.class,consumer::validateCutover);
        local.update("UPDATE shared_market_consumer_state SET cutover_sequence=0");
        local.update("DELETE FROM shared_market_cutover_approval");
        assertThrows(IllegalStateException.class,consumer::validateCutover);
    }
    @Test void unfinishedUnattributedWorkBlocksEvenWhenBoundaryApproved() {
        enableValidation();
        local.update("INSERT INTO signal_processing_work(signal_id,status) VALUES(125,'PENDING')");
        var failure=assertThrows(IllegalStateException.class,consumer::validateCutover);
        assertTrue(failure.getMessage().contains("UNATTRIBUTED_WORK"));
    }
    @Test void approvedBoundaryWithAdequateSourceHistoryPassesStartup() {
        enableValidation();
        feed.execute("CREATE TABLE market_data_stream_cursor(symbol VARCHAR(30) PRIMARY KEY,last_sequence BIGINT)");
        feed.update("INSERT INTO market_data_stream_cursor VALUES('BTCUSDT',0)");
        feed.execute("CREATE TABLE candle(id BIGINT,symbol VARCHAR(30),interval_code VARCHAR(10),closed INT)");
        for(int n=0;n<300;n++)feed.update("INSERT INTO candle VALUES(?,'BTCUSDT','1m',1)",n);
        when(source.reader()).thenReturn(feed);
        assertDoesNotThrow(consumer::validateCutover);
    }

    @Test void recoveryFirstDoesNotReserveSignalBeforeFreshLiveClose() {
        event(1,now);
        feed.update("UPDATE market_data_stream_event SET closed=true,source='STARTUP_RECOVERY',classification='HISTORICAL_ONLY',candle_close_time=? WHERE id=1",Timestamp.from(now.minusSeconds(1)));
        consumer.discover();consumer.processNext("BTCUSDT");consumer.dispatchAnalysis();verifyNoInteractions(worker);
        assertEquals("HISTORICAL_DEFERRED",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery WHERE source_event_id=1",String.class));
        event(2,now);
        feed.update("UPDATE market_data_stream_event SET closed=true,candle_close_time=? WHERE id=2",Timestamp.from(now.minusSeconds(1)));
        consumer.discover();consumer.processNext("BTCUSDT");consumer.dispatchAnalysis();
        verify(worker,times(1)).processShared(any(),any(),eq(true),eq(2L));
        assertEquals("COVERED_BY_LIVE",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery WHERE source_event_id=1",String.class));
    }
    @Test void historicalDeferralExpiresWithoutAnyLiveExecution() {
        event(1,now);feed.update("UPDATE market_data_stream_event SET closed=true,source='STARTUP_RECOVERY',classification='HISTORICAL_ONLY',candle_close_time=?",Timestamp.from(now.minusSeconds(1)));
        consumer.discover();consumer.processNext("BTCUSDT");consumer.dispatchAnalysis();verifyNoInteractions(worker);
        local.update("UPDATE shared_market_event_delivery SET analysis_not_before=?",Timestamp.from(now.minusSeconds(1)));
        local.update("UPDATE shared_market_event_delivery SET candle_close_time=?",Timestamp.from(now.minusSeconds(100)));
        consumer.dispatchHistoricalAnalysis();verify(worker).processShared(any(),any(),eq(false),eq(1L));verifyNoInteractions(protect,observer);
    }

    org.springframework.security.core.Authentication approver(String name,String role) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(name,"unused",
            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role)));
    }
    com.fix132.approval.CutoverApprovalService approvals() {
        when(source.enabled()).thenReturn(false);when(source.mode()).thenReturn("OBSERVE");
        feed.execute("CREATE TABLE market_data_stream_cursor(symbol VARCHAR(30) PRIMARY KEY,last_sequence BIGINT)");
        feed.update("INSERT INTO market_data_stream_cursor VALUES('BTCUSDT',10)");
        return new com.fix132.approval.CutoverApprovalService(local,source,manager);
    }
    @Test void reviewedApprovalHasAuthenticatedProvenanceAndAtomicAudit() {
        var service=approvals();var actor=approver("jean","CUTOVER_APPROVER");
        local.update("DELETE FROM shared_market_cutover_approval");
        local.update("UPDATE shared_market_consumer_state SET status='PENDING_CUTOVER',cutover_source='AUTO_OBSERVED'");
        var proposal=service.preview("BTCUSDT","FIRST_CUTOVER",actor);
        // Collector may continue publishing; approval must retain the REVIEWED boundary.
        feed.update("UPDATE market_data_stream_cursor SET last_sequence=15");
        service.approve((String)proposal.get("proposalId"),"review-132",actor);
        assertEquals(10L,local.queryForObject("SELECT cutover_sequence FROM shared_market_consumer_state",Long.class));
        assertEquals("jean",local.queryForObject("SELECT approved_by FROM shared_market_cutover_approval",String.class));
        assertTrue(SharedCutoverPolicy.approved(local.queryForMap("SELECT * FROM shared_market_consumer_state")));
        assertThrows(IllegalStateException.class,()->service.approve((String)proposal.get("proposalId"),"again",actor));
        assertThrows(IllegalStateException.class,()->service.preview("BTCUSDT","FIRST_CUTOVER",actor));
        local.update("UPDATE shared_market_consumer_state SET discovered_sequence=14,last_price=10,last_observed_at=?",Timestamp.from(now));
        var again=service.preview("BTCUSDT","REAPPROVAL",actor);
        assertEquals(10L,again.get("cutoverSequence"));
        service.approve((String)again.get("proposalId"),"reapproval-preserves-history",actor);
        assertEquals(14L,local.queryForObject("SELECT discovered_sequence FROM shared_market_consumer_state",Long.class));
        assertEquals(now,local.queryForObject("SELECT last_observed_at FROM shared_market_consumer_state",Timestamp.class).toInstant());
        var regressed=service.preview("BTCUSDT","REAPPROVAL",actor);
        feed.update("UPDATE market_data_stream_cursor SET last_sequence=12"); // above cutover 10, below checkpoint 14
        assertThrows(IllegalStateException.class,()->service.approve((String)regressed.get("proposalId"),"regressed",actor));
        assertEquals(2,local.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_approval",Integer.class));
        assertEquals("OBSERVE",source.mode());
    }
    @Test void approvalRejectsUnprivilegedChangedQuarantinedAndUnfinishedState() {
        var service=approvals();var actor=approver("jean","CUTOVER_APPROVER");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.preview("BTCUSDT","REAPPROVAL",approver("user","USER")));
        var proposal=service.preview("BTCUSDT","REAPPROVAL",actor);
        assertThrows(IllegalStateException.class,()->service.approve((String)proposal.get("proposalId"),"review",approver("other","CUTOVER_APPROVER")));
        local.update("UPDATE shared_market_cutover_proposal SET expires_at=?",Timestamp.from(now.minusSeconds(1)));
        assertThrows(IllegalStateException.class,()->service.approve((String)proposal.get("proposalId"),"review",actor));
        var changed=service.preview("BTCUSDT","REAPPROVAL",actor);
        local.update("UPDATE shared_market_consumer_state SET discovered_sequence=1");
        assertThrows(IllegalStateException.class,()->service.approve((String)changed.get("proposalId"),"review",actor));
        local.update("UPDATE shared_market_consumer_state SET status='REVIEW_REQUIRED'");
        assertThrows(IllegalStateException.class,()->service.preview("BTCUSDT","REAPPROVAL",actor));
        local.update("UPDATE shared_market_consumer_state SET status='READY'");
        local.update("INSERT INTO signal_processing_work(signal_id,status,symbol) VALUES(123,'RUNNING','BTCUSDT')");
        assertThrows(IllegalStateException.class,()->service.preview("BTCUSDT","REAPPROVAL",actor));
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_approval",Integer.class));
    }
    @Test void approvalFailureRollsBackStateAndAuditTogether() {
        var service=approvals();var actor=approver("jean","CUTOVER_APPROVER");
        var proposal=service.preview("BTCUSDT","REAPPROVAL",actor);
        // Force proposal consumption to fail AFTER audit AND state writes; both must roll back.
        local.execute("ALTER TABLE shared_market_cutover_proposal ADD CONSTRAINT test_reject_consumption CHECK(consumed_at IS NULL)");
        assertThrows(RuntimeException.class,()->service.approve((String)proposal.get("proposalId"),"forced-failure",actor));
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_market_cutover_approval",Integer.class));
        assertEquals(0L,local.queryForObject("SELECT cutover_sequence FROM shared_market_consumer_state",Long.class));
        assertNull(local.queryForObject("SELECT consumed_at FROM shared_market_cutover_proposal",Timestamp.class));
    }

    /** Real worker + real registration store; only indicator/scoring and wallet
     * collaborators are controlled. Each instance has its OWN JVM stripe locks. */
    CandleClosedAnalysisWorker realWorker(CountDownLatch registered,CountDownLatch release) {
        var technical=mock(com.crypto.indicator.service.TechnicalIndicatorService.class);
        when(technical.calculateAndPersist(any(),any(),any())).thenAnswer(i->{
            var indicator=new com.crypto.domain.TechnicalIndicator();
            indicator.setSymbol(i.getArgument(0));indicator.setIntervalCode(i.getArgument(1));
            indicator.setCandleOpenTime(i.getArgument(2));return Optional.of(indicator);
        });
        var quality=mock(com.crypto.service.CandleDataQualityService.class);
        when(quality.validate(any(),any())).thenReturn(new com.crypto.dto.CandleDataQualityResult(true,300,300,0,0,List.of()));
        var signals=mock(com.crypto.repository.TradeSignalRepository.class);
        when(signals.existsBySymbolAndIntervalAndCandleOpenTime(any(),any(),any())).thenAnswer(i->local.queryForObject("SELECT COUNT(*) FROM test_signal WHERE candle_open_time=?",Integer.class,Timestamp.from((Instant)i.getArgument(2)))>0);
        var scoring=mock(com.crypto.service.AnalysisService.class);
        var paper=mock(com.crypto.service.PaperTradingService.class);
        var store=new com.crypto.execution.processing.SignalProcessingStore(local,manager);
        var transaction=new org.springframework.transaction.support.TransactionTemplate(manager);
        when(scoring.analyzeForProcessing(any(),any(),anyLong())).thenAnswer(i->{
            com.crypto.domain.TechnicalIndicator indicator=i.getArgument(0);
            long event=i.getArgument(2);var signal=new com.crypto.domain.TradeSignal();
            signal.setId(700L);signal.setSymbol("BTCUSDT");signal.setInterval("1m");signal.setCandleOpenTime(indicator.getCandleOpenTime());
            transaction.executeWithoutResult(t->{local.update("INSERT INTO test_signal(id,candle_open_time) VALUES(700,?)",Timestamp.from(indicator.getCandleOpenTime()));store.register(signal,com.crypto.execution.processing.ProcessingOrigin.WORKER,event);});
            registered.countDown();assertTrue(release.await(8,TimeUnit.SECONDS));return signal;
        });
        when(scoring.analyzeRecovered(any(),any())).thenAnswer(i->{
            com.crypto.domain.TechnicalIndicator indicator=i.getArgument(0);
            var signal=new com.crypto.domain.TradeSignal();signal.setId(701L);
            signal.setSymbol(indicator.getSymbol());signal.setInterval(indicator.getIntervalCode());
            signal.setCandleOpenTime(indicator.getCandleOpenTime());
            local.update("INSERT INTO test_signal(id,candle_open_time) VALUES(701,?)",Timestamp.from(indicator.getCandleOpenTime()));
            return signal;
        });
        when(paper.processSharedSignal(any(),anyLong())).thenAnswer(i->{
            long event=i.getArgument(1);store.requireSourceOwner(700,event);
            var claim=store.claim(700,false);assertNotNull(claim);
            transaction.executeWithoutResult(t->{store.requireSourceOwner(700,event);local.update("INSERT INTO effects(id) VALUES(DEFAULT)");store.complete(claim,null);});
            return Optional.empty();
        });
        var actual=new CandleClosedAnalysisWorker(technical,scoring,paper,quality,signals,new com.crypto.indicator.event.CandleAnalysisExecutionCoordinator(),local);
        return actual;
    }
    void realWorkSchema() throws Exception {
        local.execute("DROP TABLE signal_processing_work");
        for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V86__fix_127_signal_processing.sql")).replaceAll("(?m)^\\s*--.*$", "").split(";"))if(!sql.isBlank())local.execute(sql);
        local.execute("ALTER TABLE signal_processing_work ADD source_event_id BIGINT NULL");
        local.execute("CREATE TABLE test_signal(id BIGINT PRIMARY KEY,candle_open_time TIMESTAMP(6) UNIQUE)");
    }
    void closed(long seq,boolean live) {
        event(seq,now);feed.update("UPDATE market_data_stream_event SET closed=true,candle_close_time=?,source=?,classification=? WHERE id=?",
            Timestamp.from(now.minusSeconds(1)),live?"LIVE_WEBSOCKET":"STARTUP_RECOVERY",live?"LIVE":"HISTORICAL_ONLY",seq);
        consumer.discover();consumer.processNext("BTCUSDT");
    }
    void concurrentRealWorkers(boolean recoveryFirst) throws Exception {
        realWorkSchema();closed(1,!recoveryFirst);closed(2,recoveryFirst);
        // Keep the same-candle duplicate coverage, plus a DISTINCT expired candle.
        // Merely making the fresh duplicate due cannot exercise historical selection.
        closed(3,false);
        Instant expiredOpen=now.minusSeconds(240), expiredClose=now.minusSeconds(181);
        local.update("UPDATE shared_market_event_delivery SET candle_open_time=?,candle_close_time=?,analysis_not_before=? WHERE source_event_id=3",
            Timestamp.from(expiredOpen),Timestamp.from(expiredClose),Timestamp.from(now.minusSeconds(1)));
        String historicalOwnerBefore=local.queryForObject("SELECT owner_token FROM shared_market_event_delivery WHERE source_event_id=3",String.class);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var first=new SharedMarketConsumer(source,local,coins,protect,observer,realWorker(entered,release),Runnable::run,Runnable::run,manager);
        // Separate consumer AND coordinator objects: no shared JVM lane or candle lock.
        var contender=spy(realWorker(new CountDownLatch(1),new CountDownLatch(0)));
        var second=new SharedMarketConsumer(source,local,coins,protect,observer,contender,Runnable::run,Runnable::run,manager);
        var threads=Executors.newSingleThreadExecutor();
        long liveId=recoveryFirst?2:1, historicalId=recoveryFirst?1:2;
        try {
            Future<?> running=threads.submit(first::dispatchAnalysis);
            assertTrue(entered.await(8,TimeUnit.SECONDS),"actual worker must commit owned registration before contender");
            // The duplicate remains due; the distinct expired candle is also eligible.
            // The historical dispatcher must respect the other instance's live owner.
            local.update("UPDATE shared_market_event_delivery SET analysis_not_before=? WHERE source_event_id=?",Timestamp.from(now.minusSeconds(1)),historicalId);
            assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE source_event_id=3 AND phase=4 AND closed=1 AND analysis_status='HISTORICAL_DEFERRED' AND candle_close_time<? AND analysis_not_before<?",Integer.class,
                Timestamp.from(Instant.now().minusSeconds(SharedEventPolicy.closeGraceSeconds("1m"))),Timestamp.from(Instant.now())));
            second.dispatchHistoricalAnalysis();
            verify(contender,never()).processShared(any(),any(),anyBoolean(),anyLong());
            assertEquals("HISTORICAL_DEFERRED",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery WHERE source_event_id=3",String.class));
            assertEquals(historicalOwnerBefore,local.queryForObject("SELECT owner_token FROM shared_market_event_delivery WHERE source_event_id=3",String.class));
            assertEquals(liveId,local.queryForObject("SELECT source_event_id FROM signal_processing_work WHERE signal_id=700",Long.class));
            assertEquals("HISTORICAL_DEFERRED",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery WHERE source_event_id=?",String.class,historicalId));
            assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
            release.countDown();running.get(8,TimeUnit.SECONDS);
            // Positive control: the SAME due candidate now reaches the real worker.
            second.dispatchHistoricalAnalysis();
            verify(contender,times(1)).processShared(argThat(e->expiredOpen.equals(e.openTime())),eq(expiredClose),eq(false),eq(3L));
            assertEquals("HISTORICAL_ONLY",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery WHERE source_event_id=3",String.class));
            assertNotNull(local.queryForObject("SELECT analysis_completed_at FROM shared_market_event_delivery WHERE source_event_id=3",Timestamp.class));
            assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM test_signal WHERE id=701 AND candle_open_time=?",Integer.class,Timestamp.from(expiredOpen)));
            assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM signal_processing_work",Integer.class));
            assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
            assertEquals("COMPLETED",local.queryForObject("SELECT status FROM signal_processing_work WHERE signal_id=700",String.class));
            assertEquals(liveId,local.queryForObject("SELECT source_event_id FROM signal_processing_work WHERE signal_id=700",Long.class));
            assertEquals("COVERED_BY_LIVE",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery WHERE source_event_id=?",String.class,historicalId));
        } finally {release.countDown();threads.shutdownNow();first.stop();second.stop();}
    }
    @Test void twoInstancesRecoveryFirstPersistOnlyLiveWorkOwner() throws Exception {concurrentRealWorkers(true);}
    @Test void twoInstancesLiveFirstPersistOnlyLiveWorkOwner() throws Exception {concurrentRealWorkers(false);}
    @Test void realWorkerExpiredHistoricalCloseNeverRegistersWalletWork() throws Exception {
        realWorkSchema();closed(1,false);
        local.update("UPDATE shared_market_event_delivery SET analysis_not_before=?",Timestamp.from(now.minusSeconds(1)));
        var actual=new SharedMarketConsumer(source,local,coins,protect,observer,realWorker(new CountDownLatch(1),new CountDownLatch(0)),Runnable::run,Runnable::run,manager);
        local.update("UPDATE shared_market_event_delivery SET candle_close_time=?",Timestamp.from(now.minusSeconds(100)));
        try {actual.dispatchHistoricalAnalysis();
            assertEquals("HISTORICAL_ONLY",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class));
            assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM test_signal",Integer.class));
            assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM signal_processing_work",Integer.class));
            assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
        } finally {actual.stop();}
    }

    // FIX-138 regression cases: scheduling changes must not weaken execution ownership.
    void queued(long id,String interval,Instant close,String delivery,String analysisStatus) {
        local.update("""
            INSERT INTO shared_market_event_delivery(source_event_id,symbol,symbol_sequence,interval_code,
              candle_open_time,candle_close_time,closed,received_at,source_created_at,price,source,classification,
              phase,status,analysis_status)
            VALUES(?,'BTCUSDT',?,?,?, ?,true,?,?,10,'LIVE_WEBSOCKET','LIVE',4,?,?)
            """,id,id,interval,Timestamp.from(close.minusSeconds(60)),Timestamp.from(close),Timestamp.from(now),Timestamp.from(now),delivery,analysisStatus);
    }
    String analysisStatus(long id) {
        return local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery WHERE source_event_id=?",String.class,id);
    }
    @Test void fix138FreshCloseBypassesOldPendingWithoutExecutingOldWork() {
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","PENDING");
        queued(2,"1m",now.minusSeconds(1),"COMPLETED","PENDING");
        consumer.dispatchAnalysis();
        verify(worker).processShared(any(),any(),eq(true),eq(2L));
        verify(worker,never()).processShared(any(),any(),anyBoolean(),eq(1L));
        assertEquals("PENDING",analysisStatus(1));
        assertEquals("COMPLETED",analysisStatus(2));
    }
    @Test void fix138ExpiredOriginallyLiveCloseIsForcedHistorical() {
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","PENDING");
        consumer.dispatchAnalysis();verifyNoInteractions(worker);
        consumer.dispatchHistoricalAnalysis();
        verify(worker).processShared(any(),any(),eq(false),eq(1L));
    }
    @Test void fix138HistoricalClockYieldsToFreshSameLane() {
        queued(1,"1m",now.minusSeconds(3600),"HISTORICAL_ONLY","HISTORICAL_DEFERRED");
        queued(2,"1m",now.minusSeconds(1),"COMPLETED","PENDING");
        consumer.dispatchHistoricalAnalysis();verifyNoInteractions(worker);
        assertEquals("HISTORICAL_DEFERRED",analysisStatus(1));
        consumer.dispatchAnalysis();verify(worker).processShared(any(),any(),eq(true),eq(2L));
    }
    @Test void fix138CompletedAnalysisOnlyReviewIsRetainedWithoutBlockingFreshCandle() {
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","REVIEW_REQUIRED");
        queued(2,"1m",now.minusSeconds(1),"COMPLETED","PENDING");
        local.update("UPDATE shared_market_event_delivery SET analysis_completed_at=?,last_error='original failure' WHERE source_event_id=1",Timestamp.from(now));
        consumer.dispatchAnalysis();
        assertEquals("ANALYSIS_ONLY_REVIEW",analysisStatus(1));
        assertEquals("original failure",local.queryForObject("SELECT last_error FROM shared_market_event_delivery WHERE source_event_id=1",String.class));
        verify(worker).processShared(any(),any(),eq(true),eq(2L));
        verify(worker,never()).processShared(any(),any(),anyBoolean(),eq(1L));
    }
    @Test void fix138ReviewWithProcessingWorkStillBlocks() {
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","REVIEW_REQUIRED");
        queued(2,"1m",now.minusSeconds(1),"COMPLETED","PENDING");
        local.update("UPDATE shared_market_event_delivery SET analysis_completed_at=? WHERE source_event_id=1",Timestamp.from(now));
        local.update("INSERT INTO signal_processing_work(signal_id,status,source_event_id) VALUES(99,'PENDING',1)");
        consumer.dispatchAnalysis();verifyNoInteractions(worker);
        assertEquals("REVIEW_REQUIRED",analysisStatus(1));
        assertEquals("PENDING",analysisStatus(2));
    }
    @Test void fix138ReviewWithWalletEvidenceStillBlocksWithoutWorkRow() {
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","REVIEW_REQUIRED");
        queued(2,"1m",now.minusSeconds(1),"COMPLETED","PENDING");
        local.update("UPDATE shared_market_event_delivery SET analysis_completed_at=? WHERE source_event_id=1",Timestamp.from(now));
        local.update("INSERT INTO trade_signal SELECT 99,symbol,interval_code,candle_open_time FROM shared_market_event_delivery WHERE source_event_id=1");
        local.update("INSERT INTO wallet_trade VALUES(10,99)");
        consumer.dispatchAnalysis();verifyNoInteractions(worker);
        assertEquals("REVIEW_REQUIRED",analysisStatus(1));
    }
    @Test void fix138ReviewWithoutCompletionMarkerIsNeverBypassed() {
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","REVIEW_REQUIRED");
        queued(2,"1m",now.minusSeconds(1),"COMPLETED","PENDING");
        consumer.dispatchAnalysis();consumer.dispatchHistoricalAnalysis();verifyNoInteractions(worker);
        assertEquals("REVIEW_REQUIRED",analysisStatus(1));
    }
    @Test void fix138HigherSequenceRunningOwnerBlocksOldHistoricalAcrossInstances() {
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","PENDING");
        queued(2,"1m",now.minusSeconds(1),"COMPLETED","RUNNING");
        var second=newConsumer();
        try {second.dispatchHistoricalAnalysis();verifyNoInteractions(worker);
            assertEquals("PENDING",analysisStatus(1));
        } finally {second.stop();}
    }
    @Test void fix138BusyHistoricalPoolDoesNotOccupyLiveExecutorOrDuplicateLane() {
        var historicalTask=new java.util.concurrent.atomic.AtomicReference<Runnable>();
        var periodConfig=new com.crypto.client.config.binance.BinanceMarketDataProperties();
        periodConfig.setIntervals(List.of("1m","5m"));
        consumer.stop();
        consumer=new SharedMarketConsumer(source,local,coins,protect,observer,worker,Runnable::run,historicalTask::set,manager);
        org.springframework.test.util.ReflectionTestUtils.setField(consumer,"intervals",periodConfig);
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","PENDING");
        consumer.dispatchHistoricalAnalysis();assertNotNull(historicalTask.get());
        queued(2,"1m",now.minusSeconds(1),"COMPLETED","PENDING");
        queued(3,"5m",now.minusSeconds(1),"COMPLETED","PENDING");
        consumer.dispatchAnalysis();
        verify(worker).processShared(any(),any(),eq(true),eq(3L));
        verify(worker,never()).processShared(any(),any(),anyBoolean(),eq(2L));
        historicalTask.get().run();
        consumer.dispatchAnalysis();verify(worker).processShared(any(),any(),eq(true),eq(2L));
    }
    @Test void fix138RejectedUnstartedTaskReleasesOnlyItsOwnClaim() {
        consumer.stop();
        consumer=new SharedMarketConsumer(source,local,coins,protect,observer,worker,
            r->{throw new RejectedExecutionException("pool full");},Runnable::run,manager);
        queued(1,"1m",now.minusSeconds(1),"COMPLETED","PENDING");
        consumer.dispatchAnalysis();verifyNoInteractions(worker);
        assertEquals("PENDING",analysisStatus(1));
        assertNull(local.queryForObject("SELECT analysis_started_at FROM shared_market_event_delivery WHERE source_event_id=1",Timestamp.class));
    }
    @Test void fix138HistoricalSelectionRespectsNotBeforeAndCannotTakeFreshRecovery() {
        queued(1,"1m",now.minusSeconds(1),"HISTORICAL_ONLY","HISTORICAL_DEFERRED");
        queued(2,"1m",now.minusSeconds(3600),"HISTORICAL_ONLY","HISTORICAL_DEFERRED");
        local.update("UPDATE shared_market_event_delivery SET analysis_not_before=? WHERE source_event_id=2",Timestamp.from(now.plusSeconds(300)));
        consumer.dispatchHistoricalAnalysis();consumer.dispatchAnalysis();verifyNoInteractions(worker);
    }

    /** FIX-140: real dispatcher, worker, store, coordinator and H2 transactions.
     * Only scoring, repositories and the first wallet-lock error are controlled.
     * Wait for the persisted database deadline; never manually make the retry due. */
    @Test void fix140DeliveryReclaimsDueRetryResumesSameSignalAndNeverUsesHistoryPool() throws Exception {
        realWorkSchema();
        queued(140,"1m",now.minusSeconds(1),"COMPLETED","PENDING");
        Instant open=local.queryForObject("SELECT candle_open_time FROM shared_market_event_delivery WHERE source_event_id=140",Timestamp.class).toInstant();
        var signal=new com.crypto.domain.TradeSignal();signal.setId(740L);
        signal.setSymbol("BTCUSDT");signal.setInterval("1m");signal.setCandleOpenTime(open);
        var signals=mock(com.crypto.repository.TradeSignalRepository.class);
        when(signals.findById(740L)).thenReturn(Optional.of(signal));
        var store=new com.crypto.execution.processing.SignalProcessingStore(local,manager);
        var freshness=mock(com.crypto.execution.processing.SignalProcessingFreshness.class);
        when(freshness.eligible(any(),any())).thenReturn(true);
        var positions=mock(com.crypto.wallet.repository.WalletManagedPositionRepository.class);
        var coordinator=new com.crypto.execution.processing.SignalProcessingCoordinator(store,signals,positions,freshness,manager);
        var wallet=mock(com.crypto.wallet.service.WalletTransactionCoordination.class);
        org.springframework.test.util.ReflectionTestUtils.setField(coordinator,"walletCoordination",wallet);
        doThrow(new org.springframework.dao.QueryTimeoutException("controlled initial symbol lock timeout"))
            .doNothing().when(wallet).symbol("BTCUSDT");
        var technical=mock(com.crypto.indicator.service.TechnicalIndicatorService.class);
        var indicator=new com.crypto.domain.TechnicalIndicator();indicator.setSymbol("BTCUSDT");indicator.setIntervalCode("1m");indicator.setCandleOpenTime(open);
        when(technical.calculateAndPersist("BTCUSDT","1m",open)).thenReturn(Optional.of(indicator));
        var quality=mock(com.crypto.service.CandleDataQualityService.class);
        when(quality.validate("BTCUSDT","1m")).thenReturn(new com.crypto.dto.CandleDataQualityResult(true,300,300,0,0,List.of()));
        var scoring=mock(com.crypto.service.AnalysisService.class);
        var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        when(scoring.analyzeForProcessing(any(),any(),eq(140L))).thenAnswer(call->{
            tx.executeWithoutResult(t->{
                local.update("INSERT INTO trade_signal(id,symbol,interval_code,candle_open_time) VALUES(740,'BTCUSDT','1m',?)",Timestamp.from(open));
                store.register(signal,com.crypto.execution.processing.ProcessingOrigin.WORKER,140L);
            });
            return signal;
        });
        var paper=mock(com.crypto.service.PaperTradingService.class);
        when(paper.processSharedSignal(same(signal),eq(140L))).thenAnswer(call->coordinator.process(740L,false,x->{
            local.update("INSERT INTO effects(id) VALUES(DEFAULT)");return Optional.empty();
        },140L));
        var actualWorker=new CandleClosedAnalysisWorker(technical,scoring,paper,quality,signals,
            new com.crypto.indicator.event.CandleAnalysisExecutionCoordinator(),local);
        var liveSubmissions=new java.util.concurrent.atomic.AtomicInteger();
        var historySubmissions=new java.util.concurrent.atomic.AtomicInteger();
        var actual=new SharedMarketConsumer(source,local,coins,protect,observer,actualWorker,
            task->{liveSubmissions.incrementAndGet();task.run();},
            task->{historySubmissions.incrementAndGet();task.run();},manager);
        try {
            actual.dispatchAnalysis();
            assertEquals("EXECUTION_RETRY",analysisStatus(140));
            Timestamp workDue=local.queryForObject("SELECT next_attempt_at FROM signal_processing_work WHERE signal_id=740",Timestamp.class);
            assertEquals(workDue,local.queryForObject("SELECT analysis_not_before FROM shared_market_event_delivery WHERE source_event_id=140",Timestamp.class));
            assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
            actual.dispatchHistoricalAnalysis();assertEquals(0,historySubmissions.get());
            // Use an explicit future delivery deadline only for this negative control,
            // then restore the untouched persisted work deadline for the actual wait.
            local.update("UPDATE shared_market_event_delivery SET analysis_not_before=? WHERE source_event_id=140",Timestamp.from(Instant.now().plusSeconds(10)));
            actual.dispatchAnalysis();assertEquals(1,liveSubmissions.get());
            local.update("UPDATE shared_market_event_delivery SET analysis_not_before=? WHERE source_event_id=140",workDue);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
            while(!Instant.now().isAfter(workDue.toInstant())) {
                assertTrue(System.nanoTime()<deadline,"persisted retry deadline must become due");
                Thread.sleep(10);
            }
            actual.dispatchAnalysis();
            assertEquals("COMPLETED",analysisStatus(140));
            assertEquals("COMPLETED",local.queryForObject("SELECT status FROM signal_processing_work WHERE signal_id=740",String.class));
            assertEquals(2,local.queryForObject("SELECT attempts FROM signal_processing_work WHERE signal_id=740",Integer.class));
            assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM signal_processing_work",Integer.class));
            assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
            assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM trade_signal WHERE id=740",Integer.class));
            assertEquals(2,liveSubmissions.get());assertEquals(0,historySubmissions.get());
            verify(paper,times(2)).processSharedSignal(same(signal),eq(140L));
            verify(scoring,times(1)).analyzeForProcessing(any(),any(),eq(140L));
            verify(scoring,never()).analyzeRecovered(any(),any());
            verify(technical,times(1)).calculateAndPersist("BTCUSDT","1m",open);
            verify(freshness).eligible(any(),any());
            actual.dispatchAnalysis();assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
        } finally {actual.stop();}
    }

    // FIX-141 Phase 2 reconciliation: SharedAnalysisDispatcher#reconcileAnalysisOnlyReviews
    // and #reconcileOwnerTimeoutReviews are package-private collaborators of this consumer,
    // not exposed on SharedMarketConsumer itself, so these tests reach the dispatcher the
    // same way enableValidation() above reaches private consumer state: reflection on the
    // already-wired instance, never a second ad-hoc dispatcher.
    //
    // These tests validate lock ordering, isolation correctness and scan fairness against
    // H2. They do NOT validate behavior against real MySQL row-lock semantics (MySQL's
    // locking, isolation level and FOR UPDATE wait-queue behavior differ from H2's), which
    // the reviewer explicitly required in addition to this harness; that validation still
    // needs to run against an actual MySQL instance outside this sandbox.
    SharedAnalysisDispatcher dispatcher() {
        return (SharedAnalysisDispatcher) org.springframework.test.util.ReflectionTestUtils.getField(consumer, "analysisDispatch");
    }
    /** Polls INFORMATION_SCHEMA.SESSIONS (same mechanism as twoConsumersCannotCommitTheSameProtectionEffect
     * above) until some session is observed genuinely blocked on a database lock, or the deadline
     * passes. Used in place of a fixed sleep to prove a thread actually reached and is waiting on
     * a lock attempt, not merely that "enough time probably passed." */
    boolean awaitBlockedSession(long seconds) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        while(System.nanoTime()<deadline) {
            if(blockedSessions()>0)return true;
            Thread.onSpinWait();
        }
        return blockedSessions()>0;
    }

    @Test void fix141ReconcileAnalysisOnlyReviewsIsolatesClearRowDirectly() {
        // Exercises reconcileAnalysisOnlyReviews() standalone, not via claim()'s own
        // per-lane isolation loop (already covered by fix138CompletedAnalysisOnlyReviewIsRetainedWithoutBlockingFreshCandle).
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","REVIEW_REQUIRED");
        local.update("UPDATE shared_market_event_delivery SET analysis_completed_at=?,last_error='original failure' WHERE source_event_id=1",Timestamp.from(now));
        dispatcher().reconcileAnalysisOnlyReviews();
        assertEquals("ANALYSIS_ONLY_REVIEW",analysisStatus(1));
        assertEquals("original failure",local.queryForObject("SELECT last_error FROM shared_market_event_delivery WHERE source_event_id=1",String.class));
    }
    @Test void fix141ReconcileAnalysisOnlyReviewsSkipsRowsWithDurableEvidence() {
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","REVIEW_REQUIRED");
        local.update("UPDATE shared_market_event_delivery SET analysis_completed_at=? WHERE source_event_id=1",Timestamp.from(now));
        local.update("INSERT INTO trade_signal SELECT 99,symbol,interval_code,candle_open_time FROM shared_market_event_delivery WHERE source_event_id=1");
        local.update("INSERT INTO wallet_trade VALUES(10,99)");
        dispatcher().reconcileAnalysisOnlyReviews();
        assertEquals("REVIEW_REQUIRED",analysisStatus(1));
    }
    @Test void fix141ReconcileAnalysisOnlyReviewsAdvancesPastStuckRowsInsteadOfStarvingLaterOnes() {
        // Review correction: the old fixed "ORDER BY symbol_sequence LIMIT 64" re-selected
        // the same leading rows every invocation, so a row that could never clear (real
        // evidence) permanently starved every later-ordered eligible row. 64 rows here have
        // durable evidence and can never clear; row 65 has none and is immediately clearable.
        for(long id=1;id<=64;id++) {
            queued(id,"1m",now.minusSeconds(3600+id),"COMPLETED","REVIEW_REQUIRED");
            local.update("UPDATE shared_market_event_delivery SET analysis_completed_at=? WHERE source_event_id=?",Timestamp.from(now),id);
            local.update("INSERT INTO signal_processing_work(signal_id,status,source_event_id) VALUES(?,'PENDING',?)",1000+id,id);
        }
        queued(65,"1m",now.minusSeconds(1),"COMPLETED","REVIEW_REQUIRED");
        local.update("UPDATE shared_market_event_delivery SET analysis_completed_at=? WHERE source_event_id=65",Timestamp.from(now));

        dispatcher().reconcileAnalysisOnlyReviews();
        assertEquals("REVIEW_REQUIRED",analysisStatus(65),"the first pass is bounded to 64 rows and none of ids 1-64 can ever clear");
        dispatcher().reconcileAnalysisOnlyReviews();
        assertEquals("ANALYSIS_ONLY_REVIEW",analysisStatus(65),"the cursor must advance past the stuck rows to reach row 65 on the next pass");
        for(long id=1;id<=64;id++)assertEquals("REVIEW_REQUIRED",analysisStatus(id),"rows with real evidence must never be isolated, id="+id);
    }
    /** Deterministic lock-order regression, replacing an earlier version that just ran claim()
     * and reconcileAnalysisOnlyReviews() concurrently with no coordination: both internally
     * catch their own exceptions, so an actual deadlock (resolved by H2's lock-wait timeout)
     * would have been swallowed and logged rather than surfaced, and the test could pass
     * whether or not the fix was real. This version forces the actual interleaving: thread A
     * holds the symbol lock (claim()'s own first step) while the real reconcileAnalysisOnlyReviews()
     * runs as "B" against the same row. If B also locks symbol -> delivery (the fix), B simply
     * queues behind A's symbol lock, A's subsequent delivery-lock acquisition is uncontended,
     * and both finish quickly. If B instead locked delivery -> symbol (the original defect), B
     * would already hold the delivery lock by the time A is released to acquire it, so A would
     * block on B's delivery lock while B blocks on A's symbol lock - a genuine, deterministic
     * ABBA deadlock, which the explicit aDone.await(...) assertion below turns into a hard
     * test failure instead of a silent pass. */
    @Test @Timeout(20) void fix141ReconcileLocksSymbolBeforeDeliveryDeterministic() throws Exception {
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","REVIEW_REQUIRED");
        local.update("UPDATE shared_market_event_delivery SET analysis_completed_at=? WHERE source_event_id=1",Timestamp.from(now));
        var aHasSymbolLock=new CountDownLatch(1);
        var aMayProceedToDelivery=new CountDownLatch(1);
        var aDone=new CountDownLatch(1);
        var aFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var threads=Executors.newFixedThreadPool(2);
        try {
            var txA=new org.springframework.transaction.support.TransactionTemplate(manager);
            Future<?> a=threads.submit(()->{
                try {
                    txA.executeWithoutResult(s->{
                        local.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE","BTCUSDT");
                        aHasSymbolLock.countDown();
                        try {assertTrue(aMayProceedToDelivery.await(15,TimeUnit.SECONDS),"test signal to proceed never arrived");}
                        catch(InterruptedException e) {throw new RuntimeException(e);}
                        // Mirrors claim()'s second step: delivery row FOR UPDATE, same row B targets.
                        local.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=1 FOR UPDATE");
                    });
                } catch(Throwable t) {aFailure.set(t);}
                finally {aDone.countDown();}
            });
            assertTrue(aHasSymbolLock.await(5,TimeUnit.SECONDS),"A must hold the symbol lock before B starts");
            Future<?> b=threads.submit(dispatcher()::reconcileAnalysisOnlyReviews);
            // Wait for an OBSERVED lock wait rather than a fixed sleep: B must actually reach a
            // lock attempt and be genuinely blocked on it (A holds the only lock in play so far)
            // before A is released. A fixed sleep only proves time passed, not that B got there.
            assertTrue(awaitBlockedSession(5),"B must actually be blocked on a database lock (the symbol lock A holds) before A is released");
            aMayProceedToDelivery.countDown();
            assertTrue(aDone.await(10,TimeUnit.SECONDS),
                "A must not be blocked waiting on B's delivery lock - B must not hold the delivery row while waiting on the symbol lock (lock order regression)");
            assertNull(aFailure.get(),"A must complete without error: "+aFailure.get());
            b.get(10,TimeUnit.SECONDS);
        } finally {threads.shutdownNow();}
        assertEquals("ANALYSIS_ONLY_REVIEW",analysisStatus(1));
    }
    @Test @Timeout(20) void fix141OwnerTimeoutReconciliationLocksSymbolBeforeDeliveryDeterministic() throws Exception {
        // Same deterministic construction as fix141ReconcileLocksSymbolBeforeDeliveryDeterministic,
        // against reconcileOwnerTimeoutReviews() instead: it independently acquires the same two
        // locks in the same order and was found with the same reversed-order defect.
        queued(1,"1m",now.minusSeconds(60),"COMPLETED","REVIEW_REQUIRED");
        local.update("UPDATE shared_market_event_delivery SET analysis_started_at=? WHERE source_event_id=1",Timestamp.from(now.minusSeconds(7200)));
        var dispatch=dispatcher();
        dispatch.ownerTimeoutReconciliationEnabled(true);
        dispatch.ownerTimeoutQuietSeconds(3600);
        var aHasSymbolLock=new CountDownLatch(1);
        var aMayProceedToDelivery=new CountDownLatch(1);
        var aDone=new CountDownLatch(1);
        var aFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var threads=Executors.newFixedThreadPool(2);
        try {
            var txA=new org.springframework.transaction.support.TransactionTemplate(manager);
            Future<?> a=threads.submit(()->{
                try {
                    txA.executeWithoutResult(s->{
                        local.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE","BTCUSDT");
                        aHasSymbolLock.countDown();
                        try {assertTrue(aMayProceedToDelivery.await(15,TimeUnit.SECONDS),"test signal to proceed never arrived");}
                        catch(InterruptedException e) {throw new RuntimeException(e);}
                        local.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=1 FOR UPDATE");
                    });
                } catch(Throwable t) {aFailure.set(t);}
                finally {aDone.countDown();}
            });
            assertTrue(aHasSymbolLock.await(5,TimeUnit.SECONDS),"A must hold the symbol lock before B starts");
            Future<?> b=threads.submit(dispatch::reconcileOwnerTimeoutReviews);
            assertTrue(awaitBlockedSession(5),"B must actually be blocked on a database lock (the symbol lock A holds) before A is released");
            aMayProceedToDelivery.countDown();
            assertTrue(aDone.await(10,TimeUnit.SECONDS),
                "A must not be blocked waiting on B's delivery lock - B must not hold the delivery row while waiting on the symbol lock (lock order regression)");
            assertNull(aFailure.get(),"A must complete without error: "+aFailure.get());
            b.get(10,TimeUnit.SECONDS);
        } finally {threads.shutdownNow();}
        // Diagnostic-only: must still never mutate, lock-order aside.
        assertEquals("REVIEW_REQUIRED",analysisStatus(1));
    }
    /** Meta-test: there is no reversed-order production code left to call (that was the bug,
     * now fixed), so this proves the SYNCHRONIZATION TECHNIQUE used by the two deterministic
     * lock-order tests above is actually capable of catching a reversed order, by manufacturing
     * the reversed side directly with raw SQL - thread "R" locks delivery first, then symbol,
     * the opposite of claim()'s order - against thread "A" which locks symbol first, then
     * delivery (the correct order, same as claim() and the fixed dispatcher methods). Each
     * thread is released to attempt its second (conflicting) lock only once the other is
     * confirmed to hold its first lock, so both end up wanting what the other already holds: a
     * genuine ABBA deadlock. If this test ever stopped producing a real failure on one side, it
     * would mean this harness technique is no longer sensitive to lock order, and the two
     * "Deterministic" tests above could no longer be trusted as regression coverage. */
    @Test @Timeout(20) void fix141LockOrderHarnessDetectsReversedOrderAsAGenuineConflict() throws Exception {
        queued(1,"1m",now.minusSeconds(3600),"COMPLETED","REVIEW_REQUIRED");
        var aHasSymbolLock=new CountDownLatch(1);
        var rHasDeliveryLock=new CountDownLatch(1);
        var aDone=new CountDownLatch(1);
        var rDone=new CountDownLatch(1);
        var aFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var rFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var threads=Executors.newFixedThreadPool(2);
        try {
            var txA=new org.springframework.transaction.support.TransactionTemplate(manager);
            var txR=new org.springframework.transaction.support.TransactionTemplate(manager);
            threads.submit(()->{
                try {
                    txA.executeWithoutResult(s->{
                        local.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE","BTCUSDT");
                        aHasSymbolLock.countDown();
                        try {assertTrue(rHasDeliveryLock.await(5,TimeUnit.SECONDS),"R never reached the delivery lock");}
                        catch(InterruptedException e) {throw new RuntimeException(e);}
                        // A now wants delivery, which R holds - the other half of the ABBA cycle.
                        local.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=1 FOR UPDATE");
                    });
                } catch(Throwable t) {aFailure.set(t);}
                finally {aDone.countDown();}
            });
            threads.submit(()->{
                try {
                    txR.executeWithoutResult(s->{
                        // The reversed order: delivery first, then symbol - what the original
                        // defect did.
                        local.queryForMap("SELECT * FROM shared_market_event_delivery WHERE source_event_id=1 FOR UPDATE");
                        rHasDeliveryLock.countDown();
                        try {assertTrue(aHasSymbolLock.await(5,TimeUnit.SECONDS),"A never reached the symbol lock");}
                        catch(InterruptedException e) {throw new RuntimeException(e);}
                        // R now wants symbol, which A holds - completing the ABBA cycle.
                        local.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol=? FOR UPDATE","BTCUSDT");
                    });
                } catch(Throwable t) {rFailure.set(t);}
                finally {rDone.countDown();}
            });
            // A real deadlock is broken by the database (H2's LOCK_TIMEOUT, configured at
            // 10s in this harness's datasource URL, or its own deadlock detection) - it resolves
            // by failing (at least) one side, not by hanging both forever. Both futures must
            // still resolve within the bound below.
            assertTrue(aDone.await(15,TimeUnit.SECONDS),"A must resolve (succeed or fail), not hang forever");
            assertTrue(rDone.await(15,TimeUnit.SECONDS),"R must resolve (succeed or fail), not hang forever");
            assertTrue(aFailure.get()!=null || rFailure.get()!=null,
                "reversed lock order must produce a genuine conflict - at least one side must fail "+
                "(lock-wait timeout or detected deadlock); both succeeding cleanly would mean the "+
                "two threads never actually contended for the same two locks in opposite order");
        } finally {threads.shutdownNow();}
    }
    @Test void fix141ReconcileAnalysisOnlyReviewsSkipsFailedRowAndRevisitsItAfterWrapping() {
        // Review correction: an earlier row's transaction can fail for reasons unrelated to
        // real evidence (lock-wait timeout, transient error, a constraint violation here used
        // to force it deterministically). The cursor must advance past it so later eligible
        // rows in the SAME pass still clear, the failed row must be left exactly as it was (its
        // transaction rolled back, not partially applied), and it must be revisited - and clear
        // normally - once the failure condition is gone and the cursor returns to it. The
        // existing 65-row starvation test covers rows with real evidence that are correctly
        // never cleared; this covers a row whose processing itself fails.
        queued(1,"1m",now.minusSeconds(3602),"COMPLETED","REVIEW_REQUIRED");
        local.update("UPDATE shared_market_event_delivery SET analysis_completed_at=?,last_error='row 1 original' WHERE source_event_id=1",Timestamp.from(now));
        queued(2,"1m",now.minusSeconds(3601),"COMPLETED","REVIEW_REQUIRED");
        local.update("UPDATE shared_market_event_delivery SET analysis_completed_at=?,last_error='row 2 original' WHERE source_event_id=2",Timestamp.from(now));
        // Force row 1's isolation UPDATE specifically to fail; row 1 would otherwise clear
        // exactly like row 2, so this isolates "the row's own transaction fails" from "the row
        // has real evidence."
        local.execute("ALTER TABLE shared_market_event_delivery ADD CONSTRAINT test_fix141_reject_row1 CHECK (NOT (analysis_status='ANALYSIS_ONLY_REVIEW' AND source_event_id=1))");

        dispatcher().reconcileAnalysisOnlyReviews();

        // Row 1's failed transaction must roll back cleanly: left exactly as it was.
        assertEquals("REVIEW_REQUIRED",analysisStatus(1));
        assertEquals("row 1 original",local.queryForObject("SELECT last_error FROM shared_market_event_delivery WHERE source_event_id=1",String.class));
        // Row 2, ordered after row 1 by source_event_id, must still clear in the SAME pass -
        // one row's failure must not abort the rest of the batch.
        assertEquals("ANALYSIS_ONLY_REVIEW",analysisStatus(2));

        // Remove the forced failure and confirm row 1 is revisited and clears once the cursor
        // returns to it, rather than being permanently stuck ahead of an unmoved cursor.
        local.execute("ALTER TABLE shared_market_event_delivery DROP CONSTRAINT test_fix141_reject_row1");
        dispatcher().reconcileAnalysisOnlyReviews();
        assertEquals("ANALYSIS_ONLY_REVIEW",analysisStatus(1));
    }
    @Test void fix141OwnerTimeoutReconciliationNeverMutatesStatusEvenWithNoEvidenceAfterQuietWindow() {
        // Review correction: quiet time plus absent evidence is NOT owner fencing - there is
        // no cross-JVM process-liveness/heartbeat check anywhere in this codebase, so an old
        // worker that merely stalled could still resume. Even enabled, with the quiet window
        // fully elapsed and zero durable evidence (the most favorable case for isolation),
        // this must only log a candidate and never touch analysis_status or last_error.
        queued(1,"1m",now.minusSeconds(60),"COMPLETED","REVIEW_REQUIRED");
        local.update("UPDATE shared_market_event_delivery SET analysis_started_at=? WHERE source_event_id=1",Timestamp.from(now.minusSeconds(7200)));
        var dispatch=dispatcher();
        dispatch.ownerTimeoutReconciliationEnabled(true);
        dispatch.ownerTimeoutQuietSeconds(3600);
        dispatch.reconcileOwnerTimeoutReviews();
        assertEquals("REVIEW_REQUIRED",analysisStatus(1));
        assertNull(local.queryForObject("SELECT last_error FROM shared_market_event_delivery WHERE source_event_id=1",String.class));
        assertNull(local.queryForObject("SELECT analysis_completed_at FROM shared_market_event_delivery WHERE source_event_id=1",Timestamp.class));
    }
    @Test void fix141OwnerTimeoutReconciliationDoesNothingWhenDisabled() {
        queued(1,"1m",now.minusSeconds(60),"COMPLETED","REVIEW_REQUIRED");
        local.update("UPDATE shared_market_event_delivery SET analysis_started_at=? WHERE source_event_id=1",Timestamp.from(now.minusSeconds(7200)));
        dispatcher().reconcileOwnerTimeoutReviews();
        assertEquals("REVIEW_REQUIRED",analysisStatus(1));
    }
}
