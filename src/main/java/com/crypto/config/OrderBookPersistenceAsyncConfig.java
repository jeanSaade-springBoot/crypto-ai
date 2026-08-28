package com.crypto.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * FIX-112C: isolates historical Order Book persistence from the live collector.
 *
 * Replay evidence is best-effort infrastructure: a slow/unavailable database must never
 * delay Binance sampling or change Production observation counts/timing. The queue is
 * deliberately bounded and rejects overflow rather than falling back to CallerRunsPolicy,
 * because executing a DB insert on the collector thread would reintroduce the Production
 * behavior risk this isolation is designed to remove.
 */
@Configuration
public class OrderBookPersistenceAsyncConfig {

    @Bean(name = "orderBookPersistenceExecutor")
    public Executor orderBookPersistenceExecutor(
            @Value("${analysis.order-book.persistence.core-pool-size:1}") int corePoolSize,
            @Value("${analysis.order-book.persistence.max-pool-size:2}") int maxPoolSize,
            @Value("${analysis.order-book.persistence.queue-capacity:2000}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("order-book-persist-");
        executor.setCorePoolSize(Math.max(1, corePoolSize));
        executor.setMaxPoolSize(Math.max(Math.max(1, corePoolSize), maxPoolSize));
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        // Never run persistence work on the live collector thread when saturated.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
