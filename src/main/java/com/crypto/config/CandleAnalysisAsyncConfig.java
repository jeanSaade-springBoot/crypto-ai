package com.crypto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * FIX-043: dedicated executor for closed-candle analysis.
 *
 * Production evidence on 22 Aug 2026 showed 96.7-100% 1m candle persistence but only
 * 17-20% technical_indicator/trade_signal coverage. The old AFTER_COMMIT listener ran the
 * complete indicator -> signal -> wallet path synchronously on the Binance message thread.
 * When that work took longer than the incoming market-data cadence, the websocket consumer
 * could not keep up and the recovery scheduler became the de-facto analysis clock.
 *
 * CandleAnalysisDispatcher supplies FIFO ordering per symbol+interval while this pool supplies
 * parallelism across independent streams. The pool sizes are configuration-driven because the
 * correct concurrency depends on VPS CPU/DB capacity; changing them must never change trading
 * decisions, only how quickly independent market streams can be analyzed.
 *
 * CallerRunsPolicy is intentional: if both the pool and queue are saturated, temporary
 * back-pressure is safer than silently dropping a closed-candle event. FIX-043's chronological
 * recovery remains the second safety net for any incomplete analysis row.
 *
 * IMPORTANT: this class must never contain BUY/SELL scoring, execution thresholds,
 * SETUP_CONFIRMATION_WAKEUP, ACCUMULATED_EVIDENCE or exit logic.
 */
@Configuration
public class CandleAnalysisAsyncConfig {

    @Bean(name = "candleAnalysisExecutor")
    public Executor candleAnalysisExecutor(
            @Value("${trading.analysis-worker-core-size:8}") int coreSize,
            @Value("${trading.analysis-worker-max-size:16}") int maxSize,
            @Value("${trading.analysis-worker-queue-capacity:200}") int queueCapacity
    ) {
        int safeCoreSize = Math.max(1, coreSize);
        int safeMaxSize = Math.max(safeCoreSize, maxSize);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(safeCoreSize);
        executor.setMaxPoolSize(safeMaxSize);
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setThreadNamePrefix("candle-analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
