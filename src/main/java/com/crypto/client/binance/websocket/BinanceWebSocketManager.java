package com.crypto.client.binance.websocket;

import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.service.BinanceKlineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

/** FIX-131: one owner for startup/reload/health. The actor never does database or socket I/O.
 * A timed-out, unresolved handshake is retained: cancellation alone cannot prove no late socket.
 * Availability is deliberately sacrificed until old work AND transport have terminated. */
@Component
public class BinanceWebSocketManager {
    @org.springframework.beans.factory.annotation.Value("#{'${shared-market.mode:OFF}' == 'LIVE'}")
    private boolean sharedMarketEnabled;

    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketManager.class);
    static final long SCAN_NANOS = TimeUnit.MILLISECONDS.toNanos(500);
    static final long DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(15);
    interface Connector { CompletableFuture<WebSocketSession> connect(BinanceWebSocketHandler handler, String url); }
    private final BinanceMarketDataProperties properties;
    private final Supplier<String> urls;
    private final ObjectMapper mapper;
    private final BinanceKlineService service;
    private final Connector connector;
    private final CandleGapDiagnostics gaps;
    private final java.util.function.LongSupplier nanos;
    private final ArrayBlockingQueue<CompletableFuture<Void>> requests = new ArrayBlockingQueue<>(32);
    private final ThreadPoolExecutor connectWorker = worker("fix131-connect", 1);
    private final ThreadPoolExecutor closeWorker = worker("fix131-close", 1);
    private final Object lifecycle = new Object();
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final List<CompletableFuture<Void>> waiting = new ArrayList<>();
    private final java.util.Set<BinanceWebSocketHandler> cleanup = ConcurrentHashMap.newKeySet();
    private final java.util.Set<BinanceWebSocketHandler> cleaning = ConcurrentHashMap.newKeySet();
    private volatile boolean stopRequested;
    private volatile Thread actor;
    private volatile String phase = "STOPPED";
    private volatile Attempt current;
    private long sequence;
    private long interruptionStart;
    private Instant revokedAt;
    private long nextAttempt;
    private boolean replacement;
    private static final class Attempt {
        final BinanceWebSocketHandler handler;
        final long started;
        volatile boolean settled;
        volatile Throwable failure;
        volatile String url;
        volatile boolean closing;
        long nextClose;
        long revoked;
        boolean timeoutLogged;
        long nextSilenceLog;
        Attempt(BinanceWebSocketHandler handler, long started) { this.handler = handler; this.started = started; }
    }
    @Autowired
    public BinanceWebSocketManager(BinanceMarketDataProperties properties, BinanceStreamUrlBuilder builder,
            ObjectMapper mapper, BinanceKlineService service, CandleGapDiagnostics gaps) {
        this(properties, builder::build, mapper, service,
                (handler, url) -> new StandardWebSocketClient().execute(handler, null, URI.create(url)), gaps);
    }
    BinanceWebSocketManager(BinanceMarketDataProperties properties, Supplier<String> urls,
            ObjectMapper mapper, BinanceKlineService service, Connector connector, CandleGapDiagnostics gaps) {
        this(properties, urls, mapper, service, connector, gaps, System::nanoTime);
    }
    BinanceWebSocketManager(BinanceMarketDataProperties properties, Supplier<String> urls,
            ObjectMapper mapper, BinanceKlineService service, Connector connector, CandleGapDiagnostics gaps,
            java.util.function.LongSupplier nanos) {
        this.nanos = nanos;
        this.properties = properties; this.urls = urls; this.mapper = mapper; this.service = service;
        this.connector = connector; this.gaps = gaps;
    }
    private static ThreadPoolExecutor worker(String name, int queue) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queue), task -> {
            Thread t = new Thread(task, name); t.setDaemon(true); return t;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (sharedMarketEnabled) { return; }
        synchronized (lifecycle) {
            if (actor != null || stopRequested || !properties.getWebsocket().isEnabled()) return;
            replacement = true;
            actor = new Thread(this::run, "fix131-ws-owner");
            actor.setDaemon(true);
            actor.start();
        }
    }
    void wake() { LockSupport.unpark(actor); }
    private void notice(BinanceWebSocketHandler handler) {
        if (handler.gate.state() == GenerationGate.State.REVOKED && !handler.allClosed()) {
            if (current == null || current.handler != handler) cleanup.add(handler);
            // After bounded shutdown, even the unresolved CURRENT handshake retains a cleanup path.
            // This fallback runs only during shutdown, never on a live processing callback.
            if (closeWorker.isShutdown() && handler.gate.isDrained()) handler.close();
        }
        wake();
    }
    CompletableFuture<Void> requestReload() {
        if (!properties.getWebsocket().isEnabled()) throw failure(WebSocketReconnectException.Reason.CONNECTION_FAILED);
        var request = new CompletableFuture<Void>();
        synchronized (lifecycle) {
            if (stopRequested) throw failure(WebSocketReconnectException.Reason.STOPPED);
            if (!requests.offer(request)) throw failure(WebSocketReconnectException.Reason.BUSY);
        }
        start(); wake();
        return request;
    }
    public void reload() {
        if (sharedMarketEnabled) { return; }
        var request = requestReload();
        try { request.get(30, TimeUnit.SECONDS); }
        catch (TimeoutException ex) { throw failure(WebSocketReconnectException.Reason.WAIT_TIMEOUT); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw failure(WebSocketReconnectException.Reason.WAIT_TIMEOUT); }
        catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
            throw failure(WebSocketReconnectException.Reason.CONNECTION_FAILED);
        }
    }
    private WebSocketReconnectException failure(WebSocketReconnectException.Reason reason) {
        return new WebSocketReconnectException(reason, phase);
    }
    private void run() {
        try {
            while (true) {
                // Persistent lifecycle facts are examined on EVERY pass, before more queued requests.
                tick();
                if (stopRequested && current == null && cleanup.isEmpty()) break;
                LockSupport.parkNanos(SCAN_NANOS);
            }
            phase = "STOPPED";
            finish(WebSocketReconnectException.Reason.STOPPED);
            stopped.complete(null);
        } catch (Throwable ex) {
            log.error("[FIX-131][WS-LIFECYCLE][OWNER_FAILED] phase={}; restart required", phase, ex);
            synchronized (lifecycle) { stopRequested = true; if (current != null) current.handler.gate.revoke(); }
            finish(WebSocketReconnectException.Reason.STOPPED);
            stopped.completeExceptionally(ex);
        }
    }
    private void tick() {
        long now = nanos.getAsLong();
        for (var h : cleanup) {
            if (h.allClosed()) { cleanup.remove(h); continue; }
            if (h.gate.isDrained() && cleaning.add(h)) {
                try { closeWorker.execute(() -> {
                    try { h.close(); } finally { cleaning.remove(h); wake(); }
                }); } catch (RejectedExecutionException ex) { cleaning.remove(h); }
            }
        }
        // Bound callers retained off-mailbox as well as mailbox capacity.
        CompletableFuture<Void> request;
        for (int n = 0; n < 32 && (request = requests.poll()) != null; n++) {
            if (waiting.size() >= 32) request.completeExceptionally(failure(WebSocketReconnectException.Reason.BUSY));
            else { waiting.add(request); replacement = true; }
        }
        if (current != null) {
            var a = current;
            var h = a.handler;
            if (a.failure != null && h.gate.state() != GenerationGate.State.REVOKED) {
                log.error("[FIX-131][WS-LIFECYCLE][CONNECTION_FAILED] generation={}", h.generation, a.failure);
                finish(WebSocketReconnectException.Reason.CONNECTION_FAILED);
            }
            if (stopRequested || replacement || h.failed || a.failure != null
                    || (h.gate.state() == GenerationGate.State.ADMITTING && !h.isConnected())) revoke(a, now);
            if (h.gate.state() == GenerationGate.State.PENDING) {
                if (now - a.started >= DEADLINE_NANOS) {
                    log.error("[FIX-131][WS-LIFECYCLE][CONNECTION_TIMEOUT] generation={}; awaiting terminal handshake/closure", h.generation);
                    revoke(a, now);
                    finish(WebSocketReconnectException.Reason.CONNECTION_FAILED);
                } else if (a.settled && h.isConnected()) {
                    synchronized (lifecycle) {
                        if (!stopRequested && h.gate.activate()) {
                            phase = "ACTIVE";
                            h.lastReceiptNanos = System.nanoTime();
                            log.info("[FIX-131][WS-LIFECYCLE][ADMISSION_ENABLED] generation={}, admittedAt={}, revokedAt={}, interruptionMs={}",
                                    h.generation, Instant.now(), revokedAt, interruptionStart == 0 ? 0 : TimeUnit.NANOSECONDS.toMillis(now - interruptionStart));
                            interruptionStart = 0;
                            try { if (gaps != null) gaps.subscribed(a.url, Instant.now()); }
                            catch (RuntimeException ex) { log.warn("[FIX-131][SUBSCRIPTION_DIAGNOSTIC_FAILED] generation={}", h.generation, ex); }
                            finish(null);
                        }
                    }
                }
            }
            if (h.gate.state() == GenerationGate.State.ADMITTING && now >= a.nextSilenceLog && System.nanoTime() - h.lastReceiptNanos > TimeUnit.SECONDS.toNanos(30)) {
                a.nextSilenceLog = now + TimeUnit.SECONDS.toNanos(30);
                log.warn("[FIX-131][WS-LIFECYCLE][NO_MESSAGES] generation={}, silenceMs={}; observe only, no forced reconnect", h.generation, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - h.lastReceiptNanos));
            }
            if (h.gate.state() == GenerationGate.State.REVOKED) {
                phase = h.gate.isDrained() ? "CLOSING" : "DRAINING";
                if (now - a.revoked >= DEADLINE_NANOS && !a.timeoutLogged) {
                    a.timeoutLogged = true;
                    var reason = h.gate.isDrained() ? WebSocketReconnectException.Reason.CLOSE_TIMEOUT : WebSocketReconnectException.Reason.DRAIN_TIMEOUT;
                    log.error("[FIX-131][WS-LIFECYCLE][{}] generation={}, inFlight={}; replacement remains blocked", reason, h.generation, h.gate.inFlight());
                    finish(reason);
                }
                if (h.gate.isDrained()) {
                    if (!h.allClosed() && !a.closing && now >= a.nextClose) {
                        a.closing = true;
                        try { closeWorker.execute(() -> { try { h.close(); } finally { a.closing = false; wake(); } }); }
                        catch (RejectedExecutionException ex) { a.closing = false; log.error("[FIX-131][CLOSE_WORKER_REJECTED] generation={}", h.generation); }
                        a.nextClose = now + TimeUnit.SECONDS.toNanos(5);
                    }
                    if (a.settled && h.allClosed() && !a.closing) {
                        log.info("[FIX-131][WS-LIFECYCLE][RETIRED] generation={}, drainedAt={}, rejectedMessages={}", h.generation, h.gate.drainedAt(), h.rejected.get());
                        current = null;
                        replacement = !stopRequested;
                        nextAttempt = now + (a.failure == null ? 0 : TimeUnit.SECONDS.toNanos(Math.max(1, properties.getWebsocket().getHealthCheckSeconds())));
                    }
                }
            }
        }
        synchronized (lifecycle) {
            if (!stopRequested && current == null && cleanup.isEmpty() && replacement && now >= nextAttempt) launch();
        }
    }
    private void revoke(Attempt a, long now) {
        if (a.handler.gate.state() == GenerationGate.State.REVOKED) return;
        a.handler.gate.revoke();
        a.revoked = now;
        if (interruptionStart == 0) { interruptionStart = now; revokedAt = Instant.now(); }
        log.warn("[FIX-131][WS-LIFECYCLE][REVOKED] generation={}, inFlight={}", a.handler.generation, a.handler.gate.inFlight());
    }
    private void launch() {
        replacement = false;
        phase = "PENDING";
        Attempt a = new Attempt(new BinanceWebSocketHandler(mapper, service, ++sequence, this::notice), nanos.getAsLong());
        current = a;
        log.info("[FIX-131][WS-LIFECYCLE][CONNECTING] generation={}", sequence);
        try {
            connectWorker.execute(() -> {
                try {
                    a.url = urls.get(); // DB-backed subscription snapshot is never loaded by actor.
                    synchronized (lifecycle) {
                        if (stopRequested || a.handler.gate.state() == GenerationGate.State.REVOKED) {
                            a.settled = true; wake(); return;
                        }
                        // execute returns a future; no waiting under the lifecycle lock.
                        connector.connect(a.handler, a.url).whenComplete((session, ex) -> {
                            if (session != null && a.handler.primary != session) a.handler.afterConnectionEstablished(session);
                            a.failure = ex; a.settled = true; wake();
                        });
                    }
                } catch (Throwable ex) { a.failure = ex; a.settled = true; wake(); }
            });
        } catch (RejectedExecutionException ex) { a.failure = ex; a.settled = true; }
    }
    private void finish(WebSocketReconnectException.Reason reason) {
        for (var f : waiting) { if (reason == null) f.complete(null); else f.completeExceptionally(failure(reason)); }
        waiting.clear();
    }
    boolean isStopRequested() { return stopRequested; }
    public String lifecyclePhase() { return phase; }
    @PreDestroy public void stop() {
        synchronized (lifecycle) { stopRequested = true; }
        wake();
        if (actor != null) try { stopped.get(20, TimeUnit.SECONDS); }
        catch (Exception ex) { log.error("[FIX-131][WS-LIFECYCLE][SHUTDOWN_INCOMPLETE] phase={}; cleanup not confirmed", phase); }
        connectWorker.shutdown(); closeWorker.shutdown();
    }
}
