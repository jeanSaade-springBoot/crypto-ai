package com.crypto.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** REST recovery never consumes the WebSocket, analysis, or scheduler pools. */
@Configuration
public class LocalCandleReconciliationConfig {
    @Bean(name = "candleReconciliationExecutor")
    Executor candleReconciliationExecutor(@Value("${local-candle.reconciliation.executor-size:1}") int size) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, size));
        executor.setMaxPoolSize(Math.max(1, size));
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("candle-reconcile-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
