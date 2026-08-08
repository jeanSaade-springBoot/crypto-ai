package com.crypto.execution.controller;

import com.crypto.execution.domain.ExecutionOpportunity;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/execution-intelligence")
@RequiredArgsConstructor
public class ExecutionIntelligenceController {

    private final ExecutionOpportunityRepository repository;

    @GetMapping("/opportunities")
    public List<OpportunityView> opportunities() {
        return repository.findTop50ByOrderByUpdatedAtDesc().stream()
                .map(OpportunityView::from)
                .toList();
    }

    @GetMapping("/opportunities/active")
    public List<OpportunityView> activeOpportunities() {
        return repository.findTop50ByStatusInOrderByUpdatedAtDesc(
                        List.of("BUILDING", "WEAKENING", "BLOCKED", "CONFIRMED"))
                .stream()
                .map(OpportunityView::from)
                .toList();
    }

    public record OpportunityView(
            Long id,
            String symbol,
            String direction,
            String status,
            Instant startedAt,
            Instant lastEvidenceAt,
            Long latestSignalId,
            int evidenceCount,
            int buyCount,
            int watchCount,
            int neutralCount,
            int evidenceScore,
            int averageSignalScore,
            int averageConfidence,
            String fiveMinuteDecision,
            String oneHourDecision,
            String executionSource,
            int recommendedPositionPercent,
            String decisionCode,
            String decisionExplanation,
            Instant executedAt
    ) {
        static OpportunityView from(ExecutionOpportunity value) {
            return new OpportunityView(
                    value.getId(),
                    value.getSymbol(),
                    value.getDirection(),
                    value.getStatus(),
                    value.getStartedAt(),
                    value.getLastEvidenceAt(),
                    value.getLatestSignal() == null ? null : value.getLatestSignal().getId(),
                    value.getEvidenceCount(),
                    value.getBuyCount(),
                    value.getWatchCount(),
                    value.getNeutralCount(),
                    value.getEvidenceScore(),
                    value.getAverageSignalScore(),
                    value.getAverageConfidence(),
                    value.getFiveMinuteDecision(),
                    value.getOneHourDecision(),
                    value.getExecutionSource(),
                    value.getRecommendedPositionPercent(),
                    value.getDecisionCode(),
                    value.getDecisionExplanation(),
                    value.getExecutedAt()
            );
        }
    }
}
