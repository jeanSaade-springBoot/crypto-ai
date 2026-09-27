package com.crypto.wallet.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Historical mechanism regression: a deliberately retained legacy fixture shows
 * why a Java monitor cannot protect an outer commit. The current wallet uses DB
 * coordination; WalletCoordinationMySqlIT tests that implementation separately. */
@Timeout(10)
class Fix125CommitVisibilityGapTest {
    public static class LegacyWalletFixture {
        @org.springframework.transaction.annotation.Transactional
        public synchronized boolean noOp(){return false;}
    }
    @Test
    void anotherWalletInvocationCanCommitWhileFirstProxyCommitIsPaused() throws Exception {
        LegacyWalletFixture target = new LegacyWalletFixture();
        CountDownLatch firstCommit = new CountDownLatch(1), releaseCommit = new CountDownLatch(1);
        AtomicInteger commits = new AtomicInteger();
        AbstractPlatformTransactionManager manager = new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
            @Override protected void doCommit(DefaultTransactionStatus status) {
                assertFalse(Thread.holdsLock(target), "Legacy synchronized method has already released its monitor");
                if (commits.incrementAndGet() == 1) {
                    firstCommit.countDown();
                    try { assertTrue(releaseCommit.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                }
            }
        };
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        LegacyWalletFixture proxy = (LegacyWalletFixture) factory.getProxy();
        Callable<Boolean> invoke = () -> proxy.noOp();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = workers.submit(invoke);
            assertTrue(firstCommit.await(5, TimeUnit.SECONDS));
            Future<Boolean> second = workers.submit(invoke);
            assertFalse(second.get(5, TimeUnit.SECONDS));
            assertEquals(2, commits.get());
            assertFalse(first.isDone(), "First commit remains blocked while second invocation has returned");
            releaseCommit.countDown();
            assertFalse(first.get(5, TimeUnit.SECONDS));
        } finally { releaseCommit.countDown(); workers.shutdownNow(); }
    }
}
