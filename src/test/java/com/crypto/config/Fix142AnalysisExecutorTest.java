package com.crypto.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.ThreadPoolExecutor;
import static org.junit.jupiter.api.Assertions.*;

class Fix142AnalysisExecutorTest {
    @Test void historicalExecutorHasSameValidatedCapacityAndBoundedQueue() {
        var config=new SharedAnalysisExecutorConfig();ReflectionTestUtils.setField(config,"historicalThreads",2);
        var executor=config.historical();executor.initialize();
        try {
            assertEquals(2,executor.getCorePoolSize());assertEquals(2,executor.getMaxPoolSize());
            assertEquals(2,executor.getThreadPoolExecutor().getQueue().remainingCapacity());
            assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class,executor.getThreadPoolExecutor().getRejectedExecutionHandler());
        }finally{executor.shutdown();}
    }
    @Test void compatibilityDefaultRemainsOneAndInvalidCapacitiesAreRejected() {
        var config=new SharedAnalysisExecutorConfig();var executor=config.historical();
        assertEquals(1,executor.getCorePoolSize());assertEquals(1,executor.getMaxPoolSize());
        ReflectionTestUtils.setField(config,"historicalThreads",5);assertThrows(IllegalArgumentException.class,config::historical);
        ReflectionTestUtils.setField(config,"historicalThreads",0);assertThrows(IllegalArgumentException.class,config::historical);
    }
}
