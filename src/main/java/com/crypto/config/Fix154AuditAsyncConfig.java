package com.crypto.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Audit evidence must never borrow the connection held by an execution path. */
@Configuration
public class Fix154AuditAsyncConfig {
    @Bean(name = "fix154AuditExecutor")
    public Executor fix154AuditExecutor(@Value("${fix154.audit.queue-capacity:500}") int queueCapacity) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("fix154-audit-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
