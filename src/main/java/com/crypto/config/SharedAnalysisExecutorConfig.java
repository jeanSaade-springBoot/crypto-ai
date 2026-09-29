package com.crypto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/** FIX-138: reserve live capacity; historical catch-up cannot consume it.
 * No caller-runs fallback: a saturated pool must never execute on a dispatch clock. */
@Configuration
public class SharedAnalysisExecutorConfig {
    @Bean("sharedLiveAnalysisExecutor")
    public ThreadPoolTaskExecutor live() { return pool(8, "shared-live-"); }

    @Bean("sharedHistoricalAnalysisExecutor")
    public ThreadPoolTaskExecutor historical() { return pool(1, "shared-history-"); }

    private ThreadPoolTaskExecutor pool(int size, String prefix) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        // At most one admitted task per thread; the small queue only accommodates
        // the interval between a task releasing its lane and returning to the pool.
        executor.setQueueCapacity(size);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
