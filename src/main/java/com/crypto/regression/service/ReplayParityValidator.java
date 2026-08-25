package com.crypto.regression.service;

import com.crypto.domain.TradeSignal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * FIX-091 / Fix 6: field-level pre-wallet parity comparator.
 * It deliberately compares trading-intelligence state only; wallet execution is outside scope.
 */
@Service
public class ReplayParityValidator {

    public Result compare(TradeSignal production, TradeSignal replay) {
        List<String> mismatches = new ArrayList<>();
        if (production == null || replay == null) {
            mismatches.add("Production and Replay signals must both exist");
            return new Result(false, mismatches);
        }

        same(mismatches, "detectedRegime", production.getDetectedRegime(), replay.getDetectedRegime());
        same(mismatches, "candidateRegime", production.getCandidateRegime(), replay.getCandidateRegime());
        same(mismatches, "confirmedRegime", production.getConfirmedRegime(), replay.getConfirmedRegime());
        same(mismatches, "selectedStrategy", production.getSelectedStrategy(), replay.getSelectedStrategy());
        same(mismatches, "originalDecision", production.getOriginalDecision(), replay.getOriginalDecision());
        same(mismatches, "finalDecision", production.getDecision(), replay.getDecision());
        same(mismatches, "rawConfidence", production.getRawConfidenceScore(), replay.getRawConfidenceScore());
        same(mismatches, "effectiveConfidence", production.getEffectiveConfidenceScore(), replay.getEffectiveConfidenceScore());
        same(mismatches, "primaryBlockingStage", production.getPrimaryBlockingStage(), replay.getPrimaryBlockingStage());
        same(mismatches, "btcContextStatus", production.getBtcContextStatus(), replay.getBtcContextStatus());
        same(mismatches, "liquidityStatus", production.getLiquidityStatus(), replay.getLiquidityStatus());
        same(mismatches, "entryAuthority", production.getEntryAuthority(), replay.getEntryAuthority());
        same(mismatches, "entryAuthorityMaxPositionPercent",
                production.getEntryAuthorityMaxPositionPercent(), replay.getEntryAuthorityMaxPositionPercent());
        return new Result(mismatches.isEmpty(), List.copyOf(mismatches));
    }

    private void same(List<String> mismatches, String field, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            mismatches.add(field + ": Production=" + expected + ", Replay=" + actual);
        }
    }

    public record Result(boolean matches, List<String> mismatches) {}
}
