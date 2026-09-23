package com.crypto.client.binance.websocket;

import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.service.BinanceKlineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.time.Duration;

class BinanceWebSocketManagerTest {
    private final BinanceMarketDataProperties properties = new BinanceMarketDataProperties();
    private WebSocketSession session(String id) throws Exception {
        var session = mock(WebSocketSession.class); var open = new AtomicBoolean(true);
        when(session.getId()).thenReturn(id); when(session.isOpen()).thenAnswer(i -> open.get());
        doAnswer(i -> { open.set(false); return null; }).when(session).close(CloseStatus.NORMAL);
        return session;
    }
    private void active(BinanceWebSocketManager manager) {
        await().atMost(Duration.ofSeconds(5)).until(() -> "ACTIVE".equals(manager.lifecyclePhase()));
    }
    @Test void reloadWaitsForCallbackDrainAndConfirmedTransportClosure() throws Exception {
        var first = session("first"); var second = session("second");
        var firstHandler = new AtomicReference<BinanceWebSocketHandler>();
        var calls = new AtomicInteger();
        var closeEntered = new CountDownLatch(1); var allowClose = new CountDownLatch(1);
        doAnswer(i -> { closeEntered.countDown(); assertTrue(allowClose.await(5, TimeUnit.SECONDS)); when(first.isOpen()).thenReturn(false); return null; }).when(first).close(CloseStatus.NORMAL);
        var manager = new BinanceWebSocketManager(properties, () -> "wss://example/stream?streams=btcusdt@kline_1m", new ObjectMapper(), mock(BinanceKlineService.class), (h,u) -> {
            int n=calls.incrementAndGet(); if(n==1) firstHandler.set(h);
            var s=n==1?first:second; h.afterConnectionEstablished(s); return CompletableFuture.completedFuture(s);
        }, null);
        var executor = Executors.newSingleThreadExecutor(); GenerationGate.Permit permit = null;
        try {
            manager.start(); active(manager); permit=firstHandler.get().gate.tryAdmit(); assertNotNull(permit);
            var reload=executor.submit(manager::reload);
            await().atMost(Duration.ofSeconds(5)).until(() -> firstHandler.get().gate.state()==GenerationGate.State.REVOKED);
            assertEquals(1,calls.get()); assertEquals(1,closeEntered.getCount());
            permit.close(); assertTrue(closeEntered.await(5,TimeUnit.SECONDS)); assertEquals(1,calls.get());
            allowClose.countDown(); reload.get(5,TimeUnit.SECONDS); assertEquals(2,calls.get()); active(manager);
        } finally { if(permit!=null)permit.close(); allowClose.countDown(); manager.stop(); executor.shutdownNow(); }
    }
    @Test void establishmentBeforeActorAcceptanceCannotProcessMessages() throws Exception {
        var handler = new BinanceWebSocketHandler(new ObjectMapper(),mock(BinanceKlineService.class),1,h -> {});
        var session = session("pending"); handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session,new TextMessage("{}"));
        assertEquals(1,handler.rejected.get()); assertNull(handler.gate.tryAdmit());
    }
    @Test void duplicateRegistrationRetainsBothSessionsForClosure() throws Exception {
        var h = new BinanceWebSocketHandler(new ObjectMapper(),mock(BinanceKlineService.class),1,x -> {});
        var first=session("a"); var second=session("b");
        h.afterConnectionEstablished(first); h.afterConnectionEstablished(second);
        assertEquals(2,h.sessions.size()); assertTrue(h.failed); h.gate.revoke(); h.close();
        verify(first).close(CloseStatus.NORMAL); verify(second).close(CloseStatus.NORMAL); assertTrue(h.allClosed());
    }
    @Test void shutdownDuringPendingHandshakeRejectsLateEstablishmentAndClosesIt() throws Exception {
        var started=new CountDownLatch(1); var pending=new CompletableFuture<WebSocketSession>();
        var ref=new AtomicReference<BinanceWebSocketHandler>(); var calls=new AtomicInteger();
        var manager=new BinanceWebSocketManager(properties,()->"wss://example",new ObjectMapper(),mock(BinanceKlineService.class),(h,u)->{
            ref.set(h);calls.incrementAndGet();started.countDown();return pending;
        },null);
        var executor=Executors.newSingleThreadExecutor();
        try {
            manager.start();assertTrue(started.await(5,TimeUnit.SECONDS)); var stopping=executor.submit(manager::stop);
            await().atMost(Duration.ofSeconds(5)).until(()->ref.get().gate.state()==GenerationGate.State.REVOKED);
            var late=session("late");ref.get().afterConnectionEstablished(late);pending.complete(late);
            stopping.get(5,TimeUnit.SECONDS);verify(late,atLeastOnce()).close(CloseStatus.NORMAL);
            assertEquals(1,calls.get());assertEquals("STOPPED",manager.lifecyclePhase());assertFalse(ref.get().gate.activate());
        } finally { pending.completeExceptionally(new IllegalStateException("test cleanup"));manager.stop();executor.shutdownNow(); }
    }
    @Test void timedOutHandshakeCannotBeReplacedUntilLateSessionIsClosed() throws Exception {
        var clock = new AtomicLong(1);
        var pending = new CompletableFuture<WebSocketSession>();
        var ref = new AtomicReference<BinanceWebSocketHandler>();
        var calls = new AtomicInteger(); var started = new CountDownLatch(1);
        var manager = new BinanceWebSocketManager(properties, () -> "wss://example", new ObjectMapper(), mock(BinanceKlineService.class), (h,u) -> {
            calls.incrementAndGet(); ref.set(h); started.countDown(); return pending;
        }, null, clock::get);
        try {
            manager.start(); assertTrue(started.await(5,TimeUnit.SECONDS));
            clock.addAndGet(TimeUnit.SECONDS.toNanos(16)); manager.wake();
            await().atMost(Duration.ofSeconds(5)).until(() -> ref.get().gate.state()==GenerationGate.State.REVOKED);
            assertEquals(1,calls.get()); assertEquals("CLOSING",manager.lifecyclePhase());
            // Stop before completing the handshake so this test cannot start another synthetic attempt.
            var stopper = Executors.newSingleThreadExecutor();
            try {
                var stopping = stopper.submit(manager::stop);
                await().atMost(Duration.ofSeconds(5)).until(manager::isStopRequested);
                var late = session("late-timeout"); ref.get().afterConnectionEstablished(late); pending.complete(late);
                stopping.get(5,TimeUnit.SECONDS); verify(late,atLeastOnce()).close(CloseStatus.NORMAL);
                assertEquals(1,calls.get());
            } finally { stopper.shutdownNow(); }
        } finally { pending.completeExceptionally(new IllegalStateException("test cleanup")); manager.stop(); }
    }

    @Test void saturatedReloadMailboxDoesNotLoseDrainOrEstablishmentFacts() throws Exception {
        var armed = new AtomicBoolean(); var entered = new CountDownLatch(1); var resume = new CountDownLatch(1);
        var calls = new AtomicInteger(); var first = session("queue-first"); var second = session("queue-second");
        var manager = new BinanceWebSocketManager(properties, () -> "wss://example", new ObjectMapper(), mock(BinanceKlineService.class), (h,u) -> {
            var selected = calls.incrementAndGet()==1 ? first : second;
            h.afterConnectionEstablished(selected); return CompletableFuture.completedFuture(selected);
        }, null, () -> {
            if (armed.get()) {
                entered.countDown();
                try { if (!resume.await(5,TimeUnit.SECONDS)) throw new AssertionError("barrier timeout"); }
                catch(InterruptedException ex) { throw new AssertionError(ex); }
            }
            return System.nanoTime();
        });
        try {
            manager.start(); active(manager); armed.set(true); manager.wake();
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var requests = new java.util.ArrayList<CompletableFuture<Void>>();
            for(int i=0;i<32;i++)requests.add(manager.requestReload());
            var busy=assertThrows(WebSocketReconnectException.class,manager::requestReload);
            assertEquals(WebSocketReconnectException.Reason.BUSY,busy.getReason());
            armed.set(false);resume.countDown();
            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).get(5,TimeUnit.SECONDS);
            assertEquals(2,calls.get());active(manager);verify(first,atLeastOnce()).close(CloseStatus.NORMAL);
        } finally {armed.set(false);resume.countDown();manager.stop();}
    }

    @Test void businessFailureStillReleasesAdmissionPermit() throws Exception {
        var service=mock(BinanceKlineService.class);
        when(service.processKline(any())).thenThrow(new IllegalStateException("test"));
        var handler=new BinanceWebSocketHandler(new ObjectMapper(),service,1,h -> {});
        var session=session("failure");handler.afterConnectionEstablished(session);handler.gate.activate();
        handler.handleTextMessage(session,new TextMessage("{\"k\":{\"x\":true}}"));
        handler.gate.revoke(); assertTrue(handler.gate.isDrained()); assertEquals(0,handler.gate.inFlight());
    }
    @Test void disabledWebsocketNeverConnects() {
        properties.getWebsocket().setEnabled(false);var calls=new AtomicInteger();
        var manager=new BinanceWebSocketManager(properties,()->"unused",new ObjectMapper(),mock(BinanceKlineService.class),(h,u)->{calls.incrementAndGet();return new CompletableFuture<>();},null);
        manager.start();assertEquals(0,calls.get());assertThrows(WebSocketReconnectException.class,manager::reload);manager.stop();
    }
}
