package com.crypto.config;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
/** FIX-11E: bounded live Binance Order Book collection pool, separate from FIX-112C persistence.
 * Different symbols may run concurrently; OrderBookLiquidityService enforces one in-flight request per symbol.
 * AbortPolicy keeps network work off the scheduler thread during overload.
 * Golden rule: Replay = Production; Order Book evaluation and persisted evidence semantics are unchanged. */
@Configuration
public class OrderBookCollectionAsyncConfig {
    @Bean(name = "orderBookCollectionExecutor")
    public Executor orderBookCollectionExecutor(
            @Value("${analysis.order-book.collection.core-pool-size:6}") int corePoolSize,
            @Value("${analysis.order-book.collection.max-pool-size:8}") int maxPoolSize,
            @Value("${analysis.order-book.collection.queue-capacity:40}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("order-book-collect-");
        executor.setCorePoolSize(Math.max(1, corePoolSize));
        executor.setMaxPoolSize(Math.max(Math.max(1, corePoolSize), maxPoolSize));
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
