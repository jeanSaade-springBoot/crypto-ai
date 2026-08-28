package com.crypto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.client.binance.BinanceMarketDataClient;
import com.crypto.client.binance.dto.BinanceOrderBook;
import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.config.OrderBookProperties;
import com.crypto.config.OrderBookProperties.IntervalPolicy;
import com.crypto.domain.SignalDecision;
import com.crypto.market.service.OrderBookSnapshotService;

class OrderBookLiquidityServiceConcurrencyTest {

    @Test
    void fix11eSchedulerDispatchesEachDistinctSymbolWithoutRunningBinanceOnSchedulerThread() {
        BinanceMarketDataClient client = mock(BinanceMarketDataClient.class);
        CoinConfigurationService coins = mock(CoinConfigurationService.class);
        OrderBookSnapshotService snapshots = mock(OrderBookSnapshotService.class);
        List<Runnable> submitted = new ArrayList<>();
        Executor capturingExecutor = submitted::add;
        when(coins.enabledSymbols()).thenReturn(List.of("btcusdt", "ETHUSDT", "BTCUSDT"));

        OrderBookLiquidityService service = service(client, coins, snapshots, capturingExecutor);
        service.collectConfiguredOrderBooks();

        // FIX-11E regression: scheduler only dispatches distinct normalized symbols. It must
        // not synchronously invoke Binance while iterating the configured symbol list.
        assertEquals(2, submitted.size());
        verify(client, org.mockito.Mockito.never()).getOrderBook(eq("BTCUSDT"), anyInt());
        verify(client, org.mockito.Mockito.never()).getOrderBook(eq("ETHUSDT"), anyInt());
    }

    @Test
    void fix11eSameSymbolCannotOverlapBetweenSchedulerAndLiveEvaluateFallback() throws Exception {
        BinanceMarketDataClient client = mock(BinanceMarketDataClient.class);
        CoinConfigurationService coins = mock(CoinConfigurationService.class);
        OrderBookSnapshotService snapshots = mock(OrderBookSnapshotService.class);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(coins.enabledSymbols()).thenReturn(List.of("BTCUSDT"));
        when(client.getOrderBook(eq("BTCUSDT"), anyInt())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            firstEntered.countDown();
            releaseFirst.await(2, TimeUnit.SECONDS);
            return book();
        });
        Executor newThreadExecutor = command -> new Thread(command, "fix-11e-test-collector").start();
        OrderBookLiquidityService service = service(client, coins, snapshots, newThreadExecutor);

        service.collectConfiguredOrderBooks();
        org.junit.jupiter.api.Assertions.assertTrue(firstEntered.await(1, TimeUnit.SECONDS));

        // evaluate() is the second real collectSafely caller. While the scheduled request is
        // in-flight it must not issue a second Binance request or wait for the first request.
        service.evaluate("BTCUSDT", "1m", SignalDecision.BUY, true,
                BigDecimal.ONE, null, null, Instant.now());
        assertEquals(1, calls.get());

        releaseFirst.countDown();
        Thread.sleep(100);
        assertEquals(1, calls.get());
    }

    @Test
    void fix11eHistoricalReplayNeverUsesParallelLiveBinanceCollection() {
        BinanceMarketDataClient client = mock(BinanceMarketDataClient.class);
        CoinConfigurationService coins = mock(CoinConfigurationService.class);
        OrderBookSnapshotService snapshots = mock(OrderBookSnapshotService.class);
        Instant replayTime = Instant.parse("2026-08-28T10:15:00Z");
        when(snapshots.find(eq("BTCUSDT"), org.mockito.ArgumentMatchers.any(Instant.class), eq(replayTime)))
                .thenReturn(List.of());
        OrderBookLiquidityService service = service(client, coins, snapshots, Runnable::run);

        // FIX-11E Replay parity protection: historical evaluation remains FIX-112C persisted-evidence
        // only. The new live collection executor must never become a historical Binance fallback.
        service.evaluateHistorical("BTCUSDT", "1m", SignalDecision.BUY, true,
                BigDecimal.ONE, null, null, replayTime);

        verify(snapshots).find(eq("BTCUSDT"), org.mockito.ArgumentMatchers.any(Instant.class), eq(replayTime));
        verify(client, org.mockito.Mockito.never()).getOrderBook(eq("BTCUSDT"), anyInt());
    }

    private OrderBookLiquidityService service(BinanceMarketDataClient client,
            CoinConfigurationService coins, OrderBookSnapshotService snapshots, Executor executor) {
        BinanceMarketDataProperties market = new BinanceMarketDataProperties();
        market.setEnabled(true);
        OrderBookProperties properties = new OrderBookProperties(true, 100, new BigDecimal("2.0"),
                5000L, 5000, 5, new BigDecimal("0.20"), new BigDecimal("0.40"),
                new BigDecimal("4.0"), new BigDecimal("0.15"), true,
                Map.of("1m", new IntervalPolicy(60L, 6, 20L, BigDecimal.ONE, true)));
        return new OrderBookLiquidityService(client, market, properties, coins, snapshots, executor);
    }

    private BinanceOrderBook book() {
        return new BinanceOrderBook(1L,
                List.of(List.of("100", "2")),
                List.of(List.of("101", "2")));
    }
}
