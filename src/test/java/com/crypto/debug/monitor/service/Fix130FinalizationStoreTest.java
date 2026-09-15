package com.crypto.debug.monitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class Fix130FinalizationStoreTest {
    JdbcTemplate jdbc;
    DataSourceTransactionManager manager;
    PriceMoveFinalizationStore store,second;
    PriceMoveBlockSnapshot snapshot(String symbol) { return new PriceMoveBlockSnapshot(symbol,"2026-09-15T00:00:00Z",null,null); }
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=1000","sa","");
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V88__fix_130_price_move_finalization.sql"));
        // H2 exercises coordination semantics, not MySQL engine/collation DDL support.
        sql=sql.replaceAll("ENGINE=InnoDB(?: DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci)?", "");
        for(String statement:sql.split(";"))if(!statement.isBlank())jdbc.execute(statement);
        jdbc.execute("CREATE TABLE explanation(id INT PRIMARY KEY)");
        store=new PriceMoveFinalizationStore(ds,manager);second=new PriceMoveFinalizationStore(ds,manager);
    }
    @Test void enqueueSurvivesCallerRollbackAndIsIdempotent() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {store.enqueue(snapshot("UNIUSDT"));tx.setRollbackOnly();});
        second.enqueue(snapshot("UNIUSDT"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM price_move_finalization_work",Integer.class));
        assertNotNull(second.claim()); // A fresh store instance discovers durable work.
    }
    @Test void twoInstancesCannotClaimTwoJobsAndBusyGateDoesNotPreventEnqueue() {
        store.enqueue(snapshot("UNIUSDT"));var first=store.claim();assertNotNull(first);
        second.enqueue(snapshot("BTCUSDT"));assertNull(second.claim());
        assertEquals("FINALIZATION_PENDING",jdbc.queryForObject("SELECT status FROM price_move_finalization_work WHERE symbol='BTCUSDT'",String.class));
    }
    @Test void enqueueDoesNotAcquireContendedGateRow() throws Exception {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var executor=Executors.newSingleThreadExecutor();
        var held=executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
            jdbc.queryForMap("SELECT * FROM price_move_finalization_gate WHERE id=1 FOR UPDATE");locked.countDown();
            try {assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}
        }));
        try {assertTrue(locked.await(5,TimeUnit.SECONDS));store.enqueue(snapshot("UNIUSDT"));}
        finally {release.countDown();held.get(5,TimeUnit.SECONDS);executor.shutdown();}
    }
    @Test void retryBackoffLetsOtherSymbolsRun() {
        store.enqueue(snapshot("UNIUSDT"));var first=store.claim();
        store.failed(first,true,"proven rollback");
        store.enqueue(snapshot("BTCUSDT"));var next=second.claim();
        assertEquals("BTCUSDT",next.snapshot().symbol());
        assertEquals("RETRYABLE_FAILURE",jdbc.queryForObject("SELECT status FROM price_move_finalization_work WHERE id=?",String.class,first.id()));
    }
    @Test void uncertainFailureHoldsGlobalGateAndKeepsOtherJobsDurable() {
        store.enqueue(snapshot("UNIUSDT"));var first=store.claim();store.failed(first,false,"uncertain commit");
        second.enqueue(snapshot("BTCUSDT"));assertNull(second.claim());
        assertEquals("REVIEW_REQUIRED",jdbc.queryForObject("SELECT status FROM price_move_finalization_work WHERE id=?",String.class,first.id()));
    }
    @Test void expiredOwnershipIsReviewedNeverStolen() {
        store.enqueue(snapshot("UNIUSDT"));var first=store.claim();
        jdbc.update("UPDATE price_move_finalization_work SET started_at=? WHERE id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1900)),first.id());
        assertNull(second.claim());
        assertEquals("REVIEW_REQUIRED",jdbc.queryForObject("SELECT status FROM price_move_finalization_work WHERE id=?",String.class,first.id()));
        assertThrows(IllegalStateException.class,() -> new TransactionTemplate(manager).executeWithoutResult(tx -> store.complete(first)));
    }
    @Test void explanationsAndCompletionCommitOrRollbackTogether() {
        store.enqueue(snapshot("UNIUSDT"));var job=store.claim();
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            jdbc.update("INSERT INTO explanation VALUES(1)");store.complete(job);tx.setRollbackOnly();
        });
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM explanation",Integer.class));
        assertEquals("FINALIZING",jdbc.queryForObject("SELECT status FROM price_move_finalization_work WHERE id=?",String.class,job.id()));
        new TransactionTemplate(manager).executeWithoutResult(tx -> {jdbc.update("INSERT INTO explanation VALUES(1)");store.complete(job);});
        assertEquals("FINALIZED",jdbc.queryForObject("SELECT status FROM price_move_finalization_work WHERE id=?",String.class,job.id()));
        assertNull(jdbc.queryForMap("SELECT active_job_id FROM price_move_finalization_gate WHERE id=1").get("active_job_id"));
    }
}
