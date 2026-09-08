package com.crypto.infrastructure.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** FIX-124: real JDBC commit/rollback boundaries with controlled failure injection.
 * This reproduces rollback propagation, NOT the unknown MySQL competing lock cycle.
 */
class KlineTransactionCoordinatorTest {
    JdbcTemplate jdbc;
    DataSourceTransactionManager manager;
    KlineTransactionCoordinator coordinator;
    List<String> order;
    final Instant at = Instant.parse("2026-09-08T05:41:05.434Z");
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        manager = new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE writes(id INT PRIMARY KEY, label VARCHAR(40))");
        jdbc.execute("CREATE TABLE fix124_protection_incident(id BIGINT AUTO_INCREMENT PRIMARY KEY,symbol VARCHAR(30),interval_code VARCHAR(10),candle_open_time TIMESTAMP(6),observed_at TIMESTAMP(6),price DECIMAL(30,12),attempts INT,outcome VARCHAR(20),error_message CLOB)");
        coordinator = new KlineTransactionCoordinator(manager, new Fix124ProtectionStore(ds,manager));
        order = new ArrayList<>();
    }
    InitialPositionLockDeadlock deadlock() {
        return new InitialPositionLockDeadlock(new RuntimeException(new SQLException("deadlock", "40001",1213)));
    }
    void committed(String label) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { order.add(label); }
        });
    }
    void process(Runnable protect, Runnable observer) {
        coordinator.process("UNIUSDT","1m",at.minusSeconds(5),at,new BigDecimal("7.046"),
                () -> { jdbc.update("INSERT INTO writes VALUES(1,'candle'),(2,'exact price')"); committed("input"); },
                protect, observer, () -> { committed("closed event"); });
    }
    @Test void initialDeadlockRollsBackThenRetriesAndCommitsExactlyOnce() {
        AtomicInteger attempts = new AtomicInteger();
        process(() -> {
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class));
            // Synthetic write makes rollback observable; the real initial lock has no wallet writes.
            jdbc.update("INSERT INTO writes VALUES(3,'protection')");
            if (attempts.incrementAndGet()==1) throw deadlock();
            committed("protection");
        }, () -> committed("observer"));
        assertEquals(2,attempts.get());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class));
        assertEquals(List.of("input","protection","observer","closed event"),order);
        assertEquals("RECOVERED",jdbc.queryForObject("SELECT outcome FROM fix124_protection_incident",String.class));
        assertEquals(2,jdbc.queryForObject("SELECT attempts FROM fix124_protection_incident",Integer.class));
    }
    @Test void exhaustionRetainsInputAndDispatchesClosureAfterTwoAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        process(() -> { attempts.incrementAndGet(); throw deadlock(); },null);
        assertEquals(2,attempts.get());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class));
        assertEquals(List.of("input","closed event"),order);
        assertEquals("FAILED",jdbc.queryForObject("SELECT outcome FROM fix124_protection_incident",String.class));
    }
    @Test void lateWalletFailureIsRolledBackWithoutRetryOrDuplicateExecution() {
        AtomicInteger attempts = new AtomicInteger();
        process(() -> {
            attempts.incrementAndGet();
            jdbc.update("INSERT INTO writes VALUES(3,'wallet')");
            throw new RuntimeException(new SQLException("late deadlock", "40001",1213));
        },null);
        assertEquals(1,attempts.get());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class));
        assertEquals(List.of("input","closed event"),order);
    }
    @Test void inputFailurePreventsProtectionAndCloseDispatch() {
        assertThrows(RuntimeException.class, () -> coordinator.process("UNIUSDT","1m",at,at,BigDecimal.ONE,
                () -> { jdbc.update("INSERT INTO writes VALUES(1,'candle')"); throw new IllegalStateException("price insert failed"); },
                () -> fail("unrecorded input must not trade"),null,() -> fail("must not publish")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class));
    }
    @Test void observerRollbackCannotUndoInputOrCommittedProtection() {
        process(() -> { jdbc.update("INSERT INTO writes VALUES(3,'wallet')"); committed("protection"); },
                () -> { jdbc.update("INSERT INTO writes VALUES(4,'observer')"); throw new IllegalStateException("observer failure"); });
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class));
        assertEquals(List.of("input","protection","closed event"),order);
    }
    @Test void oldSharedTransactionLosesInputAfterCaughtParticipantFailure() {
        var outer = new TransactionTemplate(manager);
        var joined = new TransactionTemplate(manager);
        assertThrows(org.springframework.transaction.UnexpectedRollbackException.class, () -> outer.executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO writes VALUES(1,'candle')");
            try { joined.executeWithoutResult(inner -> { throw deadlock(); }); }
            catch (InitialPositionLockDeadlock ignored) { /* former catch-and-continue */ }
        }));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM writes",Integer.class));
    }
    @Test void diagnosticFailureDoesNotSuppressCloseDispatch() {
        jdbc.execute("DROP TABLE fix124_protection_incident");
        process(() -> { throw deadlock(); },null);
        assertEquals(List.of("input","closed event"),order);
    }
    @Test void healthyTickWritesNoIncidentAndNonCanonicalStreamDoesNotProtect() {
        process(null,null);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM fix124_protection_incident",Integer.class));
        assertEquals(List.of("input","closed event"),order);
    }
}
