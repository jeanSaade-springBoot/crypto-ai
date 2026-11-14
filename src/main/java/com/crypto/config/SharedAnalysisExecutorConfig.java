package com.crypto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/** FIX-138: reserve live capacity; historical catch-up cannot consume it.
 * No caller-runs fallback: a saturated pool must never execute on a dispatch clock. */
@Configuration
public class SharedAnalysisExecutorConfig {
    // FIX-142: dispatcher and executor receive the same validated historical setting.
    @org.springframework.beans.factory.annotation.Value("${shared-market.analysis.historical-threads:1}")
    private int historicalThreads=1;
    @Bean("sharedLiveAnalysisExecutor")
    public ThreadPoolTaskExecutor live() { return pool(8, "shared-live-"); }

    @Bean("sharedHistoricalAnalysisExecutor")
    public ThreadPoolTaskExecutor historical() { if(historicalThreads<1||historicalThreads>4)throw new IllegalArgumentException("Historical capacity must be 1..4");return pool(historicalThreads, "shared-history-"); }

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
