package com.crypto.regression.service;

import com.crypto.domain.Candle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FIX-11H REPLAY/PARITY INFRASTRUCTURE ONLY.
 *
 * Golden Rule: Replay = Production business logic. This immutable run-local dataset changes
 * only how historical candle/lineage inputs are obtained. It never changes their meaning,
 * filtering, ordering, limits, indicator math, thresholds, or execution rules.
 */
public final class ReplayDataset {
    public record CandleKey(String symbol, String interval) {}
    public record SignalKey(String symbol, String interval, Instant candleOpenTime) {}

    private final Map<CandleKey, List<Candle>> candlesByKey;
    private final Map<SignalKey, Long> lineage;

    ReplayDataset(Map<CandleKey, List<Candle>> candlesByKey, Map<SignalKey, Long> lineage) {
        Map<CandleKey, List<Candle>> copy = new HashMap<>();
        candlesByKey.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        this.candlesByKey = Map.copyOf(copy);
        this.lineage = Map.copyOf(lineage);
    }

    /** Exact cached equivalent of findClosedCandlesAtOrBefore: <= boundary, DESC, LIMIT. */
    public List<Candle> closedCandlesAtOrBefore(String symbol, String interval, Instant maxOpenTime, int limit) {
        List<Candle> ascending = candlesByKey.getOrDefault(new CandleKey(symbol, interval), List.of());
        int low = 0, high = ascending.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (!ascending.get(mid).getOpenTime().isAfter(maxOpenTime)) low = mid + 1;
            else high = mid;
        }
        int endExclusive = low;
        int startInclusive = Math.max(0, endExclusive - Math.max(0, limit));
        List<Candle> result = new ArrayList<>(endExclusive - startInclusive);
        for (int i = endExclusive - 1; i >= startInclusive; i--) result.add(ascending.get(i));
        return result;
    }

    /** FIX-112D exact identity only. No nearest-time fallback. */
    public Long sourceSignalId(String symbol, String interval, Instant candleOpenTime) {
        return lineage.get(new SignalKey(symbol, interval, candleOpenTime));
    }
}
