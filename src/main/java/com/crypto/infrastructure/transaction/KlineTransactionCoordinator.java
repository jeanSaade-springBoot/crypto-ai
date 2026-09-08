package com.crypto.infrastructure.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;

/** FIX-124: commit market input before locking a position. Execute protection
 * before publishing the closed-candle event, preserving that ordering. No outer
 * transaction may retain candle locks while a protection transaction runs.
 */
@Service
public class KlineTransactionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(KlineTransactionCoordinator.class);
    private static final int MAX_ATTEMPTS = 2;
    private final TransactionTemplate transaction;
    private final Fix124ProtectionStore diagnostics;

    public KlineTransactionCoordinator(PlatformTransactionManager manager, Fix124ProtectionStore diagnostics) {
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.diagnostics = diagnostics;
    }

    public void process(String symbol, String interval, Instant candleOpenTime, Instant observedAt,
                        BigDecimal price, Runnable persistInput, Runnable protect,
                        Runnable observe, Runnable publishClosed) {
        // Candle and exact canonical price observation commit together. A failed
        // input write must not produce an unrecorded protection decision.
        transaction.executeWithoutResult(tx -> persistInput.run());
        if (protect != null) {
            int attempts = 0;
            RuntimeException failure = null;
            do {
                attempts++;
                try {
                    transaction.executeWithoutResult(tx -> protect.run());
                    failure = null;
                    break;
                } catch (RuntimeException ex) {
                    failure = ex;
                    // executeWithoutResult has finished rollback before this catch.
                    // Never retry a late wallet/commit error or an unknown outcome.
                    if (!(ex instanceof InitialPositionLockDeadlock) || attempts == MAX_ATTEMPTS) break;
                    log.warn("[FIX-124][PRODUCTION][INITIAL_LOCK_RETRY] symbol={}, candleOpenTime={}, observedAt={}, attempt={}",
                            symbol, candleOpenTime, observedAt, attempts);
                }
            } while (attempts < MAX_ATTEMPTS);
            if (failure != null || attempts > 1) {
                String outcome = failure == null ? "RECOVERED" : "FAILED";
                if (failure == null) {
                    log.info("[FIX-124][PRODUCTION][RECOVERED] symbol={}, observedAt={}, attempts={}", symbol, observedAt, attempts);
                } else {
                    log.error("[FIX-124][PRODUCTION][FAILED] symbol={}, observedAt={}, attempts={}; input committed, protection failed",
                            symbol, observedAt, attempts, failure);
                }
                diagnostics.record(symbol, interval, candleOpenTime, observedAt, price, attempts, outcome, failure);
            }
        }
        if (observe != null) {
            try {
                transaction.executeWithoutResult(tx -> observe.run());
            } catch (RuntimeException ex) {
                log.warn("[FIX-124][OBSERVER_FAILED] symbol={}; committed input and protection retained", symbol, ex);
            }
        }
        // AFTER_COMMIT listener still receives an actual committed transaction;
        // publishing outside a transaction would silently drop the analysis event.
        if (publishClosed != null) transaction.executeWithoutResult(tx -> publishClosed.run());
    }
}
