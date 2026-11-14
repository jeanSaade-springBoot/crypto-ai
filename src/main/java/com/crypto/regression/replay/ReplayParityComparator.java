package com.crypto.regression.replay;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** FIX-156 Replay-owned comparison of versioned decision or lifecycle snapshots. */
public final class ReplayParityComparator {
    public Result compare(String comparisonKey, Map<String, ?> expected, Map<String, ?> actual) {
        Map<String, ?> left = expected == null ? Map.of() : expected;
        Map<String, ?> right = actual == null ? Map.of() : actual;
        for (String key : orderedKeys(left, right)) {
            if (!Objects.equals(left.get(key), right.get(key))) {
                return new Result(false, comparisonKey, key, left.get(key), right.get(key));
            }
        }
        return new Result(true, comparisonKey, null, null, null);
    }

    private Iterable<String> orderedKeys(Map<String, ?> left, Map<String, ?> right) {
        Map<String, Boolean> keys = new LinkedHashMap<>();
        left.keySet().forEach(key -> keys.put(key, Boolean.TRUE));
        right.keySet().forEach(key -> keys.put(key, Boolean.TRUE));
        return keys.keySet();
    }

    public record Result(boolean matched, String comparisonKey, String firstDivergenceField,
                         Object expectedValue, Object actualValue) { }
}
