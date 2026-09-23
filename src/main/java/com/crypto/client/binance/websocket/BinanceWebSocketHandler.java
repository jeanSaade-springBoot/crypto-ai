package com.crypto.client.binance.websocket;

import com.crypto.service.BinanceKlineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** FIX-131: callbacks publish persistent facts; they never wait for the lifecycle actor. */
public class BinanceWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketHandler.class);
    final GenerationGate gate = new GenerationGate();
    final Map<WebSocketSession, Boolean> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final BinanceKlineService service;
    final long generation;
    volatile WebSocketSession primary;
    volatile boolean failed;
    volatile long lastReceiptNanos = System.nanoTime();
    final AtomicLong rejected = new AtomicLong();
    private final java.util.function.Consumer<BinanceWebSocketHandler> wake;
    public BinanceWebSocketHandler(ObjectMapper mapper, BinanceKlineService service) {
        this(mapper, service, 0, h -> {});
    }
    BinanceWebSocketHandler(ObjectMapper mapper, BinanceKlineService service, long generation, java.util.function.Consumer<BinanceWebSocketHandler> wake) {
        this.mapper = mapper; this.service = service; this.generation = generation; this.wake = wake;
    }
    @Override public void afterConnectionEstablished(WebSocketSession session) {
        synchronized (this) {
            sessions.putIfAbsent(session, false);
            if (primary == null) primary = session;
            else if (primary != session) failed = true; // Revoke the whole attempt; never lose a socket reference.
        }
        log.info("[FIX-131][WS-LIFECYCLE][ESTABLISHED] generation={}, session={}", generation, session.getId());
        wake.accept(this);
    }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Instant received = Instant.now();
        long started = System.nanoTime();
        lastReceiptNanos = started;
        GenerationGate.Permit permit = session == primary ? gate.tryAdmit() : null;
        long admissionMicros = (System.nanoTime() - started) / 1000;
        if (permit == null) {
            long count = rejected.incrementAndGet();
            if (count == 1 || count % 100 == 0) log.warn("[FIX-131][MESSAGE_REJECTED] generation={}, state={}, count={}", generation, gate.state(), count);
            return;
        }
        try (permit) {
            var root = mapper.readTree(message.getPayload());
            var data = root.has("data") ? root.path("data") : root;
            var k = data.path("k");
            long waitMicros = (System.nanoTime() - started) / 1000;
            try (var receipt = KlineReceipt.begin(generation, received, started, k.path("x").asBoolean())) {
                if (k.path("x").asBoolean() || waitMicros >= 100_000)
                    log.info("[FIX-131][CANDLE_RECEIVED] correlation={}, generation={}, symbol={}, interval={}, candleOpenTime={}, binanceEventMillis={}, receivedAt={}, admissionWaitMicros={}, admissionAndParseMicros={}",
                            receipt.correlationId(), generation, k.path("s").asText(), k.path("i").asText(), k.path("t").asLong(), data.path("E").asLong(), received, admissionMicros, waitMicros);
                service.processKline(root); // Preserve input -> protection -> observer -> dispatch ordering.
            }
        } catch (Exception ex) {
            log.error("[FIX-131][CALLBACK_FAILED] generation={}, receivedAt={}", generation, received, ex);
        } finally { if (gate.state() == GenerationGate.State.REVOKED) wake.accept(this); }
    }
    @Override public void handleTransportError(WebSocketSession session, Throwable ex) {
        failed = true;
        log.error("[FIX-131][WS-LIFECYCLE][TRANSPORT_ERROR] generation={}, session={}", generation, session.getId(), ex);
        wake.accept(this);
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.put(session, true);
        if (session == primary) failed = true;
        log.warn("[FIX-131][WS-LIFECYCLE][CLOSED] generation={}, session={}, code={}, reason={}", generation, session.getId(), status.getCode(), status.getReason());
        wake.accept(this);
    }
    public boolean isConnected() { return primary != null && primary.isOpen() && !failed; }
    boolean allClosed() { return sessions.values().stream().allMatch(Boolean::booleanValue); }
    /** Runs only on the bounded close worker. Completion is a persistent fact, not a mailbox event. */
    public void close() {
        gate.revoke();
        if (!gate.isDrained()) return;
        for (var entry : sessions.entrySet()) {
            if (entry.getValue()) continue;
            try {
                entry.getKey().close(CloseStatus.NORMAL);
                if (!entry.getKey().isOpen()) sessions.put(entry.getKey(), true);
            } catch (Exception ex) {
                log.warn("[FIX-131][WS-LIFECYCLE][CLOSE_FAILED] generation={}, session={}", generation, entry.getKey().getId(), ex);
            }
        }
    }
}
