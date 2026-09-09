package com.crypto.execution.processing;

import com.crypto.domain.TradeSignal;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.service.AnalysisService;
import com.crypto.infrastructure.transaction.InitialPositionLockDeadlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** FIX-127: real H2 transactions with injected lock failures. These tests verify
 * atomicity and retry permission, NOT MySQL's unidentified competing lock cycle. */
class SignalProcessingCoordinatorTest {
    JdbcTemplate jdbc;
    DataSourceTransactionManager manager;
    TransactionTemplate tx;
    SignalProcessingStore store;
    SignalProcessingCoordinator coordinator;
    TradeSignalRepository signals;
    WalletManagedPositionRepository positions;
    TradeSignal signal;
    Instant open=Instant.parse("2026-09-09T10:00:00Z");

    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        // Minimal test schema; full V86 MySQL migration must also be tested on MySQL.
        jdbc.execute("""
            CREATE TABLE signal_processing_work(signal_id BIGINT PRIMARY KEY,symbol VARCHAR(30),
            interval_code VARCHAR(10),candle_open_time TIMESTAMP(6),origin VARCHAR(20),status VARCHAR(30),
            attempts INT,owner_token VARCHAR(36),next_attempt_at TIMESTAMP(6),updated_at TIMESTAMP(6),
            completed_at TIMESTAMP(6),paper_position_id BIGINT,failure_stage VARCHAR(40),error_message CLOB)
            """);
        jdbc.execute("CREATE TABLE writes(id INT PRIMARY KEY,label VARCHAR(40))");
        jdbc.execute("CREATE TABLE candle(symbol VARCHAR(30),interval_code VARCHAR(10),open_time TIMESTAMP(6),close_time TIMESTAMP(6),closed INT)");
        store=new SignalProcessingStore(jdbc,manager);
        signals=mock(TradeSignalRepository.class);positions=mock(WalletManagedPositionRepository.class);
        signal=new TradeSignal();signal.setId(127L);signal.setSymbol("PEPEUSDT");signal.setInterval("1m");signal.setCandleOpenTime(open);
        when(signals.findById(127L)).thenReturn(Optional.of(signal));
        coordinator=new SignalProcessingCoordinator(store,signals,positions,new SignalProcessingFreshness(jdbc),manager);
    }
    RuntimeException deadlock() {return new RuntimeException(new SQLException("controlled initial conflict","40001",1213));}
    void register(ProcessingOrigin origin) {tx.executeWithoutResult(t->store.register(signal,origin));}
    String status() {return jdbc.queryForObject("SELECT status FROM signal_processing_work WHERE signal_id=127",String.class);}
    int countWrites() {return jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class);}
    void freshCandle() {
        // Fixed lineage, recent close: SQL gate uses exact open identity and actual close age.
        jdbc.update("INSERT INTO candle VALUES('PEPEUSDT','1m',?,?,1)",Timestamp.from(open),Timestamp.from(Instant.now().minusSeconds(5)));
    }
    @Test void normalProcessingCompletesAtomicallyAndDuplicateCallDoesNotRepeatBody() {
        AtomicInteger calls=new AtomicInteger();
        coordinator.process(127,false,s->{calls.incrementAndGet();jdbc.update("INSERT INTO writes VALUES(1,'wallet')");return Optional.empty();});
        assertEquals("COMPLETED",status());assertEquals(1,countWrites());
        coordinator.process(127,false,s->{fail("completed signals must not execute twice");return Optional.empty();});
        assertEquals(1,calls.get());
    }
    @Test void firstLockFailsBeforeBusinessThenSecondAttemptCommitsOnce() {
        freshCandle();AtomicInteger locks=new AtomicInteger();AtomicInteger calls=new AtomicInteger();
        when(positions.findFirstBySymbolAndStatusOrderByOpenedAtDesc("PEPEUSDT","OPEN")).thenAnswer(inv->{
            assertEquals(0,countWrites(),"lock must precede advisory/wallet callback");
            if(locks.incrementAndGet()==1)throw deadlock();return Optional.empty();
        });
        coordinator.process(127,false,s->{calls.incrementAndGet();jdbc.update("INSERT INTO writes VALUES(1,'wallet')");return Optional.empty();});
        assertEquals(2,locks.get());assertEquals(1,calls.get());assertEquals(1,countWrites());assertEquals("COMPLETED",status());
        assertEquals(2,jdbc.queryForObject("SELECT attempts FROM signal_processing_work",Integer.class));
        assertEquals("INITIAL_POSITION_LOCK",jdbc.queryForObject("SELECT failure_stage FROM signal_processing_work",String.class));
    }
    @Test void lateDeadlockRollsBackAllWritesAndIsNeverRetried() {
        AtomicInteger calls=new AtomicInteger();
        assertThrows(RuntimeException.class,()->coordinator.process(127,false,s->{
            calls.incrementAndGet();jdbc.update("INSERT INTO writes VALUES(1,'advisory'),(2,'wallet')");throw deadlock();
        }));
        assertEquals(1,calls.get());assertEquals(0,countWrites());assertEquals("REVIEW_REQUIRED",status());
        assertNull(store.claim(127,true));
    }
    @Test void evenAnInitialLockMarkerFromALaterBodyCannotAuthorizeRetry() {
        AtomicInteger calls=new AtomicInteger();
        assertThrows(RuntimeException.class,()->coordinator.process(127,false,s->{
            calls.incrementAndGet();throw new InitialPositionLockDeadlock(deadlock());
        }));
        assertEquals(1,calls.get());assertEquals("REVIEW_REQUIRED",status());
    }
    @Test void beforeCommitFailureRollsBackWithoutRetry() {
        AtomicInteger calls=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->coordinator.process(127,false,s->{
            calls.incrementAndGet();jdbc.update("INSERT INTO writes VALUES(1,'wallet')");
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("commit failure"); }
                });
            return Optional.empty();
        }));
        assertEquals(1,calls.get());assertEquals(0,countWrites());assertEquals("REVIEW_REQUIRED",status());
    }
    @Test void afterCommitExceptionCannotEraseDurableCompletionOrRepeatBusiness() {
        AtomicInteger calls=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->coordinator.process(127,false,s->{
            calls.incrementAndGet();jdbc.update("INSERT INTO writes VALUES(1,'wallet')");
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { throw new IllegalStateException("ack failure"); }
                });
            return Optional.empty();
        }));
        assertEquals("COMPLETED",status());assertEquals(1,countWrites());
        coordinator.process(127,false,s->{calls.incrementAndGet();return Optional.empty();});
        assertEquals(1,calls.get());
    }
    @Test void recoveredInitialFailureHasOnlyOneRemainingAttemptAfterRestart() {
        register(ProcessingOrigin.WORKER);freshCandle();var work=store.claim(127,true);
        store.finishWithoutBusiness(work,"RETRYABLE_FAILURE","INITIAL_POSITION_LOCK","confirmed rollback");
        when(positions.findFirstBySymbolAndStatusOrderByOpenedAtDesc("PEPEUSDT","OPEN")).thenThrow(deadlock());
        assertThrows(RuntimeException.class,()->coordinator.process(127,true,s->Optional.empty()));
        verify(positions).findFirstBySymbolAndStatusOrderByOpenedAtDesc("PEPEUSDT","OPEN");
        assertEquals(2,jdbc.queryForObject("SELECT attempts FROM signal_processing_work",Integer.class));
        assertEquals("REVIEW_REQUIRED",status());
    }
    @Test void exhaustedInitialLockRetriesRequireReview() {
        freshCandle();when(positions.findFirstBySymbolAndStatusOrderByOpenedAtDesc("PEPEUSDT","OPEN")).thenThrow(deadlock());
        assertThrows(RuntimeException.class,()->coordinator.process(127,false,s->{fail("no business before successful lock");return Optional.empty();}));
        verify(positions,times(2)).findFirstBySymbolAndStatusOrderByOpenedAtDesc("PEPEUSDT","OPEN");
        assertEquals("REVIEW_REQUIRED",status());assertNull(store.claim(127,false));
    }
    @Test void lockTimeoutDoesNotAcquireDeadlockRetryPermission() {
        when(positions.findFirstBySymbolAndStatusOrderByOpenedAtDesc("PEPEUSDT","OPEN"))
                .thenThrow(new RuntimeException(new SQLException("timeout","HY000",1205)));
        assertThrows(RuntimeException.class,()->coordinator.process(127,false,s->Optional.empty()));
        verify(positions).findFirstBySymbolAndStatusOrderByOpenedAtDesc("PEPEUSDT","OPEN");assertEquals("REVIEW_REQUIRED",status());
    }
    @Test void expiredRegisteredWorkCannotReachBusinessAfterLock() {
        register(ProcessingOrigin.WORKER);
        coordinator.process(127,true,s->{fail("stale/missing candle cannot trade");return Optional.empty();});
        assertEquals("EXPIRED",status());verify(positions).findFirstBySymbolAndStatusOrderByOpenedAtDesc("PEPEUSDT","OPEN");
    }
    @Test void freshnessIsCheckedAfterPositionLockRatherThanBeforeItsWait() {
        register(ProcessingOrigin.WORKER);freshCandle();
        when(positions.findFirstBySymbolAndStatusOrderByOpenedAtDesc("PEPEUSDT","OPEN")).thenAnswer(inv->{
            // Simulate a newer closed candle becoming available at lock acquisition.
            jdbc.update("INSERT INTO candle VALUES('PEPEUSDT','1m',?,?,1)",
                    Timestamp.from(open.plusSeconds(60)),Timestamp.from(Instant.now().minusSeconds(4)));
            return Optional.empty();
        });
        coordinator.process(127,true,s->{fail("superseded candle cannot execute after waiting");return Optional.empty();});
        assertEquals("EXPIRED",status());
    }
    @Test void pendingWorkSurvivesStoreRecreationAndFreshRecoveryCommits() {
        register(ProcessingOrigin.RECOVERY);freshCandle();
        store=new SignalProcessingStore(jdbc,manager);
        coordinator=new SignalProcessingCoordinator(store,signals,positions,new SignalProcessingFreshness(jdbc),manager);
        coordinator.process(127,true,s->{jdbc.update("INSERT INTO writes VALUES(1,'recovered')");return Optional.empty();});
        assertEquals("COMPLETED",status());assertEquals(1,countWrites());
    }
    @Test void startupAndExplicitWorkNeverBecomeBackgroundTrades() {
        register(ProcessingOrigin.STARTUP);assertNull(store.claim(127,true));assertTrue(store.due().isEmpty());
        jdbc.update("DELETE FROM signal_processing_work");register(ProcessingOrigin.EXPLICIT);
        assertNull(store.claim(127,true));assertTrue(store.due().isEmpty());assertEquals("PENDING",status());
    }
    @Test void interruptedOwnerIsQuarantinedWithoutReplaying() {
        register(ProcessingOrigin.WORKER);assertNotNull(store.claim(127,true));
        jdbc.update("UPDATE signal_processing_work SET updated_at=?",Timestamp.from(Instant.now().minusSeconds(301)));
        assertEquals(1,store.quarantineInterrupted());assertEquals("REVIEW_REQUIRED",status());
        assertNull(store.claim(127,true));assertTrue(store.due().isEmpty());
    }
    @Test void concurrentClaimsGrantExactlyOneOwner() throws Exception {
        register(ProcessingOrigin.WORKER);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<SignalProcessingStore.Work> claim=()->{start.await();return store.claim(127,true);};
            var first=pool.submit(claim);var second=pool.submit(claim);start.countDown();
            var a=first.get(5,TimeUnit.SECONDS);var b=second.get(5,TimeUnit.SECONDS);
            assertTrue((a==null) != (b==null));
            assertEquals(1,jdbc.queryForObject("SELECT attempts FROM signal_processing_work",Integer.class));
        } finally {pool.shutdownNow();}
    }
    @Test void completionRollsBackWithBusinessAndCannotBeCommittedByWrongOwner() {
        register(ProcessingOrigin.WORKER);var work=store.claim(127,true);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(t->{
            jdbc.update("INSERT INTO writes VALUES(1,'wallet')");store.complete(work,null);throw new IllegalStateException("rollback");
        }));
        assertEquals(0,countWrites());assertEquals("RUNNING",status());
        var wrong=new SignalProcessingStore.Work(work.signalId(),work.symbol(),work.interval(),work.candleOpenTime(),work.origin(),work.status(),work.attempts(),"wrong");
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(t->store.complete(wrong,null)));
        assertEquals("RUNNING",status());
    }
    @Test void duplicateRegistrationNeverResetsOriginOrCompletion() {
        register(ProcessingOrigin.WORKER);var work=store.claim(127,false);tx.executeWithoutResult(t->store.complete(work,99L));
        register(ProcessingOrigin.EXPLICIT);assertEquals("COMPLETED",status());
        assertEquals("WORKER",jdbc.queryForObject("SELECT origin FROM signal_processing_work",String.class));
        assertEquals(99L,jdbc.queryForObject("SELECT paper_position_id FROM signal_processing_work",Long.class));
    }
    @Test void lineageMismatchRequiresReviewWithoutBusiness() {
        register(ProcessingOrigin.WORKER);signal.setCandleOpenTime(open.plusSeconds(60));
        assertThrows(IllegalStateException.class,()->coordinator.process(127,true,s->Optional.empty()));
        assertEquals("REVIEW_REQUIRED",status());verify(positions).findFirstBySymbolAndStatusOrderByOpenedAtDesc("PEPEUSDT","OPEN");
    }
    @Test void ambientTransactionIsRejectedBeforeAnyClaimOrRegistration() {
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(t->coordinator.process(127,false,s->Optional.empty())));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM signal_processing_work",Integer.class));
    }
    @Test void registrationMustShareTheSignalCreationTransaction() {
        assertThrows(IllegalStateException.class,()->store.register(signal,ProcessingOrigin.WORKER));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(t->{
            jdbc.update("INSERT INTO writes VALUES(1,'signal')");store.register(signal,ProcessingOrigin.WORKER);throw new IllegalStateException("abort creation");
        }));
        assertEquals(0,countWrites());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM signal_processing_work",Integer.class));
    }
    @Test void realAnalysisWrapperProxyRollsSignalCreationBackWhenRegistrationFails() {
        // Real wrapper + Spring transaction interceptor; only expensive scoring is stubbed.
        var analysis=mock(AnalysisService.class,CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(analysis,"processingStore",store);
        var indicator=new TechnicalIndicator();
        doAnswer(inv->{jdbc.update("INSERT INTO writes VALUES(1,'signal')");return signal;}).when(analysis).analyze(indicator);
        var factory=new ProxyFactory(analysis);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        var proxy=(AnalysisService)factory.getProxy();
        signal.setCandleOpenTime(null);
        assertThrows(IllegalArgumentException.class,()->proxy.analyzeForProcessing(indicator,ProcessingOrigin.WORKER));
        assertEquals(0,countWrites());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM signal_processing_work",Integer.class));
        signal.setCandleOpenTime(open);
        proxy.analyzeForProcessing(indicator,ProcessingOrigin.WORKER);
        assertEquals(1,countWrites());assertEquals("PENDING",status());
    }
    @Test void freshnessUsesExactLatestClosedCandleAndRejectsFutureAndOldData() {
        var gate=new SignalProcessingFreshness(jdbc);register(ProcessingOrigin.WORKER);
        var work=tx.execute(t->store.locked(127));var now=open.plusSeconds(70);
        jdbc.update("INSERT INTO candle VALUES('PEPEUSDT','1m',?,?,1)",Timestamp.from(open),Timestamp.from(open.plusSeconds(59)));
        assertTrue(gate.eligible(work,now));assertFalse(gate.eligible(work,open.plusSeconds(150)));
        assertFalse(gate.eligible(work,open.plusSeconds(58)),"future close must not be used");
        jdbc.update("INSERT INTO candle VALUES('PEPEUSDT','1m',?,?,1)",Timestamp.from(open.plusSeconds(60)),Timestamp.from(open.plusSeconds(119)));
        assertFalse(gate.eligible(work,open.plusSeconds(120)),"no nearest-candle fallback");
    }
    // FIX-128: these tests exercise real JDBC transactions, not the InnoDB lock cycle.
    void ageOwner() {
        jdbc.update("UPDATE signal_processing_work SET updated_at=? WHERE signal_id=127",
                Timestamp.from(Instant.now().minusSeconds(600)));
    }
    @Test void fix128CompletionAfterDiscoveryCannotBeOverwritten() {
        register(ProcessingOrigin.WORKER);var work=store.claim(127,true);ageOwner();
        var cutoff=Instant.now().minusSeconds(300);var candidate=store.quarantineCandidates(cutoff).get(0);
        tx.executeWithoutResult(t->store.complete(work,99L));
        assertFalse(store.quarantineCandidate(candidate,cutoff));assertEquals("COMPLETED",status());
    }
    @Test void fix128OwnerAndTimestampChangesInvalidateDiscoveredCandidate() {
        register(ProcessingOrigin.WORKER);var work=store.claim(127,true);ageOwner();
        var cutoff=Instant.now().minusSeconds(300);var candidate=store.quarantineCandidates(cutoff).get(0);
        jdbc.update("UPDATE signal_processing_work SET owner_token='other' WHERE signal_id=127");
        assertFalse(store.quarantineCandidate(candidate,cutoff));
        jdbc.update("UPDATE signal_processing_work SET owner_token=?,updated_at=? WHERE signal_id=127",
                work.owner(),Timestamp.from(Instant.now()));
        assertFalse(store.quarantineCandidate(candidate,cutoff));assertEquals("RUNNING",status());
    }
    @Test void fix128DiscoveryIsBoundedAndExcludesFreshOwners() {
        register(ProcessingOrigin.WORKER);store.claim(127,true);
        assertTrue(store.quarantineCandidates(Instant.now().minusSeconds(300)).isEmpty());
        for(int id=200;id<260;id++) {
            signal.setId((long)id);register(ProcessingOrigin.WORKER);store.claim(id,true);
        }
        jdbc.update("UPDATE signal_processing_work SET updated_at=? WHERE signal_id>=200",Timestamp.from(Instant.now().minusSeconds(600)));
        var candidates=store.quarantineCandidates(Instant.now().minusSeconds(300));
        assertEquals(50,candidates.size());assertEquals(200,candidates.get(0).signalId());
        assertEquals(50,store.quarantineInterrupted());assertEquals(10,store.quarantineInterrupted());
        assertEquals("RUNNING",status());
    }
    @Test void fix128QuarantineCommitSurvivesAmbientCallerRollback() {
        register(ProcessingOrigin.WORKER);store.claim(127,true);ageOwner();
        var candidate=store.quarantineCandidates(Instant.now().minusSeconds(300)).get(0);
        // Independent housekeeping commit survives a caller transaction rollback.
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(t->{
            assertTrue(store.quarantineCandidate(candidate,Instant.now().minusSeconds(300)));
            jdbc.update("INSERT INTO writes VALUES(1,'unrelated')");throw new IllegalStateException("rollback");
        }));
        assertEquals("REVIEW_REQUIRED",status());assertEquals(0,countWrites());assertNull(store.claim(127,true));
    }

}
