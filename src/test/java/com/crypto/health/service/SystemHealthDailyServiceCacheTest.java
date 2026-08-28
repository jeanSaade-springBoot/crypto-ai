package com.crypto.health.service;

import com.crypto.administration.service.CoinConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * FIX-116 regression coverage for System Health resource protection.
 * These tests verify observability caching only; Production/Replay trading paths are not involved.
 */
class SystemHealthDailyServiceCacheTest {

    @Test
    void repeatedCallsReuseTheShortLivedDailyHealthSnapshot() {
        SystemHealthDailyService service = spy(new SystemHealthDailyService(
                mock(JdbcTemplate.class), mock(CoinConfigurationService.class)));
        ReflectionTestUtils.setField(service, "dailyCacheMs", 45_000L);
        Map<String, Object> payload = Map.of("status", "OK");
        doAnswer(invocation -> payload).when(service).computeDailyHealth();

        Map<String, Object> first = service.dailyHealth();
        Map<String, Object> second = service.dailyHealth();

        assertSame(first, second);
        verify(service, times(1)).computeDailyHealth();
    }

    @Test
    void concurrentCacheMissesAreSingleFlight() throws Exception {
        SystemHealthDailyService service = spy(new SystemHealthDailyService(
                mock(JdbcTemplate.class), mock(CoinConfigurationService.class)));
        ReflectionTestUtils.setField(service, "dailyCacheMs", 45_000L);
        Map<String, Object> payload = Map.of("status", "OK");
        CountDownLatch calculationStarted = new CountDownLatch(1);
        CountDownLatch allowCalculationToFinish = new CountDownLatch(1);
        AtomicInteger calculations = new AtomicInteger();
        doAnswer(invocation -> {
            calculations.incrementAndGet();
            calculationStarted.countDown();
            if (!allowCalculationToFinish.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test calculation was not released");
            }
            return payload;
        }).when(service).computeDailyHealth();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> first = executor.submit(service::dailyHealth);
            calculationStarted.await(2, TimeUnit.SECONDS);
            Future<Map<String, Object>> second = executor.submit(service::dailyHealth);
            allowCalculationToFinish.countDown();

            assertSame(payload, first.get(2, TimeUnit.SECONDS));
            assertSame(payload, second.get(2, TimeUnit.SECONDS));
            assertEquals(1, calculations.get());
            verify(service, times(1)).computeDailyHealth();
        } finally {
            executor.shutdownNow();
        }
    }
}
