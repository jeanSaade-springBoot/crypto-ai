package com.crypto.client.binance;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * Process-wide REST cooldown for Binance rate-limit responses. This is an
 * admission check only: it never locks, queues, or fences trading/protection.
 * Callers receive a retryable exception and keep their own bounded retry policy.
 */
@Component
public class BinanceRestAdmissionGate {
    private static final long DEFAULT_429_MS = 60_000L;
    private static final long DEFAULT_418_MS = 15 * 60_000L;
    private final AtomicLong blockedUntilNanos = new AtomicLong();
    private final LongSupplier nanoTime;

    public BinanceRestAdmissionGate() { this(System::nanoTime); }
    BinanceRestAdmissionGate(LongSupplier nanoTime) { this.nanoTime = nanoTime; }

    public void admit() {
        long remaining = blockedUntilNanos.get() - nanoTime.getAsLong();
        if (remaining > 0) throw new BinanceRestAdmissionDeferredException(Duration.ofNanos(remaining));
    }

    public void recordFailure(RuntimeException failure) {
        if (!(failure instanceof RestClientResponseException response)) return;
        int status = response.getStatusCode().value();
        if (status != 429 && status != 418) return;
        long millis = retryAfterMillis(response.getResponseHeaders(), status == 418 ? DEFAULT_418_MS : DEFAULT_429_MS);
        blockFor(Duration.ofMillis(millis));
    }

    void blockFor(Duration delay) {
        long nanos = Math.max(0L, delay.toNanos());
        long candidate = saturatingAdd(nanoTime.getAsLong(), nanos);
        blockedUntilNanos.accumulateAndGet(candidate, Math::max);
    }

    private static long retryAfterMillis(HttpHeaders headers, long fallback) {
        if (headers == null) return fallback;
        String raw = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (raw == null || raw.isBlank()) return fallback;
        try { return Math.max(1_000L, Long.parseLong(raw.trim()) * 1_000L); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static long saturatingAdd(long a, long b) { return b > 0 && a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }

    public static final class BinanceRestAdmissionDeferredException extends RuntimeException {
        private final Duration retryAfter;
        BinanceRestAdmissionDeferredException(Duration retryAfter) {
            super("Binance REST admission is paused for " + retryAfter.toMillis() + " ms");
            this.retryAfter = retryAfter;
        }
        public Duration retryAfter() { return retryAfter; }
    }
}
