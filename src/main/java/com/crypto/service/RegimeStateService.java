package com.crypto.service;

import com.crypto.domain.MarketRegime;
import com.crypto.dto.MarketRegimeAssessment;
import com.crypto.execution.service.ExecutionReplayScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FIX-091 / Fix 4 + FIX-109: Experimental-Replay-only regime persistence state machine.
 * Production behavior is intentionally untouched until the parity gate is passed.
 * State is keyed by replay run + symbol + interval and advances only on candle timestamps.
 */
@Service
public class RegimeStateService {
    private final ExecutionReplayScope replayScope;
    private final int requiredCandles;
    // Run id is part of the key so concurrent Replay/Test workers cannot reset or contaminate
    // one another. The map contains only Replay state; Production never reads it.
    private final Map<String, RegimeState> replayState = new HashMap<>();

    public RegimeStateService(ExecutionReplayScope replayScope,
                              @Value("${analysis.regime.persistence-candles:3}") int requiredCandles) {
        this.replayScope = replayScope;
        this.requiredCandles = Math.max(2, requiredCandles);
    }

    public synchronized Decision apply(String symbol, String interval, Instant candleTime,
                                       MarketRegimeAssessment detectedAssessment) {
        if (detectedAssessment == null || !replayScope.active() || !replayScope.experimental()) {
            MarketRegime regime = detectedAssessment == null ? MarketRegime.UNKNOWN : detectedAssessment.regime();
            return new Decision(detectedAssessment, regime, null, regime, 0, false);
        }

        long runId = replayScope.runId();
        String key = runId + "|" + symbol + "|" + interval;
        RegimeState state = replayState.get(key);
        MarketRegime detected = detectedAssessment.regime();
        if (state == null) {
            state = new RegimeState(detected, null, 0, candleTime);
            replayState.put(key, state);
            return new Decision(detectedAssessment, detected, null, detected, 0, false);
        }

        MarketRegime confirmed = state.confirmed;
        MarketRegime candidate = state.candidate;
        int count = state.candidateCount;
        boolean promoted = false;

        if (detected == confirmed) {
            candidate = null;
            count = 0;
        } else if (detected == candidate) {
            count++;
        } else {
            candidate = detected;
            count = 1;
        }

        if (candidate != null && count >= requiredCandles) {
            confirmed = candidate;
            candidate = null;
            count = 0;
            promoted = true;
        }
        replayState.put(key, new RegimeState(confirmed, candidate, count, candleTime));

        MarketRegimeAssessment effective = detected == confirmed
                ? detectedAssessment
                : new MarketRegimeAssessment(confirmed, detectedAssessment.confidence(),
                    append(detectedAssessment.evidence(), "FIX-091 Replay persistence: detected=" + detected
                            + ", candidate=" + candidate + ", confirmed=" + confirmed
                            + ", candidateCount=" + count + "/" + requiredCandles + "."));
        return new Decision(effective, detected, candidate, confirmed, count, promoted);
    }

    private List<String> append(List<String> evidence, String value) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>(evidence == null ? List.of() : evidence);
        result.add(value);
        return List.copyOf(result);
    }

    private record RegimeState(MarketRegime confirmed, MarketRegime candidate, int candidateCount, Instant lastCandleTime) {}

    public record Decision(MarketRegimeAssessment effectiveAssessment,
                           MarketRegime detectedRegime,
                           MarketRegime candidateRegime,
                           MarketRegime confirmedRegime,
                           int candidateCount,
                           boolean promoted) {}
}
