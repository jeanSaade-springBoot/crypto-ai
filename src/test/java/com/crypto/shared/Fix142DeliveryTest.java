package com.crypto.shared;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.position.service.LivePositionProtectionService;
import com.crypto.debug.monitor.service.PriceMoveMonitorService;
import com.crypto.indicator.event.CandleClosedAnalysisWorker;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** FIX-142: H2 rollback/ordering regression; real MySQL and full business parity remain release gates. */
class Fix142DeliveryTest {
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
        fix142Schema();
    }
    SharedMarketConsumer newConsumer() { return new SharedMarketConsumer(source,local,coins,protect,observer,worker,Runnable::run,Runnable::run,manager); }
    void event(long seq,Instant observed) {
        feed.update("INSERT INTO market_data_stream_event VALUES(?,'BTCUSDT',?,'1m',?,?,false,?,?,?,10,'LIVE_WEBSOCKET','LIVE')",seq,seq,
            Timestamp.from(now.minusSeconds(10)),Timestamp.from(now.plusSeconds(50)),Timestamp.from(observed),Timestamp.from(now),Timestamp.from(now));
    }
    @AfterEach void cleanup(){consumer.stop();}
    void fix142Schema() throws Exception {
        String ddl=Files.readString(Path.of("src/main/resources/db/migration/V99__fix_142_delivery_ownership.sql"))
            .replaceAll("(?m)^\\s*--.*$", "")
            .replaceAll(",\\s*ADD COLUMN", ";ALTER TABLE shared_market_consumer_state ADD COLUMN");
        for(String sql:ddl.split(";"))if(!sql.isBlank())local.execute(sql);
        // FIX-150 entry evidence is required by both protection callers; legacy rows
        // keep UNKNOWN while every new claim writes CLAIMED explicitly.
        for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V104__fix_150_protection_entry_evidence.sql")).replaceAll("(?m)^\\s*--.*$", "").split(";"))if(!sql.isBlank())local.execute(sql);
        // FIX-151: H2 fixture omits only MySQL's VIRTUAL storage keyword.
        for(String sql:Files.readString(Path.of("src/main/resources/db/migration/V105__fix_151_delivery_selection_indexes.sql")).replaceAll("(?m)^\\s*--.*$", "").replace(" VIRTUAL", "").split(";"))if(!sql.isBlank())local.execute(sql);
        local.execute("ALTER TABLE signal_processing_work ADD completed_at TIMESTAMP(6)");
        ReflectionTestUtils.setField(consumer,"fix142Enabled",true);
    }
    @Test void freshAdmissionBypassesOldRowsWithoutAdvancingCheckpoint() {
        for(int i=1;i<=200;i++)event(i,now.minusSeconds(40));
        event(201,now);consumer.discoverFresh();
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery",Integer.class));
        assertEquals(201L,local.queryForObject("SELECT source_event_id FROM shared_market_event_delivery",Long.class));
        assertEquals(0L,local.queryForObject("SELECT discovered_sequence FROM shared_market_consumer_state",Long.class));
        consumer.processNext("BTCUSDT");verify(protect,times(1)).onPrice(any(),any());
        assertEquals(4,local.queryForObject("SELECT phase FROM shared_market_event_delivery",Integer.class));
        assertEquals("NONE",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state",String.class));
    }
    @Test void higherIntervalsAdvanceNormallyWithoutProtectionMutation() {
        int sequence=1;
        for(String interval:List.of("5m","15m","1h")) {
            event(sequence,now);feed.update("UPDATE market_data_stream_event SET interval_code=?,closed=true,candle_close_time=? WHERE id=?",interval,Timestamp.from(now.minusSeconds(1)),sequence);
            consumer.discover();consumer.processNext("BTCUSDT");sequence++;
        }
        assertEquals(3,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE phase=4",Integer.class));
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE protection_started_at IS NOT NULL OR protection_completed_at IS NOT NULL OR protection_owner IS NOT NULL OR protection_outcome IS NOT NULL",Integer.class));
        verifyNoInteractions(protect);
    }
    @Test void lateEarlierObservationBlocksApplyButDoesNotStealAttempt() {
        event(101,now);consumer.discoverFresh();
        assertEquals(SharedMarketConsumer.ProtectionClaim.CLAIMED,consumer.claimProtectionAttempt("BTCUSDT",101,"later"));
        event(100,now);consumer.discoverFresh();
        assertFalse(consumer.applyProtection("BTCUSDT",101,"later"));verifyNoInteractions(protect);
        assertEquals(SharedMarketConsumer.ProtectionClaim.CLAIMED,consumer.claimProtectionAttempt("BTCUSDT",100,"earlier"));
        assertTrue(consumer.applyProtection("BTCUSDT",100,"earlier"));
        assertTrue(consumer.applyProtection("BTCUSDT",101,"later"));verify(protect,times(2)).onPrice(any(),any());
    }
    @Test void outOfSequenceOutstandingAttemptsHaveExplicitBoundAndAreNeverEvicted() {
        event(101,now);consumer.discoverFresh();consumer.claimProtectionAttempt("BTCUSDT",101,"one");
        event(100,now);consumer.discoverFresh();consumer.claimProtectionAttempt("BTCUSDT",100,"two");
        event(99,now);consumer.discoverFresh();
        assertEquals(SharedMarketConsumer.ProtectionClaim.BLOCKED,consumer.claimProtectionAttempt("BTCUSDT",99,"three"));
        assertEquals(2,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE protection_started_at IS NOT NULL AND protection_completed_at IS NULL",Integer.class));
        assertNull(local.queryForObject("SELECT protection_owner FROM shared_market_event_delivery WHERE source_event_id=99",String.class));
    }
    @Test void timedOutProtectionIsDetectedOutsideFreshWindowAndAuditedResolutionRestoresFutureTicks() {
        event(101,now);consumer.discoverFresh();consumer.claimProtectionAttempt("BTCUSDT",101,"later-owner");
        event(100,now);consumer.discoverFresh();consumer.claimProtectionAttempt("BTCUSDT",100,"earlier-owner");
        Timestamp started=Timestamp.from(now.minusSeconds(60));
        // Simulate aged durable starts without sleeping or assuming timeout proves non-execution.
        local.update("UPDATE shared_market_event_delivery SET protection_started_at=?,observed_at=?",started,started);
     // Age both copies of the observation consistently.
     // Fresh discovery must not see a conflicting immutable source identity.
     feed.update(
         "UPDATE market_data_stream_event SET observed_at=? WHERE id IN (100,101)",
         started
     );
        consumer.monitorProtectionAttempts();
        assertEquals(2,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE status='REVIEW_REQUIRED' AND protection_completed_at IS NULL",Integer.class));
        local.update("UPDATE shared_market_consumer_state SET active_delivery_state='REVIEW_REQUIRED',active_delivery_event_id=101,active_delivery_owner_token='marker-owner',active_delivery_process_owner='fenced-process'");
        event(102,now);consumer.discoverFresh();
        assertEquals(SharedMarketConsumer.ProtectionClaim.BLOCKED,consumer.claimProtectionAttempt("BTCUSDT",102,"fresh-owner"));
        for(long id:List.of(100L,101L))consumer.resolveProtection("BTCUSDT",id,id==100?"earlier-owner":"later-owner",started.toInstant(),
                "CONFIRMED_NOT_APPLIED","CONFIRMED_NO_EFFECTS","operator",
                "old process independently fenced and transactions resolved",
                "operator verified durable and in-process effects absent; old tick skipped");
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE protection_started_at IS NOT NULL AND protection_completed_at IS NULL",Integer.class));
        assertEquals(2,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit WHERE operation='PROTECTION_CONFIRMED_NOT_APPLIED'",Integer.class));
        // Neither a protection outcome nor capacity recovery silently releases phase ownership.
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state",String.class));
        assertEquals(SharedMarketConsumer.ProtectionClaim.CLAIMED,consumer.claimProtectionAttempt("BTCUSDT",102,"fresh-owner"));
        assertTrue(consumer.applyProtection("BTCUSDT",102,"fresh-owner"));verify(protect,times(1)).onPrice(any(),any());
        consumer.resolveMarker("BTCUSDT",101,"marker-owner","RESUME",null,"operator",
                "old marker process independently fenced and transactions resolved",
                "protection outcomes audited; phase zero had no observer invocation");
        consumer.processNext("BTCUSDT");
        assertEquals("NONE",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state",String.class));
        verify(protect,times(1)).onPrice(any(),any());
        assertEquals("earlier-owner",local.queryForObject("SELECT protection_owner FROM shared_market_event_delivery WHERE source_event_id=100",String.class));
        assertNotNull(local.queryForObject("SELECT last_error FROM shared_market_event_delivery WHERE source_event_id=100",String.class));
    }
    @Test void protectionResolutionRequiresExactOwnerTimeAndEffectEvidenceAndNeverReplaysCommittedAttempt() {
        event(1,now);consumer.discoverFresh();consumer.claimProtectionAttempt("BTCUSDT",1,"old-owner");
        Timestamp started=Timestamp.from(now.minusSeconds(60));
        local.update("UPDATE shared_market_event_delivery SET protection_started_at=?",started);consumer.monitorProtectionAttempts();
        String fence="old owner independently fenced; transaction resolved";
        String evidence="all position, wallet and in-process effects independently verified";
        assertThrows(IllegalStateException.class,()->consumer.resolveProtection("BTCUSDT",1,"wrong-owner",started.toInstant(),"CONFIRMED_APPLIED","COMMITTED","operator",fence,evidence));
        assertThrows(IllegalStateException.class,()->consumer.resolveProtection("BTCUSDT",1,"old-owner",started.toInstant().plusSeconds(1),"CONFIRMED_APPLIED","COMMITTED","operator",fence,evidence));
        assertThrows(IllegalArgumentException.class,()->consumer.resolveProtection("BTCUSDT",1,"old-owner",started.toInstant(),"CONFIRMED_NOT_APPLIED","ROLLED_BACK","operator",fence,evidence));
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit",Integer.class));
        consumer.resolveProtection("BTCUSDT",1,"old-owner",started.toInstant(),"CONFIRMED_APPLIED","COMMITTED","operator",fence,evidence);
        assertTrue(consumer.applyProtection("BTCUSDT",1,"old-owner"));verifyNoInteractions(protect);
        assertThrows(IllegalStateException.class,()->consumer.resolveProtection("BTCUSDT",1,"old-owner",started.toInstant(),"CONFIRMED_APPLIED","COMMITTED","operator",fence,evidence));
        assertEquals("APPLIED",local.queryForObject("SELECT protection_outcome FROM shared_market_event_delivery",String.class));
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT status FROM shared_market_event_delivery",String.class));
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit",Integer.class));
    }
    @Test void protectionResolutionRollsBackOutcomeWhenAuditCannotCommit() {
        event(1,now);consumer.discoverFresh();consumer.claimProtectionAttempt("BTCUSDT",1,"old-owner");
        Timestamp started=Timestamp.from(now.minusSeconds(60));
        local.update("UPDATE shared_market_event_delivery SET protection_started_at=?",started);consumer.monitorProtectionAttempts();
        local.execute("ALTER TABLE shared_delivery_resolution_audit ADD CONSTRAINT deny_protection_audit CHECK (operation <> 'PROTECTION_CONFIRMED_NOT_APPLIED')");
        assertThrows(RuntimeException.class,()->consumer.resolveProtection("BTCUSDT",1,"old-owner",started.toInstant(),
                "CONFIRMED_NOT_APPLIED","CONFIRMED_NO_EFFECTS","operator",
                "old owner independently fenced and all transactions resolved",
                "durable and in-process effects independently verified absent"));
        assertNull(local.queryForObject("SELECT protection_completed_at FROM shared_market_event_delivery",Timestamp.class));
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT status FROM shared_market_event_delivery",String.class));
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit",Integer.class));
    }
    @Test void protectionMonitorSurvivesInventoryFailureAndFencesOnTheNextPass() {
        event(1,now);consumer.discoverFresh();consumer.claimProtectionAttempt("BTCUSDT",1,"old-owner");
        local.update("UPDATE shared_market_event_delivery SET protection_started_at=?",Timestamp.from(now.minusSeconds(60)));
        when(coins.enabledSymbols()).thenThrow(new IllegalStateException("temporary inventory read failure"))
            .thenReturn(List.of("BTCUSDT"));
        assertDoesNotThrow(()->consumer.monitorProtectionAttempts());
        assertEquals("PROTECTION_RUNNING",local.queryForObject("SELECT status FROM shared_market_event_delivery",String.class));
        consumer.monitorProtectionAttempts();
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT status FROM shared_market_event_delivery",String.class));
        assertNull(local.queryForObject("SELECT protection_completed_at FROM shared_market_event_delivery",Timestamp.class));
    }
    @Test void freshAdmissionIsAtomicallyBoundedAcrossDuplicateAndContiguousPaths() {
        for(int i=1;i<=40;i++)event(i,now);
        consumer.discoverFresh();consumer.discover();consumer.discoverFresh();
        assertEquals(32,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery",Integer.class));
        assertEquals(32L,local.queryForObject("SELECT discovered_sequence FROM shared_market_consumer_state",Long.class));
    }
    @Test void expiredPhaseFourIsCapacityNeutralButUncertaintyIsCounted() {
        event(1,now);consumer.discoverFresh();
        local.update("UPDATE shared_market_event_delivery SET phase=4,analysis_status='PENDING',closed=true,candle_close_time=?",Timestamp.from(now.minusSeconds(600)));
        var protocol=new Fix142DeliveryProtocol(local);
        assertEquals(0,protocol.exposure("BTCUSDT","1m",Instant.now()));
        local.update("UPDATE shared_market_event_delivery SET analysis_status='REVIEW_REQUIRED'");
        assertEquals(1,protocol.exposure("BTCUSDT","1m",Instant.now()));
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery",Integer.class));
    }
    @Test void markerFenceDoesNotDisableIndependentProtection() {
        event(1,now);consumer.discoverFresh();
        local.update("UPDATE shared_market_consumer_state SET active_delivery_state='REVIEW_REQUIRED',active_delivery_event_id=999,active_delivery_owner_token='old'");
        assertEquals(SharedMarketConsumer.ProtectionClaim.CLAIMED,consumer.claimProtectionAttempt("BTCUSDT",1,"independent"));
        assertTrue(consumer.applyProtection("BTCUSDT",1,"independent"));
        verify(protect,times(1)).onPrice(any(),any());
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state",String.class));
    }
    @Test void exactCompletedChainAcknowledgesMissingDeliveryCompletionWithoutReplay() {
        event(1,now);consumer.discoverFresh();
        local.update("UPDATE shared_market_event_delivery SET phase=4,analysis_status='REVIEW_REQUIRED',analysis_completed_at=NULL,owner_token='old'");
        var candle=local.queryForObject("SELECT candle_open_time FROM shared_market_event_delivery",Timestamp.class);
        local.update("INSERT INTO trade_signal VALUES(10,'BTCUSDT','1m',?)",candle);
        local.update("INSERT INTO signal_processing_work(signal_id,source_event_id,symbol,interval_code,candle_open_time,status,completed_at) VALUES(10,1,'BTCUSDT','1m',?,'COMPLETED',CURRENT_TIMESTAMP(6))",candle);
        var dispatcher=(SharedAnalysisDispatcher)ReflectionTestUtils.getField(consumer,"analysisDispatch");dispatcher.acknowledgmentEnabled(true);
        dispatcher.reconcileCompletedWorkAcknowledgments();
        assertEquals("WORK_ACKNOWLEDGED",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class));
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit",Integer.class));
        verifyNoInteractions(protect);verify(worker,never()).processShared(any(),any(),anyBoolean(),anyLong());
    }
    @Test void unknownApplyFailureKeepsDurableAttemptAndNeverRetriesBusiness() {
        event(1,now);consumer.discoverFresh();
        assertEquals(SharedMarketConsumer.ProtectionClaim.CLAIMED,consumer.claimProtectionAttempt("BTCUSDT",1,"first"));
        doAnswer(invocation->{local.update("INSERT INTO effects(id) VALUES(DEFAULT)");throw new IllegalStateException("after business started");}).when(protect).onPrice(any(),any());
        assertThrows(IllegalStateException.class,()->consumer.applyProtection("BTCUSDT",1,"first"));
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM effects",Integer.class));
        assertNotNull(local.queryForObject("SELECT protection_started_at FROM shared_market_event_delivery",Timestamp.class));
        assertEquals(SharedMarketConsumer.ProtectionClaim.BLOCKED,consumer.claimProtectionAttempt("BTCUSDT",1,"new"));
        verify(protect,times(1)).onPrice(any(),any());
    }
    @Test void completionAcknowledgmentRejectsMismatchedSignalIdentity() {
        event(1,now);consumer.discoverFresh();
        local.update("UPDATE shared_market_event_delivery SET phase=4,analysis_status='REVIEW_REQUIRED',analysis_completed_at=NULL");
        var candle=local.queryForObject("SELECT candle_open_time FROM shared_market_event_delivery",Timestamp.class);
        local.update("INSERT INTO trade_signal VALUES(10,'ETHUSDT','1m',?)",candle);
        local.update("INSERT INTO signal_processing_work(signal_id,source_event_id,symbol,interval_code,candle_open_time,status,completed_at) VALUES(10,1,'BTCUSDT','1m',?,'COMPLETED',CURRENT_TIMESTAMP(6))",candle);
        var dispatcher=(SharedAnalysisDispatcher)ReflectionTestUtils.getField(consumer,"analysisDispatch");dispatcher.acknowledgmentEnabled(true);dispatcher.reconcileCompletedWorkAcknowledgments();
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class));
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit",Integer.class));
    }
    @Test void changedSourceIdentityIsAnIncidentRatherThanAnIgnoredDuplicate() {
        event(1,now);consumer.discoverFresh();
        feed.update("UPDATE market_data_stream_event SET price=11 WHERE id=1");
        var protocol=new Fix142DeliveryProtocol(local);
        assertThrows(IllegalStateException.class,()->protocol.insert(feed.queryForMap("SELECT * FROM market_data_stream_event WHERE id=1"),"BTCUSDT"));
        assertEquals(0,local.queryForObject("SELECT phase FROM shared_market_event_delivery",Integer.class));
    }
    @Test void markerResolutionPreservesUnresolvedProtectionAndAuditsSafeResume() {
        event(1,now);consumer.discoverFresh();
        local.update("UPDATE shared_market_consumer_state SET active_delivery_state='REVIEW_REQUIRED',active_delivery_event_id=1,active_delivery_owner_token='old',active_delivery_process_owner='stopped'");
        local.update("UPDATE shared_market_event_delivery SET protection_started_at=CURRENT_TIMESTAMP(6),protection_owner='old'");
        assertThrows(IllegalStateException.class,()->consumer.resolveMarker("BTCUSDT",1,"old","RESUME",null,"operator","independently verified stopped owner and resolved transaction","source remains untouched; business not confirmed"));
        // A separate fixture for a never-started attempt; the test does not clear uncertain work.
        local.update("DELETE FROM shared_market_event_delivery");feed.update("DELETE FROM market_data_stream_event");event(2,now);consumer.discoverFresh();
        local.update("UPDATE shared_market_consumer_state SET active_delivery_event_id=2");
        consumer.resolveMarker("BTCUSDT",2,"old","RESUME",null,"operator","independently verified stopped owner and resolved transaction","confirmed never-started protection and observer; reviewed evidence");
        consumer.processNext("BTCUSDT");
        assertEquals("NONE",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state",String.class));
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit WHERE operation='RESUME'",Integer.class));
    }
    @Test void liveDispatcherSelectsLatestEligibleClosedCandle() {
        event(1,now);event(2,now);consumer.discoverFresh();
        local.update("UPDATE shared_market_event_delivery SET phase=4,status='COMPLETED',closed=true,analysis_status='PENDING',candle_close_time=? WHERE source_event_id=1",Timestamp.from(now.minusSeconds(20)));
        local.update("UPDATE shared_market_event_delivery SET phase=4,status='COMPLETED',closed=true,analysis_status='PENDING',candle_close_time=? WHERE source_event_id=2",Timestamp.from(now.minusSeconds(1)));
        var dispatcher=(SharedAnalysisDispatcher)ReflectionTestUtils.getField(consumer,"analysisDispatch");dispatcher.freshPriority(true);dispatcher.live();
        verify(worker).processShared(any(),any(),eq(true),eq(2L));
        assertEquals("PENDING",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery WHERE source_event_id=1",String.class));
    }
    @Test void executionRetryDueTimeReclaimsOnlyThroughLiveDispatcher() throws Exception {
        local.execute("ALTER TABLE signal_processing_work ADD next_attempt_at TIMESTAMP(6)");
        event(1,now);consumer.discoverFresh();
        local.update("UPDATE shared_market_event_delivery SET phase=4,status='COMPLETED',closed=true,analysis_status='PENDING',candle_close_time=?",Timestamp.from(now.minusSeconds(1)));
        var candle=local.queryForObject("SELECT candle_open_time FROM shared_market_event_delivery",Timestamp.class);
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        when(worker.processShared(any(),any(),eq(true),eq(1L))).thenAnswer(invocation->{
            if(calls.incrementAndGet()==1){local.update("INSERT INTO signal_processing_work(signal_id,source_event_id,symbol,interval_code,candle_open_time,status,next_attempt_at) VALUES(10,1,'BTCUSDT','1m',?,'SYMBOL_LOCK_RETRY',?)",candle,Timestamp.from(Instant.now().plusMillis(200)));return "EXECUTION_RETRY";}
            local.update("UPDATE signal_processing_work SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP(6) WHERE signal_id=10");return "COMPLETED";
        });
        consumer.dispatchAnalysis();assertEquals("EXECUTION_RETRY",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class));
        consumer.dispatchHistoricalAnalysis();consumer.dispatchAnalysis();assertEquals(1,calls.get());
        Instant due=local.queryForObject("SELECT analysis_not_before FROM shared_market_event_delivery",Timestamp.class).toInstant();
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while(Instant.now().isBefore(due)&&System.nanoTime()<deadline)java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
        consumer.dispatchAnalysis();assertEquals(2,calls.get());
        assertEquals("COMPLETED",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class));
        verify(worker,never()).processShared(any(),any(),eq(false),anyLong());
    }
    @Test void historicalOnlyExceptionalRepairPreservesEvidenceAndNeverExecutes() {
        event(1,now);consumer.discoverFresh();
        local.update("UPDATE shared_market_event_delivery SET phase=4,status='HISTORICAL_ONLY',analysis_status='REVIEW_REQUIRED',owner_token='fenced',analysis_completed_at=NULL,last_error='retained original error'");
        var candle=local.queryForObject("SELECT candle_open_time FROM shared_market_event_delivery",Timestamp.class);
        local.update("INSERT INTO trade_signal VALUES(10,'BTCUSDT','1m',?)",candle);
        consumer.resolveHistoricalAnalysisOnly(1,"fenced",10,"operator","independent old-owner and transaction fencing verified","persisted signal commit independently confirmed; no business processing");
        assertEquals("ANALYSIS_ONLY_REVIEW",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class));
        assertNull(local.queryForObject("SELECT analysis_completed_at FROM shared_market_event_delivery",Timestamp.class));
        assertEquals("retained original error",local.queryForObject("SELECT last_error FROM shared_market_event_delivery",String.class));
        verifyNoInteractions(protect);verify(worker,never()).processShared(any(),any(),anyBoolean(),anyLong());
    }
    int blockedSessions(){return local.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL",Integer.class);}
    @Test void protectionClaimActuallyWaitsForAdmissionSymbolLock() throws Exception {
        event(1,now);consumer.discoverFresh();
        var held=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var threads=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var holder=threads.submit(()->new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(tx->{
                local.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol='BTCUSDT' FOR UPDATE");held.countDown();
                try{assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            }));
            assertTrue(held.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var claimant=threads.submit(()->consumer.claimProtectionAttempt("BTCUSDT",1,"claimant"));
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);boolean observed=false;
            while(System.nanoTime()<deadline){if(blockedSessions()>0){observed=true;break;}java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);}
            assertTrue(observed,"must observe actual server lock waiting, not assume a sleep is sufficient");
            assertNull(local.queryForObject("SELECT protection_started_at FROM shared_market_event_delivery",Timestamp.class));
            release.countDown();holder.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(SharedMarketConsumer.ProtectionClaim.CLAIMED,claimant.get(5,java.util.concurrent.TimeUnit.SECONDS));
        }finally{release.countDown();threads.shutdownNow();}
    }
    @Test void combinedExposureThrottlesAdmissionWithoutEvictingOwnersOrOtherLanes() {
        for(int i=1;i<=32;i++)event(i,now);consumer.discoverFresh();
        event(100,now.minusSeconds(600));event(200,now.minusSeconds(600));
        var protocol=new Fix142DeliveryProtocol(local);
        protocol.insert(feed.queryForMap("SELECT * FROM market_data_stream_event WHERE id=100"),"BTCUSDT");
        protocol.insert(feed.queryForMap("SELECT * FROM market_data_stream_event WHERE id=200"),"BTCUSDT");
        local.update("UPDATE shared_market_consumer_state SET active_delivery_state='ACTIVE',active_delivery_event_id=100,active_delivery_owner_token='old-marker',active_delivery_process_owner='old-process'");
        local.update("UPDATE shared_market_event_delivery SET phase=4,closed=true,analysis_status='RUNNING',candle_close_time=? WHERE source_event_id=200",Timestamp.from(now.minusSeconds(600)));
        assertEquals(34,protocol.exposure("BTCUSDT","1m",Instant.now())); // An admission check is not a universal ceiling.
        local.update("UPDATE shared_market_event_delivery SET analysis_status='REVIEW_REQUIRED' WHERE source_event_id=200");
        event(33,now);
        local.update("INSERT INTO shared_market_consumer_state(symbol,cutover_sequence,cutover_at,discovered_sequence,status,cutover_source,approved_by,approved_at,approval_reference,approved_sequence,approved_cutover_at) SELECT 'ETHUSDT',cutover_sequence,cutover_at,0,status,cutover_source,approved_by,approved_at,approval_reference,approved_sequence,approved_cutover_at FROM shared_market_consumer_state WHERE symbol='BTCUSDT'");
        event(1000,now);feed.update("UPDATE market_data_stream_event SET symbol='ETHUSDT',symbol_sequence=1 WHERE id=1000");
        when(coins.enabledSymbols()).thenReturn(List.of("BTCUSDT","ETHUSDT"));consumer.discoverFresh();
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE source_event_id=33",Integer.class));
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_market_event_delivery WHERE source_event_id=1000",Integer.class));
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery WHERE source_event_id=200",String.class));
        assertEquals(100L,local.queryForObject("SELECT active_delivery_event_id FROM shared_market_consumer_state WHERE symbol='BTCUSDT'",Long.class));
    }
    @Test void completedIndependentProtectionIsNotRepeatedAndObserverUsesProcessingTime() {
        Instant observation=now.minusSeconds(2);event(1,observation);consumer.discoverFresh();
        consumer.claimProtectionAttempt("BTCUSDT",1,"independent");consumer.applyProtection("BTCUSDT",1,"independent");
        consumer.processNext("BTCUSDT");consumer.processNext("BTCUSDT");
        verify(protect,times(1)).onPrice(any(),any());
        var time=org.mockito.ArgumentCaptor.forClass(Instant.class);verify(observer,times(1)).onPrice(eq("BTCUSDT"),any(),time.capture());
        assertTrue(time.getValue().isAfter(observation));
        assertEquals("COMPLETED",local.queryForObject("SELECT observer_status FROM shared_market_event_delivery",String.class));
        assertEquals("APPLIED",local.queryForObject("SELECT delivery_status FROM market_price_event",String.class));
    }
    @Test void observerFailureRetainsReviewWithoutRepeatingProtectionOrObserver() {
        event(1,now);consumer.discoverFresh();
        doThrow(new IllegalStateException("observer failure")).when(observer).onPrice(any(),any(),any());
        consumer.processNext("BTCUSDT");consumer.processNext("BTCUSDT");
        verify(protect,times(1)).onPrice(any(),any());verify(observer,times(1)).onPrice(any(),any(),any());
        assertEquals(4,local.queryForObject("SELECT phase FROM shared_market_event_delivery",Integer.class));
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT observer_status FROM shared_market_event_delivery",String.class));
        assertEquals("NONE",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state",String.class));
    }
    @Test void categoryGaugesDistinguishExpiredPendingFromUncertainReviews() throws Exception {
        event(1,now);consumer.discoverFresh();
        var health=new Fix142HealthService(local,coins);
        var query=Fix142HealthService.class.getDeclaredMethod("categoryCounts",String.class,Instant.class);query.setAccessible(true);
        @SuppressWarnings("unchecked") var fresh=(List<Map<String,Object>>)query.invoke(health,"BTCUSDT",Instant.now());
        assertEquals(1L,((Number)fresh.getFirst().get("eligible_protection")).longValue());
        local.update("UPDATE shared_market_event_delivery SET phase=4,analysis_status='PENDING',closed=true,observed_at=?,candle_close_time=?",Timestamp.from(now.minusSeconds(600)),Timestamp.from(now.minusSeconds(600)));
        @SuppressWarnings("unchecked") var expired=(List<Map<String,Object>>)query.invoke(health,"BTCUSDT",Instant.now());
        assertEquals(1L,((Number)expired.getFirst().get("fully_expired_untouched")).longValue());
        assertEquals(1L,((Number)expired.getFirst().get("phase4_pending")).longValue());
        local.update("UPDATE shared_market_event_delivery SET analysis_status='REVIEW_REQUIRED'");
        @SuppressWarnings("unchecked") var uncertain=(List<Map<String,Object>>)query.invoke(health,"BTCUSDT",Instant.now());
        assertEquals(0L,((Number)uncertain.getFirst().get("fully_expired_untouched")).longValue());
        assertEquals(1L,((Number)uncertain.getFirst().get("failed_attempt")).longValue());
    }
    // FIX-150 regressions: legacy uncertainty stays explicit, and safe retirement
    // must unblock the NEXT tick rather than merely alter a diagnostics counter.
    Fix150ProtectionRecovery recoveryFixture() {
        local.execute("CREATE TABLE wallet_managed_position(id BIGINT PRIMARY KEY,symbol VARCHAR(30),status VARCHAR(30))");
        local.execute("CREATE TABLE paper_position(id BIGINT PRIMARY KEY,symbol VARCHAR(30),status VARCHAR(30))");
        local.execute("CREATE TABLE wallet_symbol_coordination(symbol VARCHAR(30) PRIMARY KEY)");
        local.update("INSERT INTO wallet_symbol_coordination VALUES('BTCUSDT')");
        var coordination=new com.crypto.wallet.service.WalletTransactionCoordination(local,manager);
        return new Fix150ProtectionRecovery(local,coordination,manager);
    }
    void expiredFencedAttempt(String applyState) {
        event(1,now);consumer.discoverFresh();consumer.claimProtectionAttempt("BTCUSDT",1,"original-owner");
        var old=Timestamp.from(now.minusSeconds(120));
        local.update("UPDATE shared_market_event_delivery SET phase=1,status='REVIEW_REQUIRED',observed_at=?,protection_started_at=?,protection_apply_state=?,last_error='original failure retained'",old,old,applyState);
        local.update("UPDATE shared_market_consumer_state SET active_delivery_event_id=1,active_delivery_state='REVIEW_REQUIRED',active_delivery_owner_token='marker-owner',active_delivery_process_owner='old-process'");
    }
    @Test void fix150LegacyFlatRecoveryPreservesUncertaintyAndAllowsNextProtection() {
        var recovery=recoveryFixture();expiredFencedAttempt("LEGACY_UNKNOWN");
        ReflectionTestUtils.setField(consumer,"protectionRecovery",recovery);
        consumer.monitorProtectionAttempts(); // Exercise the actual scheduled integration.
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit",Integer.class));
        assertEquals("ABANDONED",local.queryForObject("SELECT protection_outcome FROM shared_market_event_delivery",String.class));
        assertEquals("original-owner",local.queryForObject("SELECT protection_owner FROM shared_market_event_delivery",String.class));
        assertEquals("original failure retained",local.queryForObject("SELECT last_error FROM shared_market_event_delivery",String.class));
        assertEquals("NONE",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state",String.class));
        assertEquals("NOT_APPLICABLE",local.queryForObject("SELECT analysis_status FROM shared_market_event_delivery",String.class));
        assertTrue(local.queryForObject("SELECT evidence FROM shared_delivery_resolution_audit",String.class).contains("UNKNOWN_RETAINED"));
        verifyNoInteractions(protect,observer);
        // The old source is now outside both fresh windows; no conflicting rediscovery.
        feed.update("UPDATE market_data_stream_event SET observed_at=? WHERE id=1",Timestamp.from(now.minusSeconds(120)));
        event(2,Instant.now());consumer.discoverFresh();
        assertEquals(SharedMarketConsumer.ProtectionClaim.CLAIMED,consumer.claimProtectionAttempt("BTCUSDT",2,"next-owner"));
        assertTrue(consumer.applyProtection("BTCUSDT",2,"next-owner"));
        verify(protect,times(1)).onPrice(any(),any());
        consumer.processNext("BTCUSDT");
        assertEquals(4,local.queryForObject("SELECT phase FROM shared_market_event_delivery WHERE source_event_id=2",Integer.class));
        assertEquals("NONE",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state",String.class));
        assertEquals(0,recovery.recover("BTCUSDT").size());
    }
    @Test void fix150LegacyRecoveryRefusesOpenWalletPosition() {
        var recovery=recoveryFixture();expiredFencedAttempt("LEGACY_UNKNOWN");
        local.update("INSERT INTO wallet_managed_position VALUES(1,'BTCUSDT','OPEN')");
        assertEquals(0,recovery.recover("BTCUSDT").size());
        assertNull(local.queryForObject("SELECT protection_completed_at FROM shared_market_event_delivery",Timestamp.class));
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state",String.class));
    }
    @Test void fix150LegacyRecoveryRefusesOpenPaperPosition() {
        var recovery=recoveryFixture();expiredFencedAttempt("APPLYING");
        local.update("INSERT INTO paper_position VALUES(1,'BTCUSDT','OPEN')");
        assertEquals(0,recovery.recover("BTCUSDT").size());
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit",Integer.class));
    }
    @Test void fix150NeverEnteredEvidenceRecoversEvenWithOpenPositionWithoutReplay() {
        var recovery=recoveryFixture();expiredFencedAttempt("CLAIMED");
        local.update("INSERT INTO wallet_managed_position VALUES(1,'BTCUSDT','OPEN')");
        assertEquals(1,recovery.recover("BTCUSDT").size());
        assertEquals("REJECTED",local.queryForObject("SELECT protection_outcome FROM shared_market_event_delivery",String.class));
        assertTrue(local.queryForObject("SELECT evidence FROM shared_delivery_resolution_audit",String.class).contains("BUSINESS_NOT_ENTERED"));
        assertEquals("OPEN",local.queryForObject("SELECT status FROM wallet_managed_position",String.class));
        verifyNoInteractions(protect,observer);
    }
    @Test void fix150AuditFailureRollsBackAttemptAndMarkerTogether() {
        var recovery=recoveryFixture();expiredFencedAttempt("LEGACY_UNKNOWN");
        local.execute("ALTER TABLE shared_delivery_resolution_audit ADD CONSTRAINT reject_flat_audit CHECK (operation <> 'PRICE_ABANDONED_FLAT')");
        assertThrows(RuntimeException.class,()->recovery.recover("BTCUSDT").size());
        assertNull(local.queryForObject("SELECT protection_completed_at FROM shared_market_event_delivery",Timestamp.class));
        assertEquals(1,local.queryForObject("SELECT phase FROM shared_market_event_delivery",Integer.class));
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state",String.class));
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit",Integer.class));
    }
    @Test void fix150RecoveryCannotReleaseDifferentMarkerOrAnalysisOwnership() {
        var recovery=recoveryFixture();expiredFencedAttempt("LEGACY_UNKNOWN");
        local.update("UPDATE shared_market_consumer_state SET active_delivery_event_id=999");
        assertEquals(0,recovery.recover("BTCUSDT").size());
        local.update("UPDATE shared_market_consumer_state SET active_delivery_event_id=1");
        local.update("UPDATE shared_market_event_delivery SET analysis_started_at=CURRENT_TIMESTAMP(6)");
        assertEquals(0,recovery.recover("BTCUSDT").size());
        assertNull(local.queryForObject("SELECT protection_completed_at FROM shared_market_event_delivery",Timestamp.class));
    }
    @Test void fix150CommittedEntryIntentCannotBeReenteredWithSameToken() {
        event(1,now);consumer.discoverFresh();consumer.claimProtectionAttempt("BTCUSDT",1,"one-owner");
        assertTrue(consumer.prepareProtectionApply("BTCUSDT",1,"one-owner"));
        assertFalse(consumer.prepareProtectionApply("BTCUSDT",1,"one-owner"));
        assertFalse(consumer.applyProtection("BTCUSDT",1,"one-owner"));
        assertEquals("APPLYING",local.queryForObject("SELECT protection_apply_state FROM shared_market_event_delivery",String.class));
        verifyNoInteractions(protect);
    }
    @Test void fix150ExpiredClaimCompletesWithoutBusinessAndDoesNotConsumeSlot() {
        var recovery=recoveryFixture();
        event(1,now);consumer.discoverFresh();consumer.claimProtectionAttempt("BTCUSDT",1,"owner");
        local.update("UPDATE shared_market_event_delivery SET observed_at=?",Timestamp.from(now.minusSeconds(20)));
        assertFalse(consumer.applyProtection("BTCUSDT",1,"owner"));
        assertEquals(1,recovery.recover("BTCUSDT").size()); // No 30-second wait for proven never-entry.
        assertEquals("NOT_ENTERED",local.queryForObject("SELECT protection_apply_state FROM shared_market_event_delivery",String.class));
        assertNotNull(local.queryForObject("SELECT protection_completed_at FROM shared_market_event_delivery",Timestamp.class));
        verifyNoInteractions(protect);
    }

    @Test void fix150RecoveryWaitsForWalletWriterAndRechecksCommittedPosition() throws Exception {
        var recovery=recoveryFixture();expiredFencedAttempt("LEGACY_UNKNOWN");
        var coordination=new com.crypto.wallet.service.WalletTransactionCoordination(local,manager);
        var held=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var threads=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var writer=threads.submit(()->new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(tx->{
                coordination.symbol("BTCUSDT");held.countDown();
                try{assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                local.update("INSERT INTO wallet_managed_position VALUES(1,'BTCUSDT','OPEN')");
            }));
            assertTrue(held.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var resolving=threads.submit(()->recovery.recover("BTCUSDT").size());
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
            boolean observed=false;
            while(System.nanoTime()<deadline){if(blockedSessions()>0){observed=true;break;}java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);}
            assertTrue(observed,"recovery must actually wait for the position-creation wallet fence");
            assertNull(local.queryForObject("SELECT protection_completed_at FROM shared_market_event_delivery",Timestamp.class));
            release.countDown();writer.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(0,resolving.get(5,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state",String.class));
        }finally{release.countDown();threads.shutdownNow();}
    }
    @Test void fix150DelayedOriginalOwnerCannotApplyAfterRetirement() {
        var recovery=recoveryFixture();expiredFencedAttempt("LEGACY_UNKNOWN");
        assertEquals(1,recovery.recover("BTCUSDT").size());
        assertTrue(consumer.applyProtection("BTCUSDT",1,"original-owner"));
        consumer.processNext("BTCUSDT");
        verifyNoInteractions(protect,observer);
        assertEquals("ABANDONED",local.queryForObject("SELECT protection_outcome FROM shared_market_event_delivery",String.class));
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit",Integer.class));
    }


    // FIX-151: frozen pre-change predicate is an independent equivalence oracle.
    private static final String PRE_FIX151_EXPOSURE_SQL="""

            SELECT COUNT(*) FROM shared_market_event_delivery d WHERE d.symbol=? AND d.interval_code=? AND (
              (d.phase=4 AND (d.analysis_status IN ('RUNNING','EXECUTION_RETRY','REVIEW_REQUIRED')
                OR (d.analysis_status IN ('PENDING','HISTORICAL_DEFERRED') AND d.closed=1 AND d.candle_close_time BETWEEN ? AND ?)))
              OR (d.phase<4 AND (
                EXISTS(SELECT 1 FROM shared_market_consumer_state s WHERE s.symbol=d.symbol
                  AND s.active_delivery_event_id=d.source_event_id AND s.active_delivery_state IN ('ACTIVE','REVIEW_REQUIRED'))
                OR (d.protection_started_at IS NOT NULL AND d.protection_completed_at IS NULL)
                OR (d.interval_code='1m' AND d.observed_at BETWEEN ? AND ?)
                OR (d.closed=1 AND d.candle_close_time BETWEEN ? AND ?))))
                    """;
    @Test void fix151ExposureMatchesOriginalWithOverlappingAndExpiredEvidence() {
        var protocol=new Fix142DeliveryProtocol(local);
        var random=new Random(151);
        for(int i=1;i<=120;i++) {
            event(i,now.minusSeconds(3600));
            protocol.insert(feed.queryForMap("SELECT * FROM market_data_stream_event WHERE id=?",i),"BTCUSDT");
            String interval=List.of("1m","5m","15m").get(random.nextInt(3));
            String analysis=List.of("PENDING","HISTORICAL_DEFERRED","RUNNING","EXECUTION_RETRY","REVIEW_REQUIRED","COMPLETED","WAITING").get(random.nextInt(7));
            local.update("UPDATE shared_market_event_delivery SET interval_code=?,phase=?,analysis_status=?,closed=?,observed_at=?,candle_close_time=?,protection_started_at=?,protection_completed_at=? WHERE source_event_id=?",
                interval,random.nextInt(7)-1,analysis,random.nextBoolean(),
                Timestamp.from(now.minusSeconds(random.nextInt(3600))),Timestamp.from(now.minusSeconds(random.nextInt(3600))),
                random.nextBoolean()?Timestamp.from(now.minusSeconds(7200)):null,
                random.nextBoolean()?Timestamp.from(now.minusSeconds(7100)):null,i);
        }
        // One row overlaps marker, open protection, live price and closed-candle categories.
        local.update("UPDATE shared_market_event_delivery SET interval_code='1m',phase=1,closed=1,observed_at=?,candle_close_time=?,protection_started_at=?,protection_completed_at=NULL WHERE source_event_id=1",Timestamp.from(now),Timestamp.from(now),Timestamp.from(now.minusSeconds(600)));
        for(String marker:List.of("NONE","ACTIVE","REVIEW_REQUIRED")) {
            local.update("UPDATE shared_market_consumer_state SET active_delivery_state=?,active_delivery_event_id=1 WHERE symbol='BTCUSDT'",marker);
            for(String interval:List.of("1m","5m","15m","1h")) {
                Timestamp closeFrom=Timestamp.from(now.minusSeconds(SharedEventPolicy.closeGraceSeconds(interval))),until=Timestamp.from(now);
                Integer expected=local.queryForObject(PRE_FIX151_EXPOSURE_SQL,Integer.class,"BTCUSDT",interval,closeFrom,until,Timestamp.from(now.minusSeconds(15)),until,closeFrom,until);
                assertEquals(expected,protocol.exposure("BTCUSDT",interval,now),marker+" / "+interval);
            }
        }
    }
    @Test void fix151OldestSelectionPreservesSequenceAcrossPhasesAndSkipsReviews() {
        var protocol=new Fix142DeliveryProtocol(local);
        for(int i=1;i<=40;i++) {
            event(i,now.minusSeconds(3600));
            protocol.insert(feed.queryForMap("SELECT * FROM market_data_stream_event WHERE id=?",i),"BTCUSDT");
        }
        local.update("UPDATE shared_market_event_delivery SET phase=4");
        local.update("UPDATE shared_market_event_delivery SET phase=1,status='REVIEW_REQUIRED' WHERE source_event_id=1");
        local.update("UPDATE shared_market_event_delivery SET phase=3 WHERE source_event_id=7");
        local.update("UPDATE shared_market_event_delivery SET phase=0 WHERE source_event_id=9");
        Long expected=local.queryForObject("SELECT source_event_id FROM shared_market_event_delivery WHERE symbol='BTCUSDT' AND phase<4 AND status<>'REVIEW_REQUIRED' ORDER BY symbol_sequence LIMIT 1",Long.class);
        var selected=new org.springframework.transaction.support.TransactionTemplate(manager).execute(tx->{
            var state=local.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol='BTCUSDT' FOR UPDATE");
            return protocol.select("BTCUSDT",state);
        });
        assertNotNull(selected);assertEquals(expected.longValue(),((Number)selected.get("source_event_id")).longValue());
        assertEquals(7L,expected.longValue());
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT status FROM shared_market_event_delivery WHERE source_event_id=1",String.class));
    }
    @Test void fix151GeneratedEligibilityTracksPhaseAndReviewChangesWithoutBackfill() {
        event(1,now);new Fix142DeliveryProtocol(local).insert(feed.queryForMap("SELECT * FROM market_data_stream_event WHERE id=1"),"BTCUSDT");
        assertEquals(1,local.queryForObject("SELECT phase_walk_pending FROM shared_market_event_delivery WHERE source_event_id=1",Integer.class));
        local.update("UPDATE shared_market_event_delivery SET status='REVIEW_REQUIRED' WHERE source_event_id=1");
        assertEquals(0,local.queryForObject("SELECT phase_walk_pending FROM shared_market_event_delivery WHERE source_event_id=1",Integer.class));
        local.update("UPDATE shared_market_event_delivery SET status='PENDING',phase=4 WHERE source_event_id=1");
        assertEquals(0,local.queryForObject("SELECT phase_walk_pending FROM shared_market_event_delivery WHERE source_event_id=1",Integer.class));
        local.update("UPDATE shared_market_event_delivery SET phase=2 WHERE source_event_id=1");
        assertEquals(1,local.queryForObject("SELECT phase_walk_pending FROM shared_market_event_delivery WHERE source_event_id=1",Integer.class));
    }

    // FIX-152: reproduce the incident's rejected completion / pending price / fenced marker.
    void rejectedCompletedMarker() {
        event(1,now.minusSeconds(120));
        new Fix142DeliveryProtocol(local).insert(feed.queryForMap("SELECT * FROM market_data_stream_event WHERE id=1"),"BTCUSDT");
        local.update("UPDATE shared_market_event_delivery SET phase=1,status='PROCESSING',protection_completed_at=?,protection_outcome='REJECTED' WHERE source_event_id=1",Timestamp.from(now.minusSeconds(60)));
        local.update("INSERT INTO market_price_event(symbol,observed_at,price,source,source_event_id,delivery_status) VALUES('BTCUSDT',?,10,'COLLECTOR_KLINE_LIVE',1,'PENDING')",Timestamp.from(now.minusSeconds(120)));
        local.update("UPDATE shared_market_consumer_state SET active_delivery_state='REVIEW_REQUIRED',active_delivery_event_id=1,active_delivery_owner_token='old-marker',active_delivery_process_owner='old-process',active_delivery_started_at=?,active_delivery_updated_at=? WHERE symbol='BTCUSDT'",Timestamp.from(now.minusSeconds(90)),Timestamp.from(now.minusSeconds(90)));
    }
    @Test void fix152RejectedMarkerContinuesWithoutProtectionReplayThenProcessesNewTick() {
        var recovery=recoveryFixture();rejectedCompletedMarker();
        ReflectionTestUtils.setField(consumer,"protectionRecovery",recovery);
        consumer.monitorProtectionAttempts();
        assertEquals("ACTIVE",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state WHERE symbol='BTCUSDT'",String.class));
        assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit WHERE operation='REJECTED_MARKER_RESUMED'",Integer.class));
        consumer.processNext("BTCUSDT");
        assertEquals(4,local.queryForObject("SELECT phase FROM shared_market_event_delivery WHERE source_event_id=1",Integer.class));
        assertEquals("HISTORICAL_ONLY",local.queryForObject("SELECT delivery_status FROM market_price_event WHERE source_event_id=1",String.class));
        assertEquals("NONE",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state WHERE symbol='BTCUSDT'",String.class));
        verifyNoInteractions(protect,observer);
        event(2,Instant.now());consumer.discoverFresh();consumer.processNext("BTCUSDT");
        verify(protect,times(1)).onPrice(any(),any());
        assertEquals(4,local.queryForObject("SELECT phase FROM shared_market_event_delivery WHERE source_event_id=2",Integer.class));
    }
    @Test void fix152NewTokenFencesOldMarkerCallerWithoutChangingProtectionEvidence() {
        var recovery=recoveryFixture();rejectedCompletedMarker();
        var protocol=(Fix142DeliveryProtocol)ReflectionTestUtils.getField(consumer,"protocol");
        assertTrue(recovery.resumeRejectedMarker("BTCUSDT",protocol.processOwner));
        var state=local.queryForMap("SELECT * FROM shared_market_consumer_state WHERE symbol='BTCUSDT'");
        assertFalse(protocol.owned(state,1L,"old-marker"));
        assertNotEquals("old-marker",state.get("active_delivery_owner_token"));
        assertThrows(IllegalStateException.class,()->protocol.progress("BTCUSDT",1L,"old-marker",4));
        assertFalse(consumer.applyProtection("BTCUSDT",1L,"old-marker"));
        assertNull(local.queryForObject("SELECT protection_started_at FROM shared_market_event_delivery WHERE source_event_id=1",Timestamp.class));
        assertEquals("REJECTED",local.queryForObject("SELECT protection_outcome FROM shared_market_event_delivery WHERE source_event_id=1",String.class));
        verifyNoInteractions(protect);
    }
    @Test void fix152AuditFailureRollsBackMarkerTakeover() {
        var recovery=recoveryFixture();rejectedCompletedMarker();
        local.execute("ALTER TABLE shared_delivery_resolution_audit ADD CONSTRAINT reject_fix152_audit CHECK(reviewer<>'FIX152_SYSTEM')");
        assertThrows(RuntimeException.class,()->recovery.resumeRejectedMarker("BTCUSDT","new-process"));
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state WHERE symbol='BTCUSDT'",String.class));
        assertEquals("old-marker",local.queryForObject("SELECT active_delivery_owner_token FROM shared_market_consumer_state WHERE symbol='BTCUSDT'",String.class));
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit",Integer.class));
    }
    @Test void fix152RefusesStartedAppliedObserverAndConflictingPriceStates() {
        var recovery=recoveryFixture();rejectedCompletedMarker();
        local.update("UPDATE shared_market_event_delivery SET protection_started_at=? WHERE source_event_id=1",Timestamp.from(now.minusSeconds(120)));
        assertFalse(recovery.resumeRejectedMarker("BTCUSDT","new-process"));
        local.update("UPDATE shared_market_event_delivery SET protection_started_at=NULL,protection_outcome='APPLIED' WHERE source_event_id=1");
        assertFalse(recovery.resumeRejectedMarker("BTCUSDT","new-process"));
        local.update("UPDATE shared_market_event_delivery SET protection_outcome='REJECTED',phase=2 WHERE source_event_id=1");
        assertFalse(recovery.resumeRejectedMarker("BTCUSDT","new-process"));
        local.update("UPDATE shared_market_event_delivery SET phase=1 WHERE source_event_id=1");
        local.update("UPDATE market_price_event SET delivery_status='APPLIED' WHERE source_event_id=1");
        assertFalse(recovery.resumeRejectedMarker("BTCUSDT","new-process"));
        assertEquals("REVIEW_REQUIRED",local.queryForObject("SELECT active_delivery_state FROM shared_market_consumer_state WHERE symbol='BTCUSDT'",String.class));
        assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM shared_delivery_resolution_audit",Integer.class));
    }
}
