package com.crypto.indicator.event;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FIX-074 coordinates live candle-close analysis with FIX-043 recovery for the
 * same symbol + interval + candle open time.
 *
 * A bounded striped-lock array avoids an ever-growing map of historical candle keys.
 * Different candles normally use different stripes and continue to run concurrently;
 * an occasional hash collision can only delay work, never change a trading decision.
 */
@Component
public class CandleAnalysisExecutionCoordinator {

    private static final int STRIPE_COUNT = 1024;
    private final ReentrantLock[] stripes = new ReentrantLock[STRIPE_COUNT];

    public CandleAnalysisExecutionCoordinator() {
        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new ReentrantLock();
        }
    }

    public LockHandle lock(String symbol, String interval, Instant openTime) {
        ReentrantLock lock = stripes[stripeIndex(symbol, interval, openTime)];
        lock.lock();
        return lock::unlock;
    }

    private int stripeIndex(String symbol, String interval, Instant openTime) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        String normalizedInterval = interval == null ? "" : interval.trim();
        int hash = 17;
        hash = 31 * hash + normalizedSymbol.hashCode();
        hash = 31 * hash + normalizedInterval.hashCode();
        hash = 31 * hash + (openTime == null ? 0 : openTime.hashCode());
        return Math.floorMod(hash, STRIPE_COUNT);
    }

    @FunctionalInterface
    public interface LockHandle extends AutoCloseable {
        @Override
        void close();
    }
}
