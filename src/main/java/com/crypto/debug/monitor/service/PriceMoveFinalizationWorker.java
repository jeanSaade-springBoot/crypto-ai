package com.crypto.debug.monitor.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** FIX-130: one local worker plus a durable database-global gate. No trading
 * executor, scheduler thread or WebSocket thread performs historical finalization. */
@Component
public class PriceMoveFinalizationWorker {
    private static final Logger log=LoggerFactory.getLogger(PriceMoveFinalizationWorker.class);
    private final PriceMoveFinalizationStore store;
    private final PriceMoveMonitorService monitor;
    private final TransactionTemplate transaction;
    private final AtomicBoolean started=new AtomicBoolean();
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(task -> {
        Thread t=new Thread(task,"fix130-finalizer");t.setDaemon(true);return t;
    });
    public PriceMoveFinalizationWorker(PriceMoveFinalizationStore store,PriceMoveMonitorService monitor,PlatformTransactionManager manager) {
        this.store=store;this.monitor=monitor;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Ordinary consistent reads do not request shared row locks as SERIALIZABLE could.
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @EventListener(ApplicationReadyEvent.class)
    public void start() { if(started.compareAndSet(false,true))executor.scheduleWithFixedDelay(this::poll,5,5,TimeUnit.SECONDS); }
    void poll() {
        PriceMoveFinalizationStore.Job job=null;
        try {
            job=store.claim(); if(job==null)return;
            var claimed=job;
            log.info("[FIX-130][FINALIZING] jobId={}, symbol={}, attempt={}",job.id(),job.snapshot().symbol(),job.attempts());
            transaction.executeWithoutResult(tx -> { monitor.finalizeSnapshot(claimed.snapshot());store.complete(claimed); });
            log.info("[FIX-130][FINALIZED] jobId={}, symbol={}",job.id(),job.snapshot().symbol());
        } catch(RuntimeException ex) {
            if(job!=null) {
                try { store.failed(job,rollbackProven(ex),ex.toString()); }
                catch(RuntimeException recordFailure) { log.error("[FIX-130][FAILURE_RECORD_FAILED] jobId={}; gate remains held",job.id(),recordFailure); }
            }
            log.error("[FIX-130][WORKER_FAILED] jobId={}; inspect durable state",job==null?null:job.id(),ex);
        }
    }
    static boolean rollbackProven(Throwable failure) {
        for(Throwable ex=failure;ex!=null;ex=ex.getCause())
            if(ex instanceof SQLException sql && sql.getErrorCode()==1213 && "40001".equals(sql.getSQLState()))return true;
        return false;
    }
    @PreDestroy public void stop() {
        // Never release ownership simply because shutdown/lease time elapsed.
        // Pending work survives. An interrupted active job requires review on restart.
        executor.shutdown();
    }
}
